package com.plane.cube.domain.repository

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.Plane

interface PlaneRepository {

    suspend fun fetchPlanes(): List<Plane>
}
