package app.dongyu.share

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import app.dongyu.data.seed.DemoData
import app.dongyu.domain.stats.Period
import app.dongyu.domain.stats.StatsAggregator
import app.dongyu.model.Presets
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.ui.share.ShareCardFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Renders every template with demo data; PNGs land in build/outputs/sharecards for review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN")
class ShareCardTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 22)
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sports: List<SportType> = (Presets.all + DemoData.customSports).mapIndexed { i, s -> s.copy(id = i + 1L, sortOrder = i) }
    private val items = DemoData.generate(sports, today, zone, days = 60).mapIndexed { i, it -> it.copy(workout = it.workout.copy(id = i + 1L)) }
    private val renderer = ShareCardRenderer(TextMeasurer(createFontFamilyResolver(ctx), Density(1f), LayoutDirection.Ltr))
    private val factory = ShareCardFactory(ctx, zone)
    private val out = File("build/outputs/sharecards").apply { mkdirs() }

    private fun save(name: String, b: androidx.compose.ui.graphics.ImageBitmap) {
        File(out, "$name.png").outputStream().use { b.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun item(f: SportFamily, needHr: Boolean = false) = items.last { i ->
        sports.first { it.id == i.workout.sportId }.family == f && (!needHr || i.hr.isNotEmpty())
    }

    @Test fun workoutTemplates() {
        val run = item(SportFamily.RUN, needHr = true)
        val d = factory.workout(run.workout, sports.first { it.id == run.workout.sportId }, run.track.map { it.lat to it.lon }, run.hr)
        assertTrue(d.track.size > 10)
        assertTrue(d.hr.isNotEmpty())
        for (a in CardAspect.entries) for (s in listOf(CardStyle.AURORA, CardStyle.PAPER)) {
            val b = renderer.renderWorkout(d, a, s)
            assertEquals(a.w, b.width); assertEquals(a.h, b.height)
            save("run_${a.name.lowercase()}_${s.name.lowercase()}", b)
        }
        val hike = item(SportFamily.MOUNTAINEER)
        val hd = factory.workout(hike.workout, sports.first { it.id == hike.workout.sportId }, hike.track.map { it.lat to it.lon }, hike.hr)
        save("mountain_portrait_paper", renderer.renderWorkout(hd, CardAspect.PORTRAIT, CardStyle.PAPER))
        // Map style without a snapshot falls back to aurora rather than failing.
        save("run_portrait_map_fallback", renderer.renderWorkout(d, CardAspect.PORTRAIT, CardStyle.MAP, null))
    }

    @Test fun metricsOnlyCardForNonGps() {
        val swim = item(SportFamily.SWIM)
        val sp = sports.first { it.id == swim.workout.sportId }
        val d = factory.workout(swim.workout, sp, emptyList(), swim.hr)
        assertTrue(d.track.isEmpty())
        save("swim_portrait_aurora", renderer.renderWorkout(d, CardAspect.PORTRAIT, CardStyle.AURORA))
        val custom = items.last { i -> sports.first { it.id == i.workout.sportId }.presetKey == null }
        val cs = sports.first { it.id == custom.workout.sportId }
        save("custom_square_paper", renderer.renderWorkout(factory.workout(custom.workout, cs, emptyList(), emptyList()), CardAspect.SQUARE, CardStyle.PAPER))
    }

    @Test fun reportCards() {
        val agg = StatsAggregator(zone)
        val byId = sports.associateBy { it.id }
        val week = ReportBuilder.build(items.map { it.workout }, Period.WEEK, today, agg)
        assertEquals(7, week.daily.size)
        save("report_week_portrait_aurora", renderer.renderReport(factory.report(week, byId, today), CardAspect.PORTRAIT, CardStyle.AURORA))
        val month = ReportBuilder.build(items.map { it.workout }, Period.MONTH, today, agg)
        assertEquals(31, month.daily.size)
        save("report_month_story_paper", renderer.renderReport(factory.report(month, byId, today), CardAspect.STORY, CardStyle.PAPER))
        save("report_month_square_aurora", renderer.renderReport(factory.report(month, byId, today), CardAspect.SQUARE, CardStyle.AURORA))
        val empty = ReportBuilder.build(emptyList(), Period.WEEK, today, agg)
        save("report_empty", renderer.renderReport(factory.report(empty, byId, today), CardAspect.PORTRAIT, CardStyle.AURORA))
    }
}
