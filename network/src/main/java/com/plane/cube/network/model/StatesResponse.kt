package com.plane.cube.network.model

import com.plane.cube.domain.entity.GeoPoint
import com.plane.cube.domain.entity.Plane
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenSky `/api/states/all` response. The `states` array is an array of arrays
 * where each inner array uses positional indexes (see API docs):
 *   0 icao24, 1 callsign, 2 origin_country, 3 time_position, 4 last_contact,
 *   5 longitude, 6 latitude, 7 baro_altitude, 8 on_ground, 9 velocity,
 *   10 true_track, 11 vertical_rate, 12 sensors, 13 geo_altitude, ...
 */
@Serializable
data class StatesResponse(
    @SerialName("time") val time: Long,
    @SerialName("states") val states: List<JsonArray>? = null,
)

fun StatesResponse.toPlanes(): List<Plane> =
    states.orEmpty().mapNotNull { it.toPlane() }

private fun JsonArray.toPlane(): Plane? {
    val icao24 = stringAt(0) ?: return null
    val longitude = doubleAt(5) ?: return null
    val latitude = doubleAt(6) ?: return null
    val baroAltitude = doubleAt(7)
    val geoAltitude = doubleAt(13)
    return Plane(
        icao24 = icao24,
        callsign = stringAt(1)?.trim()?.takeIf { it.isNotEmpty() },
        originCountry = stringAt(2),
        position = GeoPoint(latitude, longitude),
        altitudeMeters = geoAltitude ?: baroAltitude,
        velocityMetersPerSec = doubleAt(9),
        trueTrackDegrees = doubleAt(10),
        onGround = boolAt(8) ?: false,
    )
}

private fun JsonArray.elementOrNull(index: Int): JsonElement? =
    getOrNull(index)?.takeIf { it !is JsonNull }

private fun JsonArray.stringAt(index: Int): String? =
    elementOrNull(index)?.jsonPrimitive?.content

private fun JsonArray.doubleAt(index: Int): Double? =
    elementOrNull(index)?.jsonPrimitive?.runCatching { double }?.getOrNull()

private fun JsonArray.boolAt(index: Int): Boolean? =
    elementOrNull(index)?.jsonPrimitive?.runCatching { boolean }?.getOrNull()
