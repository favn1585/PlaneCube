package com.plane.cube.network.repository

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.network.api.OpenSkyApi
import javax.inject.Inject

class PlaneNetworkRepository @Inject constructor(
    private val api: OpenSkyApi,
) : PlaneRepository {

    override suspend fun fetchPlanes(area: Area): List<Plane> = api.statesIn(area)
}
