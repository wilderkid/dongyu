package app.dongyu.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun formatDateTime(ms: Long): String {
    val f = DateTimeFormatter.ofPattern(stringResource(R.string.fmt_datetime))
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(f)
}

/** Second headline metric for a workout depending on sport. */
@Composable
fun secondaryMetric(w: Workout, sport: SportType): Formatted? = when {
    sport.family == SportFamily.SWIM -> w.values[MetricKind.SWIM_PACE.key]?.let { Formatted(Pace.format(it), stringResource(R.string.unit_per_100m)) }
    sport.family == SportFamily.MOUNTAINEER || sport.family == SportFamily.HIKE ->
        w.values[MetricKind.ELEVATION_GAIN.key]?.let { Formatted("+" + Fmt.num(it), stringResource(R.string.unit_m)) }
    sport.family == SportFamily.CYCLE -> w.values[MetricKind.SPEED.key]?.let { Formatted(Fmt.num(it, 1), stringResource(R.string.unit_kmh)) }
    sport.showsPace -> w.values[MetricKind.PACE.key]?.let { Formatted(Pace.format(it), stringResource(R.string.unit_per_km)) }
    else -> w.calories.takeIf { it > 0 }?.let { Formatted(Fmt.num(it), stringResource(R.string.unit_kcal)) }
}

@Composable
fun WorkoutRow(w: Workout, sport: SportType?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DCard(modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sport != null) SportBadge(sport, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sport?.displayName() ?: "—", style = MaterialTheme.typography.titleSmall)
                Text(formatDateTime(w.startTime), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                    if (w.distanceM > 0) {
                        val d = formatMetric(app.dongyu.model.MetricDef.of(MetricKind.DISTANCE), w.distanceM)
                        Stat(d.value, d.unit)
                    }
                    Stat(Pace.formatDuration(w.durationSec), "")
                    if (sport != null) secondaryMetric(w, sport)?.let { Stat(it.value, it.unit) }
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = extraColors.subtle)
        }
    }
}

@Composable
private fun Stat(v: String, u: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(v, style = NumberStyle.Small)
        if (u.isNotEmpty()) Text(" $u", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
    }
}
