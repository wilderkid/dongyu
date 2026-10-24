package app.dongyu.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.SportType
import app.dongyu.ui.components.SportIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SportEditorViewModel(private val c: AppContainer) : ViewModel() {
    val sport = MutableStateFlow<SportType?>(null)
    private var loaded = false

    fun load(id: Long?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            sport.value = id?.let { c.sports.get(it) } ?: SportEditorViewModel.blank()
        }
    }

    fun save(s: SportType, then: (Long) -> Unit) = viewModelScope.launch { then(c.sports.save(s)) }

    companion object {
        fun blank() = SportType(
            name = "", icon = "badminton", color = SportIcons.palette[3], usesGps = false,
            metrics = listOf(MetricDef.of(MetricKind.DURATION), MetricDef.of(MetricKind.CALORIES), MetricDef.of(MetricKind.HEART_RATE), MetricDef.of(MetricKind.RPE)),
        )
    }
}

@Composable
fun SportEditorRoute(container: AppContainer, sportId: Long?, onDone: () -> Unit, onBack: () -> Unit) {
    val vm: SportEditorViewModel = viewModel { SportEditorViewModel(container) }
    LaunchedEffect(sportId) { vm.load(sportId) }
    val s by vm.sport.collectAsStateWithLifecycle()
    s?.let { SportEditorScreen(it, it.id == 0L, { edited -> vm.save(edited) { onDone() } }, onBack) }
}
