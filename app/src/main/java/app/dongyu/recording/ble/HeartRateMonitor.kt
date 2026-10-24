package app.dongyu.recording.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.dongyu.domain.hr.HeartRateParser
import app.dongyu.domain.hr.HrMeasurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class HrConnState { IDLE, CONNECTING, CONNECTED, RECONNECTING }

data class HrDevice(val address: String, val name: String, val rssi: Int)

/**
 * Talks to any strap / watch exposing the standard BLE Heart Rate Service (0x180D, measurement 0x2A37).
 * Keeps trying to reconnect while [connect] is in effect, so a strap that drops out mid-run comes back.
 */
@SuppressLint("MissingPermission")
class HeartRateMonitor(private val context: Context, private val scope: CoroutineScope) {

    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm
    private val _state = MutableStateFlow(HrConnState.IDLE)
    val state: StateFlow<HrConnState> = _state
    private val _scanResults = MutableStateFlow<List<HrDevice>>(emptyList())
    val scanResults: StateFlow<List<HrDevice>> = _scanResults
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    /** Raw measurement listener (used by the recorder to persist samples). */
    @Volatile var onMeasurement: ((Long, HrMeasurement) -> Unit)? = null

    private var gatt: BluetoothGatt? = null
    @Volatile private var target: String? = null
    private var reconnectJob: Job? = null
    private var scanStopJob: Job? = null
    private var attempts = 0

    val isSupported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null
    val isEnabled: Boolean get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // ---- scanning ----

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: result.device.address
            val d = HrDevice(result.device.address, name, result.rssi)
            _scanResults.value = (_scanResults.value.filter { it.address != d.address } + d).sortedByDescending { it.rssi }
        }
        override fun onScanFailed(errorCode: Int) { _scanning.value = false }
    }

    fun startScan(durationMs: Long = 15_000) {
        if (!hasPermissions() || !isEnabled) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        stopScan()
        _scanResults.value = emptyList()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(filters, settings, scanCallback); _scanning.value = true }
        scanStopJob = scope.launch { delay(durationMs); stopScan() }
    }

    fun stopScan() {
        scanStopJob?.cancel()
        if (_scanning.value) runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    // ---- connection ----

    fun connect(address: String) {
        if (target == address && _state.value != HrConnState.IDLE) return
        disconnect()
        target = address
        attempts = 0
        open(autoConnect = false)
    }

    fun disconnect() {
        target = null
        reconnectJob?.cancel()
        gatt?.let { g -> runCatching { g.disconnect(); g.close() } }
        gatt = null
        _bpm.value = null
        _state.value = HrConnState.IDLE
    }

    private fun open(autoConnect: Boolean) {
        val addr = target ?: return
        if (!hasPermissions()) return
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(addr) }.getOrNull() ?: return
        gatt?.let { runCatching { it.close() } }
        _state.value = if (attempts == 0) HrConnState.CONNECTING else HrConnState.RECONNECTING
        gatt = runCatching { device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
        // Watchdog: a direct connect that never completes is retried in background-autoConnect mode.
        if (!autoConnect) {
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(15_000)
                if (target == addr && _state.value != HrConnState.CONNECTED) { attempts++; open(autoConnect = true) }
            }
        }
    }

    private fun scheduleReconnect() {
        val addr = target ?: return
        _state.value = HrConnState.RECONNECTING
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            attempts++
            delay((1000L * attempts).coerceAtMost(10_000))
            if (target == addr) open(autoConnect = attempts > 2)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (gatt != null && g != gatt) { runCatching { g.close() }; return }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectJob?.cancel()
                runCatching { g.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runCatching { g.close() }
                gatt = null
                _bpm.value = null
                if (target != null) scheduleReconnect() else _state.value = HrConnState.IDLE
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (ch == null) { runCatching { g.disconnect() }; return }
            g.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) { attempts = 0; _state.value = HrConnState.CONNECTED }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            ch.value?.let(::handle)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) = handle(value)
    }

    private fun handle(bytes: ByteArray) {
        val m = HeartRateParser.parse(bytes) ?: return
        if (_state.value != HrConnState.CONNECTED) _state.value = HrConnState.CONNECTED
        // Contact explicitly lost -> don't show stale/zero values.
        _bpm.value = if (m.contact == false || m.bpm <= 0) null else m.bpm
        onMeasurement?.invoke(System.currentTimeMillis(), m)
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** Runtime permissions needed to scan + connect. Android 12+ uses the new Bluetooth permissions. */
        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
