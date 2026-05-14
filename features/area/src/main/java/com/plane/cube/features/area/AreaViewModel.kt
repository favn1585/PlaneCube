package com.plane.cube.features.area

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plane.cube.domain.TrackingScheduler
import com.plane.cube.domain.entity.TrackingPreferences
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AreaViewModel @Inject constructor(
    private val trackingRepository: TrackingPreferencesRepository,
    private val scheduler: TrackingScheduler,
) : ViewModel() {

    private val _viewState = MutableStateFlow(AreaViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = trackingRepository.observePreferences()
            existing.collect { preferences ->
                if (preferences != null && _viewState.value.firstCorner == null) {
                    _viewState.update {
                        it.copy(
                            firstCorner = preferences.area.southWest,
                            secondCorner = preferences.area.northEast,
                            maxAltitudeMeters = preferences.maxAltitudeMeters.toFloat(),
                        )
                    }
                }
            }
        }
    }

    fun onIntent(intent: AreaUiIntent) {
        when (intent) {
            is AreaUiIntent.TapMap -> onTap(intent)
            AreaUiIntent.Reset -> _viewState.update {
                it.copy(firstCorner = null, secondCorner = null)
            }
            is AreaUiIntent.AltitudeChange -> _viewState.update {
                it.copy(maxAltitudeMeters = intent.meters)
            }
            is AreaUiIntent.AltitudeAdjusting -> _viewState.update {
                it.copy(isAdjustingAltitude = intent.adjusting)
            }
            AreaUiIntent.Save -> save()
        }
    }

    private fun onTap(intent: AreaUiIntent.TapMap) {
        _viewState.update {
            when {
                it.firstCorner == null -> it.copy(firstCorner = intent.point, secondCorner = null)
                it.secondCorner == null -> it.copy(secondCorner = intent.point)
                else -> it.copy(firstCorner = intent.point, secondCorner = null)
            }
        }
    }

    private fun save() {
        val area = _viewState.value.area ?: return
        viewModelScope.launch {
            _viewState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                trackingRepository.savePreferences(
                    TrackingPreferences(
                        area = area,
                        maxAltitudeMeters = _viewState.value.maxAltitudeMeters.toDouble(),
                    ),
                )
                scheduler.schedule()
            }
                .onSuccess { _viewState.update { it.copy(isSaving = false, saved = true) } }
                .onFailure { error ->
                    _viewState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }
}
