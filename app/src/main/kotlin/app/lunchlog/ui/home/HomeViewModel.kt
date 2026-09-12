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

/** 画面に出す 1 日分。未同期の記録 ID を添える。 */
data class HomeState(
    val sections: List<DaySection> = emptyList(),
    val pendingSyncIds: Set<String> = emptySet(),
)

/**
 * ホームの状態 (SPEC F-201)。
 *
 * 集約そのものは :core の TimelineGrouping が持つ (テスト済み)。
 * ここは Room の Flow をつなぐだけに留める。
 */
class HomeViewModel(repository: RecordRepository) : ViewModel() {

    val state: StateFlow<HomeState> = repository.observeAllWithSync()
        .map { items ->
            HomeState(
                sections = TimelineGrouping.group(items.map { it.record }, ZoneId.systemDefault()),
                pendingSyncIds = items.filter { it.needsSync }.map { it.record.id }.toSet(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
