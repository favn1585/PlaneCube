package com.plane.cube.features.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How close to a multiple of [EditState.ALTITUDE_STEP_M] the thumb has to be
 * before it locks onto it. The slider stays continuous — anything outside these
 * windows (623 m, say) is still selectable — the detents are only magnetic.
 */
private const val ALTITUDE_SNAP_TOLERANCE_M = 40f

/** Pull [meters] onto the nearest detent when it is within the snap window. */
private fun snapAltitude(meters: Float): Float {
    val nearest = (meters / EditState.ALTITUDE_STEP_M).roundToInt() * EditState.ALTITUDE_STEP_M
    return if (abs(meters - nearest) <= ALTITUDE_SNAP_TOLERANCE_M) nearest else meters
}

/**
 * Bottom-sheet body holding the controls for area editing: max altitude
 * plus the cancel/save actions.
 *
 * Hosted by a non-modal [androidx.compose.material3.BottomSheetScaffold] so the
 * map behind it keeps receiving taps — corners are still picked while this is
 * on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlightSheetContent(
    state: EditState,
    onIntent: (MapUiIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.map_sheet_max_altitude),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.map_sheet_altitude_value, state.maxAltitudeMeters.toInt()),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Slider(
            value = state.maxAltitudeMeters,
            track = { sliderState -> AltitudeTrack(sliderState) },
            onValueChange = { meters ->
                onIntent(MapUiIntent.DraftAltitudeChange(snapAltitude(meters)))
            },
            valueRange = EditState.MIN_ALTITUDE_M..EditState.MAX_ALTITUDE_M,
        )
        state.errorMessage?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(
                enabled = state.area != null && !state.saving,
                onClick = {
                    onIntent(MapUiIntent.ClearPreferences)
                    // Leaving edit mode drops the draft and closes the sheet.
                    onIntent(MapUiIntent.CancelEditing)
                },
            ) { Text(stringResource(R.string.map_sheet_clear)) }
            Button(
                enabled = state.canSave && !state.saving,
                onClick = { onIntent(MapUiIntent.SaveDraft) },
            ) {
                Text(
                    stringResource(
                        if (state.saving) R.string.map_sheet_saving else R.string.map_sheet_set,
                    ),
                )
            }
        }
        if (!state.canSave) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.map_sheet_hint_pick_corners),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The stock Material track with a dot drawn at every detent, so the magnetic
 * stops are visible even though the slider itself is continuous.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AltitudeTrack(sliderState: SliderState) {
    val colors = SliderDefaults.colors()
    Box(contentAlignment = Alignment.Center) {
        SliderDefaults.Track(sliderState = sliderState, colors = colors)
        Canvas(modifier = Modifier.matchParentSize()) {
            val span = EditState.MAX_ALTITUDE_M - EditState.MIN_ALTITUDE_M
            val detents = (span / EditState.ALTITUDE_STEP_M).toInt()
            val progress = (sliderState.value - EditState.MIN_ALTITUDE_M) / span
            // Skip the two ends: the track's own stop indicators already mark them.
            for (i in 1 until detents) {
                val fraction = i.toFloat() / detents
                drawCircle(
                    color = if (fraction <= progress) {
                        colors.activeTickColor
                    } else {
                        colors.inactiveTickColor
                    },
                    radius = 2.dp.toPx(),
                    center = Offset(size.width * fraction, size.height / 2f),
                )
            }
        }
    }
}

/**
 * Sheet state that is hidden outside edit mode and expands when [editActive]
 * turns true. Hiding is allowed, so the sheet can leave the screen entirely
 * once editing ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberFlightSheetState(
    editActive: Boolean,
    onDismissed: () -> Unit,
): SheetState {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = if (editActive) SheetValue.Expanded else SheetValue.Hidden,
        skipHiddenState = false,
    )
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    LaunchedEffect(editActive) {
        if (!editActive) {
            sheetState.hide()
            return@LaunchedEffect
        }
        sheetState.expand()
        // Putting the sheet away is a way out of edit mode, so report it. Any
        // settled state other than Expanded counts: with no peek height the
        // Hidden and PartiallyExpanded anchors sit at the same offset, so a
        // swipe-away can land on either and both leave the sheet off screen.
        // dropWhile guards the start-up window — the sheet reads as Hidden
        // until the expand settles, and that is not the user dismissing it.
        snapshotFlow { sheetState.currentValue }
            .dropWhile { it != SheetValue.Expanded }
            .filter { it != SheetValue.Expanded }
            .collect { currentOnDismissed() }
    }
    return sheetState
}

@Composable
private fun FlightSheetPreview(state: EditState) {
    Surface {
        Column { FlightSheetContent(state = state, onIntent = {}) }
    }
}

private val sampleArea = Area.of(GeoPoint(52.10, 20.85), GeoPoint(52.35, 21.20))

@Preview(showBackground = true, name = "Flight sheet · area set")
@Composable
private fun FlightSheetAreaSetPreview() {
    FlightSheetPreview(
        EditState(active = true, area = sampleArea, maxAltitudeMeters = 1_500f),
    )
}

@Preview(showBackground = true, name = "Flight sheet · no area yet")
@Composable
private fun FlightSheetNoAreaPreview() {
    FlightSheetPreview(EditState(active = true, maxAltitudeMeters = 500f))
}

@Preview(showBackground = true, name = "Flight sheet · saving")
@Composable
private fun FlightSheetSavingPreview() {
    FlightSheetPreview(
        EditState(active = true, area = sampleArea, maxAltitudeMeters = 1_800f, saving = true),
    )
}

@Preview(showBackground = true, name = "Flight sheet · error")
@Composable
private fun FlightSheetErrorPreview() {
    FlightSheetPreview(
        EditState(
            active = true,
            area = sampleArea,
            maxAltitudeMeters = 1_000f,
            errorMessage = R.string.map_error_save_failed,
        ),
    )
}
