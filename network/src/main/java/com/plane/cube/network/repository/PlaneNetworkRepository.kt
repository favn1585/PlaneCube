package com.plane.cube.network.repository

import com.plane.cube.domain.entity.GeoPoint
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.network.api.AdsbApi
import com.plane.cube.network.model.toPlanes
import javax.inject.Inject
import kotlin.math.ceil

class PlaneNetworkRepository @Inject constructor(
    private val api: AdsbApi,
) : PlaneRepository {

    override suspend fun fetchPlanes(center: GeoPoint, radiusNm: Double): List<Plane> {
        val dist = ceil(radiusNm).toInt()
            .coerceIn(MIN_DIST_NM, PlaneRepository.MAX_QUERY_RADIUS_NM.toInt())
        return api.aircraft(center.latitude, center.longitude, dist).toPlanes()
    }

    private companion object {
        const val MIN_DIST_NM = 1
    }
}
