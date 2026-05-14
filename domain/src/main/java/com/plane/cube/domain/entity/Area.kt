package com.plane.cube.domain.entity

data class Area(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val southWest: GeoPoint get() = GeoPoint(south, west)
    val northEast: GeoPoint get() = GeoPoint(north, east)
    val center: GeoPoint get() = GeoPoint((south + north) / 2.0, (west + east) / 2.0)

    fun contains(point: GeoPoint): Boolean =
        point.latitude in south..north && point.longitude in west..east

    companion object {
        fun of(a: GeoPoint, b: GeoPoint) = Area(
            south = minOf(a.latitude, b.latitude),
            west = minOf(a.longitude, b.longitude),
            north = maxOf(a.latitude, b.latitude),
            east = maxOf(a.longitude, b.longitude),
        )
    }
}
