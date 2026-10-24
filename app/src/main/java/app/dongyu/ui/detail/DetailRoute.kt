package app.dongyu.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.domain.metrics.SplitCalculator
import app.dongyu.domain.metrics.TrackSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import app.dongyu.domain.hr.HrSampling
import app.dongyu.domain.hr.HrZones
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(private val c: AppContainer) : ViewModel() {
    val state = MutableStateFlow<DetailState?>(null)

    fun load(id: Long) = viewModelScope.launch {
        c.workouts.observe(id).filterNotNull().collect { w ->
            val sport = c.sports.get(w.sportId) ?: return@collect
            val st = withContext(Dispatchers.Default) {
                val pts = c.workouts.points(id)
                val hr = c.workouts.hrSamples(id)
                val maxHr = c.settings.settings.first().effectiveMaxHr
                DetailState(
                    hrSamples = HrSampling.downsample(hr, 240),
                    hrMin = hr.filter { it.bpm in 25..250 }.minOfOrNull { it.bpm },
                    hrZones = HrZones.fromValues(w.values) ?: HrZones.summarize(hr, maxHr)?.zoneSec,
                    maxHr = maxHr,
                    workout = w, sport = sport,
                    path = pts.map { it.lat to it.lon },
                    splits = sport.splitDistanceM?.let { SplitCalculator.splits(pts, it) } ?: emptyList(),
                    series = TrackSeries.build(pts),
                )
            }
            state.value = st
        }
    }

    fun delete(id: Long, then: () -> Unit) = viewModelScope.launch { c.workouts.delete(id); then() }
}

@Composable
fun DetailRoute(container: AppContainer, id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onShare: (Long) -> Unit = {}) {
    val vm: DetailViewModel = viewModel { DetailViewModel(container) }
    LaunchedEffect(id) { vm.load(id) }
    val st by vm.state.collectAsStateWithLifecycle()
    st?.let { DetailScreen(it, onBack, { onEdit(id) }, { vm.delete(id, onBack) }, onShare = { onShare(id) }) }
}
