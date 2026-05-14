package com.plane.cube.features.map

import com.plane.cube.domain.entity.GeoPoint
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.entity.TrackingPreferences

data class MapViewState(
    val hasLocationPermission: Boolean = false,
    val userLocation: GeoPoint? = null,
    val preferences: TrackingPreferences? = null,
    val planes: List<Plane> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

sealed class MapUiIntent {
    data object PermissionGranted : MapUiIntent()
    data object RefreshNow : MapUiIntent()
}
