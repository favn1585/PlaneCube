package com.plane.cube.network.api

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.Plane
import com.plane.cube.network.auth.OpenSkyTokenProvider
import com.plane.cube.network.di.OpenSkyClient
import com.plane.cube.network.model.StatesResponse
import com.plane.cube.network.model.toPlanes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import javax.inject.Inject

class OpenSkyApi @Inject constructor(
    @OpenSkyClient private val client: HttpClient,
    private val tokenProvider: OpenSkyTokenProvider,
) {
    suspend fun statesIn(area: Area): List<Plane> {
        val token = tokenProvider.bearer()
        val response: StatesResponse = client.get("/api/states/all") {
            header(HttpHeaders.Authorization, "Bearer $token")
            parameter("lamin", area.south)
            parameter("lomin", area.west)
            parameter("lamax", area.north)
            parameter("lomax", area.east)
        }.body()
        return response.toPlanes()
    }
}
