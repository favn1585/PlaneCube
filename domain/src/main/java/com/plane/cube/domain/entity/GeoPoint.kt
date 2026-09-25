package com.plane.cube.domain.entity

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    /** Great-circle (haversine) distance to [other], in nautical miles. */
    fun distanceNmTo(other: GeoPoint): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(other.longitude - longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_NM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private companion object {
        const val EARTH_RADIUS_NM = 3440.065
    }
}
