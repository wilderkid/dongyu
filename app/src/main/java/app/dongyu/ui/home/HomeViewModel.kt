package app.dongyu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dongyu.AppContainer
import app.dongyu.domain.stats.Period
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = combine(c.sports.activeSports, c.workouts.finished, c.recording.state) { sports, ws, live ->
        val today = LocalDate.now()
        HomeState(
            sports = sports,
            week = c.stats.compare(ws, Period.WEEK, today, toDate = true),
            streaks = c.stats.streaks(ws, today),
            recent = ws.take(3),
            live = live,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

}
