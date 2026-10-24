package app.dongyu.domain.metrics

import app.dongyu.model.DerivedKeys
import app.dongyu.model.MetricKind
import app.dongyu.model.SportFamily
import app.dongyu.model.SportType
import app.dongyu.model.TrackPoint

data class TrackSummary(
    val distanceM: Double,
    val movingSec: Double,
    val values: Map<String, Double>,
)

object WorkoutSummaryCalculator {
    const val DEFAULT_WEIGHT_KG = 65.0

    fun estimateCalories(met: Double, durationSec: Double, weightKg: Double = DEFAULT_WEIGHT_KG): Double =
        met * weightKg * durationSec / 3600.0

    /** Derives all auto metrics from a GPS track. */
    fun fromTrack(points: List<TrackPoint>, sport: SportType, weightKg: Double = DEFAULT_WEIGHT_KG): TrackSummary {
        val c = TrackMath.cumulative(points)
        val dist = c.distance.lastOrNull() ?: 0.0
        val moving = c.time.lastOrNull() ?: 0.0
        val values = mutableMapOf<String, Double>()
        val hasBaro = points.any { it.baroAltitude != null }
        val elev = ElevationCalculator.compute(
            points.map { it.baroAltitude ?: it.altitude },
            threshold = if (hasBaro) 1.0 else 3.0,
        )
        Pace.secPerKm(dist, moving)?.let { values[MetricKind.PACE.key] = it }
        Pace.kmh(dist, moving)?.let { values[MetricKind.SPEED.key] = it }
        Pace.secPer100m(dist, moving)?.takeIf { sport.family == SportFamily.SWIM }?.let { values[MetricKind.SWIM_PACE.key] = it }
        maxSpeedKmh(points)?.let { values[MetricKind.MAX_SPEED.key] = it }
        values[MetricKind.ELEVATION_GAIN.key] = elev.gain
        values[MetricKind.ELEVATION_LOSS.key] = elev.loss
        elev.max?.let { values[MetricKind.MAX_ALTITUDE.key] = it }
        if (moving > 60) values[MetricKind.ASCENT_RATE.key] = elev.gain / (moving / 3600.0)
        values[MetricKind.CALORIES.key] = estimateCalories(sport.met, moving, weightKg)
        if (sport.family == SportFamily.RUN) {
            BestEffort.fastest(points, 1000.0)?.let { values[DerivedKeys.BEST_1K] = it }
            BestEffort.fastest(points, 5000.0)?.let { values[DerivedKeys.BEST_5K] = it }
            BestEffort.fastest(points, 10000.0)?.let { values[DerivedKeys.BEST_10K] = it }
            BestEffort.fastest(points, 21097.5)?.let { values[DerivedKeys.BEST_HALF] = it }
        }
        return TrackSummary(dist, moving, values)
    }

    /** Max speed using a 3-sample window over computed segment speeds to suppress spikes. */
    fun maxSpeedKmh(points: List<TrackPoint>): Double? {
        if (points.size < 4) return null
        var best = 0.0
        for (i in 3 until points.size) {
            val a = points[i - 3]
            val b = points[i]
            if (a.segment != b.segment) continue
            var d = 0.0
            for (k in i - 2..i) d += GeoMath.haversine(points[k - 1].lat, points[k - 1].lon, points[k].lat, points[k].lon)
            val dt = (b.time - a.time) / 1000.0
            if (dt > 0) best = maxOf(best, d / dt * 3.6)
        }
        return best.takeIf { it > 0 }
    }
}
