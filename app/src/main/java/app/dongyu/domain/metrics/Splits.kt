package app.dongyu.domain.metrics

import app.dongyu.model.TrackPoint

data class Split(
    val index: Int,
    val distanceM: Double,
    val durationSec: Double,
    val elevationDelta: Double,
    /** True for the trailing partial split. */
    val partial: Boolean,
) {
    val paceSecPerKm: Double? get() = Pace.secPerKm(distanceM, durationSec)
    val speedKmh: Double? get() = Pace.kmh(distanceM, durationSec)
}

/** Cumulative moving distance / time along a track; pauses (segment changes) contribute nothing. */
data class Cumulative(val distance: DoubleArray, val time: DoubleArray, val altitude: Array<Double?>)

object TrackMath {
    fun cumulative(points: List<TrackPoint>): Cumulative {
        val n = points.size
        val d = DoubleArray(n)
        val t = DoubleArray(n)
        val alt = arrayOfNulls<Double>(n)
        for (i in 0 until n) {
            alt[i] = points[i].baroAltitude ?: points[i].altitude
            if (i == 0) continue
            val a = points[i - 1]
            val b = points[i]
            if (a.segment == b.segment) {
                d[i] = d[i - 1] + GeoMath.haversine(a.lat, a.lon, b.lat, b.lon)
                t[i] = t[i - 1] + (b.time - a.time).coerceAtLeast(0) / 1000.0
            } else {
                d[i] = d[i - 1]
                t[i] = t[i - 1]
            }
        }
        return Cumulative(d, t, alt)
    }

    fun totalDistance(points: List<TrackPoint>): Double = cumulative(points).distance.lastOrNull() ?: 0.0
    fun movingTimeSec(points: List<TrackPoint>): Double = cumulative(points).time.lastOrNull() ?: 0.0
}

object SplitCalculator {
    /**
     * Splits every [splitM] meters with linear interpolation at the boundary.
     * A trailing partial split is included when it is at least [minPartialM] long.
     */
    fun splits(points: List<TrackPoint>, splitM: Double = 1000.0, minPartialM: Double = 50.0): List<Split> {
        if (points.size < 2) return emptyList()
        val c = TrackMath.cumulative(points)
        val result = mutableListOf<Split>()
        var startD = 0.0
        var startT = 0.0
        var startAlt: Double? = c.altitude.firstOrNull()
        var next = splitM
        for (i in 1 until points.size) {
            while (c.distance[i] >= next) {
                val d0 = c.distance[i - 1]
                val d1 = c.distance[i]
                val f = if (d1 - d0 > 0) (next - d0) / (d1 - d0) else 1.0
                val tAt = c.time[i - 1] + f * (c.time[i] - c.time[i - 1])
                val a0 = c.altitude[i - 1]
                val a1 = c.altitude[i]
                val altAt = if (a0 != null && a1 != null) a0 + f * (a1 - a0) else a1 ?: a0
                result += Split(
                    index = result.size + 1,
                    distanceM = splitM,
                    durationSec = tAt - startT,
                    elevationDelta = if (altAt != null && startAlt != null) altAt - startAlt else 0.0,
                    partial = false,
                )
                startD = next
                startT = tAt
                startAlt = altAt
                next += splitM
            }
        }
        val remaining = c.distance.last() - startD
        if (remaining >= minPartialM) {
            val endAlt = c.altitude.last()
            result += Split(
                index = result.size + 1,
                distanceM = remaining,
                durationSec = c.time.last() - startT,
                elevationDelta = if (endAlt != null && startAlt != null) endAlt - startAlt else 0.0,
                partial = true,
            )
        }
        return result
    }
}

object BestEffort {
    /** Fastest time (seconds) to cover [targetM] anywhere within the track, or null if too short. */
    fun fastest(points: List<TrackPoint>, targetM: Double): Double? {
        if (points.size < 2) return null
        val c = TrackMath.cumulative(points)
        if (c.distance.last() < targetM) return null
        var best = Double.MAX_VALUE
        var i = 0
        for (j in 1 until points.size) {
            // advance i while the window still covers the target
            while (i + 1 < j && c.distance[j] - c.distance[i + 1] >= targetM) i++
            val covered = c.distance[j] - c.distance[i]
            if (covered >= targetM) {
                // interpolate the exact start inside [i, i+1]
                val excess = covered - targetM
                val segD = c.distance[i + 1] - c.distance[i]
                val segT = c.time[i + 1] - c.time[i]
                val startT = c.time[i] + if (segD > 0) segT * (excess / segD).coerceIn(0.0, 1.0) else 0.0
                val t = c.time[j] - startT
                if (t in 0.0..<best) best = t
            }
        }
        return if (best == Double.MAX_VALUE) null else best
    }
}
