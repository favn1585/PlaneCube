package com.plane.cube.domain

import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.entity.TrackingPreferences

/**
 * Raises an alert when a plane enters the tracking box. Every source of fresh
 * plane data (the open map, the background monitor, the periodic check) feeds
 * the same instance, so a plane is announced once no matter who saw it first.
 */
interface PlaneAlerts {

    /** [planes] is a fresh snapshot covering at least [preferences]' area. */
    fun onPlanesUpdated(preferences: TrackingPreferences, planes: List<Plane>)
}
