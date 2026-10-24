package app.dongyu.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.data.prefs.Settings
import app.dongyu.model.SportType
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.IconBadge
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.components.SegmentedTabs
import app.dongyu.ui.components.SportBadge
import app.dongyu.ui.components.displayName
import app.dongyu.ui.theme.Brand

data class MeState(
    val sports: List<SportType> = emptyList(),
    val settings: Settings = Settings(),
    val showDebug: Boolean = false,
    val seeding: Boolean = false,
)

data class MeActions(
    val onAddSport: () -> Unit = {},
    val onEditSport: (Long) -> Unit = {},
    val onArchive: (Long, Boolean) -> Unit = { _, _ -> },
    val onTheme: (Int) -> Unit = {},
    val onAutoPause: (Boolean) -> Unit = {},
    val onKeepScreen: (Boolean) -> Unit = {},
    val onWeight: (Double) -> Unit = {},
    val onOemGuide: () -> Unit = {},
    val onSeed: () -> Unit = {},
)

@Composable
fun MeScreen(state: MeState, a: MeActions) {
    var weightDialog by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    val active = state.sports.filter { !it.archived }
    val archived = state.sports.filter { it.archived }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.tab_me), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(top = 12.dp)) }
        item {
            SectionTitle(stringResource(R.string.my_sports), action = {
                FilledTonalButton(onClick = a.onAddSport) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_sport))
                }
            })
        }
        items(active, key = { it.id }) { s -> SportRow(s, a) }
        if (archived.isNotEmpty()) {
            item {
                Text(
                    stringResource(if (showArchived) R.string.hide_archived else R.string.show_archived, archived.size),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { showArchived = !showArchived }.padding(vertical = 6.dp),
                )
            }
            if (showArchived) items(archived, key = { it.id }) { s -> SportRow(s, a) }
        }
        item { SectionTitle(stringResource(R.string.settings), Modifier.padding(top = 8.dp)) }
        item {
            DCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.DarkMode, Brand.Ocean, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
                }
                SegmentedTabs(
                    listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    state.settings.themeMode, a.onTheme, Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
        item {
            DCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                SwitchRow(Icons.Filled.PauseCircle, Brand.Teal, stringResource(R.string.setting_auto_pause), stringResource(R.string.setting_auto_pause_desc), state.settings.autoPause, a.onAutoPause)
                SwitchRow(Icons.Filled.ScreenLockPortrait, Brand.Coral, stringResource(R.string.setting_keep_screen), stringResource(R.string.setting_keep_screen_desc), state.settings.keepScreenOn, a.onKeepScreen)
                NavRow(Icons.Filled.MonitorWeight, Brand.Sun, stringResource(R.string.setting_weight), stringResource(R.string.fmt_kg, Fmt.num(state.settings.weightKg, 1))) { weightDialog = true }
                NavRow(Icons.Filled.BatteryChargingFull, Brand.Up, stringResource(R.string.oem_guide_title), stringResource(R.string.setting_oem_desc), a.onOemGuide)
            }
        }
        if (state.showDebug) item {
            DCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                NavRow(Icons.Filled.DataObject, Color(0xFF7C5CFF), stringResource(R.string.debug_seed), stringResource(if (state.seeding) R.string.debug_seeding else R.string.debug_seed_desc), a.onSeed)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.about_line), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
    if (weightDialog) {
        var text by remember { mutableStateOf(Fmt.num(state.settings.weightKg, 1)) }
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            title = { Text(stringResource(R.string.setting_weight)) },
            text = {
                OutlinedTextField(text, { text = it }, singleLine = true, suffix = { Text(stringResource(R.string.unit_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                TextButton(onClick = {
                    text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..300.0 }?.let(a.onWeight)
                    weightDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SportRow(s: SportType, a: MeActions) {
    DCard(Modifier.fillMaxWidth().alpha(if (s.archived) 0.6f else 1f), padding = 12.dp, onClick = { a.onEditSport(s.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(s, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.displayName(), style = MaterialTheme.typography.titleSmall)
                Text(
                    (if (s.presetKey != null) stringResource(R.string.preset) else stringResource(R.string.custom_sport)) +
                        " · " + stringResource(R.string.fmt_metric_count, s.metrics.size) +
                        (if (s.usesGps) " · GPS" else ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = { a.onEditSport(s.id) }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
            IconButton(onClick = { a.onArchive(s.id, !s.archived) }) {
                Icon(if (s.archived) Icons.Filled.Unarchive else Icons.Filled.Archive, stringResource(if (s.archived) R.string.unarchive else R.string.archive))
            }
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, color: Color, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun NavRow(icon: ImageVector, color: Color, title: String, desc: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
