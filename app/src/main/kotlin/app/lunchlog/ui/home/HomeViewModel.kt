package app.lunchlog.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lunchlog.core.timeline.DaySection
import app.lunchlog.core.timeline.TimelineGrouping
import app.lunchlog.data.RecordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/**
 * ホームの状態 (SPEC F-201)。
 *
 * 集約そのものは :core の TimelineGrouping が持つ (テスト済み)。
 * ここは Room の Flow をつなぐだけに留める。
 */
class HomeViewModel(repository: RecordRepository) : ViewModel() {

    val sections: StateFlow<List<DaySection>> = repository.observeAll()
        .map { records -> TimelineGrouping.group(records, ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
