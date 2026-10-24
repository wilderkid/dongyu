package app.dongyu.ui.share

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.Color
import app.dongyu.R
import app.dongyu.domain.hr.HrSample
import app.dongyu.domain.hr.HrSampling
import app.dongyu.domain.hr.HrZones
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.stats.Period
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import app.dongyu.share.CardMetric
import app.dongyu.share.ReportCardData
import app.dongyu.share.ReportSportRow
import app.dongyu.share.ReportSummary
import app.dongyu.share.WorkoutCardData
import app.dongyu.ui.components.Fmt
import app.dongyu.ui.components.SportIcons
import app.dongyu.ui.components.displayName
import app.dongyu.ui.components.formatMetric
import app.dongyu.ui.components.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Turns domain objects into localized, ready-to-draw card data. */
class ShareCardFactory(private val ctx: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    private fun s(id: Int) = ctx.getString(id)
    private val locale get() = ctx.resources.configuration.locales[0]
    private fun fmt(id: Int) = DateTimeFormatter.ofPattern(s(id), locale)

    fun workout(w: Workout, sport: SportType, path: List<Pair<Double, Double>>, hr: List<HrSample>): WorkoutCardData {
        val name = sport.displayName(::s)
        val color = Color(sport.color)
        val hero = if (w.distanceM > 0) metric(MetricDef.of(MetricKind.DISTANCE), w.distanceM)
        else metric(MetricDef.of(MetricKind.DURATION), w.durationSec.toDouble())
        val hrCurve = HrSampling.downsample(hr, 120).map { it.bpm }.filter { it in 25..250 }
        val avgHr = w.values[HrZones.AVG_KEY]?.toInt() ?: hrCurve.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val showHrStrip = hrCurve.size >= 2 && avgHr != null
        val skip = buildSet {
            add(MetricKind.DISTANCE); add(MetricKind.NOTE); add(MetricKind.RPE); add(MetricKind.POOL_LENGTH)
            add(MetricKind.STROKE); add(MetricKind.MAX_HEART_RATE)
            if (hero.label == s(R.string.metric_duration)) add(MetricKind.DURATION)
            if (sport.has(MetricKind.PACE) || sport.has(MetricKind.SWIM_PACE)) add(MetricKind.SPEED)
            if (showHrStrip) add(MetricKind.HEART_RATE)
        }
        val metrics = sport.metrics.filter { it.kind !in skip }
            .mapNotNull { d -> w.valueOf(d.id)?.takeIf { it > 0 }?.let { d to it } }
            .sortedBy { (d, _) -> priority(d.kind) }
            .map { (d, v) -> metric(d, v) }
        val date = Instant.ofEpochMilli(w.startTime).atZone(zone)
        return WorkoutCardData(
            sportName = name, sportColor = color, sportIcon = SportIcons.of(sport.icon),
            dateText = fmt(R.string.fmt_card_date).format(date),
            hero = hero, metrics = metrics,
            track = if (sport.usesGps) path else emptyList(),
            hr = if (showHrStrip) hrCurve else emptyList(),
            hrLabel = avgHr?.let { ctx.getString(R.string.share_hr_avg, it) } ?: "",
            brand = s(R.string.share_brand), tagline = s(R.string.home_tagline),
        )
    }

    private fun priority(k: MetricKind) = when (k) {
        MetricKind.DURATION -> 0
        MetricKind.PACE, MetricKind.SWIM_PACE -> 1
        MetricKind.SPEED -> 2
        MetricKind.LAPS -> 3
        MetricKind.ELEVATION_GAIN -> 4
        MetricKind.HEART_RATE -> 5
        MetricKind.CALORIES -> 6
        else -> 7
    }

    private fun metric(d: MetricDef, v: Double): CardMetric {
        val f = formatMetric(d, v, ::s)
        return CardMetric(f.value, f.unit, d.label(::s), SportIcons.metric(d.kind))
    }

    fun report(r: ReportSummary, sports: Map<Long, SportType>, today: LocalDate): ReportCardData {
        val current = !today.isBefore(r.start) && !today.isAfter(r.end)
        val title = when {
            r.period == Period.WEEK && current -> s(R.string.report_title_week)
            r.period == Period.WEEK -> s(R.string.report_title_week)
            current -> s(R.string.report_title_month)
            else -> ctx.getString(R.string.report_title_month_past, fmt(R.string.fmt_month_title).format(r.start))
        }
        val day = fmt(R.string.fmt_range_day)
        val range = "${day.format(r.start)} – ${day.format(r.end)}"
        val t = r.totals
        val hero = if (t.distanceM > 0) metric(MetricDef.of(MetricKind.DISTANCE), t.distanceM)
        else CardMetric(Pace.formatDuration(t.durationSec), "", s(R.string.metric_duration), Icons.Filled.Timer)
        val metrics = buildList {
            add(CardMetric(t.count.toString(), "", s(R.string.stat_workouts), Icons.Filled.EmojiEvents))
            if (t.distanceM > 0) add(CardMetric(Fmt.hm(t.durationSec), "", s(R.string.metric_duration), Icons.Filled.Timer))
            add(CardMetric("${t.activeDays}", "/${r.daily.size}", s(R.string.stat_active_days), Icons.Filled.CalendarMonth))
            if (t.calories > 0) add(CardMetric(Fmt.num(t.calories), s(R.string.unit_kcal), s(R.string.metric_calories), Icons.Filled.LocalFireDepartment))
            else if (t.distanceM <= 0) add(CardMetric(Fmt.km(0.0), s(R.string.unit_km), s(R.string.metric_distance), Icons.Filled.Straighten))
        }
        val weekly = r.period == Period.WEEK
        val dow = DateTimeFormatter.ofPattern("EEEEE", locale)
        val labels = r.daily.mapIndexed { i, (d, _) ->
            when {
                weekly -> dow.format(d)
                i == 0 || (d.dayOfMonth % 5 == 0 && d.dayOfMonth < 30) -> d.dayOfMonth.toString()
                else -> ""
            }
        }
        val maxDur = r.bySport.maxOfOrNull { it.durationSec }?.coerceAtLeast(1) ?: 1
        val rows = r.bySport.mapNotNull { sl ->
            val sp = sports[sl.sportId] ?: return@mapNotNull null
            ReportSportRow(
                sp.displayName(::s), Color(sp.color), SportIcons.of(sp.icon),
                ctx.getString(R.string.report_sport_detail, sl.count, Pace.formatDuration(sl.durationSec)),
                sl.durationSec.toFloat() / maxDur,
            )
        }
        return ReportCardData(
            title = title, range = range, accent = Color(0xFF0FA3A3), hero = hero, metrics = metrics,
            bars = r.daily.map { it.second / 60f }, barLabels = labels, chartTitle = s(R.string.report_daily),
            sports = rows, emptyText = s(R.string.report_empty),
            brand = s(R.string.share_brand), tagline = s(R.string.home_tagline),
        )
    }
}
