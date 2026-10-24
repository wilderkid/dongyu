package app.dongyu.ui.sports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeViewModel(private val c: AppContainer) : ViewModel() {
    private val seeding = MutableStateFlow(false)
    val state = combine(c.sports.allSports, c.settings.settings, seeding) { s, st, seed ->
        MeState(s, st, BuildConfig.DEBUG, seed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MeState(showDebug = BuildConfig.DEBUG))

    fun actions(onAdd: () -> Unit, onEdit: (Long) -> Unit, onOem: () -> Unit, onHr: () -> Unit) = MeActions(
        onAddSport = onAdd,
        onEditSport = onEdit,
        onArchive = { id, a -> viewModelScope.launch { c.sports.setArchived(id, a) } },
        onTheme = { viewModelScope.launch { c.settings.setThemeMode(it) } },
        onAutoPause = { viewModelScope.launch { c.settings.setAutoPause(it) } },
        onKeepScreen = { viewModelScope.launch { c.settings.setKeepScreenOn(it) } },
        onWeight = { viewModelScope.launch { c.settings.setWeightKg(it) } },
        onOemGuide = onOem,
        onHr = onHr,
        onSeed = {
            if (BuildConfig.DEBUG && !seeding.value) viewModelScope.launch {
                seeding.value = true
                try { c.seeder.seed() } finally { seeding.value = false }
            }
        },
    )
}

@Composable
fun MeRoute(container: AppContainer, onAddSport: () -> Unit, onEditSport: (Long) -> Unit, onOemGuide: () -> Unit, onHr: () -> Unit = {}) {
    val vm: MeViewModel = viewModel { MeViewModel(container) }
    val st by vm.state.collectAsStateWithLifecycle()
    MeScreen(st, vm.actions(onAddSport, onEditSport, onOemGuide, onHr))
}
