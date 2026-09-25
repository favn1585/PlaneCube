package com.plane.cube.domain.entity

import kotlin.math.cos
import kotlin.math.hypot

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

    /** Axis-aligned bounding box, e.g. for logging or map bounds. */
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

    /** Radius of the smallest circle around [center] that reaches every corner. */
    val radiusNm: Double get() = corners.maxOf { center.distanceNmTo(it) }

    /**
     * Distance from [point] to the area in kilometers: 0 inside, otherwise the
     * distance to the nearest edge. Uses a local flat projection around the
     * point, which is accurate to well under 1% at the scales of a tracking
     * area plus its buffer.
     */
    fun distanceKmTo(point: GeoPoint): Double {
        if (contains(point)) return 0.0
        val kmPerDegLat = KM_PER_DEG_LAT
        val kmPerDegLon = KM_PER_DEG_LAT * cos(Math.toRadians(point.latitude))
        fun x(p: GeoPoint) = (p.longitude - point.longitude) * kmPerDegLon
        fun y(p: GeoPoint) = (p.latitude - point.latitude) * kmPerDegLat
        return corners.indices.minOf { i ->
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            distanceToSegment(x(a), y(a), x(b), y(b))
        }
    }

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

    /** Distance from the origin to segment (ax, ay)–(bx, by). */
    private fun distanceToSegment(ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }

    companion object {
        private const val KM_PER_DEG_LAT = 111.32

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
