package com.plane.cube.network.repository

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.network.api.AdsbApi
import com.plane.cube.network.model.toPlanes
import javax.inject.Inject
import kotlin.math.ceil

class PlaneNetworkRepository @Inject constructor(
    private val api: AdsbApi,
) : PlaneRepository {

    /**
     * The feed is queried by center + radius, so the area is turned into the
     * smallest circle around its center that reaches every corner.
     */
    override suspend fun fetchPlanes(area: Area): List<Plane> {
        val center = area.center
        val dist = ceil(area.radiusNm).toInt()
            .coerceIn(MIN_DIST_NM, PlaneRepository.MAX_QUERY_RADIUS_NM.toInt())
        return api.aircraft(center.latitude, center.longitude, dist).toPlanes()
    }

    private companion object {
        const val MIN_DIST_NM = 1
    }
}
