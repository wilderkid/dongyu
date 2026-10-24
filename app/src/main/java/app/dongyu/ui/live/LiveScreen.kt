package app.dongyu.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import app.dongyu.recording.LiveState
import app.dongyu.recording.RecStatus
import app.dongyu.ui.charts.drawTrack
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.composeColor
import app.dongyu.ui.components.displayName
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors

private data class LiveMetric(val icon: ImageVector, val value: String, val unit: String, val label: String)

@Composable
fun LiveScreen(
    state: LiveState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onLength: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val sport = state.sport ?: return
    val color = sport.composeColor()
    val paused = state.status == RecStatus.PAUSED || state.status == RecStatus.AUTO_PAUSED
    var confirmStop by remember { mutableStateOf(false) }
    val pool = state.swimMode == SwimMode.POOL

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // colored glow at the top
        Box(Modifier.fillMaxWidth().height(380.dp).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Icon(SportIcons.of(sport.icon), null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(sport.displayName(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.barometer) StatusChip(Icons.Filled.Compress, stringResource(R.string.barometer), color)
                if (state.usesGps) {
                    Spacer(Modifier.width(6.dp))
                    GpsChip(state)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                StatusPill(state.status)
                Spacer(Modifier.height(12.dp))
                val (big, bigUnit, bigLabel) = primary(state)
                Text(big, style = NumberStyle.Huge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Text(
                    if (bigUnit.isNotEmpty()) "$bigLabel · $bigUnit" else bigLabel,
                    style = MaterialTheme.typography.titleSmall, color = extraColors.subtle,
                )
                Spacer(Modifier.height(22.dp))
                val metrics = secondary(state, sport)
                metrics.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { m -> MetricBox(m, color, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (pool) {
                    Spacer(Modifier.height(6.dp))
                    LengthCounter(state.lengths, color, onLength)
                } else if (state.usesGps) {
                    LivePath(state.path, color)
                } else {
                    Text(stringResource(R.string.live_timer_hint), style = MaterialTheme.typography.bodySmall, color = extraColors.subtle, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
            }
            // Controls
            Row(
                Modifier.fillMaxWidth().padding(bottom = 22.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(paused) {
                    RoundButton(Icons.Filled.Stop, MaterialTheme.colorScheme.error, 72.dp, stringResource(R.string.finish)) { confirmStop = true }
                }
                RoundButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, color, 92.dp,
                    stringResource(if (paused) R.string.action_resume else R.string.action_pause),
                ) { if (paused) onResume() else onPause() }
            }
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = { Text(stringResource(R.string.finish_message)) },
            confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text(stringResource(R.string.finish_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmStop = false; onDiscard() }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

@Composable
private fun primary(s: LiveState): Triple<String, String, String> = when {
    s.swimMode == SwimMode.POOL -> Triple(Fmt.num(s.distanceM), stringResource(R.string.unit_m), stringResource(R.string.metric_distance))
    s.usesGps -> Triple(Fmt.num(s.distanceM / 1000.0, 2), stringResource(R.string.unit_km), stringResource(R.string.metric_distance))
    else -> Triple(Pace.formatDuration(s.movingMs / 1000), "", stringResource(R.string.metric_duration))
}

@Composable
private fun secondary(s: LiveState, sport: SportType): List<LiveMetric> {
    val out = mutableListOf<LiveMetric>()
    val dur = LiveMetric(SportIcons.metric(MetricKind.DURATION), Pace.formatDuration(s.movingMs / 1000), "", stringResource(R.string.metric_duration))
    when {
        s.swimMode == SwimMode.POOL -> {
            out += dur
            out += LiveMetric(SportIcons.metric(MetricKind.SWIM_PACE), Pace.format(s.swimPace), stringResource(R.string.unit_per_100m), stringResource(R.string.metric_swim_pace))
            out += LiveMetric(SportIcons.metric(MetricKind.LAPS), s.lengths.toString(), stringResource(R.string.unit_lengths), stringResource(R.string.metric_laps))
            out += LiveMetric(SportIcons.metric(MetricKind.POOL_LENGTH), Fmt.num(s.poolLength), stringResource(R.string.unit_m), stringResource(R.string.metric_pool_len))
        }
        s.usesGps -> {
            out += dur
            when (sport.family) {
                SportFamily.CYCLE -> {
                    out += LiveMetric(SportIcons.metric(MetricKind.SPEED), s.currentSpeedKmh?.let { Fmt.num(it, 1) } ?: "--", stringResource(R.string.unit_kmh), stringResource(R.string.live_current_speed))
                    out += LiveMetric(SportIcons.metric(MetricKind.SPEED), s.avgSpeedKmh?.let { Fmt.num(it, 1) } ?: "--", stringResource(R.string.unit_kmh), stringResource(R.string.live_avg_speed))
                }
                SportFamily.MOUNTAINEER -> {
                    out += LiveMetric(SportIcons.metric(MetricKind.MAX_ALTITUDE), s.altitude?.let { Fmt.num(it) } ?: "--", stringResource(R.string.unit_m), stringResource(R.string.live_altitude))
                    val rate = if (s.movingMs > 60_000) s.elevationGain / (s.movingMs / 3_600_000.0) else null
                    out += LiveMetric(SportIcons.metric(MetricKind.ASCENT_RATE), rate?.let { Fmt.num(it) } ?: "--", stringResource(R.string.unit_m_per_h), stringResource(R.string.metric_ascent_rate))
                }
                SportFamily.SWIM -> {
                    out += LiveMetric(SportIcons.metric(MetricKind.SWIM_PACE), Pace.format(s.swimPace), stringResource(R.string.unit_per_100m), stringResource(R.string.metric_swim_pace))
                }
                else -> {
                    out += LiveMetric(SportIcons.metric(MetricKind.PACE), Pace.format(s.currentPace), stringResource(R.string.unit_per_km), stringResource(R.string.live_current_pace))
                    out += LiveMetric(SportIcons.metric(MetricKind.PACE), Pace.format(s.avgPace), stringResource(R.string.unit_per_km), stringResource(R.string.live_avg_pace))
                }
            }
            if (sport.family != SportFamily.SWIM) {
                out += LiveMetric(SportIcons.metric(MetricKind.ELEVATION_GAIN), Fmt.num(s.elevationGain), stringResource(R.string.unit_m), stringResource(R.string.metric_elev_gain))
            }
            if (sport.family == SportFamily.RUN || sport.family == SportFamily.HIKE) {
                out += LiveMetric(SportIcons.metric(MetricKind.MAX_ALTITUDE), s.altitude?.let { Fmt.num(it) } ?: "--", stringResource(R.string.unit_m), stringResource(R.string.live_altitude))
                if (sport.family == SportFamily.RUN) out += LiveMetric(SportIcons.metric(MetricKind.PACE), Pace.format(s.lastSplitPace), stringResource(R.string.unit_per_km), stringResource(R.string.live_last_km))
            }
        }
        else -> {} // timer only: the big number is the duration
    }
    return out
}

@Composable
private fun MetricBox(m: LiveMetric, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(extraColors.card).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(m.label, style = MaterialTheme.typography.labelMedium, color = extraColors.subtle, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(m.value, style = NumberStyle.Large)
            if (m.unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(m.unit, style = MaterialTheme.typography.labelMedium, color = extraColors.subtle, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun StatusPill(status: RecStatus) {
    val (text, c) = when (status) {
        RecStatus.RECORDING -> stringResource(R.string.rec_recording) to Brand.Up
        RecStatus.PAUSED -> stringResource(R.string.rec_paused) to MaterialTheme.colorScheme.secondary
        RecStatus.AUTO_PAUSED -> stringResource(R.string.rec_auto_paused) to MaterialTheme.colorScheme.secondary
        RecStatus.IDLE -> "" to Color.Gray
    }
    val alpha = if (status == RecStatus.RECORDING && !LocalInspectionMode.current) {
        rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    } else 1f
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.copy(alpha = alpha)))
            Spacer(Modifier.width(6.dp))
            Text(text, color = c, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private typealias Brand = app.dongyu.ui.theme.Brand

@Composable
private fun StatusChip(icon: ImageVector, text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = extraColors.card) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun GpsChip(s: LiveState) {
    val acc = s.gpsAccuracy
    val (c, label) = when {
        !s.hasFix || acc == null -> MaterialTheme.colorScheme.error to stringResource(R.string.gps_searching)
        acc <= 10 -> Brand.Up to stringResource(R.string.gps_good)
        acc <= 25 -> Brand.Sun to stringResource(R.string.gps_fair)
        else -> MaterialTheme.colorScheme.error to stringResource(R.string.gps_weak)
    }
    StatusChip(if (s.hasFix) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed, label, c)
}

@Composable
private fun LivePath(path: List<Pair<Double, Double>>, color: Color) {
    val bg = extraColors.card
    val grid = extraColors.grid
    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(22.dp)).background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(grid, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y)); y += step }
            drawTrack(path, color, 4.dp.toPx(), 20.dp.toPx())
        }
        if (path.size < 2) Text(stringResource(R.string.live_waiting_track), Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
    }
}

@Composable
private fun LengthCounter(lengths: Int, color: Color, onLength: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledIconButton(
            onClick = { onLength(-1) }, enabled = lengths > 0, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = extraColors.card, contentColor = color),
        ) { Icon(Icons.Filled.Remove, stringResource(R.string.length_minus)) }
        Surface(
            onClick = { onLength(1) }, shape = RoundedCornerShape(28.dp), color = color,
            modifier = Modifier.height(96.dp).width(200.dp).shadow(8.dp, RoundedCornerShape(28.dp), spotColor = color),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Add, null, tint = Color.White)
                Text(stringResource(R.string.length_plus), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, color: Color, size: Dp, desc: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape, color = color,
        modifier = Modifier.size(size).shadow(12.dp, CircleShape, spotColor = color),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(size * 0.45f)) }
    }
}

/** Shown before the session starts: explains GPS / permissions and offers a big start button. */
@Composable
fun PreStartScreen(
    sport: SportType,
    swimMode: SwimMode?,
    needsPermission: Boolean,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val color = sport.composeColor()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(listOf(color.copy(alpha = 0.4f), Color.Transparent))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            }
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(120.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(SportIcons.of(sport.icon), null, tint = color, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(sport.displayName(), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            val hint = when {
                swimMode == SwimMode.POOL -> R.string.prestart_pool
                needsPermission -> R.string.prestart_permission
                sport.usesGps || swimMode == SwimMode.OPEN_WATER -> R.string.prestart_gps
                else -> R.string.prestart_timer
            }
            Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = extraColors.subtle, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
            Spacer(Modifier.weight(1f))
            RoundButton(Icons.Filled.PlayArrow, color, 110.dp, stringResource(R.string.start_recording), onStart)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.start_recording), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(40.dp))
        }
    }
}
