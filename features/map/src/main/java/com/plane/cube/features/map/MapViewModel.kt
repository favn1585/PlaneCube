package com.plane.cube.features.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        }
    }

    private fun onPermissionGranted() {
        _viewState.update { it.copy(hasLocationPermission = true) }
        viewModelScope.launch {
            val location = runCatching { locationProvider.currentLocation() }.getOrNull()
            _viewState.update { it.copy(userLocation = location) }
        }
    }

    private fun restartTicker(preferences: TrackingPreferences?) {
        tickerJob?.cancel()
        if (preferences == null) {
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
        viewModelScope.launch {
            _viewState.update { it.copy(isRefreshing = true) }
            runCatching { planeRepository.fetchPlanes(preferences.area) }
                .onSuccess { planes ->
                    val filtered = planes.filter { plane ->
                        val altitude = plane.altitudeMeters
                        altitude != null && altitude <= preferences.maxAltitudeMeters
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
