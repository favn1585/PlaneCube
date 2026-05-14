package com.plane.cube.features.map

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.plane.cube.domain.entity.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): GeoPoint? {
        // lastLocation is cached and answers immediately on devices that have
        // any other Maps-using app open recently; getCurrentLocation is the
        // fallback when the cache is empty.
        val cached = runCatching { client.lastLocation.await() }.getOrNull()
        val location = cached
            ?: runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }.getOrNull()
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }
}
