package com.plane.cube.network.api

import com.plane.cube.network.model.AircraftResponse
import retrofit2.http.GET

/**
 * dump1090-style ADS-B feed. The receiver returns every aircraft it currently
 * tracks (no server-side bbox), so callers filter by area client-side.
 */
interface AdsbApi {

    @GET("data/aircraft.json")
    suspend fun aircraft(): AircraftResponse
}
