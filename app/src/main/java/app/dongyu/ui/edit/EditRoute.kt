package app.dongyu.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.domain.metrics.ManualEntry
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import app.dongyu.model.WorkoutSource
import app.dongyu.model.WorkoutStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class EditState(val sport: SportType, val workout: Workout, val hasTrack: Boolean)

class EditViewModel(private val c: AppContainer) : ViewModel() {
    val state = MutableStateFlow<EditState?>(null)

    fun load(workoutId: Long?, sportId: Long?) = viewModelScope.launch {
        if (state.value != null) return@launch
        if (workoutId != null) {
            val w = c.workouts.get(workoutId) ?: return@launch
            val s = c.sports.get(w.sportId) ?: return@launch
            state.value = EditState(s, w, w.source == WorkoutSource.GPS)
        } else if (sportId != null) {
            val s = c.sports.get(sportId) ?: return@launch
            val now = System.currentTimeMillis() - 3600_000
            state.value = EditState(s, Workout(sportId = s.id, startTime = now, endTime = now, durationSec = 0, source = WorkoutSource.MANUAL), false)
        }
    }

    fun save(r: EditResult, then: (Long) -> Unit) = viewModelScope.launch {
        val st = state.value ?: return@launch
        val weight = c.settings.settings.first().weightKg
        val base = st.workout.copy(startTime = if (st.hasTrack) st.workout.startTime else r.startTime, status = WorkoutStatus.FINISHED)
        val w = ManualEntry.apply(base, st.sport, r.durationSec, r.distanceM, r.values, r.note, weight, st.hasTrack)
        val id = if (w.id == 0L) c.workouts.insert(w) else { c.workouts.update(w); w.id }
        then(id)
    }
}

@Composable
fun EditRoute(container: AppContainer, workoutId: Long?, sportId: Long?, isFinish: Boolean, onDone: (Long) -> Unit, onBack: () -> Unit) {
    val vm: EditViewModel = viewModel { EditViewModel(container) }
    LaunchedEffect(workoutId, sportId) { vm.load(workoutId, sportId) }
    val st by vm.state.collectAsStateWithLifecycle()
    st?.let { s ->
        EditWorkoutScreen(s.sport, s.workout, s.hasTrack, isNew = workoutId == null, isFinish = isFinish,
            onSave = { vm.save(it, onDone) }, onBack = onBack)
    }
}
