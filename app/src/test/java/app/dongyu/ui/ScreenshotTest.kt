package app.dongyu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.dongyu.data.seed.DemoData
import app.dongyu.data.prefs.Settings
import app.dongyu.domain.metrics.SplitCalculator
import app.dongyu.domain.metrics.TrackSeries
import app.dongyu.domain.metrics.WorkoutSummaryCalculator
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.model.SwimMode
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.Presets
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.recording.LiveState
import app.dongyu.recording.OemGuide
import app.dongyu.recording.RecStatus
import app.dongyu.ui.detail.DetailScreen
import app.dongyu.ui.detail.DetailState
import app.dongyu.ui.editor.SportEditorScreen
import app.dongyu.ui.guide.OemGuideScreen
import app.dongyu.ui.home.HomeScreen
import app.dongyu.ui.home.HomeState
import app.dongyu.ui.live.LiveScreen
import app.dongyu.ui.sports.MeActions
import app.dongyu.ui.sports.MeScreen
import app.dongyu.ui.sports.MeState
import app.dongyu.ui.stats.SportStatsBuilder
import app.dongyu.ui.stats.SportStatsScreen
import app.dongyu.ui.stats.StatsBuilder
import app.dongyu.ui.stats.StatsScreen
import app.dongyu.ui.theme.DongyuTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Roborazzi screenshots of the key screens fed with deterministic demo data.
 * Record with: ./gradlew recordRoborazziDebug  (images land in app/build/outputs/roborazzi)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 22)
    private val agg = StatsAggregator(zone)
    private val sports: List<SportType> = (Presets.all + DemoData.customSports).mapIndexed { i, s -> s.copy(id = i + 1L, sortOrder = i) }
    private val items = DemoData.generate(sports, today, zone, days = 200).mapIndexed { i, it -> it.copy(workout = it.workout.copy(id = i + 1L)) }
    private val ws = items.map { it.workout }.sortedByDescending { it.startTime }
    private fun sport(f: SportFamily) = sports.first { it.family == f && it.presetKey != null }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        // Inspection mode swaps the native MapLibre view for the Canvas track renderer (no JNI in Robolectric).
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { DongyuTheme(darkTheme = dark) { content() } } }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun home() = shot("01_home") {
        HomeScreen(
            HomeState(
                sports = sports,
                week = agg.compare(ws, Period.WEEK, today, toDate = true),
                streaks = agg.streaks(ws, today),
                recent = ws.take(4),
            ),
            { _, _, _ -> }, {}, {}, {}, {}, {},
        )
    }

    @Test fun sportPicker() = shot("02_start_sheet") {
        HomeScreen(HomeState(sports = sports, recent = ws.take(3)), { _, _, _ -> }, {}, {}, {}, {}, {}, initialSheetSport = sport(SportFamily.SWIM))
    }

    @Test fun liveRun() = shot("03_live_run", dark = true) {
        val run = sport(SportFamily.RUN)
        val track = items.first { it.workout.sportId == run.id && it.track.size > 50 }.track
        val part = track.take(track.size * 2 / 3)
        val sum = WorkoutSummaryCalculator.fromTrack(part, run)
        LiveScreen(
            LiveState(
                status = RecStatus.RECORDING, workoutId = 1, sport = run, usesGps = true,
                movingMs = (sum.movingSec * 1000).toLong(), distanceM = sum.distanceM,
                currentPace = 318.0, currentSpeedKmh = 11.3, elevationGain = sum.values[MetricKind.ELEVATION_GAIN.key] ?: 0.0,
                altitude = 42.0, gpsAccuracy = 4f, hasFix = true, path = part.map { it.lat to it.lon }, lastSplitPace = 312.0,
            ),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun liveSwim() = shot("04_live_swim_pool") {
        LiveScreen(
            LiveState(status = RecStatus.RECORDING, workoutId = 1, sport = sport(SportFamily.SWIM), movingMs = 1_265_000, distanceM = 1_000.0, lengths = 40, poolLength = 25.0, swimMode = SwimMode.POOL, usesGps = false),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test fun detail() = shot("05_history_detail") {
        val hike = sport(SportFamily.MOUNTAINEER)
        val item = items.first { it.workout.sportId == hike.id && it.track.size > 50 }
        DetailScreen(
            DetailState(item.workout, hike, item.track.map { it.lat to it.lon },
                hike.splitDistanceM?.let { SplitCalculator.splits(item.track, it) } ?: SplitCalculator.splits(item.track), TrackSeries.build(item.track)),
            {}, {}, {},
        )
    }

    @Test fun detailRun() = shot("06_run_detail") {
        val run = sport(SportFamily.RUN)
        val item = items.first { it.workout.sportId == run.id && it.workout.distanceM > 8000 && it.track.isNotEmpty() }
        DetailScreen(
            DetailState(item.workout, run, item.track.map { it.lat to it.lon }, SplitCalculator.splits(item.track), TrackSeries.build(item.track)),
            {}, {}, {},
        )
    }

    @Test fun stats() = shot("07_stats_dashboard") {
        StatsScreen(StatsBuilder.dashboard(agg, sports, ws, Period.MONTH, today), {}, {})
    }

    @Test fun statsDark() = shot("08_stats_dashboard_dark", dark = true) {
        StatsScreen(StatsBuilder.dashboard(agg, sports, ws, Period.YEAR, today), {}, {})
    }

    @Test fun sportStats() = shot("09_sport_stats_run") {
        SportStatsScreen(SportStatsBuilder.build(agg, sport(SportFamily.RUN), ws, Period.WEEK, today), {}, {}, {})
    }

    @Test fun sportStatsSwim() = shot("10_sport_stats_swim", dark = true) {
        SportStatsScreen(SportStatsBuilder.build(agg, sport(SportFamily.SWIM), ws, Period.MONTH, today), {}, {}, {})
    }

    @Test fun editor() = shot("11_custom_sport_editor") {
        val badminton = sports.first { it.presetKey == null }
        SportEditorScreen(badminton.copy(metrics = badminton.metrics + MetricDef.custom("c_smash", "杀球", "次", app.dongyu.model.Aggregation.SUM)), isNew = false, onSave = {}, onBack = {})
    }

    @Test fun me() = shot("12_me") {
        MeScreen(MeState(sports = sports, settings = Settings(), showDebug = true), MeActions())
    }

    @Test fun oemGuide() = shot("13_oem_guide") {
        OemGuideScreen(OemGuide.Vendor.XIAOMI, {}, {})
    }
}
