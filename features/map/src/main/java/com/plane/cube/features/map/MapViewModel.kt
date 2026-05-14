package com.plane.cube.features.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plane.cube.domain.TrackingScheduler
import com.plane.cube.domain.entity.TrackingPreferences
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MapViewModel @Inject constructor(
    private val planeRepository: PlaneRepository,
    private val trackingRepository: TrackingPreferencesRepository,
    private val locationProvider: LocationProvider,
    private val scheduler: TrackingScheduler,
) : ViewModel() {

    private val _viewState = MutableStateFlow(MapViewState())
    val viewState = _viewState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        viewModelScope.launch {
            trackingRepository.observePreferences().collectLatest { preferences ->
                _viewState.update { it.copy(preferences = preferences) }
                restartTicker(preferences)
            }
        }
    }

    fun onIntent(intent: MapUiIntent) {
        when (intent) {
            MapUiIntent.PermissionGranted -> onPermissionGranted()
            MapUiIntent.RefreshNow -> refresh()
            MapUiIntent.StartEditing -> startEditing()
            MapUiIntent.CancelEditing -> stopEditing()
            MapUiIntent.ResetDraftCorners -> _viewState.update {
                it.copy(edit = it.edit.copy(firstCorner = null, area = null))
            }
            MapUiIntent.SaveDraft -> saveDraft()
            MapUiIntent.ClearPreferences -> clearPreferences()
            is MapUiIntent.TapFirstCorner -> _viewState.update {
                it.copy(edit = it.edit.copy(firstCorner = intent.point, area = null))
            }
            is MapUiIntent.CompleteArea -> _viewState.update {
                it.copy(edit = it.edit.copy(area = intent.area))
            }
            is MapUiIntent.DraftAltitudeChange -> _viewState.update {
                it.copy(edit = it.edit.copy(maxAltitudeMeters = intent.meters))
            }
            is MapUiIntent.DraftAltitudeAdjusting -> _viewState.update {
                it.copy(edit = it.edit.copy(adjustingAltitude = intent.adjusting))
            }
        }
    }

    private fun onPermissionGranted() {
        _viewState.update { it.copy(hasLocationPermission = true) }
        viewModelScope.launch {
            val location = runCatching { locationProvider.currentLocation() }.getOrNull()
            _viewState.update { it.copy(userLocation = location) }
        }
    }

    private fun startEditing() {
        _viewState.update { state ->
            val seed = state.preferences
            val draft = if (seed != null) {
                EditState(
                    active = true,
                    firstCorner = null,
                    area = seed.area,
                    maxAltitudeMeters = seed.maxAltitudeMeters.toFloat(),
                )
            } else {
                EditState(active = true)
            }
            state.copy(edit = draft)
        }
        tickerJob?.cancel()
        _viewState.update { it.copy(planes = emptyList()) }
    }

    private fun stopEditing() {
        _viewState.update { it.copy(edit = EditState()) }
        restartTicker(_viewState.value.preferences)
    }

    private fun saveDraft() {
        val area = _viewState.value.edit.area ?: return
        viewModelScope.launch {
            _viewState.update { it.copy(edit = it.edit.copy(saving = true, errorMessage = null)) }
            runCatching {
                trackingRepository.savePreferences(
                    TrackingPreferences(
                        area = area,
                        maxAltitudeMeters = _viewState.value.edit.maxAltitudeMeters.toDouble(),
                    ),
                )
                scheduler.schedule()
            }
                .onSuccess { _viewState.update { it.copy(edit = EditState()) } }
                .onFailure { error ->
                    _viewState.update {
                        it.copy(
                            edit = it.edit.copy(saving = false, errorMessage = error.message),
                        )
                    }
                }
        }
    }

    private fun clearPreferences() {
        viewModelScope.launch {
            runCatching {
                trackingRepository.clear()
                scheduler.cancel()
            }
        }
    }

    private fun restartTicker(preferences: TrackingPreferences?) {
        tickerJob?.cancel()
        if (preferences == null || _viewState.value.edit.active) {
            _viewState.update { it.copy(planes = emptyList()) }
            return
        }
        tickerJob = viewModelScope.launch {
            while (true) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refresh() {
        val preferences = _viewState.value.preferences ?: return
        if (_viewState.value.edit.active) return
        viewModelScope.launch {
            _viewState.update { it.copy(isRefreshing = true) }
            runCatching { planeRepository.fetchPlanes(preferences.area) }
                .onSuccess { planes ->
                    val filtered = planes.filter { plane ->
                        val altitude = plane.altitudeMeters
                        altitude != null &&
                            altitude <= preferences.maxAltitudeMeters &&
                            preferences.area.contains(plane.position)
                    }
                    _viewState.update {
                        it.copy(planes = filtered, isRefreshing = false, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    _viewState.update {
                        it.copy(isRefreshing = false, errorMessage = error.message)
                    }
                }
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
    }
}
