package com.plane.cube.network.api

import com.plane.cube.network.model.StatesResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for OpenSky's REST API. Authentication is supplied by
 * an OkHttp interceptor (see `AuthInterceptor`) so individual methods don't
 * have to thread the bearer token through.
 */
interface OpenSkyApi {

    @GET("/api/states/all")
    suspend fun states(
        @Query("lamin") laMin: Double,
        @Query("lomin") loMin: Double,
        @Query("lamax") laMax: Double,
        @Query("lomax") loMax: Double,
    ): StatesResponse
}
