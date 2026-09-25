package com.plane.cube.tracking

import android.os.SystemClock
import com.plane.cube.domain.PlaneAlerts
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.entity.TrackingPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifies once when a plane enters the tracking box, not on every update
 * while it stays inside.
 *
 * A plane counts as "still inside" until it has been missing from the box for
 * [FORGET_AFTER_MS]. That grace period covers feed gaps (a plane dropping out
 * of one response and back into the next) so they don't re-trigger the alert.
 */
@Singleton
class PlaneAlertMonitor @Inject constructor(
    private val notifier: PlaneNotifier,
) : PlaneAlerts {

    private val lock = Any()

    /** ICAO address → last time the plane was seen inside the box. */
    private val insideSince = HashMap<String, Long>()
    private var watchedPreferences: TrackingPreferences? = null

    override fun onPlanesUpdated(preferences: TrackingPreferences, planes: List<Plane>) {
        val entered = synchronized(lock) {
            if (preferences != watchedPreferences) {
                // A different box: planes already inside it are news again.
                insideSince.clear()
                watchedPreferences = preferences
            }
            val now = SystemClock.elapsedRealtime()
            insideSince.values.removeAll { now - it > FORGET_AFTER_MS }
            planes.filter { it.isAircraft && preferences.isInside(it) }
                .filter { plane -> insideSince.put(plane.icao24, now) == null }
        }
        if (entered.isNotEmpty()) notifier.notifyPlanes(entered)
    }

    private companion object {
        const val FORGET_AFTER_MS = 60_000L
    }
}
