package app.dongyu.transfer

import app.dongyu.domain.hr.HrSample
import app.dongyu.domain.hr.HrZones
import app.dongyu.domain.metrics.Pace
import app.dongyu.domain.metrics.WorkoutSummaryCalculator
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.TrackPoint
import app.dongyu.model.Workout
import app.dongyu.model.WorkoutSource
import app.dongyu.model.WorkoutStatus

/** Converts between our workouts and file-level activities, and picks a sport for imports. */
object ActivityMapper {
    fun toActivity(w: Workout, sport: SportType, points: List<TrackPoint>, hr: List<HrSample>) = ActivityData(
        sport = SportHint(sport.family.takeUnless { it == SportFamily.GENERIC }, if (sport.presetKey == null) sport.name else null),
        name = w.note.takeIf { it.isNotBlank() },
        startTime = w.startTime,
        endTime = if (w.endTime > w.startTime) w.endTime else w.startTime + w.durationSec * 1000,
        durationSec = w.durationSec,
        distanceM = w.distanceM,
        calories = w.calories.takeIf { it > 0 },
        points = points, hr = hr,
    )

    /**
     * Picks the user's sport for an imported activity: an exact custom-sport name match first, then
     * the declared family, then a guess from average speed (≥ 15 km/h cycling, ≥ 6.5 running, else hiking).
     */
    fun matchSport(a: ActivityData, sports: List<SportType>): SportType {
        val active = sports.filterNot { it.archived }.ifEmpty { sports }
        a.sport.label?.let { l -> active.firstOrNull { it.presetKey == null && it.name.equals(l.trim(), ignoreCase = true) }?.let { return it } }
        val family = a.sport.family ?: guessFamily(a)
        return active.firstOrNull { it.family == family && it.presetKey != null }
            ?: active.firstOrNull { it.family == family }
            ?: active.first()
    }

    fun guessFamily(a: ActivityData): SportFamily {
        val dist = a.distanceM ?: cumulativeDistance(a.points).lastOrNull() ?: 0.0
        val sec = a.durationSec ?: ((a.endTime - a.startTime) / 1000)
        val kmh = Pace.kmh(dist, sec.toDouble()) ?: return SportFamily.RUN
        return when {
            kmh >= 15 -> SportFamily.CYCLE
            kmh >= 6.5 -> SportFamily.RUN
            else -> SportFamily.HIKE
        }
    }

    data class Imported(val workout: Workout, val points: List<TrackPoint>, val hr: List<HrSample>)

    /** Builds the stored workout, recomputing derived metrics with the same code as live recording. */
    fun toWorkout(a: ActivityData, sport: SportType, weightKg: Double, maxHr: Int): Imported {
        val values = mutableMapOf<String, Double>()
        var distance = a.distanceM?.takeIf { it > 0 } ?: 0.0
        var duration = a.durationSec
        if (a.points.size >= 2) {
            val s = WorkoutSummaryCalculator.fromTrack(a.points, sport, weightKg)
            values.putAll(s.values)
            if (distance <= 0) distance = s.distanceM
            if (duration == null || duration <= 0) duration = s.movingSec.toLong()
        }
        val dur = (duration ?: ((a.endTime - a.startTime) / 1000)).coerceAtLeast(0)
        // Pace/speed follow the file's own distance & timer so they agree with what the source app showed.
        Pace.secPerKm(distance, dur.toDouble())?.let { values[MetricKind.PACE.key] = it }
        Pace.kmh(distance, dur.toDouble())?.let { values[MetricKind.SPEED.key] = it }
        if (sport.family == SportFamily.SWIM) Pace.secPer100m(distance, dur.toDouble())?.let { values[MetricKind.SWIM_PACE.key] = it }
        values[MetricKind.CALORIES.key] = a.calories ?: WorkoutSummaryCalculator.estimateCalories(sport.met, dur.toDouble(), weightKg)
        HrZones.summarize(a.hr, maxHr)?.let { values.putAll(HrZones.toValues(it)) }
        val w = Workout(
            sportId = sport.id, startTime = a.startTime, endTime = maxOf(a.endTime, a.startTime + dur * 1000),
            durationSec = dur, distanceM = distance, values = values, note = a.name?.takeUnless { it == sport.name }.orEmpty(),
            source = WorkoutSource.IMPORT, status = WorkoutStatus.FINISHED,
        )
        return Imported(w, a.points, a.hr)
    }
}
