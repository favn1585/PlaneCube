package com.plane.cube.features.area

import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint

data class AreaViewState(
    val firstCorner: GeoPoint? = null,
    val secondCorner: GeoPoint? = null,
    val maxAltitudeMeters: Float = DEFAULT_ALTITUDE_M,
    val isAdjustingAltitude: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
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

sealed class AreaUiIntent {
    data class TapMap(val point: GeoPoint) : AreaUiIntent()
    data object Reset : AreaUiIntent()
    data class AltitudeChange(val meters: Float) : AreaUiIntent()
    data class AltitudeAdjusting(val adjusting: Boolean) : AreaUiIntent()
    data object Save : AreaUiIntent()
}
