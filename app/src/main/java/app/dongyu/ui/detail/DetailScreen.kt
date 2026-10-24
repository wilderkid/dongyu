package app.dongyu.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.hr.HrSample
import app.dongyu.domain.hr.HrZones
import app.dongyu.domain.metrics.Pace
import app.dongyu.ui.hr.HrDetailCard
import app.dongyu.domain.metrics.SeriesPoint
import app.dongyu.domain.metrics.Split
import app.dongyu.model.DerivedKeys
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import app.dongyu.ui.charts.HBar
import app.dongyu.ui.charts.LineChart
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.IconBadge
import app.dongyu.ui.components.MetricTile
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.components.SportBadge
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.TrackMap
import app.dongyu.ui.components.composeColor
import app.dongyu.ui.components.displayName
import app.dongyu.ui.components.formatDateTime
import app.dongyu.ui.components.formatMetric
import app.dongyu.ui.components.label
import app.dongyu.ui.components.prLabel
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors

data class DetailState(
    val workout: Workout,
    val sport: SportType,
    val path: List<Pair<Double, Double>> = emptyList(),
    val splits: List<Split> = emptyList(),
    val series: List<SeriesPoint> = emptyList(),
    val hrSamples: List<HrSample> = emptyList(),
    val hrMin: Int? = null,
    val hrZones: LongArray? = null,
    val maxHr: Int = 190,
) {
    val hasHr: Boolean get() = hrSamples.isNotEmpty() || hrZones != null
}

@Composable
fun DetailScreen(state: DetailState, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, dark: Boolean = isSystemInDarkTheme(), onShare: () -> Unit = {}) {
    val w = state.workout
    val sport = state.sport
    val color = sport.composeColor()
    var confirmDelete by remember { mutableStateOf(false) }
    val hasMap = state.path.size >= 2
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (hasMap) {
                TrackMap(state.path, color, Modifier.fillMaxWidth().height(300.dp), dark = dark)
            } else {
                Box(Modifier.fillMaxWidth().height(150.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent))))
            }
            Column(Modifier.padding(horizontal = 16.dp).offset(y = if (hasMap) (-28).dp else (-60).dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderCard(w, sport)
                MetricsCard(w, sport, skipHr = state.hasHr)
                if (state.hasHr) HrDetailCard(
                    avg = w.values[HrZones.AVG_KEY]?.toInt(), peak = w.values[HrZones.MAX_KEY]?.toInt(), min = state.hrMin,
                    samples = state.hrSamples, zoneSec = state.hrZones, maxHr = state.maxHr,
                )
                if (state.splits.isNotEmpty()) SplitsCard(state.splits, sport)
                if (state.series.isNotEmpty()) ChartsCard(state.series, sport)
                val records = w.values.filterKeys { it.startsWith("best_") }
                if (records.isNotEmpty()) BestEffortsCard(records, sport)
                if (w.note.isNotBlank()) {
                    DCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Top) {
                            IconBadge(Icons.Filled.StickyNote2, color, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(w.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
            val btn = IconButtonDefaults.filledTonalIconButtonColors(containerColor = extraColors.card.copy(alpha = 0.92f))
            FilledTonalIconButton(onClick = onBack, colors = btn) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = onShare, colors = btn) { Icon(Icons.Filled.Share, stringResource(R.string.share_action)) }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onEdit, colors = btn) { Icon(Icons.Filled.Edit, stringResource(R.string.edit_workout)) }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { confirmDelete = true }, colors = btn) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun HeaderCard(w: Workout, sport: SportType) {
    DCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(sport, 50.dp, filled = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sport.displayName(), style = MaterialTheme.typography.titleLarge)
                Text(formatDateTime(w.startTime), style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (w.distanceM > 0) {
                val d = formatMetric(MetricDef.of(MetricKind.DISTANCE), w.distanceM)
                Big(d.value, d.unit, stringResource(R.string.metric_distance))
            }
            Big(Pace.formatDuration(w.durationSec), "", stringResource(R.string.metric_duration))
            val third = when {
                sport.family == SportFamily.SWIM -> w.values[MetricKind.SWIM_PACE.key]?.let { Triple(Pace.format(it), stringResource(R.string.unit_per_100m), stringResource(R.string.metric_swim_pace)) }
                sport.family == SportFamily.CYCLE -> w.values[MetricKind.SPEED.key]?.let { Triple(Fmt.num(it, 1), stringResource(R.string.unit_kmh), stringResource(R.string.metric_speed)) }
                sport.family == SportFamily.MOUNTAINEER -> w.values[MetricKind.ELEVATION_GAIN.key]?.let { Triple(Fmt.num(it), stringResource(R.string.unit_m), stringResource(R.string.metric_elev_gain)) }
                sport.showsPace -> w.values[MetricKind.PACE.key]?.let { Triple(Pace.format(it), stringResource(R.string.unit_per_km), stringResource(R.string.metric_pace)) }
                else -> w.values[MetricKind.CALORIES.key]?.let { Triple(Fmt.num(it), stringResource(R.string.unit_kcal), stringResource(R.string.metric_calories)) }
            }
            third?.let { Big(it.first, it.second, it.third) }
        }
    }
}

@Composable
private fun Big(v: String, u: String, l: String) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v, style = NumberStyle.Large)
            if (u.isNotEmpty()) Text(" $u", style = MaterialTheme.typography.labelMedium, color = extraColors.subtle, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(l, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
    }
}

@Composable
private fun MetricsCard(w: Workout, sport: SportType, skipHr: Boolean = false) {
    val color = sport.composeColor()
    val defs = sport.metrics.filter { it.kind != MetricKind.NOTE && it.kind != MetricKind.DISTANCE && it.kind != MetricKind.DURATION && w.valueOf(it.id) != null &&
        !(skipHr && (it.kind == MetricKind.HEART_RATE || it.kind == MetricKind.MAX_HEART_RATE)) }
    if (defs.isEmpty()) return
    DCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.detail_metrics))
        defs.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { d ->
                    val f = formatMetric(d, w.valueOf(d.id)!!)
                    MetricTile(SportIcons.metric(d.kind), color, f.value, f.unit, d.label(), Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SplitsCard(splits: List<Split>, sport: SportType) {
    val color = sport.composeColor()
    val useSpeed = sport.family == SportFamily.CYCLE
    val swim = sport.family == SportFamily.SWIM
    val full = splits.filter { !it.partial }.ifEmpty { splits }
    val fastest = if (useSpeed) full.maxOfOrNull { it.speedKmh ?: 0.0 } else full.minOfOrNull { it.paceSecPerKm ?: Double.MAX_VALUE }
    val slowest = if (useSpeed) 0.0 else full.maxOfOrNull { it.paceSecPerKm ?: 0.0 } ?: 0.0
    DCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(if (useSpeed) R.string.detail_splits_5k else if (swim) R.string.detail_splits_100 else R.string.detail_splits))
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(stringResource(if (swim) R.string.col_100m else R.string.col_km), Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            Text(stringResource(if (useSpeed) R.string.metric_speed else R.string.metric_pace), Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.col_elev), Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
        }
        splits.forEach { s ->
            val pace = s.paceSecPerKm
            val isBest = !s.partial && if (useSpeed) s.speedKmh == fastest else pace == fastest
            val frac = if (useSpeed) ((s.speedKmh ?: 0.0) / (fastest ?: 1.0)).toFloat()
            else if (pace != null && slowest > 0) ((fastest ?: pace) / pace).toFloat().let { 0.35f + 0.65f * ((it - 0.6f) / 0.4f).coerceIn(0f, 1f) } else 0f
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val idxLabel = if (s.partial) Fmt.num(s.distanceM / (if (swim) 100.0 else if (useSpeed) 1000.0 else 1000.0), if (swim) 1 else 2) else
                    (if (useSpeed) (s.index * 5).toString() else s.index.toString())
                Text(idxLabel, Modifier.width(44.dp), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (useSpeed) Fmt.num(s.speedKmh ?: 0.0, 1) else if (swim) Pace.format(pace?.div(10)) else Pace.format(pace),
                    Modifier.width(64.dp), style = NumberStyle.Small,
                )
                Box(Modifier.weight(1f).padding(end = 8.dp)) {
                    HBar(frac, if (isBest) app.dongyu.ui.theme.Brand.Up else color, Modifier.fillMaxWidth())
                }
                Text(
                    (if (s.elevationDelta >= 0) "+" else "") + Fmt.num(s.elevationDelta),
                    Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, color = extraColors.subtle,
                )
            }
        }
    }
}

@Composable
private fun ChartsCard(series: List<SeriesPoint>, sport: SportType) {
    val color = sport.composeColor()
    val xl = series.map { Fmt.num(it.distanceKm, 1) }
    DCard(Modifier.fillMaxWidth()) {
        if (sport.family == SportFamily.CYCLE) {
            SectionTitle(stringResource(R.string.chart_speed))
            LineChart(series.map { it.speedKmh.toFloat() }, color, xLabels = xl, yFormatter = { Fmt.num(it.toDouble(), 0) })
        } else if (sport.family != SportFamily.SWIM) {
            SectionTitle(stringResource(R.string.chart_pace))
            val paces = series.map { if (it.speedKmh > 0.5) (3600 / it.speedKmh).toFloat() else Float.NaN }
            val clean = paces.map { if (it.isNaN()) paces.filter { p -> !p.isNaN() }.average().toFloat() else it.coerceAtMost(1800f) }
            LineChart(clean, color, xLabels = xl, invert = true, yFormatter = { Pace.format(it.toDouble()) })
        }
        val alts = series.mapNotNull { it.altitude?.toFloat() }
        if (alts.size == series.size && alts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionTitle(stringResource(R.string.chart_elevation))
            LineChart(alts, Color(0xFF7C5CFF), xLabels = xl, yFormatter = { Fmt.num(it.toDouble()) + "m" })
        }
    }
}

@Composable
private fun BestEffortsCard(records: Map<String, Double>, sport: SportType) {
    DCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.detail_best_efforts))
        val order = listOf(DerivedKeys.BEST_1K, DerivedKeys.BEST_5K, DerivedKeys.BEST_10K, DerivedKeys.BEST_HALF)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            order.filter { it in records }.forEach { k ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(36.dp).background(app.dongyu.ui.theme.Brand.Sun.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = app.dongyu.ui.theme.Brand.Sun, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(Pace.formatDuration(records[k]!!.toLong()), style = NumberStyle.Small)
                    Text(prLabel(k, sport), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                }
            }
        }
    }
}
