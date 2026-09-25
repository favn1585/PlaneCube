package com.plane.cube.domain.repository

import com.plane.cube.domain.entity.GeoPoint
import com.plane.cube.domain.entity.Plane

interface PlaneRepository {

    /**
     * Planes within [radiusNm] nautical miles of [center]. The radius is
     * clamped to [MAX_QUERY_RADIUS_NM].
     */
    suspend fun fetchPlanes(center: GeoPoint, radiusNm: Double): List<Plane>

    companion object {
        /** Largest radius the feed serves (adsb.fi caps queries at 250 NM). */
        const val MAX_QUERY_RADIUS_NM = 250.0
    }
}
