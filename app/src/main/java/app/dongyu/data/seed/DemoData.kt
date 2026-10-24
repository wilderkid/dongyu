package app.dongyu.data.seed

import app.dongyu.domain.metrics.WorkoutSummaryCalculator
import app.dongyu.domain.hr.HrSample
import app.dongyu.domain.hr.HrZones
import app.dongyu.model.Aggregation
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.SwimMode
import app.dongyu.model.SwimStroke
import app.dongyu.model.TrackPoint
import app.dongyu.model.Workout
import app.dongyu.model.WorkoutSource
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic demo/seed data so charts and screenshots look realistic.
 * Only exposed in the UI for debug builds.
 */
object DemoData {

    val customSports: List<SportType> = listOf(
        SportType(
            name = "羽毛球", icon = "badminton", color = 0xFFE5487B, usesGps = false, met = 5.5,
            metrics = listOf(
                MetricDef.of(MetricKind.DURATION), MetricDef.of(MetricKind.COUNT),
                MetricDef.custom("c_games_won", "胜局", "局", Aggregation.SUM),
                MetricDef.of(MetricKind.HEART_RATE), MetricDef.of(MetricKind.CALORIES), MetricDef.of(MetricKind.RPE), MetricDef.of(MetricKind.NOTE),
            ),
        ),
        SportType(
            name = "力量训练", icon = "strength", color = 0xFF00A8A8, usesGps = false, met = 5.0,
            metrics = listOf(
                MetricDef.of(MetricKind.DURATION), MetricDef.of(MetricKind.SETS), MetricDef.of(MetricKind.REPS),
                MetricDef.of(MetricKind.WEIGHT), MetricDef.of(MetricKind.CALORIES), MetricDef.of(MetricKind.RPE), MetricDef.of(MetricKind.NOTE),
            ),
        ),
    )

    data class Item(val workout: Workout, val track: List<TrackPoint>, val hr: List<HrSample> = emptyList())

    /** Hangzhou West Lake area as the demo origin (WGS-84). */
    private const val BASE_LAT = 30.2470
    private const val BASE_LON = 120.1400

    fun generate(
        sports: List<SportType>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        days: Int = 270,
        seed: Int = 42,
        withTracks: Boolean = true,
    ): List<Item> {
        val rnd = Random(seed)
        val out = mutableListOf<Item>()
        fun sportOf(f: SportFamily) = sports.firstOrNull { it.family == f && it.presetKey != null }
        val run = sportOf(SportFamily.RUN)
        val hike = sportOf(SportFamily.HIKE)
        val mount = sportOf(SportFamily.MOUNTAINEER)
        val swim = sportOf(SportFamily.SWIM)
        val cycle = sportOf(SportFamily.CYCLE)
        val customs = sports.filter { it.presetKey == null }

        for (back in days downTo 0) {
            val date = today.minusDays(back.toLong())
            val dow = date.dayOfWeek.value // 1 = Mon
            // fitness slowly improves over time
            val progress = 1.0 - back / days.toDouble()
            fun startAt(hour: Int, minute: Int = rnd.nextInt(0, 50)) =
                date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

            if (run != null && (dow == 2 || dow == 4 || dow == 6) && rnd.nextDouble() < 0.88) {
                val long = dow == 6
                val km = if (long) rnd.nextDouble(9.0, 16.0 + 5 * progress) else rnd.nextDouble(4.0, 8.0)
                val pace = (if (long) 345.0 else 325.0) - 25 * progress + rnd.nextDouble(-12.0, 12.0)
                out += gpsWorkout(run, startAt(if (long) 7 else 19), km * 1000, pace, 5, rnd, Terrain.FLAT, withTracks)
            }
            if (cycle != null && dow == 7 && rnd.nextDouble() < 0.75) {
                val km = rnd.nextDouble(25.0, 60.0)
                val kmh = rnd.nextDouble(20.0, 27.0) + 2 * progress
                out += gpsWorkout(cycle, startAt(8), km * 1000, 3600.0 / kmh, 10, rnd, Terrain.ROLLING, withTracks)
            }
            if (hike != null && dow == 7 && date.dayOfMonth in 1..14 && rnd.nextDouble() < 0.45) {
                val km = rnd.nextDouble(8.0, 15.0)
                out += gpsWorkout(hike, startAt(9), km * 1000, rnd.nextDouble(700.0, 900.0), 10, rnd, Terrain.HILL, withTracks)
            }
            if (mount != null && dow == 6 && date.dayOfMonth in 15..21 && rnd.nextDouble() < 0.6) {
                val km = rnd.nextDouble(10.0, 18.0)
                out += gpsWorkout(mount, startAt(6), km * 1000, rnd.nextDouble(950.0, 1250.0), 15, rnd, Terrain.MOUNTAIN, withTracks)
            }
            if (swim != null && (dow == 3 || (dow == 5 && rnd.nextBoolean())) && rnd.nextDouble() < 0.8) {
                out += swimWorkout(swim, startAt(20), rnd, progress)
            }
            customs.forEachIndexed { idx, s ->
                val day = if (idx % 2 == 0) 1 else 5
                if (dow == day && rnd.nextDouble() < 0.7) out += genericWorkout(s, startAt(18 + idx), rnd)
            }
        }
        return out
    }

    enum class Terrain { FLAT, ROLLING, HILL, MOUNTAIN }

    private fun gpsWorkout(
        sport: SportType, start: Long, distanceM: Double, paceSecPerKm: Double, stepSec: Int,
        rnd: Random, terrain: Terrain, withTrack: Boolean,
    ): Item {
        val track = track(start, distanceM, paceSecPerKm, stepSec, rnd, terrain)
        val summary = WorkoutSummaryCalculator.fromTrack(track, sport)
        val hr = when (sport.family) {
            SportFamily.RUN -> rnd.nextInt(142, 168)
            SportFamily.CYCLE -> rnd.nextInt(128, 150)
            else -> rnd.nextInt(115, 140)
        }
        // ~75% of GPS sessions were "recorded with a strap": synthesize a per-sample curve.
        val samples = if (withTrack && rnd.nextDouble() < 0.75) hrCurve(track, hr, rnd) else emptyList()
        val values = summary.values.toMutableMap().apply {
            put(MetricKind.HEART_RATE.key, hr.toDouble())
            put(MetricKind.MAX_HEART_RATE.key, (hr + rnd.nextInt(12, 25)).toDouble())
            put(MetricKind.RPE.key, rnd.nextInt(4, 9).toDouble())
            HrZones.summarize(samples, DEMO_MAX_HR)?.let { putAll(HrZones.toValues(it)) }
        }
        val w = Workout(
            sportId = sport.id, startTime = start, endTime = track.last().time,
            durationSec = summary.movingSec.toLong(), distanceM = summary.distanceM,
            values = values, source = WorkoutSource.GPS,
        )
        return Item(w, if (withTrack) track else emptyList(), samples)
    }

    const val DEMO_MAX_HR = 190

    /** Warm-up ramp, climbs push HR up, a little noise and a final kick. One sample every 5 s. */
    fun hrCurve(track: List<TrackPoint>, target: Int, rnd: Random): List<HrSample> {
        if (track.size < 2) return emptyList()
        val out = ArrayList<HrSample>()
        var cur = 92.0
        val t0 = track.first().time
        val total = (track.last().time - t0).coerceAtLeast(1)
        var i = 0
        var t = t0
        while (t <= track.last().time) {
            while (i < track.size - 2 && track[i + 1].time < t) i++
            val a = track[i]; val b = track[minOf(i + 1, track.size - 1)]
            val dt = ((b.time - a.time) / 1000.0).coerceAtLeast(1.0)
            val climb = (((b.altitude ?: 0.0) - (a.altitude ?: 0.0)) / dt).coerceIn(-1.0, 1.0)
            val frac = (t - t0).toDouble() / total
            val kick = if (frac > 0.92) 12.0 else 0.0
            val goal = target + climb * 40 + kick + 6 * sin(frac * 9.0)
            cur += (goal - cur) * 0.08 + rnd.nextDouble(-1.6, 1.6)
            out += HrSample(t, cur.roundToInt().coerceIn(60, 205))
            t += 5_000
        }
        return out
    }

    /** A wobbly loop around the base point with speed variation and an altitude profile. */
    fun track(start: Long, distanceM: Double, paceSecPerKm: Double, stepSec: Int, rnd: Random, terrain: Terrain): List<TrackPoint> {
        val radius = distanceM / (2 * PI)
        val phase = rnd.nextDouble(0.0, 2 * PI)
        val centerLat = BASE_LAT + rnd.nextDouble(-0.03, 0.03)
        val centerLon = BASE_LON + rnd.nextDouble(-0.03, 0.03)
        val baseAlt = when (terrain) {
            Terrain.FLAT -> 12.0; Terrain.ROLLING -> 30.0; Terrain.HILL -> 60.0; Terrain.MOUNTAIN -> 1400.0
        }
        val climb = when (terrain) {
            Terrain.FLAT -> 8.0; Terrain.ROLLING -> 60.0; Terrain.HILL -> rnd.nextDouble(350.0, 650.0); Terrain.MOUNTAIN -> rnd.nextDouble(900.0, 1500.0)
        }
        val pts = mutableListOf<TrackPoint>()
        var covered = 0.0
        var t = start
        var i = 0
        while (covered <= distanceM) {
            val p = covered / distanceM
            val theta = phase + 2 * PI * p
            val r = radius * (1 + 0.18 * sin(3 * theta + phase))
            val dx = r * cos(theta) + rnd.nextDouble(-2.0, 2.0)
            val dy = r * sin(theta) + rnd.nextDouble(-2.0, 2.0)
            val lat = centerLat + dy / 111_320.0
            val lon = centerLon + dx / (111_320.0 * cos(Math.toRadians(centerLat)))
            val profile = if (terrain == Terrain.FLAT || terrain == Terrain.ROLLING)
                sin(p * 6 * PI + phase) * 0.5 + 0.5 else sin(p * PI)
            val alt = baseAlt + climb * profile + rnd.nextDouble(-1.5, 1.5)
            // slower on climbs, faster downhill, plus fatigue + random noise
            val grade = if (terrain == Terrain.HILL || terrain == Terrain.MOUNTAIN) cos(p * PI) else 0.0
            val pace = paceSecPerKm * (1 + 0.25 * grade + 0.04 * sin(i / 17.0) + rnd.nextDouble(-0.05, 0.05))
            val speed = 1000.0 / pace
            pts += TrackPoint(t, lat, lon, alt, accuracy = rnd.nextDouble(3.0, 9.0).toFloat(), speed = speed.toFloat(), segment = 0)
            covered += speed * stepSec
            t += stepSec * 1000L
            i++
        }
        return pts
    }

    private fun swimWorkout(sport: SportType, start: Long, rnd: Random, progress: Double): Item {
        val pool = if (rnd.nextDouble() < 0.7) 25.0 else 50.0
        val lengths = (rnd.nextInt(1200, 2600) / pool).roundToInt()
        val dist = lengths * pool
        val per100 = 135.0 - 15 * progress + rnd.nextDouble(-8.0, 8.0)
        val dur = (dist / 100 * per100).toLong()
        val values = mapOf(
            MetricKind.LAPS.key to lengths.toDouble(),
            MetricKind.POOL_LENGTH.key to pool,
            MetricKind.SWIM_PACE.key to per100,
            MetricKind.STROKE.key to (if (rnd.nextDouble() < 0.7) SwimStroke.FREESTYLE else SwimStroke.BREASTSTROKE).ordinal.toDouble(),
            MetricKind.HEART_RATE.key to rnd.nextInt(120, 145).toDouble(),
            MetricKind.CALORIES.key to WorkoutSummaryCalculator.estimateCalories(sport.met, dur.toDouble()),
            MetricKind.RPE.key to rnd.nextInt(4, 8).toDouble(),
        )
        return Item(
            Workout(
                sportId = sport.id, startTime = start, endTime = start + dur * 1000, durationSec = dur, distanceM = dist,
                values = values, source = WorkoutSource.TIMER, swimMode = SwimMode.POOL,
            ),
            emptyList(),
        )
    }

    private fun genericWorkout(sport: SportType, start: Long, rnd: Random): Item {
        val dur = rnd.nextLong(45 * 60, 100 * 60)
        val values = mutableMapOf<String, Double>()
        for (m in sport.metrics) {
            val v: Double? = when (m.kind) {
                MetricKind.COUNT -> rnd.nextInt(4, 12).toDouble()
                MetricKind.SETS -> rnd.nextInt(12, 24).toDouble()
                MetricKind.REPS -> rnd.nextInt(120, 260).toDouble()
                MetricKind.WEIGHT -> (rnd.nextInt(16, 26) * 5).toDouble()
                MetricKind.HEART_RATE -> rnd.nextInt(110, 150).toDouble()
                MetricKind.CALORIES -> WorkoutSummaryCalculator.estimateCalories(sport.met, dur.toDouble())
                MetricKind.RPE -> rnd.nextInt(5, 9).toDouble()
                MetricKind.CUSTOM -> rnd.nextInt(1, 8).toDouble()
                else -> null
            }
            if (v != null) values[m.id] = max(0.0, v)
        }
        return Item(
            Workout(sportId = sport.id, startTime = start, endTime = start + dur * 1000, durationSec = dur, values = values, source = WorkoutSource.TIMER),
            emptyList(),
        )
    }
}
