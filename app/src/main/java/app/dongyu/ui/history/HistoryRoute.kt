package app.dongyu.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow<Long?>(null)
    val state = combine(c.sports.allSports, c.workouts.finished, filter) { s, w, f ->
        val used = w.map { it.sportId }.toSet()
        HistoryState(s.filter { !it.archived || it.id in used }, w, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())
    fun setFilter(id: Long?) { filter.value = id }
}

@Composable
fun HistoryRoute(container: AppContainer, onOpen: (Long) -> Unit, onManual: (Long) -> Unit) {
    val vm: HistoryViewModel = viewModel { HistoryViewModel(container) }
    val st by vm.state.collectAsStateWithLifecycle()
    HistoryScreen(st, vm::setFilter, onOpen, onManual)
}
