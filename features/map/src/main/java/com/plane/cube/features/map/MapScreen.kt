package com.plane.cube.features.map

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenAreaSelection: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.viewState.collectAsState()

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    val locationGranted = permissionState.permissions
        .any { it.permission == Manifest.permission.ACCESS_FINE_LOCATION && it.status.isGranted } ||
            permissionState.permissions
                .any { it.permission == Manifest.permission.ACCESS_COARSE_LOCATION && it.status.isGranted }

    LaunchedEffect(locationGranted) {
        if (locationGranted) viewModel.onIntent(MapUiIntent.PermissionGranted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PlaneCube") },
            )
        },
        floatingActionButton = {
            if (state.hasLocationPermission) {
                FloatingActionButton(onClick = onOpenAreaSelection) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit area")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !locationGranted -> PermissionGate(
                    rationale = !permissionState.shouldShowRationale &&
                            permissionState.revokedPermissions.isNotEmpty(),
                    onRequest = { permissionState.launchMultiplePermissionRequest() },
                )
                else -> MapContent(state = state)
            }
        }
    }
}

@Composable
private fun PermissionGate(rationale: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "PlaneCube needs your location to show planes near you.",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (rationale) {
            Text(
                "Permission was denied. Please grant it from system settings.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

@Composable
private fun MapContent(state: MapViewState) {
    val initialPosition = state.userLocation ?: state.preferences?.area?.center
        ?: GeoPoint(52.2297, 21.0122)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition.toLatLng(), 9f)
    }
    LaunchedEffect(state.userLocation) {
        state.userLocation?.let { loc ->
            cameraState.position = CameraPosition.fromLatLngZoom(loc.toLatLng(), 10f)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(isMyLocationEnabled = state.hasLocationPermission),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true),
        contentPadding = PaddingValues(top = 56.dp),
    ) {
        state.preferences?.let { prefs -> AreaPolygon(prefs.area) }
        state.planes.forEach { plane ->
            Marker(
                state = MarkerState(position = plane.position.toLatLng()),
                title = plane.callsign ?: plane.icao24,
                snippet = plane.altitudeMeters?.let { "alt ${it.toInt()} m" },
            )
        }
    }
}

@Composable
private fun AreaPolygon(area: Area) {
    val points = listOf(
        LatLng(area.south, area.west),
        LatLng(area.south, area.east),
        LatLng(area.north, area.east),
        LatLng(area.north, area.west),
    )
    Polygon(
        points = points,
        fillColor = androidx.compose.ui.graphics.Color(0x3322AA77),
        strokeColor = androidx.compose.ui.graphics.Color(0xFF22AA77),
        strokeWidth = 3f,
    )
}

internal fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

@Preview(showBackground = true, name = "Permission gate · initial")
@Composable
private fun PermissionGateInitialPreview() {
    PermissionGate(rationale = false, onRequest = {})
}

@Preview(showBackground = true, name = "Permission gate · denied")
@Composable
private fun PermissionGateDeniedPreview() {
    PermissionGate(rationale = true, onRequest = {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Map · scaffold (no map)")
@Composable
private fun MapScaffoldPreview() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("PlaneCube") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) {
                Icon(Icons.Default.Edit, contentDescription = "Edit area")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = "GoogleMap renders only on a real device/emulator.",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
