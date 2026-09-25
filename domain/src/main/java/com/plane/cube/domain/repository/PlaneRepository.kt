package com.plane.cube.domain.repository

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.Plane

interface PlaneRepository {

    /**
     * Planes in and around [area]. The result may include aircraft slightly
     * outside it (the feed is queried by radius), so callers that need an
     * exact match should check [Area.contains].
     */
    suspend fun fetchPlanes(area: Area): List<Plane>

    companion object {
        /**
         * Largest radius the feed serves (adsb.fi caps queries at 250 NM).
         * Areas wider than this are only covered around their center.
         */
        const val MAX_QUERY_RADIUS_NM = 250.0
    }
}
