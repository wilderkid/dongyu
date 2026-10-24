package app.dongyu.domain.stats

import app.dongyu.model.Aggregation
import app.dongyu.model.DerivedKeys
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.Workout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Period { WEEK, MONTH, YEAR }

data class Totals(
    val count: Int = 0,
    val durationSec: Long = 0,
    val distanceM: Double = 0.0,
    val elevationGain: Double = 0.0,
    val calories: Double = 0.0,
    val activeDays: Int = 0,
)

data class Comparison(val current: Totals, val previous: Totals) {
    fun delta(selector: (Totals) -> Double): Double? {
        val p = selector(previous)
        val c = selector(current)
        return if (p == 0.0) null else (c - p) / p
    }
}

data class SportShare(val sportId: Long, val durationSec: Long, val count: Int)
data class WeekStack(val weekStart: LocalDate, val bySport: Map<Long, Double>) {
    val total: Double get() = bySport.values.sum()
}
data class Streaks(val currentDays: Int, val longestDays: Int, val currentWeeks: Int, val longestWeeks: Int)
data class TrendPoint(val bucketStart: LocalDate, val value: Double)
data class PersonalRecord(val key: String, val value: Double, val workoutId: Long, val date: LocalDate, val lowerIsBetter: Boolean)
data class MetricComparison(val metric: MetricDef, val current: Double?, val previous: Double?)

/** Pure, testable aggregation over workouts. Weeks start on Monday (Chinese convention). */
class StatsAggregator(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun dateOf(w: Workout): LocalDate = Instant.ofEpochMilli(w.startTime).atZone(zone).toLocalDate()

    fun periodStart(period: Period, ref: LocalDate): LocalDate = when (period) {
        Period.WEEK -> ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTH -> ref.withDayOfMonth(1)
        Period.YEAR -> ref.withDayOfYear(1)
    }

    fun periodEndExclusive(period: Period, start: LocalDate): LocalDate = when (period) {
        Period.WEEK -> start.plusWeeks(1)
        Period.MONTH -> start.plusMonths(1)
        Period.YEAR -> start.plusYears(1)
    }

    fun inRange(ws: List<Workout>, from: LocalDate, toExclusive: LocalDate) =
        ws.filter { val d = dateOf(it); !d.isBefore(from) && d.isBefore(toExclusive) }

    fun totals(ws: List<Workout>): Totals = Totals(
        count = ws.size,
        durationSec = ws.sumOf { it.durationSec },
        distanceM = ws.sumOf { it.distanceM },
        elevationGain = ws.sumOf { it.elevationGain },
        calories = ws.sumOf { it.calories },
        activeDays = ws.map { dateOf(it) }.distinct().size,
    )

    fun periodTotals(ws: List<Workout>, period: Period, ref: LocalDate): Totals {
        val s = periodStart(period, ref)
        return totals(inRange(ws, s, periodEndExclusive(period, s)))
    }

    /** Current period vs the previous one. For fairness the previous period is cut at the same elapsed offset when [toDate] is true. */
    fun compare(ws: List<Workout>, period: Period, ref: LocalDate, toDate: Boolean = false): Comparison {
        val s = periodStart(period, ref)
        val prevStart = when (period) {
            Period.WEEK -> s.minusWeeks(1)
            Period.MONTH -> s.minusMonths(1)
            Period.YEAR -> s.minusYears(1)
        }
        val current = totals(inRange(ws, s, periodEndExclusive(period, s)))
        val prevEnd = if (toDate) {
            val offset = ChronoUnit.DAYS.between(s, ref) + 1
            minOf(prevStart.plusDays(offset), s)
        } else s
        return Comparison(current, totals(inRange(ws, prevStart, prevEnd)))
    }

    /** Minutes per day for the last [days] days ending at [ref]. */
    fun heatmap(ws: List<Workout>, ref: LocalDate, days: Int = 26 * 7): Map<LocalDate, Double> {
        val from = ref.minusDays(days.toLong() - 1)
        return inRange(ws, from, ref.plusDays(1))
            .groupBy { dateOf(it) }
            .mapValues { (_, list) -> list.sumOf { it.durationSec } / 60.0 }
    }

    fun timeBySport(ws: List<Workout>): List<SportShare> =
        ws.groupBy { it.sportId }
            .map { (id, list) -> SportShare(id, list.sumOf { it.durationSec }, list.size) }
            .sortedByDescending { it.durationSec }

    /** Last [weeks] weeks (oldest first) of minutes per sport. */
    fun weeklyStacked(ws: List<Workout>, ref: LocalDate, weeks: Int = 12): List<WeekStack> {
        val thisWeek = periodStart(Period.WEEK, ref)
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val inWeek = inRange(ws, start, start.plusWeeks(1))
            WeekStack(start, inWeek.groupBy { it.sportId }.mapValues { (_, l) -> l.sumOf { it.durationSec } / 60.0 })
        }
    }

    fun streaks(ws: List<Workout>, ref: LocalDate): Streaks {
        val days = ws.map { dateOf(it) }.toSortedSet()
        var longest = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            longest = maxOf(longest, run)
            prev = d
        }
        // current: counting back from today (or yesterday if nothing today yet)
        var cursor = if (ref in days) ref else ref.minusDays(1)
        var current = 0
        while (cursor in days) { current++; cursor = cursor.minusDays(1) }

        val weeks = days.map { periodStart(Period.WEEK, it) }.toSortedSet()
        var longestW = 0
        var runW = 0
        var prevW: LocalDate? = null
        for (w in weeks) {
            runW = if (prevW != null && prevW.plusWeeks(1) == w) runW + 1 else 1
            longestW = maxOf(longestW, runW)
            prevW = w
        }
        val thisWeek = periodStart(Period.WEEK, ref)
        var wc = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        var currentW = 0
        while (wc in weeks) { currentW++; wc = wc.minusWeeks(1) }
        return Streaks(current, longest, currentW, longestW)
    }

    /** Aggregates the values of one metric over a set of workouts according to its aggregation. */
    fun aggregate(ws: List<Workout>, metric: MetricDef): Double? {
        val vals = ws.mapNotNull { it.valueOf(metric.id) }
        if (vals.isEmpty()) return null
        return when (metric.aggregation) {
            Aggregation.SUM -> vals.sum()
            Aggregation.AVG -> weightedAvg(ws, metric) ?: vals.average()
            Aggregation.MAX -> vals.max()
            Aggregation.MIN -> vals.min()
            Aggregation.LAST -> vals.last()
        }
    }

    /** Pace/speed averages are distance/time weighted rather than a mean of means. */
    private fun weightedAvg(ws: List<Workout>, metric: MetricDef): Double? {
        val withDist = ws.filter { it.distanceM > 0 && it.durationSec > 0 && it.valueOf(metric.id) != null }
        if (withDist.isEmpty()) return null
        val d = withDist.sumOf { it.distanceM }
        val t = withDist.sumOf { it.durationSec }.toDouble()
        return when (metric.kind) {
            MetricKind.PACE -> t / (d / 1000.0)
            MetricKind.SWIM_PACE -> t / (d / 100.0)
            MetricKind.SPEED -> d / t * 3.6
            else -> null
        }
    }

    /** Trend of a metric bucketed by week or month, oldest first; empty buckets are omitted for averages and 0 for sums. */
    fun metricTrend(ws: List<Workout>, metric: MetricDef, bucket: Period, ref: LocalDate, buckets: Int): List<TrendPoint> {
        val last = periodStart(bucket, ref)
        return (buckets - 1 downTo 0).mapNotNull { back ->
            val start = when (bucket) {
                Period.WEEK -> last.minusWeeks(back.toLong())
                Period.MONTH -> last.minusMonths(back.toLong())
                Period.YEAR -> last.minusYears(back.toLong())
            }
            val v = aggregate(inRange(ws, start, periodEndExclusive(bucket, start)), metric)
            when {
                v != null -> TrendPoint(start, v)
                metric.aggregation == Aggregation.SUM -> TrendPoint(start, 0.0)
                else -> null
            }
        }
    }

    fun compareMetrics(ws: List<Workout>, metrics: List<MetricDef>, period: Period, ref: LocalDate): List<MetricComparison> {
        val s = periodStart(period, ref)
        val prev = when (period) {
            Period.WEEK -> s.minusWeeks(1); Period.MONTH -> s.minusMonths(1); Period.YEAR -> s.minusYears(1)
        }
        val cur = inRange(ws, s, periodEndExclusive(period, s))
        val old = inRange(ws, prev, s)
        return metrics.filter { it.kind.isNumeric }.map { MetricComparison(it, aggregate(cur, it), aggregate(old, it)) }
    }

    /** Personal records for one sport; keys are DerivedKeys or "max_<metricId>". */
    fun personalRecords(sport: SportType, ws: List<Workout>): List<PersonalRecord> {
        val sportWs = ws.filter { it.sportId == sport.id }
        val out = mutableListOf<PersonalRecord>()
        fun best(key: String, lowerIsBetter: Boolean, selector: (Workout) -> Double?) {
            val candidates = sportWs.mapNotNull { w -> selector(w)?.takeIf { it > 0 }?.let { w to it } }
            val pick = if (lowerIsBetter) candidates.minByOrNull { it.second } else candidates.maxByOrNull { it.second }
            pick?.let { (w, v) -> out += PersonalRecord(key, v, w.id, dateOf(w), lowerIsBetter) }
        }
        if (sport.family == SportFamily.RUN) {
            best(DerivedKeys.BEST_1K, true) { it.values[DerivedKeys.BEST_1K] }
            best(DerivedKeys.BEST_5K, true) { it.values[DerivedKeys.BEST_5K] }
            best(DerivedKeys.BEST_10K, true) { it.values[DerivedKeys.BEST_10K] }
            best(DerivedKeys.BEST_HALF, true) { it.values[DerivedKeys.BEST_HALF] }
        }
        for (m in sport.metrics) {
            if (!m.kind.isNumeric) continue
            when (m.kind) {
                MetricKind.POOL_LENGTH, MetricKind.RPE, MetricKind.HEART_RATE -> continue
                else -> {}
            }
            // Pace from short sessions is noisy; only consider sessions with some distance.
            if (m.kind == MetricKind.PACE || m.kind == MetricKind.SWIM_PACE) {
                best("best_${m.id}", true) { w -> w.valueOf(m.id)?.takeIf { w.distanceM >= if (m.kind == MetricKind.PACE) 3000 else 400 } }
            } else {
                best("max_${m.id}", m.kind.lowerIsBetter) { it.valueOf(m.id) }
            }
        }
        return out
    }
}
