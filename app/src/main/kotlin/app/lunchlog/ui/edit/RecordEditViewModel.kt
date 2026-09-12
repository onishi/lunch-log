package app.lunchlog.ui.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lunchlog.core.id.Ulid
import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.model.MealTypeInference
import app.lunchlog.core.model.Photo
import app.lunchlog.core.model.PhotoKind
import app.lunchlog.core.ocr.MenuTextParser
import app.lunchlog.core.record.EatenAtResolver
import app.lunchlog.core.restaurant.RestaurantSuggestion
import app.lunchlog.core.suggest.DishSuggestions
import app.lunchlog.core.tabelog.TabelogUrl
import app.lunchlog.core.validation.RecordError
import app.lunchlog.core.validation.ValidationResult
import app.lunchlog.data.RecordRepository
import app.lunchlog.data.ocr.MenuOcr
import app.lunchlog.location.CurrentLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class RecordEditState(
    val id: String = Ulid.generate(),
    val eatenAt: Instant = Instant.now(),
    val mealType: MealType = MealType.LUNCH,
    val dishName: String = "",
    val restaurantName: String = "",
    val photos: List<Photo> = emptyList(),
    val price: String = "",
    val rating: Int? = null,
    val memo: String = "",
    val tags: List<String> = emptyList(),
    val tabelogUrl: String = "",
    val tabelogWarning: TabelogUrl.Warning? = null,
    val suggestions: List<RestaurantSuggestion> = emptyList(),
    val dishSuggestions: List<String> = emptyList(),
    val menuCandidates: List<MenuTextParser.MenuCandidate> = emptyList(),
    val loadingSuggestions: Boolean = false,
    val saving: Boolean = false,
    val errors: List<RecordError> = emptyList(),
    val isNew: Boolean = true,
    /** 位置情報の使い道を説明する必要があるか (まだ許可も拒否もしていない)。 */
    val needsLocationRationale: Boolean = false,
    /** 既存の記録を編集しているときの作成時刻。新規なら null。 */
    val createdAt: Instant? = null,
)

/**
 * 記録の作成・編集 (SPEC F-101, F-103, F-104, F-106, F-116)。
 *
 * 入力を減らすのがこの画面の役目 (SPEC §1.2)。日時・食事種別・店名は
 * 既定値が入った状態で開き、利用者は確認するだけで保存できる。
 */
class RecordEditViewModel(
    private val repository: RecordRepository,
    private val currentLocation: CurrentLocation,
    private val menuOcr: MenuOcr,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordEditState())
    val state: StateFlow<RecordEditState> = _state.asStateFlow()

    private var location: app.lunchlog.core.model.GeoLocation? = null

    /** 新規作成として開く。位置情報と店舗候補の取得を先に走らせる。 */
    fun startNew() {
        val now = Instant.now()
        _state.value = RecordEditState(
            eatenAt = now,
            mealType = MealTypeInference.infer(now, ZoneId.systemDefault()),
            // 権限がまだなら、OS のダイアログの前に使い道を説明する (SPEC §10)。
            needsLocationRationale = !currentLocation.hasPermission(),
        )
        loadSuggestions()
    }

    /** 位置情報の説明を閉じる。許可・拒否のどちらでも候補は読み直す。 */
    fun onLocationPermissionResult(granted: Boolean) {
        _state.update { it.copy(needsLocationRationale = false) }
        if (granted) loadSuggestions()
    }

    fun dismissLocationRationale() {
        _state.update { it.copy(needsLocationRationale = false) }
    }

    fun load(id: String) {
        viewModelScope.launch {
            val record = repository.find(id) ?: return@launch
            location = record.location
            _state.value = RecordEditState(
                id = record.id,
                eatenAt = record.eatenAt,
                mealType = record.mealType,
                dishName = record.dishName.orEmpty(),
                restaurantName = record.restaurantName.orEmpty(),
                photos = record.photos,
                price = record.price?.toString().orEmpty(),
                rating = record.rating,
                memo = record.memo.orEmpty(),
                tags = record.tags,
                tabelogUrl = record.tabelogUrl.orEmpty(),
                isNew = false,
                createdAt = record.createdAt,
            )
        }
    }

    fun addPhoto(uri: Uri, kind: PhotoKind = PhotoKind.DISH) {
        viewModelScope.launch {
            val prepared = repository.photoStore().prepare(uri, kind, _state.value.photos.size)

            // メニュー写真なら読み取って候補を出す (SPEC §6.2, F-110)。
            if (kind == PhotoKind.MENU) {
                val candidates = menuOcr.candidates(uri)
                _state.update { it.copy(menuCandidates = candidates) }
            }
            _state.update { current ->
                val photos = current.photos + prepared.photo
                // 撮影日時が取れたら記録の日時に反映する (SPEC F-106)。
                val eatenAt = EatenAtResolver.resolveFromPhotos(photos.map { it.takenAt }, Instant.now())
                current.copy(
                    photos = photos,
                    eatenAt = eatenAt,
                    // 既存の記録では、利用者が選んだ種別を上書きしない。
                    mealType = if (current.isNew) {
                        MealTypeInference.infer(eatenAt, ZoneId.systemDefault())
                    } else {
                        current.mealType
                    },
                )
            }
        }
    }

    fun setDishName(value: String) = _state.update { it.copy(dishName = value) }

    fun setRestaurantName(value: String) {
        _state.update { it.copy(restaurantName = value) }
        loadDishSuggestions()
    }

    /** メニュー名の候補 (SPEC F-103)。同じ店の履歴を優先する。 */
    private fun loadDishSuggestions() {
        viewModelScope.launch {
            val current = _state.value
            val history = repository.observeAll().first()
            _state.update {
                it.copy(
                    dishSuggestions = DishSuggestions.suggest(
                        records = history,
                        restaurantName = current.restaurantName,
                        query = current.dishName,
                    ),
                )
            }
        }
    }

    fun setMealType(value: MealType) = _state.update { it.copy(mealType = value) }

    fun setPrice(value: String) = _state.update { it.copy(price = value.filter(Char::isDigit)) }

    fun setRating(value: Int?) = _state.update { current ->
        // 同じ星をもう一度押したら解除する (間違えて付けたときに戻せるように)。
        current.copy(rating = if (current.rating == value) null else value)
    }

    fun setMemo(value: String) = _state.update { it.copy(memo = value) }

    fun addTag(value: String) = _state.update { current ->
        val tag = value.trim().removePrefix("#")
        if (tag.isEmpty() || tag in current.tags) current else current.copy(tags = current.tags + tag)
    }

    fun removeTag(value: String) = _state.update { it.copy(tags = it.tags - value) }

    /**
     * 食べログ URL を受け取る (SPEC §6.3)。
     * 食べログのものでなくても保存は許可し、警告だけを出す。
     */
    fun setTabelogUrl(value: String) = _state.update { current ->
        if (value.isBlank()) {
            current.copy(tabelogUrl = value, tabelogWarning = null)
        } else {
            val inspection = TabelogUrl.inspect(value)
            current.copy(tabelogUrl = inspection.url, tabelogWarning = inspection.warning)
        }
    }

    fun applyMenuCandidate(candidate: MenuTextParser.MenuCandidate) = _state.update { current ->
        current.copy(
            dishName = candidate.name,
            price = candidate.price?.toString() ?: current.price,
        )
    }

    fun setEatenAt(value: Instant) = _state.update { it.copy(eatenAt = value) }

    fun applySuggestion(suggestion: RestaurantSuggestion) {
        _state.update { it.copy(restaurantName = suggestion.name) }
        loadDishSuggestions()
    }

    /**
     * 店舗候補を読み込む。位置情報が取れなくても履歴から候補を出す
     * (SPEC §6.1 のフォールバック)。
     */
    private fun loadSuggestions() {
        viewModelScope.launch {
            _state.update { it.copy(loadingSuggestions = true) }
            val here = currentLocation.current()
            location = here
            val suggestions = repository.suggestRestaurants(here?.lat, here?.lng)
            _state.update { it.copy(suggestions = suggestions, loadingSuggestions = false) }
        }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(saving = true, errors = emptyList()) }

            val now = Instant.now()
            val record = LunchRecord(
                id = current.id,
                eatenAt = current.eatenAt,
                mealType = current.mealType,
                dishName = current.dishName,
                restaurantName = current.restaurantName,
                photos = current.photos,
                location = location,
                price = current.price.toIntOrNull(),
                rating = current.rating,
                memo = current.memo,
                tags = current.tags,
                tabelogUrl = current.tabelogUrl.takeIf { it.isNotBlank() },
                // 編集では作成時刻を引き継ぐ。上書きすると並び順や履歴が狂う。
                createdAt = current.createdAt ?: now,
                updatedAt = now,
            )

            when (val result = repository.save(record)) {
                is ValidationResult.Valid -> onSaved()
                is ValidationResult.Invalid ->
                    _state.update { it.copy(saving = false, errors = result.errors) }
            }
        }
    }
}
