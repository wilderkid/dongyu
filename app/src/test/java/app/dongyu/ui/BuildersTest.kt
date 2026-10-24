package app.dongyu.ui

import app.dongyu.data.seed.DemoData
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.Presets
import app.dongyu.model.SportFamily
import app.dongyu.ui.editor.SportEditorRules
import app.dongyu.ui.stats.SportStatsBuilder
import app.dongyu.ui.stats.StatsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SportEditorRulesTest {
    @Test fun gpsOnlyMetricsHiddenWithoutGps() {
        val noGps = SportEditorRules.selectableKinds(false)
        assertFalse(noGps.any { it.needsGps })
        assertFalse(MetricKind.DURATION in noGps)
        assertTrue(SportEditorRules.selectableKinds(true).any { it.needsGps })
    }

    @Test fun normaliseKeepsDurationFirstAndDropsGpsMetrics() {
        val gpsKind = MetricKind.entries.first { it.needsGps }
        val list = listOf(MetricDef.of(MetricKind.HEART_RATE), MetricDef.of(gpsKind), MetricDef.of(MetricKind.DURATION))
        val n = SportEditorRules.normalise(list, usesGps = false)
        assertEquals(MetricKind.DURATION, n.first().kind)
        assertEquals(1, n.count { it.kind == MetricKind.DURATION })
        assertFalse(n.any { it.kind == gpsKind })
        assertTrue(SportEditorRules.normalise(list, usesGps = true).any { it.kind == gpsKind })
    }
}

class StatsBuildersTest {
    private val zone = ZoneOffset.UTC
    private val agg = StatsAggregator(zone)
    private val today = LocalDate.of(2026, 10, 22)
    private val sports = (Presets.all + DemoData.customSports).mapIndexed { i, s -> s.copy(id = i + 1L) }
    private val ws = DemoData.generate(sports, today, zone, days = 120, withTracks = false).mapIndexed { i, it -> it.workout.copy(id = i + 1L) }

    @Test fun demoDataCoversEverySport() {
        val used = ws.map { it.sportId }.toSet()
        assertEquals(sports.map { it.id }.toSet(), used)
    }

    @Test fun dashboardHasAllSections() {
        for (p in Period.entries) {
            val st = StatsBuilder.dashboard(agg, sports, ws, p, today)
            assertTrue(st.hasData)
            assertEquals(12, st.weekly.size)
            assertTrue(st.shares.isNotEmpty())
            assertTrue(st.heatmap.isNotEmpty())
        }
    }

    @Test fun sportStatsHaveTrendsAndRecords() {
        val run = sports.first { it.family == SportFamily.RUN }
        val st = SportStatsBuilder.build(agg, run, ws, Period.WEEK, today)
        assertTrue(st.records.isNotEmpty())
        assertTrue(st.trends.isNotEmpty())
        assertTrue(st.trends.none { it.metric.kind == MetricKind.NOTE })
    }

    @Test fun emptyDashboard() {
        assertFalse(StatsBuilder.dashboard(agg, sports, emptyList(), Period.MONTH, today).hasData)
    }
}
