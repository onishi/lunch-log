package app.lunchlog.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.search.RecordQuery
import app.lunchlog.core.search.RecordSearch
import app.lunchlog.data.RecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 検索とフィルタ (SPEC F-205, F-206)。
 *
 * 絞り込みそのものは :core の RecordSearch が持つ (テスト済み)。
 * ここは入力とデータをつなぐだけ。
 */
class SearchViewModel(repository: RecordRepository) : ViewModel() {

    private val _query = MutableStateFlow(RecordQuery())
    val query: StateFlow<RecordQuery> = _query

    val results: StateFlow<List<LunchRecord>> =
        combine(repository.observeAll(), _query) { records, query ->
            RecordSearch.apply(records, query)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setText(text: String) {
        _query.value = _query.value.copy(text = text)
    }

    fun toggleMealType(mealType: MealType) {
        val current = _query.value.mealTypes
        _query.value = _query.value.copy(
            mealTypes = if (mealType in current) current - mealType else current + mealType,
        )
    }
}
