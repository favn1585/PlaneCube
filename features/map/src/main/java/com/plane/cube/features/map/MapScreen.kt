package com.plane.cube.features.map

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.graphics.Point
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.Projection
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint
import kotlinx.coroutines.launch

private const val CAMERA_IDLE_DEBOUNCE_MS = 1_000L
private const val LOCATE_ZOOM = 12f
// Enough room under the map for the overlay button row.
private val OVERLAY_BUTTON_ROW_HEIGHT = 88.dp

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.viewState.collectAsState()

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    val locationGranted = permissionState.permissions.any {
        (it.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
                it.permission == Manifest.permission.ACCESS_COARSE_LOCATION) && it.status.isGranted
    }

    var permissionDismissed by remember { mutableStateOf(false) }
    val showPermissionDialog = !locationGranted && !permissionDismissed

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(locationGranted) {
        if (locationGranted) viewModel.onIntent(MapUiIntent.PermissionGranted)
    }

    val context = LocalContext.current
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = context.getString(it),
                duration = SnackbarDuration.Long,
            )
        }
    }

    val initialTarget = state.userLocation
        ?: state.preferences?.area?.center
        ?: GeoPoint(52.2297, 21.0122)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialTarget.toLatLng(), 9f)
    }

    LaunchedEffect(state.userLocation) {
        if (!state.edit.active) {
            state.userLocation?.let { loc ->
                cameraState.animate(
                    CameraUpdateFactory.newLatLngZoom(loc.toLatLng(), 10f),
                    durationMs = 600,
                )
            }
        }
    }

    // Pause plane updates for the whole gesture: redrawing markers while the
    // camera moves is what makes the map stutter.
    LaunchedEffect(cameraState.isMoving) {
        viewModel.onIntent(MapUiIntent.CameraMovingChanged(cameraState.isMoving))
    }

    // When the user stops panning/zooming, debounce 5s, then push the current
    // visible region to the VM so it can fetch planes. While editing, the VM
    // ignores fetches, so we skip the side effect entirely.
    LaunchedEffect(cameraState.isMoving, state.edit.active) {
        if (state.edit.active) return@LaunchedEffect
        if (cameraState.isMoving) return@LaunchedEffect
        kotlinx.coroutines.delay(CAMERA_IDLE_DEBOUNCE_MS)
        val projection = cameraState.projection ?: return@LaunchedEffect
        val bounds = projection.visibleRegion.latLngBounds
        val area = Area.of(
            GeoPoint(bounds.southwest.latitude, bounds.southwest.longitude),
            GeoPoint(bounds.northeast.latitude, bounds.northeast.longitude),
        )
        viewModel.onIntent(MapUiIntent.UpdateVisibleArea(area))
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberFlightSheetState(
            editActive = state.edit.active,
            onDismissed = { viewModel.onIntent(MapUiIntent.CancelEditing) },
        ),
        snackbarHostState = snackbarHostState,
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        // No peek state: a downward swipe should leave edit mode outright
        // rather than parking the sheet half-open.
        sheetPeekHeight = 0.dp,
        sheetContent = {
            FlightSheetContent(state = state.edit, onIntent = viewModel::onIntent)
        },
    ) { _ ->
        // No top bar: the map runs edge to edge and the overlay buttons carry
        // their own system-bar insets.
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraState,
                onMapClick = { latLng ->
                    if (state.edit.active) {
                        handleEditTap(
                            tap = GeoPoint(latLng.latitude, latLng.longitude),
                            edit = state.edit,
                            cameraState = cameraState,
                            onIntent = viewModel::onIntent,
                        )
                    }
                },
                properties = MapProperties(
                    isMyLocationEnabled = state.hasLocationPermission,
                    mapType = MapType.HYBRID,
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    // Google's own compass and location button are fixed to the
                    // top corners, so they are replaced by the overlay buttons
                    // below, which can sit where they are wanted.
                    compassEnabled = false,
                    myLocationButtonEnabled = false,
                    // Hide Google Maps' default "Navigate / Open in Maps"
                    // toolbar that appears when a marker is selected.
                    mapToolbarEnabled = false,
                ),
                // Lifts Google's logo and attribution clear of the button row;
                // they must stay visible, and setPadding is the sanctioned way
                // to move them.
                contentPadding = PaddingValues(bottom = OVERLAY_BUTTON_ROW_HEIGHT),
            ) {
                if (state.edit.active) {
                    state.edit.area?.let { AreaPolygon(it) }
                } else {
                    state.preferences?.let { prefs -> AreaPolygon(prefs.area) }
                    val density = LocalDensity.current.density
                    state.planes.forEach { plane ->
                        // key() ties each marker's state + icon cache to the
                        // aircraft identity, so at 1 Hz they move/update in
                        // place instead of being torn down and recreated.
                        key(plane.icao24) {
                            val heading = plane.trueTrackDegrees?.toFloat() ?: 0f
                            val altitudeM = plane.altitudeMeters?.toInt()
                            val icon = remember(heading, altitudeM, density) {
                                PlaneIcon.create(
                                    headingDegrees = heading,
                                    altitudeMeters = altitudeM,
                                    density = density,
                                )
                            }
                            val markerState = remember { MarkerState(plane.position.toLatLng()) }
                            SideEffect { markerState.position = plane.position.toLatLng() }
                            Marker(
                                state = markerState,
                                title = plane.callsign ?: plane.icao24,
                                snippet = altitudeM?.let { stringResource(R.string.map_marker_altitude, it) },
                                icon = icon,
                                flat = true,
                                anchor = Offset(0.5f, PlaneIcon.anchorY),
                            )
                        }
                    }
                }
            }

            if (state.edit.active) {
                EditOverlay(
                    editState = state.edit,
                    cameraState = cameraState,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Hidden while the sheet is up, since it covers the bottom of
                // the screen and the settings button would only reopen what is
                // already open.
                MapOverlayButton(
                    onClick = { viewModel.onIntent(MapUiIntent.StartEditing) },
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.map_action_settings),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp),
                )
                CompassButton(
                    cameraState = cameraState,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
                MapOverlayButton(
                    onClick = {
                        val location = state.userLocation
                        when {
                            location != null -> scope.launch {
                                cameraState.animate(
                                    CameraUpdateFactory.newLatLngZoom(
                                        location.toLatLng(),
                                        LOCATE_ZOOM,
                                    ),
                                    durationMs = 600,
                                )
                            }
                            // No fix yet: ask for a fresh one. Guarded on the
                            // permission, because this intent also flips
                            // isMyLocationEnabled, which throws without it.
                            state.hasLocationPermission ->
                                viewModel.onIntent(MapUiIntent.PermissionGranted)
                        }
                    },
                    icon = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.map_action_my_location),
                    small = false,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            rationale = !permissionState.shouldShowRationale &&
                    permissionState.revokedPermissions.isNotEmpty(),
            onRequest = {
                permissionState.launchMultiplePermissionRequest()
                permissionDismissed = true
            },
            onDismiss = { permissionDismissed = true },
        )
    }
}

/**
 * Round map control: brand blue with a white glyph in both themes, so it reads
 * the same over dark satellite imagery and bright terrain.
 */
@Composable
private fun MapOverlayButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    small: Boolean = true,
    iconModifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.primary
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = contentDescription, modifier = iconModifier)
    }
    if (small) {
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = containerColor,
            contentColor = Color.White,
            modifier = modifier,
            content = content,
        )
    } else {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = containerColor,
            contentColor = Color.White,
            modifier = modifier,
            content = content,
        )
    }
}

/** Resets the map to north-up. Doubles as a compass: the needle tracks the camera. */
@Composable
private fun CompassButton(
    cameraState: CameraPositionState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    MapOverlayButton(
        onClick = {
            scope.launch {
                cameraState.animate(
                    update = CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder(cameraState.position)
                            .bearing(0f)
                            .tilt(0f)
                            .build(),
                    ),
                    durationMs = 400,
                )
            }
        },
        icon = Icons.Default.Explore,
        contentDescription = stringResource(R.string.map_action_face_north),
        modifier = modifier,
        // Read here rather than in MapScreen so a camera rotation recomposes
        // the needle alone.
        iconModifier = Modifier.rotate(-cameraState.position.bearing),
    )
}

@Composable
private fun PermissionDialog(
    rationale: Boolean,
    onRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text(stringResource(R.string.map_permission_dialog_title)) },
        text = {
            Text(
                stringResource(
                    if (rationale) {
                        R.string.map_permission_dialog_body_denied
                    } else {
                        R.string.map_permission_dialog_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onRequest) { Text(stringResource(R.string.map_permission_dialog_grant)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_permission_dialog_dismiss)) }
        },
    )
}

@Composable
private fun EditOverlay(
    editState: EditState,
    cameraState: CameraPositionState,
    modifier: Modifier = Modifier,
) {
    // Read the camera so a pan or zoom repaints the markers.
    cameraState.position
    val projection = cameraState.projection ?: return

    val area = editState.area
    val cornerScreens: List<Offset> = when {
        area != null -> area.corners.map {
            val p = projection.toScreenLocation(it.toLatLng())
            Offset(p.x.toFloat(), p.y.toFloat())
        }
        editState.firstCorner != null -> listOf(
            projection.toScreenLocation(editState.firstCorner.toLatLng())
                .let { Offset(it.x.toFloat(), it.y.toFloat()) },
        )
        else -> emptyList()
    }

    val markerColor = MaterialTheme.colorScheme.tertiary

    // Fixed-pixel corner markers (do not zoom with the map).
    Canvas(modifier = modifier) {
        val cornerFill = markerColor
        val cornerHalo = markerColor.copy(alpha = 0.33f)
        val cornerStroke = Color.White
        val cornerStrokeWidth = 2.dp.toPx()
        val coreRadius = 6.dp.toPx()
        val haloRadius = 14.dp.toPx()
        cornerScreens.forEach { centre ->
            drawCircle(cornerHalo, haloRadius, centre)
            drawCircle(cornerFill, coreRadius, centre)
            drawCircle(cornerStroke, coreRadius, centre, style = Stroke(width = cornerStrokeWidth))
        }
    }
}

@Composable
private fun AreaPolygon(area: Area) {
    val color = MaterialTheme.colorScheme.tertiary
    Polygon(
        points = area.corners.map { it.toLatLng() },
        fillColor = color.copy(alpha = 0.2f),
        strokeColor = color,
        strokeWidth = 4f,
    )
}

internal fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/**
 * Handle a tap in edit mode. The first tap stores one corner; the second tap
 * projects both corners to screen, lays out a screen-aligned rectangle, then
 * unprojects the four screen corners back to lat/lng so the saved rectangle
 * is oriented to whatever direction the user is looking at — not to north.
 */
private fun handleEditTap(
    tap: GeoPoint,
    edit: EditState,
    cameraState: CameraPositionState,
    onIntent: (MapUiIntent) -> Unit,
) {
    val first = edit.firstCorner
    val hasArea = edit.area != null
    if (first == null || hasArea) {
        onIntent(MapUiIntent.TapFirstCorner(tap))
        return
    }
    val projection = cameraState.projection
    if (projection == null) {
        onIntent(MapUiIntent.CompleteArea(Area.of(first, tap)))
        return
    }
    val p1 = projection.toScreenLocation(first.toLatLng())
    val p3 = projection.toScreenLocation(tap.toLatLng())
    onIntent(MapUiIntent.CompleteArea(screenAlignedAreaFromDiagonals(p1, p3, projection)))
}

/** Build a screen-aligned rectangle from two diagonal screen points. */
private fun screenAlignedAreaFromDiagonals(
    p1: Point,
    p3: Point,
    projection: Projection,
): Area {
    val p2 = Point(p3.x, p1.y)
    val p4 = Point(p1.x, p3.y)
    val ll1 = projection.fromScreenLocation(p1)
    val ll2 = projection.fromScreenLocation(p2)
    val ll3 = projection.fromScreenLocation(p3)
    val ll4 = projection.fromScreenLocation(p4)
    return Area(
        listOf(
            GeoPoint(ll1.latitude, ll1.longitude),
            GeoPoint(ll2.latitude, ll2.longitude),
            GeoPoint(ll3.latitude, ll3.longitude),
            GeoPoint(ll4.latitude, ll4.longitude),
        ),
    )
}

@Preview(showBackground = true, name = "Permission dialog · initial")
@Composable
private fun PermissionDialogInitialPreview() {
    PermissionDialog(rationale = false, onRequest = {}, onDismiss = {})
}

@Preview(showBackground = true, name = "Permission dialog · denied")
@Composable
private fun PermissionDialogDeniedPreview() {
    PermissionDialog(rationale = true, onRequest = {}, onDismiss = {})
}

