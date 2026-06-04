package com.plane.cube.network.model

import com.plane.cube.domain.entity.GeoPoint
import com.plane.cube.domain.entity.Plane
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * dump1090 `aircraft.json`. dump1090 reports altitude in **feet** and speed in
 * **knots**; the `altitude` field is the string `"ground"` when a plane is on
 * the ground, hence the lenient [JsonElement] type.
 */
@Serializable
data class AircraftResponse(
    @SerialName("now") val now: Double = 0.0,
    @SerialName("aircraft") val aircraft: List<AircraftDto> = emptyList(),
)

@Serializable
data class AircraftDto(
    @SerialName("hex") val hex: String? = null,
    @SerialName("flight") val flight: String? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    @SerialName("altitude") val altitude: JsonElement? = null,
    @SerialName("track") val track: Double? = null,
    @SerialName("speed") val speed: Double? = null,
)

private const val FEET_TO_METERS = 0.3048
private const val KNOTS_TO_MPS = 0.514444

fun AircraftResponse.toPlanes(): List<Plane> = aircraft.mapNotNull { it.toPlaneOrNull() }

private fun AircraftDto.toPlaneOrNull(): Plane? {
    val icao = hex?.takeIf { it.isNotBlank() } ?: return null
    val latitude = lat ?: return null
    val longitude = lon ?: return null

    val altitudeContent = altitude?.jsonPrimitive?.content
    val onGround = altitudeContent.equals("ground", ignoreCase = true)
    val altitudeFeet = if (onGround) null else altitude?.jsonPrimitive?.doubleOrNull

    return Plane(
        icao24 = icao,
        callsign = flight?.trim()?.takeIf { it.isNotEmpty() },
        originCountry = null,
        position = GeoPoint(latitude, longitude),
        altitudeMeters = altitudeFeet?.let { it * FEET_TO_METERS },
        velocityMetersPerSec = speed?.let { it * KNOTS_TO_MPS },
        trueTrackDegrees = track,
        onGround = onGround,
    )
}
