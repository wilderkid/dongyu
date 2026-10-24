package app.dongyu.domain

import app.dongyu.domain.metrics.AutoPauseDetector
import app.dongyu.domain.metrics.BestEffort
import app.dongyu.domain.metrics.ElevationCalculator
import app.dongyu.domain.metrics.GeoMath
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.metrics.SplitCalculator
import app.dongyu.domain.metrics.TrackMath
import app.dongyu.domain.metrics.WorkoutSummaryCalculator
import app.dongyu.model.MetricKind
import app.dongyu.model.Presets
import app.dongyu.model.SportFamily
import app.dongyu.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test fun oneDegreeLatitude() {
        assertEquals(TestTracks.M_PER_DEG_LAT, GeoMath.haversine(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test fun zeroDistance() {
        assertEquals(0.0, GeoMath.haversine(30.0, 120.0, 30.0, 120.0), 1e-9)
    }

    @Test fun longitudeShrinksWithLatitude() {
        val equator = GeoMath.haversine(0.0, 0.0, 0.0, 1.0)
        val at60 = GeoMath.haversine(60.0, 0.0, 60.0, 1.0)
        assertEquals(equator / 2, at60, 50.0)
    }
}

class PaceTest {
    @Test fun secPerKm() = assertEquals(300.0, Pace.secPerKm(1000.0, 300.0)!!, 1e-9)
    @Test fun secPer100m() = assertEquals(120.0, Pace.secPer100m(1500.0, 1800.0)!!, 1e-9)
    @Test fun kmh() = assertEquals(36.0, Pace.kmh(10_000.0, 1000.0)!!, 1e-9)
    @Test fun zeroDistanceHasNoPace() = assertNull(Pace.secPerKm(0.0, 100.0))
    @Test fun formatPace() = assertEquals("5'05\"", Pace.format(305.0))
    @Test fun formatMissing() = assertEquals("--'--\"", Pace.format(null))
    @Test fun formatDurationShort() = assertEquals("05:25", Pace.formatDuration(325))
    @Test fun formatDurationLong() = assertEquals("1:02:05", Pace.formatDuration(3725))
}

class SplitsTest {
    @Test fun fullAndPartialSplits() {
        val pts = TestTracks.straight(2_500.0, 3.333, stepSec = 3)
        val splits = SplitCalculator.splits(pts, 1000.0)
        assertEquals(3, splits.size)
        assertTrue(!splits[0].partial && !splits[1].partial && splits[2].partial)
        assertEquals(1000.0, splits[0].distanceM, 1.0)
        assertEquals(500.0, splits[2].distanceM, 2.0)
        // 3.333 m/s ≈ 5'00"/km
        assertEquals(300.0, splits[0].paceSecPerKm!!, 2.0)
    }

    @Test fun pausedSegmentsDoNotAddDistanceOrTime() {
        val a = TestTracks.straight(1_000.0, 2.5)
        val last = a.last()
        // resume after a 10 minute pause, at the same spot, in a new segment
        val b = TestTracks.straight(1_000.0, 2.5).map {
            it.copy(time = last.time + 600_000 + it.time, lat = last.lat + (it.lat - 30.0), segment = 1)
        }
        val pts = a + b
        assertEquals(2_000.0, TrackMath.totalDistance(pts), 2.0)
        assertEquals(800.0, TrackMath.movingTimeSec(pts), 1.0)
    }

    @Test fun emptyTrack() = assertTrue(SplitCalculator.splits(emptyList()).isEmpty())
}

class BestEffortTest {
    @Test fun fastestKmInsideVariableRun() {
        // 1 km easy (6'00"), 1 km fast (4'00"), 1 km easy
        val slow1 = TestTracks.straight(1_000.0, 1000.0 / 360, 2)
        val fast = TestTracks.straight(1_000.0, 1000.0 / 240, 2).map { it.copy(time = slow1.last().time + it.time, lat = slow1.last().lat + (it.lat - 30.0)) }.drop(1)
        val slow2 = TestTracks.straight(1_000.0, 1000.0 / 360, 2).map { it.copy(time = fast.last().time + it.time, lat = fast.last().lat + (it.lat - 30.0)) }.drop(1)
        val best = BestEffort.fastest(slow1 + fast + slow2, 1000.0)
        assertNotNull(best)
        assertEquals(240.0, best!!, 4.0)
    }

    @Test fun tooShortReturnsNull() = assertNull(BestEffort.fastest(TestTracks.straight(800.0, 3.0), 1000.0))
}

class ElevationTest {
    @Test fun noiseIsIgnored() {
        val noisy = (0 until 200).map { 100.0 + if (it % 2 == 0) 1.0 else -1.0 }
        val s = ElevationCalculator.compute(noisy)
        assertTrue("gain ${s.gain}", s.gain < 3.0)
    }

    @Test fun steadyClimbAndDescent() {
        val up = (0..100).map { 100.0 + it * 2 }   // +200 m
        val down = (1..50).map { 300.0 - it * 2 }  // -100 m
        val s = ElevationCalculator.compute(up + down)
        assertEquals(200.0, s.gain, 10.0)
        assertEquals(100.0, s.loss, 10.0)
        assertEquals(300.0, s.max!!, 3.0)
    }

    @Test fun missingAltitudes() {
        val s = ElevationCalculator.compute(listOf(null, null))
        assertEquals(0.0, s.gain, 0.0)
        assertNull(s.max)
    }
}

class AutoPauseTest {
    @Test fun pausesAfterSustainedStopAndResumes() {
        val d = AutoPauseDetector(pauseAfterMs = 6_000, resumeSamples = 2)
        assertEquals(AutoPauseDetector.Event.NONE, d.update(0, 3.0))
        assertEquals(AutoPauseDetector.Event.NONE, d.update(1_000, 0.1))
        assertEquals(AutoPauseDetector.Event.NONE, d.update(5_000, 0.1))
        assertEquals(AutoPauseDetector.Event.PAUSE, d.update(7_000, 0.1))
        assertTrue(d.paused)
        assertEquals(AutoPauseDetector.Event.NONE, d.update(8_000, 2.0))
        assertEquals(AutoPauseDetector.Event.RESUME, d.update(9_000, 2.0))
    }

    @Test fun briefSlowdownDoesNotPause() {
        val d = AutoPauseDetector()
        d.update(0, 0.2); d.update(3_000, 0.2)
        assertEquals(AutoPauseDetector.Event.NONE, d.update(4_000, 2.5))
        assertEquals(AutoPauseDetector.Event.NONE, d.update(9_000, 0.2))
        assertTrue(!d.paused)
    }

    @Test fun hikingIsMorePatient() {
        val d = AutoPauseDetector.forFamily(SportFamily.HIKE)
        d.update(0, 0.1)
        assertEquals(AutoPauseDetector.Event.NONE, d.update(10_000, 0.1))
        assertEquals(AutoPauseDetector.Event.PAUSE, d.update(21_000, 0.1))
    }
}

class SummaryTest {
    @Test fun runSummaryHasPaceAndCalories() {
        val run = Presets.all.first { it.family == SportFamily.RUN }
        val pts = TestTracks.straight(5_000.0, 1000.0 / 330, 3) { d -> 50.0 + d / 100 } // +50 m
        val s = WorkoutSummaryCalculator.fromTrack(pts, run, 70.0)
        assertEquals(5_000.0, s.distanceM, 5.0)
        assertEquals(330.0, s.values[MetricKind.PACE.key]!!, 3.0)
        assertTrue((s.values[MetricKind.ELEVATION_GAIN.key] ?: 0.0) in 40.0..60.0)
        assertTrue((s.values[MetricKind.CALORIES.key] ?: 0.0) > 200)
    }

    @Test fun caloriesFormula() {
        // MET 10 × 70 kg × 1 h = 700 kcal
        assertEquals(700.0, WorkoutSummaryCalculator.estimateCalories(10.0, 3600.0, 70.0), 1.0)
    }

}
