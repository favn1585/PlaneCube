package com.plane.cube.domain.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPreferencesTest {

    // Roughly 1.1 km × 0.7 km box near Wrocław.
    private val area = Area.of(GeoPoint(51.10, 17.00), GeoPoint(51.11, 17.01))
    private val preferences = TrackingPreferences(
        area = area,
        maxAltitudeMeters = 1_000.0,
        warningDistanceMeters = 2_000.0,
    )

    private fun plane(position: GeoPoint, altitude: Double?, onGround: Boolean = false) = Plane(
        icao24 = "abc123",
        callsign = null,
        originCountry = null,
        position = position,
        altitudeMeters = altitude,
        velocityMetersPerSec = null,
        trueTrackDegrees = null,
        onGround = onGround,
    )

    private val inside = GeoPoint(51.105, 17.005)

    @Test
    fun `inside the box is fully red`() {
        assertEquals(1.0, preferences.proximityOf(plane(inside, 500.0))!!, 1e-9)
    }

    @Test
    fun `above the area 2000 m over the ceiling is not red at all`() {
        assertEquals(0.0, preferences.proximityOf(plane(inside, 3_000.0))!!, 1e-9)
    }

    @Test
    fun `descending to 1000 m over the ceiling is half red`() {
        assertEquals(0.5, preferences.proximityOf(plane(inside, 2_000.0))!!, 1e-9)
    }

    @Test
    fun `below the ceiling 1 km outside the edge is half red`() {
        // 1 km north of the northern edge (51.11°): 1 / 111.32 degrees of latitude.
        val north = GeoPoint(51.11 + 1.0 / 111.32, 17.005)
        assertEquals(0.5, preferences.proximityOf(plane(north, 500.0))!!, 0.01)
    }

    @Test
    fun `on the ground counts as altitude zero`() {
        assertEquals(1.0, preferences.proximityOf(plane(inside, null, onGround = true))!!, 1e-9)
    }

    @Test
    fun `unknown altitude has no proximity`() {
        assertNull(preferences.proximityOf(plane(inside, null)))
    }

    @Test
    fun `isInside needs both the area and the altitude ceiling`() {
        assertTrue(preferences.isInside(plane(inside, 1_000.0)))
        assertFalse(preferences.isInside(plane(inside, 1_001.0)))
        assertFalse(preferences.isInside(plane(GeoPoint(51.2, 17.005), 500.0)))
        assertFalse(preferences.isInside(plane(inside, null)))
    }

    @Test
    fun `towers and ground vehicles are not aircraft`() {
        val base = plane(inside, null, onGround = true)
        assertFalse(base.copy(typeDesignator = "TWR", category = "C0").isAircraft)
        assertFalse(base.copy(typeDesignator = "GND").isAircraft)
        assertFalse(base.copy(category = "C2").isAircraft)
        assertTrue(base.copy(typeDesignator = "B738", category = "A3").isAircraft)
        assertTrue(base.isAircraft)
    }
}
