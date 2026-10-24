package app.dongyu.domain

import app.dongyu.model.TrackPoint

/** Synthetic tracks heading due north: 1 degree latitude ≈ 111,195 m. */
object TestTracks {
    const val M_PER_DEG_LAT = 111_195.08

    /** Straight line of [totalM] metres at constant [mps], one point every [stepSec] seconds. */
    fun straight(totalM: Double, mps: Double, stepSec: Int = 5, alt: (Double) -> Double? = { null }): List<TrackPoint> {
        val out = mutableListOf<TrackPoint>()
        var d = 0.0
        var t = 0L
        while (true) {
            out += TrackPoint(time = t * 1000, lat = 30.0 + d / M_PER_DEG_LAT, lon = 120.0, altitude = alt(d))
            if (d >= totalM) break
            d = minOf(totalM, d + mps * stepSec)
            t += stepSec
        }
        return out
    }
}
