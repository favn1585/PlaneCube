package com.plane.cube.network.api

import com.plane.cube.network.model.AircraftResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * adsb.fi open data (readsb v3). Returns every aircraft within [distNm]
 * nautical miles of the given point; the server caps the radius at 250 NM and
 * allows roughly one request per second.
 */
interface AdsbApi {

    @GET("v3/lat/{lat}/lon/{lon}/dist/{dist}")
    suspend fun aircraft(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Path("dist") distNm: Int,
    ): AircraftResponse
}
