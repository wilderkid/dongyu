package app.dongyu.transfer

import app.dongyu.data.seed.DemoData
import app.dongyu.domain.hr.HrZones
import app.dongyu.model.MetricKind
import app.dongyu.model.Presets
import app.dongyu.model.SportFamily
import app.dongyu.model.WorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class TransferTest {
    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name")) { name }.readBytes()

    private val sports = (Presets.all + DemoData.customSports).mapIndexed { i, s -> s.copy(id = i + 1L, sortOrder = i) }
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun detectsFormats() {
        assertEquals(FileFormat.GPX, FileFormat.detect(fixture("morning_run.gpx").copyOf(512), null))
        assertEquals(FileFormat.TCX, FileFormat.detect(fixture("weekend_ride.tcx").copyOf(512), "x.bin"))
        assertEquals(FileFormat.FIT, FileFormat.detect(fixture("west_lake_hike.fit").copyOf(64), "renamed.gpx"))
        assertEquals(FileFormat.GPX, FileFormat.detect("garbage".toByteArray(), "Track.GPX"))
        assertNull(FileFormat.detect("garbage".toByteArray(), "photo.jpg"))
    }

    @Test fun parsesStravaGpx() {
        val a = ActivityCodec.parse(fixture("morning_run.gpx"), "morning_run.gpx").single()
        assertEquals(SportFamily.RUN, a.sport.family)
        assertEquals("Morning Run & coffee", a.name)
        assertEquals(180, a.points.size)
        assertEquals(setOf(0, 1), a.points.map { it.segment }.toSet())
        assertEquals(ms("2026-09-12T22:30:00Z"), a.startTime)
        assertEquals(180, a.hr.size)
        assertEquals(140, a.hr.first().bpm)
        assertNotNull(a.points.first().altitude)
        assertEquals(31.2304, a.points.first().lat, 1e-6)
    }

    @Test fun parsesGarminTcx() {
        val a = ActivityCodec.parse(fixture("weekend_ride.tcx"), null).single()
        assertEquals(SportFamily.CYCLE, a.sport.family)
        assertEquals(597L, a.durationSec)
        assertEquals(9420.5, a.distanceM!!, 1e-6)
        assertEquals(310.0, a.calories!!, 1e-6)
        assertEquals(200, a.points.size)
        assertEquals(200, a.hr.size)
        assertEquals(ms("2026-09-13T01:00:00Z"), a.startTime)
    }

    @Test fun parsesFitWithCompressedTimestampsBigEndianAndDeveloperFields() {
        val a = ActivityCodec.parse(fixture("west_lake_hike.fit"), "west_lake_hike.fit").single()
        assertEquals(SportFamily.HIKE, a.sport.family)
        assertEquals(90, a.points.size)
        assertEquals(890L, a.durationSec)
        assertEquals(3770.0, a.distanceM!!, 1e-6)
        assertEquals(255.0, a.calories!!, 1e-6)
        val t0 = ms("2026-09-14T00:00:00Z")
        assertEquals(t0, a.startTime)
        assertEquals(t0 + 50_000, a.points[5].time) // compressed-timestamp record
        assertEquals(30.2741, a.points.first().lat, 1e-6)
        assertEquals(30.0, a.points.first().altitude!!, 0.21)
        assertEquals(90, a.hr.size)
    }

    @Test fun rejectsCorruptFit() {
        val bytes = fixture("west_lake_hike.fit")
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        try { ActivityCodec.parse(bytes, "x.fit"); fail("expected failure") } catch (_: ImportFormatException) {}
        try { ActivityCodec.parse("<html/>".toByteArray(), "x.txt"); fail("expected failure") } catch (_: ImportFormatException) {}
    }

    @Test fun roundTripsEveryFormat() {
        val src = ActivityCodec.parse(fixture("morning_run.gpx"), null).single()
            .copy(durationSec = 1380, distanceM = 4321.0, calories = 288.0)
        for (f in FileFormat.entries) {
            val out = ActivityCodec.write(src, f)
            java.io.File("build/outputs/export").apply { mkdirs() }.resolve("roundtrip.${f.ext}").writeBytes(out)
            val back = ActivityCodec.parse(out, "out.${f.ext}").single()
            assertEquals("$f family", SportFamily.RUN, back.sport.family)
            assertEquals("$f start", src.startTime, back.startTime)
            assertEquals("$f points", src.points.size, back.points.size)
            assertEquals("$f hr", src.hr.size, back.hr.size)
            assertEquals("$f segments", src.points.map { it.segment }.toSet(), back.points.map { it.segment }.toSet())
            src.points.zip(back.points).forEach { (p, q) ->
                assertEquals(p.time, q.time)
                assertEquals(p.lat, q.lat, 1e-6)
                assertEquals(p.lon, q.lon, 1e-6)
                assertEquals(p.altitude!!, q.altitude!!, 0.21)
            }
            if (f != FileFormat.GPX) {
                assertEquals("$f timer", 1380L, back.durationSec)
                assertEquals("$f distance", 4321.0, back.distanceM!!, 0.02)
                assertEquals("$f kcal", 288.0, back.calories!!, 0.5)
            }
        }
    }

    @Test fun customSportLabelSurvivesGpxAndTcx() {
        val badminton = sports.first { it.name == "羽毛球" }
        val src = ActivityData(SportHint(null, badminton.name), startTime = 1_000_000_000_000, endTime = 1_000_003_600_000, durationSec = 3600)
        val back = ActivityCodec.parse(ActivityCodec.write(src, FileFormat.TCX), null).single()
        assertEquals(badminton.id, ActivityMapper.matchSport(back, sports).id)
        assertEquals("", ActivityMapper.toWorkout(back, badminton, 70.0, 190).workout.note)
        assertEquals(3600L, ActivityCodec.parse(ActivityCodec.write(src, FileFormat.FIT), null).single().durationSec)
        // A GPS track's custom label survives GPX too.
        val g = ActivityCodec.parse(fixture("morning_run.gpx"), null).single().copy(sport = SportHint(null, "越野跑"))
        assertEquals("越野跑", ActivityCodec.parse(ActivityCodec.write(g, FileFormat.GPX), null).single().sport.label)
        // GPX cannot carry a workout without a track.
        try { ActivityCodec.parse(ActivityCodec.write(src, FileFormat.GPX), null); fail() } catch (_: ImportFormatException) {}
    }

    @Test fun matchesSportsByLabelFamilyAndSpeed() {
        fun hint(f: SportFamily?, l: String? = null, dist: Double = 0.0, sec: Long = 0) =
            ActivityData(SportHint(f, l), startTime = 0, endTime = sec * 1000, durationSec = sec, distanceM = dist)
        assertEquals("swim", ActivityMapper.matchSport(hint(SportFamily.SWIM), sports).presetKey)
        assertEquals("mountaineer", ActivityMapper.matchSport(hint(SportFamily.MOUNTAINEER), sports).presetKey)
        assertEquals("力量训练", ActivityMapper.matchSport(hint(null, " 力量训练 "), sports).name)
        assertEquals("cycle", ActivityMapper.matchSport(hint(null, "Morning ride?", 20_000.0, 2400), sports).presetKey)
        assertEquals("run", ActivityMapper.matchSport(hint(null, null, 10_000.0, 3000), sports).presetKey)
        assertEquals("hike", ActivityMapper.matchSport(hint(null, null, 4_000.0, 3600), sports).presetKey)
        assertEquals(SportFamily.CYCLE, SportNames.familyOf("Road Biking"))
        assertEquals(SportFamily.SWIM, SportNames.familyOf("open_water_swimming"))
        assertEquals(SportFamily.RUN, SportNames.familyOf("跑步"))
        assertEquals(SportFamily.MOUNTAINEER, SportNames.fromFit(16, null))
    }

    @Test fun importedWorkoutRecomputesMetrics() {
        val a = ActivityCodec.parse(fixture("weekend_ride.tcx"), null).single()
        val sport = ActivityMapper.matchSport(a, sports)
        val w = ActivityMapper.toWorkout(a, sport, 70.0, 190).workout
        assertEquals(WorkoutSource.IMPORT, w.source)
        assertEquals(sport.id, w.sportId)
        assertEquals(597L, w.durationSec)
        assertEquals(9420.5, w.distanceM, 1e-6)
        assertEquals(310.0, w.values[MetricKind.CALORIES.key]!!, 1e-6)
        assertEquals(9420.5 / 1000 / (597 / 3600.0), w.values[MetricKind.SPEED.key]!!, 0.01)
        assertTrue(w.values[HrZones.AVG_KEY]!! in 140.0..160.0)
        assertTrue(w.values.containsKey(HrZones.keys[2]))
        // GPX has no totals: distance and time come from the track.
        val g = ActivityCodec.parse(fixture("morning_run.gpx"), null).single()
        val gw = ActivityMapper.toWorkout(g, ActivityMapper.matchSport(g, sports), 70.0, 190).workout
        assertTrue(gw.distanceM in 4500.0..5500.0)
        assertTrue(gw.durationSec in 800..1000)
        assertTrue(gw.values[MetricKind.CALORIES.key]!! > 0)
    }

    @Test fun backupRoundTripAndSportPlan() {
        val g = ActivityCodec.parse(fixture("morning_run.gpx"), null).single()
        val w = ActivityMapper.toWorkout(g, sports[0], 70.0, 190).workout.copy(id = 9, note = "晨跑")
        val file = BackupFile(
            appVersion = "0.2.0", exportedAt = 1L, settings = BackupSettings(weightKg = 62.5, maxHr = 188),
            sports = sports.map(BackupCodec::sport),
            workouts = listOf(BackupCodec.workout(w, g.points, g.hr)),
        )
        val bytes = BackupCodec.encode(file)
        assertEquals('P'.code.toByte(), bytes[0])
        val back = BackupCodec.decode(bytes)
        assertEquals(file, back)
        val bw = back.workouts.single()
        assertEquals(w.copy(id = 0), BackupCodec.workout(bw, w.sportId))
        assertEquals(g.points, BackupCodec.points(bw))
        assertEquals(g.hr, BackupCodec.hr(bw))
        val strength = back.sports.first { it.name == "力量训练" }
        assertEquals(sports.first { it.name == "力量训练" }.metrics, strength.metrics)
        // Restore into a fresh install that only has presets: presets map, custom sports get created.
        val fresh = Presets.all.mapIndexed { i, s -> s.copy(id = 100L + i) }
        val plan = BackupCodec.planSports(back.sports, fresh)
        assertEquals(100L, plan[1L]!!.first!!.id)
        assertNull(plan[strength.id]!!.first)
        assertEquals("力量训练", plan[strength.id]!!.second!!.name)
        // Bare JSON also accepted; foreign JSON rejected.
        assertEquals(file, BackupCodec.decode(BackupCodec.json.encodeToString(BackupFile.serializer(), file).toByteArray()))
        try { BackupCodec.decode("{\"format\":\"other\"}".toByteArray()); fail() } catch (_: ImportFormatException) {}
    }
}
