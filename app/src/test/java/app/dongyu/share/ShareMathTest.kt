package app.dongyu.share

import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ShareMathTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test fun projectionFitsTrackInsideArea() {
        val pts = listOf(30.0 to 120.0, 30.01 to 120.02, 30.02 to 120.01)
        val area = PxRect(90f, 300f, 990f, 900f)
        val p = TrackProjection.fit(pts, area, 1080, 1440)!!
        pts.forEach { (la, lo) ->
            val (x, y) = p.project(la, lo)
            assertTrue(x >= area.left - 0.5f && x <= area.right + 0.5f)
            assertTrue(y >= area.top - 0.5f && y <= area.bottom + 0.5f)
        }
        // North is up.
        assertTrue(p.project(30.02, 120.01).second < p.project(30.0, 120.0).second)
    }

    @Test fun canvasBoundsRoundTrip() {
        val p = TrackProjection.fit(listOf(39.9 to 116.3, 39.95 to 116.4), PxRect(0f, 0f, 1080f, 1080f), 1080, 1440)!!
        val (s, w, n, e) = p.canvasBounds().toList()
        val (x0, y0) = p.project(n, w)
        val (x1, y1) = p.project(s, e)
        assertEquals(0f, x0, 0.5f); assertEquals(0f, y0, 0.5f)
        assertEquals(1080f, x1, 0.5f); assertEquals(1440f, y1, 0.5f)
    }

    @Test fun singlePointTrackDoesNotExplode() {
        val p = TrackProjection.fit(listOf(30.0 to 120.0, 30.0 to 120.0), PxRect(0f, 0f, 100f, 100f), 100, 100)!!
        assertEquals(50f, p.project(30.0, 120.0).first, 0.5f)
    }

    private fun w(id: Long, day: LocalDate, hour: Int, sport: Long, dur: Long, dist: Double = 0.0) = Workout(
        id = id, sportId = sport, startTime = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        endTime = 0, durationSec = dur, distanceM = dist,
    )

    @Test fun weeklyReportBucketsDaysAndSports() {
        val mon = LocalDate.of(2026, 10, 19)
        val ws = listOf(
            w(1, mon, 7, 1, 1800, 5000.0), w(2, mon, 19, 2, 3600), w(3, mon.plusDays(3), 7, 1, 2400, 7000.0),
            w(4, mon.minusDays(1), 7, 1, 9999), // previous week (Sunday)
        )
        val r = ReportBuilder.build(ws, Period.WEEK, LocalDate.of(2026, 10, 22), StatsAggregator(zone))
        assertEquals(mon, r.start); assertEquals(mon.plusDays(6), r.end)
        assertEquals(listOf(5400L, 0L, 0L, 2400L, 0L, 0L, 0L), r.daily.map { it.second })
        assertEquals(3, r.totals.count); assertEquals(2, r.totals.activeDays)
        assertEquals(listOf(1L, 2L), r.bySport.map { it.sportId })
        assertEquals(12000.0, r.bySport[0].distanceM, 0.01)
        assertEquals(2L, r.longest!!.id)
    }

    @Test fun monthlyReportHasEveryDay() {
        val r = ReportBuilder.build(emptyList(), Period.MONTH, LocalDate.of(2026, 2, 10), StatsAggregator(zone))
        assertEquals(28, r.daily.size)
        assertEquals(LocalDate.of(2026, 2, 28), r.end)
    }
}
