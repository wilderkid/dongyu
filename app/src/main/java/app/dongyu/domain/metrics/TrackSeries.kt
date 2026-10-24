package app.dongyu.domain.metrics

import app.dongyu.model.TrackPoint

data class SeriesPoint(val distanceKm: Double, val speedKmh: Double, val altitude: Double?)

/** Resamples a track into [buckets] evenly spaced (by distance) points for charts. */
object TrackSeries {
    fun build(points: List<TrackPoint>, buckets: Int = 60): List<SeriesPoint> {
        if (points.size < 3) return emptyList()
        val c = TrackMath.cumulative(points)
        val total = c.distance.last()
        if (total < 50) return emptyList()
        val alts = ElevationCalculator.smooth(c.altitude.map { it ?: Double.NaN }.let { l ->
            // fill NaN with nearest known value so smoothing works
            var last = l.firstOrNull { !it.isNaN() } ?: return@let l
            l.map { if (it.isNaN()) last else { last = it; it } }
        }, 7)
        val out = ArrayList<SeriesPoint>(buckets)
        val step = total / buckets
        var j = 0
        for (b in 0 until buckets) {
            val from = b * step
            val to = from + step
            while (j < c.distance.size - 1 && c.distance[j] < from) j++
            var k = j
            while (k < c.distance.size - 1 && c.distance[k] < to) k++
            val dd = c.distance[k] - c.distance[j]
            val dt = c.time[k] - c.time[j]
            val speed = if (dt > 0) dd / dt * 3.6 else out.lastOrNull()?.speedKmh ?: 0.0
            val alt = alts.getOrNull(k)?.takeIf { !it.isNaN() }
            out += SeriesPoint((from + to) / 2 / 1000.0, speed, alt)
        }
        // light smoothing of speed (3-point)
        return out.mapIndexed { i, p ->
            val a = out.getOrNull(i - 1)?.speedKmh ?: p.speedKmh
            val b = out.getOrNull(i + 1)?.speedKmh ?: p.speedKmh
            p.copy(speedKmh = (a + p.speedKmh + b) / 3)
        }
    }
}
