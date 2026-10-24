package app.dongyu.ui.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dongyu.AppContainer
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LiveViewModel(private val c: AppContainer) : ViewModel() {
    val live = c.recording.state
    val sport = MutableStateFlow<SportType?>(null)
    val keepScreenOn = MutableStateFlow(true)

    fun load(sportId: Long) = viewModelScope.launch {
        sport.value = c.sports.get(sportId)
        keepScreenOn.value = c.settings.settings.first().keepScreenOn
    }

    fun start(mode: SwimMode?, pool: Double) = viewModelScope.launch {
        val s = sport.value ?: return@launch
        if (!c.recording.state.value.active) c.recording.start(s, mode, pool)
    }

    fun pause() = c.recording.pause()
    fun resume() = c.recording.resume()
    fun addLength(d: Int) = c.recording.addLength(d)
    fun stop(then: (Long) -> Unit) = viewModelScope.launch { c.recording.stop()?.let(then) }
    fun discard(then: () -> Unit) = viewModelScope.launch { c.recording.discard(); then() }
}

@Composable
fun LiveRoute(
    container: AppContainer,
    sportId: Long,
    swimMode: SwimMode?,
    poolLength: Double,
    onFinished: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val vm: LiveViewModel = viewModel { LiveViewModel(container) }
    val live by vm.live.collectAsStateWithLifecycle()
    val sport by vm.sport.collectAsStateWithLifecycle()
    val keepOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(sportId) { vm.load(if (live.active) live.sport?.id ?: sportId else sportId) }

    // Keep the screen on while recording if the user wants it.
    DisposableEffect(live.active, keepOn) {
        val window = (context as? ComponentActivity)?.window
        if (live.active && keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val needsGps = (sport?.usesGps == true && swimMode != SwimMode.POOL) || swimMode == SwimMode.OPEN_WATER
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var hasLoc by remember { mutableStateOf(granted(Manifest.permission.ACCESS_FINE_LOCATION)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasLoc = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || granted(Manifest.permission.ACCESS_FINE_LOCATION)
        // Location is required for GPS sports; notifications are optional (recording still works).
        if (!needsGps || hasLoc) vm.start(swimMode, poolLength)
    }

    fun requestAndStart() {
        val perms = buildList {
            if (needsGps && !granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isEmpty()) vm.start(swimMode, poolLength) else launcher.launch(perms.toTypedArray())
    }

    if (live.active) {
        BackHandler(onBack = onBack)
        LiveScreen(
            state = live,
            onPause = vm::pause, onResume = vm::resume,
            onStop = { vm.stop(onFinished) },
            onDiscard = { vm.discard(onBack) },
            onLength = { vm.addLength(it) },
            onBack = onBack,
        )
    } else {
        sport?.let { s ->
            PreStartScreen(s, swimMode, needsPermission = needsGps && !hasLoc, onStart = ::requestAndStart, onBack = onBack)
        }
    }
}
