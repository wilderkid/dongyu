package app.dongyu.domain

import app.dongyu.domain.metrics.ManualEntry
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.model.DerivedKeys
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.Presets
import app.dongyu.model.SportFamily
import app.dongyu.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsAggregatorTest {
    private val zone = ZoneOffset.UTC
    private val agg = StatsAggregator(zone)
    private val today = LocalDate.of(2026, 10, 22) // Thursday

    private var nextId = 1L
    private fun w(date: LocalDate, sportId: Long = 1, min: Long = 30, km: Double = 5.0, values: Map<String, Double> = emptyMap()): Workout {
        val start = date.atTime(7, 0).toInstant(zone).toEpochMilli()
        return Workout(id = nextId++, sportId = sportId, startTime = start, endTime = start + min * 60_000, durationSec = min * 60, distanceM = km * 1000, values = values)
    }

    @Test fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 10, 19), agg.periodStart(Period.WEEK, today))
        assertEquals(LocalDate.of(2026, 10, 1), agg.periodStart(Period.MONTH, today))
        assertEquals(LocalDate.of(2026, 1, 1), agg.periodStart(Period.YEAR, today))
    }

    @Test fun totalsAndComparison() {
        val ws = listOf(w(today), w(today), w(today.minusDays(1), km = 10.0), w(today.minusDays(7)), w(today.minusDays(30)))
        val c = agg.compare(ws, Period.WEEK, today)
        assertEquals(3, c.current.count)
        assertEquals(2, c.current.activeDays)
        assertEquals(20_000.0, c.current.distanceM, 0.1)
        assertEquals(1, c.previous.count)
        assertEquals(2.0, c.delta { it.count.toDouble() }!!, 1e-9) // +200 %
    }

    @Test fun compareToDateCutsPreviousPeriod() {
        // previous week: Mon (inside offset) and Sun (after Thursday offset)
        val ws = listOf(w(LocalDate.of(2026, 10, 12)), w(LocalDate.of(2026, 10, 18)))
        assertEquals(2, agg.compare(ws, Period.WEEK, today).previous.count)
        assertEquals(1, agg.compare(ws, Period.WEEK, today, toDate = true).previous.count)
    }

    @Test fun streaks() {
        val days = listOf(0, 1, 2, 5, 6, 7, 8, 9).map { today.minusDays(it.toLong()) }
        val s = agg.streaks(days.map { w(it) }, today)
        assertEquals(3, s.currentDays)
        assertEquals(5, s.longestDays)
        assertEquals(2, s.currentWeeks)
    }

    @Test fun streakSurvivesUntilEndOfToday() {
        val s = agg.streaks(listOf(w(today.minusDays(1)), w(today.minusDays(2))), today)
        assertEquals(2, s.currentDays)
    }

    @Test fun heatmapAndWeeklyStack() {
        val ws = listOf(w(today, min = 30), w(today, min = 15, sportId = 2), w(today.minusDays(400)))
        val h = agg.heatmap(ws, today)
        assertEquals(45.0, h[today]!!, 1e-9)
        assertEquals(1, h.size)
        val weeks = agg.weeklyStacked(ws, today, 12)
        assertEquals(12, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 19), weeks.last().weekStart)
        assertEquals(30.0, weeks.last().bySport[1L]!!, 1e-9)
        assertEquals(15.0, weeks.last().bySport[2L]!!, 1e-9)
    }

    @Test fun timeBySportSortedDescending() {
        val shares = agg.timeBySport(listOf(w(today, 1, 10), w(today, 2, 60), w(today, 2, 5)))
        assertEquals(2L, shares.first().sportId)
        assertEquals(65 * 60L, shares.first().durationSec)
    }

    @Test fun averagePaceIsDistanceWeighted() {
        val pace = MetricDef.of(MetricKind.PACE)
        // 10 km in 50 min (5'00") and 1 km in 10 min (10'00") => 60 min / 11 km = 5'27"
        val ws = listOf(
            w(today, min = 50, km = 10.0, values = mapOf(MetricKind.PACE.key to 300.0)),
            w(today, min = 10, km = 1.0, values = mapOf(MetricKind.PACE.key to 600.0)),
        )
        assertEquals(3600.0 / 11, agg.aggregate(ws, pace)!!, 0.01)
    }

    @Test fun sumTrendFillsEmptyBuckets() {
        val dist = MetricDef.of(MetricKind.DISTANCE)
        val t = agg.metricTrend(listOf(w(today, km = 3.0)), dist, Period.WEEK, today, 4)
        assertEquals(listOf(0.0, 0.0, 0.0, 3000.0), t.map { it.value })
    }

    @Test fun avgTrendSkipsEmptyBuckets() {
        val hr = MetricDef.of(MetricKind.HEART_RATE)
        val t = agg.metricTrend(listOf(w(today, values = mapOf(MetricKind.HEART_RATE.key to 150.0))), hr, Period.MONTH, today, 6)
        assertEquals(1, t.size)
    }

    @Test fun personalRecordsForRunning() {
        val run = Presets.all.first { it.family == SportFamily.RUN }.copy(id = 1)
        val a = w(today.minusDays(3), km = 5.0, values = mapOf(DerivedKeys.BEST_5K to 1500.0, MetricKind.PACE.key to 300.0))
        val b = w(today, km = 10.0, values = mapOf(DerivedKeys.BEST_5K to 1440.0, MetricKind.PACE.key to 310.0))
        val prs = agg.personalRecords(run, listOf(a, b)).associateBy { it.key }
        assertEquals(1440.0, prs[DerivedKeys.BEST_5K]!!.value, 1e-9)
        assertEquals(b.id, prs[DerivedKeys.BEST_5K]!!.workoutId)
        assertEquals(10_000.0, prs["max_${MetricKind.DISTANCE.key}"]!!.value, 1e-9)
        assertEquals(300.0, prs["best_${MetricKind.PACE.key}"]!!.value, 1e-9)
    }
}

class ManualEntryTest {
    private val run = Presets.all.first { it.family == SportFamily.RUN }.copy(id = 1)
    private val swim = Presets.all.first { it.family == SportFamily.SWIM }.copy(id = 4)
    private val mountain = Presets.all.first { it.family == SportFamily.MOUNTAINEER }.copy(id = 3)
    private val base = Workout(sportId = 1, startTime = 1_000_000L, endTime = 1_000_000L, durationSec = 0)

    @Test fun derivesPaceSpeedAndCalories() {
        val w = ManualEntry.apply(base, run, 1500, 5000.0, emptyMap(), "", 60.0, hasTrack = false)
        assertEquals(300.0, w.values[MetricKind.PACE.key]!!, 1e-6)
        assertEquals(12.0, w.values[MetricKind.SPEED.key]!!, 1e-6)
        assertEquals(9.8 * 60 * 1500 / 3600, w.calories, 1e-6)
        assertEquals(base.startTime + 1_500_000, w.endTime)
    }

    @Test fun swimPacePer100m() {
        val w = ManualEntry.apply(base.copy(sportId = 4), swim, 1800, 1500.0, mapOf(MetricKind.LAPS.key to 60.0), "", 60.0, false)
        assertEquals(120.0, w.values[MetricKind.SWIM_PACE.key]!!, 1e-6)
        assertEquals(60.0, w.values[MetricKind.LAPS.key]!!, 1e-6)
    }

    @Test fun ascentRateFromEnteredGain() {
        val w = ManualEntry.apply(base, mountain, 7200, 8000.0, mapOf(MetricKind.ELEVATION_GAIN.key to 900.0), "", 60.0, false)
        assertEquals(450.0, w.values[MetricKind.ASCENT_RATE.key]!!, 1e-6)
    }

    @Test fun enteredCaloriesWinAndNullClears() {
        val first = ManualEntry.apply(base, run, 1500, 5000.0, mapOf(MetricKind.CALORIES.key to 999.0, MetricKind.HEART_RATE.key to 150.0), "hi", 60.0, false)
        assertEquals(999.0, first.calories, 0.0)
        val second = ManualEntry.apply(first, run, 1500, 5000.0, mapOf(MetricKind.HEART_RATE.key to null), "", 60.0, false)
        assertNull(second.values[MetricKind.HEART_RATE.key])
    }

    @Test fun gpsWorkoutsHideAutoMetrics() {
        val kinds = ManualEntry.editableKinds(run, hasTrack = true).map { it.kind }
        assertFalse(MetricKind.ELEVATION_GAIN in kinds)
        assertTrue(MetricKind.HEART_RATE in kinds)
        assertFalse(MetricKind.PACE in kinds)
        assertTrue(MetricKind.ELEVATION_GAIN in ManualEntry.editableKinds(run, hasTrack = false).map { it.kind })
    }
}
