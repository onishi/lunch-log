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
import app.lunchlog.core.record.EatenAtResolver
import app.lunchlog.core.restaurant.RestaurantSuggestion
import app.lunchlog.core.validation.RecordError
import app.lunchlog.core.validation.ValidationResult
import app.lunchlog.data.RecordRepository
import app.lunchlog.location.CurrentLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val suggestions: List<RestaurantSuggestion> = emptyList(),
    val loadingSuggestions: Boolean = false,
    val saving: Boolean = false,
    val errors: List<RecordError> = emptyList(),
    val isNew: Boolean = true,
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
        )
        loadSuggestions()
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
                isNew = false,
                createdAt = record.createdAt,
            )
        }
    }

    fun addPhoto(uri: Uri, kind: PhotoKind = PhotoKind.DISH) {
        viewModelScope.launch {
            val prepared = repository.photoStore().prepare(uri, kind, _state.value.photos.size)
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

    fun setRestaurantName(value: String) = _state.update { it.copy(restaurantName = value) }

    fun setMealType(value: MealType) = _state.update { it.copy(mealType = value) }

    fun setEatenAt(value: Instant) = _state.update { it.copy(eatenAt = value) }

    fun applySuggestion(suggestion: RestaurantSuggestion) {
        _state.update { it.copy(restaurantName = suggestion.name) }
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
