package com.plane.cube.domain.entity

import kotlin.math.hypot

data class TrackingPreferences(
    val area: Area,
    val maxAltitudeMeters: Double,
    /**
     * How far from the tracking box (the area, from the ground up to
     * [maxAltitudeMeters]) a plane starts to count as approaching it.
     */
    val warningDistanceMeters: Double = DEFAULT_WARNING_DISTANCE_M,
) {
    /**
     * Whether [plane] is inside the tracking box: over the area, at or below
     * [maxAltitudeMeters]. Planes with an unknown altitude never count.
     */
    fun isInside(plane: Plane): Boolean {
        val altitude = if (plane.onGround) 0.0 else plane.altitudeMeters ?: return false
        return altitude <= maxAltitudeMeters && area.contains(plane.position)
    }

    /**
     * How close [plane] is to the tracking box, from 0 (at least
     * [warningDistanceMeters] away) to 1 (inside it). The distance is 3D:
     * horizontal distance to the area's edge combined with height above the
     * altitude ceiling, so e.g. with a 2 km warning distance a plane 1 km
     * above the ceiling, or 1 km outside the edge, is at 0.5.
     *
     * Null when the plane's altitude is unknown.
     */
    fun proximityOf(plane: Plane): Double? {
        val altitude = if (plane.onGround) 0.0 else plane.altitudeMeters ?: return null
        val horizontal = area.distanceKmTo(plane.position) * METERS_PER_KM
        val vertical = (altitude - maxAltitudeMeters).coerceAtLeast(0.0)
        val distance = hypot(horizontal, vertical)
        return (1.0 - distance / warningDistanceMeters).coerceIn(0.0, 1.0)
    }

    companion object {
        const val DEFAULT_WARNING_DISTANCE_M = 2_000.0
        private const val METERS_PER_KM = 1_000.0
    }
}
