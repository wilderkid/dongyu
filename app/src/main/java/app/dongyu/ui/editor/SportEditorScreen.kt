package app.dongyu.ui.editor

import app.dongyu.ui.components.displayName
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.model.Aggregation
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.ui.components.ChipButton
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.components.SegmentedTabs
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.label
import app.dongyu.ui.components.labelRes
import app.dongyu.ui.theme.extraColors

object SportEditorRules {
    /** Metric kinds that can be toggled for a sport with/without GPS. */
    fun selectableKinds(usesGps: Boolean): List<MetricKind> = MetricKind.pickable.filter {
        it != MetricKind.DURATION && it != MetricKind.NOTE && (usesGps || !it.needsGps)
    }

    /** Normalises a metric list after GPS is toggled: always keeps duration, drops GPS-only kinds when GPS is off. */
    fun normalise(metrics: List<MetricDef>, usesGps: Boolean): List<MetricDef> {
        val base = metrics.filter { usesGps || !it.kind.needsGps }.filter { it.kind != MetricKind.DURATION }
        return listOf(MetricDef.of(MetricKind.DURATION)) + base
    }

    val metOptions = listOf(4.0, 6.0, 8.0, 10.0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportEditorScreen(initial: SportType, isNew: Boolean, onSave: (SportType) -> Unit, onBack: () -> Unit) {
    // Untouched presets are stored with their zh name; show the localized one and keep the stored name if unchanged.
    val shownName = initial.displayName()
    var name by remember { mutableStateOf(shownName) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    var usesGps by remember { mutableStateOf(initial.usesGps) }
    var metrics by remember { mutableStateOf(SportEditorRules.normalise(initial.metrics, initial.usesGps)) }
    var met by remember { mutableStateOf(initial.met) }
    var customDialog by remember { mutableStateOf(false) }
    val c = Color(color)
    val isPreset = initial.presetKey != null
    val gpsLocked = isPreset && initial.family != SportFamily.GENERIC

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(if (isNew) R.string.new_sport else R.string.edit_sport), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live preview
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.6f)))).padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                            Icon(SportIcons.of(icon), null, tint = Color.White, modifier = Modifier.size(38.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(name.ifBlank { stringResource(R.string.sport_name_hint) }, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (usesGps) Icons.Filled.SatelliteAlt else Icons.Filled.Timer, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(if (usesGps) R.string.mode_gps else R.string.mode_timer), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        metrics.forEach { m ->
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(SportIcons.metric(m.kind), null, tint = Color.White, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(m.label(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            DCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(name, { name = it.take(16) }, label = { Text(stringResource(R.string.sport_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.sport_icon))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SportIcons.all.forEach { (key, vec) ->
                        val sel = key == icon
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (sel) c else c.copy(alpha = 0.1f))
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vec, key, tint = if (sel) Color.White else c, modifier = Modifier.size(24.dp)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SectionTitle(stringResource(R.string.sport_color))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SportIcons.palette.forEach { p ->
                        val sel = p == color
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(p))
                                .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = p },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
            }

            DCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SatelliteAlt, null, tint = c)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sport_uses_gps), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (gpsLocked) R.string.sport_gps_locked else R.string.sport_uses_gps_desc), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                    }
                    Switch(usesGps, { usesGps = it; metrics = SportEditorRules.normalise(metrics, it) }, enabled = !gpsLocked)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.sport_intensity), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                SegmentedTabs(
                    listOf(stringResource(R.string.intensity_light), stringResource(R.string.intensity_moderate), stringResource(R.string.intensity_hard), stringResource(R.string.intensity_max)),
                    SportEditorRules.metOptions.indexOfFirst { it >= met }.let { if (it < 0) 3 else it },
                    { met = SportEditorRules.metOptions[it] }, Modifier.fillMaxWidth(),
                )
            }

            DCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.sport_metrics))
                Text(stringResource(R.string.sport_metrics_desc), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SportEditorRules.selectableKinds(usesGps).forEach { k ->
                        val sel = metrics.any { it.kind == k }
                        ChipButton(stringResource(k.labelRes()), sel, c, SportIcons.metric(k)) {
                            metrics = if (sel) metrics.filterNot { it.kind == k } else metrics + MetricDef.of(k)
                        }
                    }
                }
                val customs = metrics.filter { it.kind == MetricKind.CUSTOM }
                if (customs.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.custom_metrics), style = MaterialTheme.typography.titleSmall)
                    customs.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(SportIcons.metric(MetricKind.CUSTOM), null, tint = c, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(m.customLabel.orEmpty(), Modifier.weight(1f))
                            Text(listOfNotNull(m.customUnit?.takeIf { it.isNotBlank() }, stringResource(aggRes(m.aggregation))).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                            IconButton(onClick = { metrics = metrics - m }) { Icon(Icons.Filled.Close, stringResource(R.string.delete)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { customDialog = true }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_custom_metric))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                onSave(initial.copy(name = if (initial.presetKey != null && name.trim() == shownName) initial.name else name.trim(), icon = icon, color = color, usesGps = usesGps, metrics = SportEditorRules.normalise(metrics, usesGps), met = met))
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(54.dp),
        ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
    }

    if (customDialog) CustomMetricDialog(
        onDismiss = { customDialog = false },
        onAdd = { label, unit, agg ->
            metrics = metrics + MetricDef.custom("c_" + System.currentTimeMillis().toString(36), label, unit, agg)
            customDialog = false
        },
    )
}

private fun aggRes(a: Aggregation) = when (a) {
    Aggregation.SUM -> R.string.agg_sum
    Aggregation.AVG -> R.string.agg_avg
    Aggregation.MAX -> R.string.agg_max
    Aggregation.MIN -> R.string.agg_min
    Aggregation.LAST -> R.string.agg_last
}

@Composable
private fun CustomMetricDialog(onDismiss: () -> Unit, onAdd: (String, String, Aggregation) -> Unit) {
    var label by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var agg by remember { mutableStateOf(Aggregation.SUM) }
    val options = listOf(Aggregation.SUM, Aggregation.AVG, Aggregation.MAX)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_custom_metric)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it.take(12) }, label = { Text(stringResource(R.string.metric_name)) }, singleLine = true)
                OutlinedTextField(unit, { unit = it.take(6) }, label = { Text(stringResource(R.string.metric_unit_hint)) }, singleLine = true)
                Text(stringResource(R.string.metric_aggregation), style = MaterialTheme.typography.labelLarge)
                SegmentedTabs(options.map { stringResource(aggRes(it)) }, options.indexOf(agg), { agg = options[it] }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(label.trim(), unit.trim(), agg) }, enabled = label.isNotBlank()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
