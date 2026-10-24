package app.dongyu.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.stats.MetricComparison
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.PersonalRecord
import app.dongyu.domain.stats.Totals
import app.dongyu.domain.stats.TrendPoint
import app.dongyu.model.DerivedKeys
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.MetricUnit
import app.dongyu.model.SportType
import app.dongyu.ui.charts.LineChart
import app.dongyu.ui.charts.StackSegment
import app.dongyu.ui.charts.StackedBarChart
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.DeltaPill
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.IconBadge
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.components.SegmentedTabs
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.composeColor
import app.dongyu.ui.components.displayName
import app.dongyu.ui.components.formatMetric
import app.dongyu.ui.components.label
import app.dongyu.ui.components.prLabel
import app.dongyu.ui.theme.Brand
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors
import java.time.format.DateTimeFormatter
import app.dongyu.model.Aggregation

data class MetricTrend(val metric: MetricDef, val points: List<TrendPoint>)

data class SportStatsState(
    val sport: SportType,
    val allTime: Totals,
    val bucket: Period = Period.WEEK,
    val records: List<PersonalRecord> = emptyList(),
    val comparison: List<MetricComparison> = emptyList(),
    val trends: List<MetricTrend> = emptyList(),
)

@Composable
fun SportStatsScreen(state: SportStatsState, onBucket: (Period) -> Unit, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val sport = state.sport
    val color = sport.composeColor()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Hero(state, onBack) }
        if (state.records.isNotEmpty()) item { RecordsCard(state.records, sport, onOpenWorkout) }
        if (state.comparison.any { it.current != null || it.previous != null }) item { CompareCard(state.comparison, color) }
        item {
            SegmentedTabs(
                listOf(stringResource(R.string.bucket_weekly), stringResource(R.string.bucket_monthly)),
                if (state.bucket == Period.WEEK) 0 else 1, { onBucket(if (it == 0) Period.WEEK else Period.MONTH) },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        items(state.trends, key = { it.metric.id }) { t -> TrendCard(t, color, state.bucket) }
    }
}

@Composable
private fun Hero(state: SportStatsState, onBack: () -> Unit) {
    val sport = state.sport
    val color = sport.composeColor()
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f))))
            .statusBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Text(stringResource(R.string.sport_stats_title), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleSmall)
            }
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    Icon(SportIcons.of(sport.icon), null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(sport.displayName(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(stringResource(R.string.all_time), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(16.dp))
            val t = state.allTime
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroNum(t.count.toString(), stringResource(R.string.stat_workouts))
                HeroNum(Fmt.hours(t.durationSec), stringResource(R.string.unit_hours))
                if (t.distanceM > 0) HeroNum(Fmt.km(t.distanceM), stringResource(R.string.unit_km))
                if (t.elevationGain > 0) HeroNum(Fmt.num(t.elevationGain), stringResource(R.string.metric_elev_gain_m))
                else HeroNum(Fmt.num(t.calories), stringResource(R.string.unit_kcal))
            }
        }
    }
}

@Composable
private fun HeroNum(v: String, l: String) {
    Column {
        Text(v, style = NumberStyle.Medium, color = Color.White)
        Text(l, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

private fun prValueDef(key: String, sport: SportType): MetricDef? = when {
    key.startsWith("best_") && key in setOf(DerivedKeys.BEST_1K, DerivedKeys.BEST_5K, DerivedKeys.BEST_10K, DerivedKeys.BEST_HALF) -> MetricDef.of(MetricKind.DURATION)
    else -> sport.metrics.firstOrNull { it.id == key.substringAfter('_') }
}

@Composable
private fun RecordsCard(records: List<PersonalRecord>, sport: SportType, onOpen: (Long) -> Unit) {
    val fmt = DateTimeFormatter.ofPattern(stringResource(R.string.fmt_date))
    DCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(stringResource(R.string.personal_records))
        records.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { pr ->
                    val def = prValueDef(pr.key, sport)
                    val f = def?.let { formatMetric(it, pr.value) }
                    DCard(Modifier.weight(1f).padding(vertical = 5.dp), padding = 12.dp, onClick = { onOpen(pr.workoutId) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.EmojiEvents, Brand.Sun, 28.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(prLabel(pr.key, sport), style = MaterialTheme.typography.labelMedium, color = extraColors.subtle, maxLines = 1)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(f?.value ?: Fmt.num(pr.value), style = NumberStyle.Medium)
                            if (!f?.unit.isNullOrEmpty()) Text(" " + f!!.unit, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.padding(bottom = 3.dp))
                        }
                        Text(pr.date.format(fmt), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompareCard(items: List<MetricComparison>, color: Color) {
    DCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(stringResource(R.string.month_vs_last))
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1.3f))
            Text(stringResource(R.string.this_month), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = color)
            Text(stringResource(R.string.last_month), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            Spacer(Modifier.width(56.dp))
        }
        items.filter { it.current != null || it.previous != null }.forEach { mc ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SportIcons.metric(mc.metric.kind), null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(mc.metric.label(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                val cur = mc.current?.let { formatMetric(mc.metric, it) }
                val prev = mc.previous?.let { formatMetric(mc.metric, it) }
                Text(cur?.let { it.value + " " + it.unit } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(prev?.let { it.value + " " + it.unit } ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = extraColors.subtle)
                Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
                    val d = if (mc.current != null && mc.previous != null && mc.previous!! > 0) (mc.current!! - mc.previous!!) / mc.previous!! else null
                    DeltaPill(d, invert = mc.metric.kind.lowerIsBetter)
                }
            }
        }
    }
}

@Composable
private fun TrendCard(t: MetricTrend, color: Color, bucket: Period) {
    val def = t.metric
    val fmtLabel = stringResource(R.string.fmt_month_day_pattern)
    val xl = t.points.map { it.bucketStart.format(DateTimeFormatter.ofPattern(if (bucket == Period.MONTH) stringResource(R.string.fmt_month_pattern) else fmtLabel)) }
    val latest = t.points.lastOrNull()?.value
    DCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(SportIcons.metric(def.kind), color, 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(def.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            latest?.let { v ->
                val f = formatMetric(def, v)
                Text(f.value, style = NumberStyle.Small)
                if (f.unit.isNotEmpty()) Text(" " + f.unit, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            }
        }
        Spacer(Modifier.height(8.dp))
        val yfmt: (Float) -> String = { v ->
            when (def.kind.unit) {
                MetricUnit.SECONDS -> if (def.aggregation == Aggregation.SUM) Fmt.num(v / 3600.0, 1) + "h" else Pace.formatDuration(v.toLong())
                MetricUnit.PACE_KM, MetricUnit.PACE_100M -> Pace.format(v.toDouble())
                MetricUnit.METERS -> if (def.kind == MetricKind.DISTANCE) Fmt.num(v / 1000.0, if (v < 10000) 1 else 0) else Fmt.num(v.toDouble())
                else -> Fmt.num(v.toDouble(), if (v < 10 && v % 1 != 0f) 1 else 0)
            }
        }
        if (def.aggregation == Aggregation.SUM) {
            StackedBarChart(t.points.map { listOf(StackSegment(it.value.toFloat(), color)) }, xl, height = 140.dp, yFormatter = yfmt)
        } else {
            LineChart(t.points.map { it.value.toFloat() }, color, height = 140.dp, xLabels = xl, invert = def.kind.lowerIsBetter, yFormatter = yfmt)
        }
    }
}
