package app.dongyu.domain.metrics

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance between two WGS-84 points in meters. */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
