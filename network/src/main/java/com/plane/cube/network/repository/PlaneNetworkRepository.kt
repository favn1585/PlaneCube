package com.plane.cube.network.repository

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.network.api.AdsbApi
import com.plane.cube.network.model.toPlanes
import javax.inject.Inject

class PlaneNetworkRepository @Inject constructor(
    private val api: AdsbApi,
) : PlaneRepository {
    override suspend fun fetchPlanes(): List<Plane> = api.aircraft().toPlanes()
}
