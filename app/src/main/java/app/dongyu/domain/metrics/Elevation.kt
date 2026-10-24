package app.dongyu.domain.metrics

data class ElevationStats(val gain: Double, val loss: Double, val max: Double?, val min: Double?)

object ElevationCalculator {
    /** Centered moving average; nulls are skipped. */
    fun smooth(values: List<Double>, window: Int = 5): List<Double> {
        if (values.isEmpty() || window <= 1) return values
        val half = window / 2
        return values.indices.map { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(values.lastIndex)
            var s = 0.0
            for (k in from..to) s += values[k]
            s / (to - from + 1)
        }
    }

    /**
     * Gain/loss with smoothing + hysteresis: a climb only counts once the altitude moved
     * [threshold] meters away from the last reference point, which filters GPS jitter.
     */
    fun compute(rawAltitudes: List<Double?>, threshold: Double = 3.0, window: Int = 5): ElevationStats {
        val alts = rawAltitudes.filterNotNull()
        if (alts.isEmpty()) return ElevationStats(0.0, 0.0, null, null)
        val s = smooth(alts, window)
        var gain = 0.0
        var loss = 0.0
        var ref = s.first()
        for (a in s) {
            val diff = a - ref
            if (diff >= threshold) {
                gain += diff; ref = a
            } else if (-diff >= threshold) {
                loss += -diff; ref = a
            }
        }
        return ElevationStats(gain, loss, s.max(), s.min())
    }
}
