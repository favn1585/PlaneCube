package com.plane.cube.domain.entity

data class Plane(
    val icao24: String,
    val callsign: String?,
    val originCountry: String?,
    val position: GeoPoint,
    val altitudeMeters: Double?,
    val velocityMetersPerSec: Double?,
    val trueTrackDegrees: Double?,
    val onGround: Boolean,
    /** ICAO aircraft type designator, e.g. `A20N`, `C172`, `EC45`. */
    val typeDesignator: String? = null,
    /** ADS-B emitter category, e.g. `A3` (large aircraft) or `A7` (rotorcraft). */
    val category: String? = null,
) {
    /**
     * False for things that transmit ADS-B but don't fly: airport towers and
     * ground stations (`TWR`/`GND` type codes) and surface vehicles such as
     * follow-me cars (emitter categories `C0`–`C3`).
     */
    val isAircraft: Boolean
        get() = category?.startsWith("C") != true && typeDesignator !in GROUND_TYPES

    private companion object {
        val GROUND_TYPES = setOf("TWR", "GND")
    }
}
