package com.plane.cube.domain.entity

/**
 * An oriented rectangular area on the Earth's surface defined by four corners
 * in order (any winding). The corners may be axis-aligned (north-up) or rotated
 * to any bearing; the consumer should not assume axis alignment.
 */
data class Area(
    val corners: List<GeoPoint>,
) {
    init {
        require(corners.size == 4) { "Area must have exactly 4 corners, got ${corners.size}" }
    }

    /** Axis-aligned bounding box, useful for API queries like OpenSky's bbox. */
    val south: Double get() = corners.minOf { it.latitude }
    val west: Double get() = corners.minOf { it.longitude }
    val north: Double get() = corners.maxOf { it.latitude }
    val east: Double get() = corners.maxOf { it.longitude }

    val southWest: GeoPoint get() = GeoPoint(south, west)
    val northEast: GeoPoint get() = GeoPoint(north, east)

    val center: GeoPoint
        get() = GeoPoint(
            corners.sumOf { it.latitude } / corners.size.toDouble(),
            corners.sumOf { it.longitude } / corners.size.toDouble(),
        )

    /** Polygon point-in-polygon test using the ray-casting algorithm. */
    fun contains(point: GeoPoint): Boolean {
        var inside = false
        var j = corners.size - 1
        for (i in corners.indices) {
            val pi = corners[i]
            val pj = corners[j]
            val intersects = (pi.latitude > point.latitude) != (pj.latitude > point.latitude) &&
                point.longitude <
                (pj.longitude - pi.longitude) * (point.latitude - pi.latitude) /
                (pj.latitude - pi.latitude) + pi.longitude
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        /** Build an axis-aligned (north-up) rectangle from two diagonal corners. */
        fun of(a: GeoPoint, b: GeoPoint): Area {
            val south = minOf(a.latitude, b.latitude)
            val north = maxOf(a.latitude, b.latitude)
            val west = minOf(a.longitude, b.longitude)
            val east = maxOf(a.longitude, b.longitude)
            return Area(
                listOf(
                    GeoPoint(south, west),
                    GeoPoint(south, east),
                    GeoPoint(north, east),
                    GeoPoint(north, west),
                ),
            )
        }
    }
}
