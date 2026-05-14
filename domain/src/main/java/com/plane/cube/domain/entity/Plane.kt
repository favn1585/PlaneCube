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
)
