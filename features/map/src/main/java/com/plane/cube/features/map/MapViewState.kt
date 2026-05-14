package com.plane.cube.features.map

import com.plane.cube.domain.entity.Area
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
    val edit: EditState = EditState(),
)

data class EditState(
    val active: Boolean = false,
    val firstCorner: GeoPoint? = null,
    val secondCorner: GeoPoint? = null,
    val maxAltitudeMeters: Float = DEFAULT_ALTITUDE_M,
    val adjustingAltitude: Boolean = false,
    val saving: Boolean = false,
    val errorMessage: String? = null,
) {
    val area: Area?
        get() {
            val a = firstCorner ?: return null
            val b = secondCorner ?: return null
            return Area.of(a, b)
        }

    val canSave: Boolean get() = area != null

    companion object {
        const val MIN_ALTITUDE_M = 0f
        const val MAX_ALTITUDE_M = 12_000f
        const val DEFAULT_ALTITUDE_M = 3_000f
    }
}

sealed class MapUiIntent {
    data object PermissionGranted : MapUiIntent()
    data object RefreshNow : MapUiIntent()
    data object StartEditing : MapUiIntent()
    data object CancelEditing : MapUiIntent()
    data object ResetDraftCorners : MapUiIntent()
    data object SaveDraft : MapUiIntent()
    data class TapMap(val point: GeoPoint) : MapUiIntent()
    data class DraftAltitudeChange(val meters: Float) : MapUiIntent()
    data class DraftAltitudeAdjusting(val adjusting: Boolean) : MapUiIntent()
}
