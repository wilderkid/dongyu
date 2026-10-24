package app.dongyu.domain.metrics

import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.Workout

/** Builds/updates a workout from user-entered fields, deriving pace/speed/calories where possible. */
object ManualEntry {
    /** Metrics that are always derived from distance + duration and therefore not editable. */
    val derived = setOf(MetricKind.PACE, MetricKind.SPEED, MetricKind.SWIM_PACE)

    /** Which metric inputs to show for a workout of [sport]. GPS workouts hide auto-computed values. */
    fun editableKinds(sport: SportType, hasTrack: Boolean): List<app.dongyu.model.MetricDef> =
        sport.metrics.filter { d ->
            val k = d.kind
            k != MetricKind.DURATION && k != MetricKind.DISTANCE && k !in derived && k != MetricKind.NOTE &&
                !(hasTrack && k.auto)
        }

    fun apply(
        base: Workout,
        sport: SportType,
        durationSec: Long,
        distanceM: Double,
        entered: Map<String, Double?>,
        note: String,
        weightKg: Double,
        hasTrack: Boolean,
    ): Workout {
        val values = base.values.toMutableMap()
        entered.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
        if (!hasTrack) {
            values.remove(MetricKind.PACE.key); values.remove(MetricKind.SPEED.key); values.remove(MetricKind.SWIM_PACE.key)
            if (distanceM > 0 && durationSec > 0) {
                if (sport.has(MetricKind.PACE)) Pace.secPerKm(distanceM, durationSec.toDouble())?.let { values[MetricKind.PACE.key] = it }
                if (sport.has(MetricKind.SPEED) || sport.family == SportFamily.CYCLE) Pace.kmh(distanceM, durationSec.toDouble())?.let { values[MetricKind.SPEED.key] = it }
                if (sport.family == SportFamily.SWIM || sport.has(MetricKind.SWIM_PACE)) Pace.secPer100m(distanceM, durationSec.toDouble())?.let { values[MetricKind.SWIM_PACE.key] = it }
            }
            val gain = values[MetricKind.ELEVATION_GAIN.key]
            if (sport.has(MetricKind.ASCENT_RATE) && gain != null && durationSec > 60) values[MetricKind.ASCENT_RATE.key] = gain / (durationSec / 3600.0)
        }
        if (entered[MetricKind.CALORIES.key] == null && durationSec > 0) {
            values[MetricKind.CALORIES.key] = WorkoutSummaryCalculator.estimateCalories(sport.met, durationSec.toDouble(), weightKg)
        }
        val end = if (hasTrack) base.endTime else base.startTime + durationSec * 1000
        return base.copy(durationSec = durationSec, distanceM = distanceM, values = values, note = note, endTime = maxOf(end, base.startTime))
    }
}
