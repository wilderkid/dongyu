package app.dongyu.ui.hr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.hr.HrZones
import app.dongyu.recording.ble.HrConnState
import app.dongyu.recording.ble.HrDevice
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.IconBadge
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.components.SegmentedTabs
import app.dongyu.ui.sports.NavRow
import app.dongyu.ui.theme.Brand
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors

data class HrSettingsState(
    val supported: Boolean = true,
    val enabled: Boolean = true,
    val hasPermission: Boolean = true,
    val legacyPermission: Boolean = false,
    val scanning: Boolean = false,
    val devices: List<HrDevice> = emptyList(),
    val pairedAddress: String? = null,
    val pairedName: String? = null,
    val connState: HrConnState = HrConnState.IDLE,
    val bpm: Int? = null,
    val testing: Boolean = false,
    val customMax: Int = 0,
    val age: Int = 30,
) {
    val effectiveMax: Int get() = HrZones.effectiveMax(customMax, age)
}

data class HrSettingsActions(
    val onBack: () -> Unit = {},
    val onGrant: () -> Unit = {},
    val onOpenBt: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onPair: (HrDevice) -> Unit = {},
    val onForget: () -> Unit = {},
    val onTest: (Boolean) -> Unit = {},
    val onMaxHr: (Int) -> Unit = {},
    val onAge: (Int) -> Unit = {},
)

@Composable
fun HrSettingsScreen(s: HrSettingsState, a: HrSettingsActions) {
    var ageDialog by remember { mutableStateOf(false) }
    var maxDialog by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = a.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.hr_settings), style = MaterialTheme.typography.titleLarge)
            }
        }
        // ---- device ----
        item { PairedCard(s, a) }
        when {
            !s.supported -> item { Notice(Icons.Filled.BluetoothDisabled, stringResource(R.string.hr_bt_unsupported), null, null) }
            !s.hasPermission -> item {
                Notice(Icons.Filled.Lock, stringResource(if (s.legacyPermission) R.string.hr_perm_needed_old else R.string.hr_perm_needed), stringResource(R.string.hr_grant), a.onGrant)
            }
            !s.enabled -> item { Notice(Icons.Filled.BluetoothDisabled, stringResource(R.string.hr_bt_off), stringResource(R.string.hr_open_bt), a.onOpenBt) }
            else -> {
                item {
                    SectionTitle(stringResource(R.string.hr_nearby), action = {
                        FilledTonalButton(onClick = a.onScan, enabled = !s.scanning) {
                            if (s.scanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.BluetoothSearching, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (s.scanning) R.string.hr_scanning else R.string.hr_scan))
                        }
                    })
                }
                val others = s.devices.filter { it.address != s.pairedAddress }
                if (others.isEmpty()) item {
                    Text(
                        stringResource(if (s.scanning) R.string.hr_scan_hint else R.string.hr_no_devices_hint),
                        style = MaterialTheme.typography.bodySmall, color = extraColors.subtle, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                items(others, key = { it.address }) { d -> DeviceRow(d, a) }
            }
        }
        // ---- zones ----
        item { SectionTitle(stringResource(R.string.hr_zones), Modifier.padding(top = 8.dp)) }
        item {
            DCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Speed, HrColors.heart, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.hr_max), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(if (s.customMax in 100..240) R.string.hr_max_custom else R.string.hr_max_auto),
                            style = MaterialTheme.typography.labelSmall, color = extraColors.subtle,
                        )
                    }
                    Text(s.effectiveMax.toString(), style = NumberStyle.Medium)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.unit_bpm), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                }
                SegmentedTabs(
                    listOf(stringResource(R.string.hr_auto), stringResource(R.string.hr_max_custom)),
                    if (s.customMax in 100..240) 1 else 0,
                    { if (it == 0) a.onMaxHr(0) else maxDialog = true },
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
        item {
            DCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                NavRow(Icons.Filled.Cake, Brand.Sun, stringResource(R.string.hr_age), stringResource(R.string.hr_age_desc, s.age, HrZones.maxHrForAge(s.age))) { ageDialog = true }
            }
        }
        item { ZonePreview(s.effectiveMax) }
    }
    if (ageDialog) NumberDialog(stringResource(R.string.hr_age), s.age, 5..100, null, { ageDialog = false }) { a.onAge(it) }
    if (maxDialog) NumberDialog(stringResource(R.string.hr_max), s.effectiveMax, 100..240, stringResource(R.string.hr_max_desc), { maxDialog = false }) { a.onMaxHr(it) }
}

@Composable
private fun PairedCard(s: HrSettingsState, a: HrSettingsActions) {
    val paired = s.pairedAddress != null
    DCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(HrColors.heart.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(if (paired) Icons.Filled.MonitorHeart else Icons.Filled.Favorite, null, tint = HrColors.heart, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (paired) s.pairedName ?: s.pairedAddress!! else stringResource(R.string.hr_device),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1,
                )
                Text(
                    if (paired) stateLabel(s.connState) else stringResource(R.string.hr_settings_desc_none),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (s.connState == HrConnState.CONNECTED) Brand.Up else extraColors.subtle,
                )
            }
            if (paired && s.testing) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(s.bpm?.toString() ?: "--", style = NumberStyle.Large, color = s.bpm?.let { HrColors.zones[HrZones.zoneOf(it, s.effectiveMax)] } ?: MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.unit_bpm), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                }
            }
        }
        if (paired) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { a.onTest(!s.testing) }, enabled = s.hasPermission && s.enabled) {
                    Icon(Icons.Filled.Bluetooth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (s.testing) R.string.hr_stop_test else R.string.hr_test))
                }
                OutlinedButton(onClick = a.onForget) {
                    Icon(Icons.Filled.LinkOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.hr_forget))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.hr_auto_record), style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
    }
}

@Composable
fun stateLabel(st: HrConnState): String = when (st) {
    HrConnState.CONNECTED -> stringResource(R.string.hr_state_connected)
    HrConnState.CONNECTING -> stringResource(R.string.hr_state_connecting)
    HrConnState.RECONNECTING -> stringResource(R.string.hr_state_reconnecting)
    else -> stringResource(R.string.hr_state_idle)
}

@Composable
private fun DeviceRow(d: HrDevice, a: HrSettingsActions) {
    DCard(Modifier.fillMaxWidth(), padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Watch, Brand.Ocean, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name ?: d.address, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("${d.address} · ${d.rssi} dBm", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            }
            SignalBars(d.rssi)
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = { a.onPair(d) }) { Text(stringResource(R.string.hr_pair)) }
        }
    }
}

@Composable
private fun SignalBars(rssi: Int) {
    val level = when { rssi > -60 -> 4; rssi > -70 -> 3; rssi > -80 -> 2; else -> 1 }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..4).forEach { i ->
            Box(Modifier.width(4.dp).height((4 + i * 4).dp).clip(RoundedCornerShape(2.dp))
                .background(if (i <= level) Brand.Up else extraColors.grid))
        }
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: String?, onAction: (() -> Unit)?) {
    DCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, Brand.Coral, 38.dp)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (action != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
fun ZonePreview(maxHr: Int) {
    val bounds = HrZones.bounds(maxHr)
    DCard(Modifier.fillMaxWidth()) {
        bounds.reversed().forEachIndexed { ri, r ->
            val z = bounds.size - 1 - ri
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(HrColors.zones[z]), contentAlignment = Alignment.Center) {
                    Text("Z${z + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(zoneName(z), style = MaterialTheme.typography.titleSmall)
                    Text("${(HrZones.lowerPct[z] * 100).toInt()}%+", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                }
                Text(stringResource(R.string.hr_bpm_range, r.first, r.last), style = NumberStyle.Small)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.unit_bpm), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            }
        }
    }
}

@Composable
private fun NumberDialog(title: String, initial: Int, range: IntRange, hint: String?, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val v = text.trim().toIntOrNull()
    val ok = v != null && v in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it.filter(Char::isDigit).take(3) }, singleLine = true, isError = !ok,
                supportingText = hint?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onOk(v!!); onDismiss() }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
