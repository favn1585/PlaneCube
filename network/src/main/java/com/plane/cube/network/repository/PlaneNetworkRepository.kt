package com.plane.cube.network.repository

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.network.api.OpenSkyApi
import com.plane.cube.network.model.toPlanes
import javax.inject.Inject

class PlaneNetworkRepository @Inject constructor(
    private val api: OpenSkyApi,
) : PlaneRepository {

    override suspend fun fetchPlanes(area: Area): List<Plane> = api.states(
        laMin = area.south,
        loMin = area.west,
        laMax = area.north,
        loMax = area.east,
    ).toPlanes()
}
