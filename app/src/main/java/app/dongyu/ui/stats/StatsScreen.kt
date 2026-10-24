package app.dongyu.ui.stats

import androidx.compose.material.icons.filled.IosShare

import app.dongyu.domain.stats.HrTrendPoint
import androidx.compose.material.icons.filled.Favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dongyu.R
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.stats.Comparison
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.SportShare
import app.dongyu.domain.stats.Streaks
import app.dongyu.domain.stats.Totals
import app.dongyu.domain.stats.WeekStack
import app.dongyu.model.SportType
import app.dongyu.ui.charts.CalendarHeatmap
import app.dongyu.ui.charts.DonutChart
import app.dongyu.ui.charts.DonutSlice
import app.dongyu.ui.charts.StackSegment
import app.dongyu.ui.charts.StackedBarChart
import app.dongyu.ui.components.DCard
import app.dongyu.ui.components.DeltaPill
import app.dongyu.ui.components.EmptyState
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.IconBadge
import app.dongyu.ui.components.SectionTitle
import app.dongyu.ui.components.SegmentedTabs
import app.dongyu.ui.components.SportBadge
import app.dongyu.ui.components.composeColor
import app.dongyu.ui.components.displayName
import app.dongyu.ui.theme.Brand
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors
import java.time.LocalDate

data class SportRow(val sport: SportType, val totals: Totals)

data class StatsState(
    val period: Period = Period.WEEK,
    val today: LocalDate = LocalDate.now(),
    val comparison: Comparison? = null,
    val streaks: Streaks? = null,
    val heatmap: Map<LocalDate, Double> = emptyMap(),
    val shares: List<SportShare> = emptyList(),
    val weekly: List<WeekStack> = emptyList(),
    val sports: List<SportType> = emptyList(),
    val perSport: List<SportRow> = emptyList(),
    val hasData: Boolean = false,
    val hrZones: LongArray? = null,
    val hrTrend: List<HrTrendPoint> = emptyList(),
    val hrTrendMonthly: Boolean = false,
    val maxHr: Int = 190,
)

@Composable
fun StatsScreen(state: StatsState, onPeriod: (Period) -> Unit, onOpenSport: (Long) -> Unit, onShareReport: () -> Unit = {}) {
    val byId = state.sports.associateBy { it.id }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_stats), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.FilledTonalIconButton(onClick = onShareReport) {
                    Icon(androidx.compose.material.icons.Icons.Filled.IosShare, stringResource(R.string.report_share))
                }
            }
        }
        item {
            SegmentedTabs(
                listOf(stringResource(R.string.period_week), stringResource(R.string.period_month), stringResource(R.string.period_year)),
                state.period.ordinal, { onPeriod(Period.entries[it]) }, Modifier.fillMaxWidth(),
            )
        }
        if (!state.hasData) {
            item { EmptyState(Icons.Filled.FitnessCenter, stringResource(R.string.stats_empty)) }
            return@LazyColumn
        }
        state.comparison?.let { c -> item { TotalsCard(c, state.period) } }
        state.streaks?.let { s -> item { StreakCard(s) } }
        item {
            DCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.stats_heatmap))
                val monthFmt = stringResource(R.string.fmt_month_short)
                CalendarHeatmap(state.heatmap, state.today, 26, MaterialTheme.colorScheme.primary, { monthLabel(monthFmt, it.monthValue) })
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.less), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                    listOf(0f, 0.3f, 0.5f, 0.75f, 1f).forEach { a ->
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.size(10.dp).background(if (a == 0f) extraColors.heat0 else MaterialTheme.colorScheme.primary.copy(alpha = a), RoundedCornerShape(3.dp)))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.more), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                }
            }
        }
        if (state.shares.isNotEmpty()) item {
            DCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.stats_time_by_sport))
                val total = state.shares.sumOf { it.durationSec }
                DonutChart(
                    state.shares.mapNotNull { sh ->
                        byId[sh.sportId]?.let { DonutSlice(sh.durationSec.toFloat(), it.composeColor(), it.displayName(), Fmt.hm(sh.durationSec)) }
                    },
                    centerValue = Fmt.hours(total), centerLabel = stringResource(R.string.unit_hours),
                )
            }
        }
        if (state.hrZones != null || state.hrTrend.isNotEmpty()) item { HrStatsCard(state.hrZones, state.hrTrend, state.hrTrendMonthly, state.maxHr) }
        item {
            DCard(Modifier.fillMaxWidth()) {
                SectionTitle(stringResource(R.string.stats_weekly_volume))
                val order = state.sports.map { it.id }
                StackedBarChart(
                    bars = state.weekly.map { wk ->
                        order.mapNotNull { id -> wk.bySport[id]?.let { m -> StackSegment((m / 60).toFloat(), byId[id]!!.composeColor()) } }
                    },
                    labels = state.weekly.map { stringResource(R.string.fmt_month_day, it.weekStart.monthValue, it.weekStart.dayOfMonth) },
                    yFormatter = { Fmt.num(it.toDouble(), 0) + "h" },
                )
                Legend(state.sports.filter { s -> state.weekly.any { it.bySport.containsKey(s.id) } })
            }
        }
        item { SectionTitle(stringResource(R.string.stats_by_sport)) }
        items(state.perSport, key = { it.sport.id }) { row -> SportStatRow(row, state.period) { onOpenSport(row.sport.id) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(sports: List<SportType>) {
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sports.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(s.composeColor(), RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(5.dp))
                Text(s.displayName(), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
            }
        }
    }
}

@Composable
private fun periodLabelRes(p: Period) = when (p) {
    Period.WEEK -> R.string.vs_prev_week
    Period.MONTH -> R.string.vs_prev_month
    Period.YEAR -> R.string.vs_prev_year
}

@Composable
private fun TotalsCard(c: Comparison, period: Period) {
    val t = c.current
    DCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.stats_totals), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(periodLabelRes(period)), style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
        }
        Spacer(Modifier.height(10.dp))
        val cells = listOf(
            Cell(Icons.Filled.Timer, Brand.Teal, Pace.formatDuration(t.durationSec), "", stringResource(R.string.metric_duration), c.delta { it.durationSec.toDouble() }),
            Cell(Icons.Filled.Route, Brand.Coral, Fmt.km(t.distanceM), stringResource(R.string.unit_km), stringResource(R.string.metric_distance), c.delta { it.distanceM }),
            Cell(Icons.Filled.FitnessCenter, Brand.Ocean, t.count.toString(), stringResource(R.string.unit_times), stringResource(R.string.stat_workouts), c.delta { it.count.toDouble() }),
            Cell(Icons.Filled.LocalFireDepartment, Brand.Down, Fmt.num(t.calories), stringResource(R.string.unit_kcal), stringResource(R.string.metric_calories), c.delta { it.calories }),
            Cell(Icons.Filled.Terrain, Color(0xFF7C5CFF), Fmt.num(t.elevationGain), stringResource(R.string.unit_m), stringResource(R.string.metric_elev_gain), c.delta { it.elevationGain }),
            Cell(Icons.Filled.EventAvailable, Brand.Up, t.activeDays.toString(), stringResource(R.string.unit_days), stringResource(R.string.stat_active_days), c.delta { it.activeDays.toDouble() }),
        )
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                row.forEach { cell -> TotalCell(cell, Modifier.weight(1f)) }
            }
        }
    }
}

private data class Cell(val icon: ImageVector, val color: Color, val value: String, val unit: String, val label: String, val delta: Double?)

@Composable
private fun TotalCell(c: Cell, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconBadge(c.icon, c.color, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(c.value, style = NumberStyle.Medium)
                if (c.unit.isNotEmpty()) Text(" " + c.unit, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.padding(bottom = 3.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.label, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                Spacer(Modifier.width(4.dp))
                DeltaPill(c.delta)
            }
        }
    }
}

@Composable
private fun StreakCard(s: Streaks) {
    DCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.stats_streaks))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StreakItem(Icons.Filled.LocalFireDepartment, Brand.Coral, s.currentDays, stringResource(R.string.unit_days), stringResource(R.string.streak_current_days))
            StreakItem(Icons.Filled.WorkspacePremium, Brand.Sun, s.longestDays, stringResource(R.string.unit_days), stringResource(R.string.streak_longest_days))
            StreakItem(Icons.Filled.CalendarMonth, Brand.Teal, s.currentWeeks, stringResource(R.string.unit_weeks), stringResource(R.string.streak_current_weeks))
            StreakItem(Icons.Filled.EventAvailable, Brand.Ocean, s.longestWeeks, stringResource(R.string.unit_weeks), stringResource(R.string.streak_longest_weeks))
        }
    }
}

@Composable
private fun StreakItem(icon: ImageVector, color: Color, v: Int, unit: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, color, 44.dp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v.toString(), style = NumberStyle.Medium)
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
    }
}

@Composable
private fun SportStatRow(row: SportRow, period: Period, onClick: () -> Unit) {
    DCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportBadge(row.sport, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.sport.displayName(), style = MaterialTheme.typography.titleSmall)
                val t = row.totals
                val parts = buildList {
                    add(stringResource(R.string.fmt_count_times, t.count))
                    add(Pace.formatDuration(t.durationSec))
                    if (t.distanceM > 0) add(Fmt.km(t.distanceM) + " " + stringResource(R.string.unit_km))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = extraColors.subtle)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = extraColors.subtle)
        }
    }
}

@Composable
fun HrStatsCard(zones: LongArray?, trend: List<HrTrendPoint>, monthly: Boolean, maxHr: Int) {
    DCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.hr_title), action = {
            app.dongyu.ui.components.IconBadge(Icons.Filled.Favorite, app.dongyu.ui.hr.HrColors.heart, 30.dp)
        })
        if (zones != null && zones.sum() > 0) {
            Text(stringResource(R.string.hr_zone_distribution), style = MaterialTheme.typography.labelLarge, color = extraColors.subtle)
            Spacer(Modifier.height(8.dp))
            val names = (0 until 5).map { app.dongyu.ui.hr.zoneName(it) }
            DonutChart(
                zones.mapIndexed { z, sec -> DonutSlice(sec.toFloat(), app.dongyu.ui.hr.HrColors.zones[z], "Z${z + 1} " + names[z], Fmt.hm(sec)) }.reversed(),
                centerValue = Fmt.hours(zones.sum()), centerLabel = stringResource(R.string.unit_hours), size = 130.dp,
            )
        }
        if (trend.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.hr_trend), style = MaterialTheme.typography.labelLarge, color = extraColors.subtle)
            Spacer(Modifier.height(6.dp))
            val monthFmt = stringResource(R.string.fmt_month_short)
            app.dongyu.ui.hr.HrTrendChart(trend, trend.map { if (monthly) monthLabel(monthFmt, it.bucketStart.monthValue) else "${it.bucketStart.monthValue}/${it.bucketStart.dayOfMonth}" })
        }
    }
}

/** zh uses "%d月"; other locales get the localized short month name (Apr, May…). */
fun monthLabel(fmt: String, month: Int): String =
    if (fmt.contains("%d月")) fmt.format(month)
    else java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
