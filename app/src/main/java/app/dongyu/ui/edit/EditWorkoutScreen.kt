package app.dongyu.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.metrics.ManualEntry
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.MetricUnit
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.SwimStroke
import app.dongyu.model.Workout
import app.dongyu.ui.components.ChipButton
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.SportBadge
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.composeColor
import app.dongyu.ui.components.displayName
import app.dongyu.ui.components.formatDateTime
import app.dongyu.ui.components.label
import app.dongyu.ui.components.labelRes
import app.dongyu.ui.components.unitRes
import app.dongyu.ui.theme.extraColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

data class EditResult(
    val startTime: Long,
    val durationSec: Long,
    val distanceM: Double,
    val values: Map<String, Double?>,
    val note: String,
)

private fun numStr(v: Double?): String = when {
    v == null -> ""
    v % 1.0 == 0.0 -> v.toLong().toString()
    else -> Fmt.num(v, 2).trimEnd('0').trimEnd('.')
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditWorkoutScreen(
    sport: SportType,
    workout: Workout,
    hasTrack: Boolean,
    isNew: Boolean,
    isFinish: Boolean,
    onSave: (EditResult) -> Unit,
    onBack: () -> Unit,
) {
    val color = sport.composeColor()
    var start by remember { mutableStateOf(workout.startTime) }
    val d0 = workout.durationSec
    var hh by remember { mutableStateOf(if (d0 > 0) (d0 / 3600).toString() else "") }
    var mm by remember { mutableStateOf(if (d0 > 0) ((d0 % 3600) / 60).toString() else "") }
    var ss by remember { mutableStateOf(if (d0 > 0) (d0 % 60).toString() else "") }
    val showDistance = sport.has(MetricKind.DISTANCE)
    val distInKm = sport.family != SportFamily.SWIM
    var dist by remember {
        mutableStateOf(if (workout.distanceM > 0) numStr(if (distInKm) workout.distanceM / 1000 else workout.distanceM) else "")
    }
    var note by remember { mutableStateOf(workout.note) }
    val editable = remember(sport, hasTrack) { ManualEntry.editableKinds(sport, hasTrack) }
    val fields = remember { mutableStateMapOf<String, String>().apply { editable.forEach { put(it.id, numStr(workout.values[it.id])) } } }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(when { isFinish -> R.string.finish_title_done; isNew -> R.string.manual_entry; else -> R.string.edit_workout })) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SportBadge(sport, 48.dp, filled = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sport.displayName(), style = MaterialTheme.typography.titleMedium)
                        Text(formatDateTime(start), style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                    }
                }
                if (!hasTrack) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChipButton(stringResource(R.string.pick_date), false, color, Icons.Filled.CalendarMonth) { showDate = true }
                        ChipButton(stringResource(R.string.pick_time), false, color, Icons.Filled.Schedule) { showTime = true }
                    }
                }
            }
            DCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.metric_duration), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(hh, { hh = it }, stringResource(R.string.unit_hour), Modifier.weight(1f), enabled = !hasTrack, decimal = false)
                    NumField(mm, { mm = it }, stringResource(R.string.unit_minute), Modifier.weight(1f), enabled = !hasTrack, decimal = false)
                    NumField(ss, { ss = it }, stringResource(R.string.unit_second), Modifier.weight(1f), enabled = !hasTrack, decimal = false)
                }
                if (showDistance) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.metric_distance), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    NumField(dist, { dist = it }, stringResource(if (distInKm) R.string.unit_km else R.string.unit_m), Modifier.fillMaxWidth(), enabled = !hasTrack)
                }
            }
            if (editable.isNotEmpty()) {
                DCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.edit_metrics), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    editable.forEach { def ->
                        MetricInput(def, fields[def.id] ?: "", { fields[def.id] = it }, color)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            DCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    note, { note = it }, label = { Text(stringResource(R.string.metric_note)) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
            }
            Button(
                onClick = {
                    val dur = (hh.toLongOrNull() ?: 0) * 3600 + (mm.toLongOrNull() ?: 0) * 60 + (ss.toLongOrNull() ?: 0)
                    val dm = (dist.toDoubleOrNull() ?: 0.0) * if (distInKm) 1000 else 1
                    onSave(EditResult(start, if (hasTrack) workout.durationSec else dur, if (hasTrack) workout.distanceM else dm,
                        editable.associate { it.id to fields[it.id]?.toDoubleOrNull() }, note))
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color),
            ) {
                Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneId.systemDefault())
        val dp = rememberDatePickerState(initialSelectedDateMillis = ldt.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { sel ->
                        val date = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                        start = date.atTime(ldt.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(dp) }
    }
    if (showTime) {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneId.systemDefault())
        val tp = rememberTimePickerState(ldt.hour, ldt.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    start = ldt.withHour(tp.hour).withMinute(tp.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(tp) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricInput(def: MetricDef, value: String, onChange: (String) -> Unit, color: androidx.compose.ui.graphics.Color) {
    when (def.kind.unit) {
        MetricUnit.CHOICE -> {
            Text(def.label(), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwimStroke.entries.forEach { s ->
                    ChipButton(stringResource(s.labelRes()), value == s.ordinal.toString(), color) {
                        onChange(if (value == s.ordinal.toString()) "" else s.ordinal.toString())
                    }
                }
            }
        }
        MetricUnit.RPE -> {
            val v = value.toFloatOrNull() ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(def.label(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(if (v > 0) "${v.toInt()}/10" else "—", style = MaterialTheme.typography.labelLarge, color = color)
            }
            Slider(v, { onChange(it.toInt().toString()) }, valueRange = 0f..10f, steps = 9)
        }
        else -> {
            val unit = def.customUnit ?: (if (def.kind == app.dongyu.model.MetricKind.LAPS) R.string.unit_lengths else def.kind.unit.unitRes())?.let { stringResource(it) } ?: ""
            OutlinedTextField(
                value, { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(8)) },
                label = { Text(def.label()) },
                leadingIcon = { Icon(SportIcons.metric(def.kind), null, tint = color) },
                suffix = { Text(unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NumField(v: String, on: (String) -> Unit, suffix: String, modifier: Modifier, enabled: Boolean = true, decimal: Boolean = true) {
    OutlinedTextField(
        v, { s -> on(s.filter { it.isDigit() || (decimal && it == '.') }.take(7)) },
        suffix = { Text(suffix) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
