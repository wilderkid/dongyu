package app.dongyu.domain.metrics

import kotlin.math.roundToLong

object Pace {
    /** Seconds per kilometer, or null if distance is too small to be meaningful. */
    fun secPerKm(distanceM: Double, durationSec: Double): Double? =
        if (distanceM < 10 || durationSec <= 0) null else durationSec / (distanceM / 1000.0)

    /** Seconds per 100 m (swimming). */
    fun secPer100m(distanceM: Double, durationSec: Double): Double? =
        if (distanceM < 10 || durationSec <= 0) null else durationSec / (distanceM / 100.0)

    fun kmh(distanceM: Double, durationSec: Double): Double? =
        if (durationSec <= 0) null else distanceM / durationSec * 3.6

    fun mpsToSecPerKm(mps: Double): Double? = if (mps <= 0.2) null else 1000.0 / mps

    /** 332.4 -> 5'32" */
    fun format(secPerUnit: Double?): String {
        if (secPerUnit == null || secPerUnit.isNaN() || secPerUnit.isInfinite() || secPerUnit > 99 * 60) return "--'--\""
        val total = secPerUnit.roundToLong()
        return "%d'%02d\"".format(total / 60, total % 60)
    }

    /** 3725 -> 1:02:05 ; 325 -> 05:25 */
    fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
