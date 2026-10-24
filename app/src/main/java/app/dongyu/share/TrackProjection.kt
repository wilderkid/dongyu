package app.dongyu.share

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.math.cos

/** Rectangle in card pixels. */
data class PxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
}

/**
 * Web-Mercator fit of a track into [area] of a [canvasW]x[canvasH] card. The same mapping is used to
 * request a map snapshot covering the whole card, so the drawn track lines up with the tiles.
 */
class TrackProjection private constructor(
    val x0: Double, val y0: Double, val scale: Double, val canvasW: Int, val canvasH: Int,
) {
    fun project(lat: Double, lon: Double): Pair<Float, Float> =
        ((mx(lon) - x0) * scale).toFloat() to ((my(lat) - y0) * scale).toFloat()

    /** Lat/lon bounds of the whole canvas: (south, west, north, east). */
    fun canvasBounds(): DoubleArray {
        val x1 = x0 + canvasW / scale
        val y1 = y0 + canvasH / scale
        return doubleArrayOf(lat(y1), lon(x0), lat(y0), lon(x1))
    }

    companion object {
        /** Minimum span (~250 m at the equator) so a tiny track isn't blown up. */
        private const val MIN_SPAN = 250.0 / 40_075_016.0

        fun mx(lon: Double) = (lon + 180.0) / 360.0
        fun my(lat: Double): Double {
            val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
            return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2
        }
        fun lon(x: Double) = x * 360.0 - 180.0
        fun lat(y: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))

        fun fit(points: List<Pair<Double, Double>>, area: PxRect, canvasW: Int, canvasH: Int): TrackProjection? {
            if (points.isEmpty()) return null
            var bx0 = Double.MAX_VALUE; var bx1 = -Double.MAX_VALUE; var by0 = Double.MAX_VALUE; var by1 = -Double.MAX_VALUE
            for ((la, lo) in points) {
                val x = mx(lo); val y = my(la)
                bx0 = min(bx0, x); bx1 = max(bx1, x); by0 = min(by0, y); by1 = max(by1, y)
            }
            val bw = max(bx1 - bx0, MIN_SPAN)
            val bh = max(by1 - by0, MIN_SPAN)
            val s = min(area.width / bw, area.height / bh)
            val cx = (bx0 + bx1) / 2; val cy = (by0 + by1) / 2
            return TrackProjection(cx - area.cx / s, cy - area.cy / s, s, canvasW, canvasH)
        }
    }
}
