package app.lunchlog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lunchlog.core.model.LunchRecord
import app.lunchlog.data.RecordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordDetailViewModel(
    private val repository: RecordRepository,
    private val recordId: String,
) : ViewModel() {

    val record: StateFlow<LunchRecord?> = repository.observe(recordId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recordId)
            onDeleted()
        }
    }
}
