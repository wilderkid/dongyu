package app.dongyu.ui.hr

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Build
import app.dongyu.AppContainer
import app.dongyu.recording.RecStatus
import app.dongyu.recording.ble.HeartRateMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HrSettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val hr = c.heartRate
    private data class Sys(val supported: Boolean, val enabled: Boolean, val perm: Boolean)
    private val sys = MutableStateFlow(read())
    private val testing = MutableStateFlow(false)

    private fun read() = Sys(hr.isSupported, hr.isEnabled, hr.hasPermissions())
    fun refresh() { sys.value = read() }

    private val radio = combine(hr.scanning, hr.scanResults, hr.state, hr.bpm) { sc, res, st, b -> listOf(sc, res, st, b) }

    val state = combine(sys, radio, c.settings.settings, testing) { y, r, st, t ->
        @Suppress("UNCHECKED_CAST")
        HrSettingsState(
            supported = y.supported, enabled = y.enabled, hasPermission = y.perm,
            legacyPermission = Build.VERSION.SDK_INT < 31,
            scanning = r[0] as Boolean, devices = r[1] as List<app.dongyu.recording.ble.HrDevice>,
            pairedAddress = st.hrDeviceAddress, pairedName = st.hrDeviceName,
            connState = r[2] as app.dongyu.recording.ble.HrConnState, bpm = r[3] as Int?, testing = t,
            customMax = st.maxHr, age = st.age,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HrSettingsState())

    private val recording get() = c.recording.state.value.status != RecStatus.IDLE

    fun scan() { refresh(); if (sys.value.perm && sys.value.enabled) hr.startScan() }
    fun pair(d: app.dongyu.recording.ble.HrDevice) = viewModelScope.launch {
        hr.stopScan()
        c.settings.setHrDevice(d.address, d.name)
        if (!recording) { hr.connect(d.address); testing.value = true }
    }
    fun forget() = viewModelScope.launch {
        if (!recording) hr.disconnect()
        testing.value = false
        c.settings.setHrDevice(null, null)
    }
    fun test(on: Boolean) = viewModelScope.launch {
        val addr = state.value.pairedAddress ?: return@launch
        if (recording) return@launch
        if (on) hr.connect(addr) else hr.disconnect()
        testing.value = on
    }
    fun setMax(v: Int) = viewModelScope.launch { c.settings.setMaxHr(v) }
    fun setAge(v: Int) = viewModelScope.launch { c.settings.setAge(v) }

    fun leave() {
        hr.stopScan()
        if (testing.value && !recording) hr.disconnect()
        testing.value = false
    }

    override fun onCleared() { leave() }
}

@Composable
fun HrSettingsRoute(container: AppContainer, onBack: () -> Unit) {
    val vm: HrSettingsViewModel = viewModel { HrSettingsViewModel(container) }
    val st by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh(); vm.scan() }
    // Bluetooth may be toggled from quick settings while we're open.
    LaunchedEffect(Unit) { while (true) { vm.refresh(); delay(1500) } }
    DisposableEffect(Unit) { onDispose { vm.leave() } }
    HrSettingsScreen(
        st,
        HrSettingsActions(
            onBack = onBack,
            onGrant = { perms.launch(HeartRateMonitor.requiredPermissions()) },
            onOpenBt = { runCatching { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } },
            onScan = vm::scan,
            onPair = { vm.pair(it) },
            onForget = { vm.forget() },
            onTest = { vm.test(it) },
            onMaxHr = { vm.setMax(it) },
            onAge = { vm.setAge(it) },
        ),
    )
}
