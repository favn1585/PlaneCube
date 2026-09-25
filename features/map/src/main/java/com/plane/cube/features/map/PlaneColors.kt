package com.plane.cube.features.map

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.entity.TrackingPreferences
import kotlin.math.roundToInt

/**
 * Marker fill colors.
 *
 * - No tracking area: tar1090's altitude palette, so the map reads like
 *   adsb.planebox.org (orange near the ground through yellow and green to
 *   magenta at cruise).
 * - Tracking area set: white, turning brand red as a plane closes in on the
 *   tracking box; see [TrackingPreferences.proximityOf].
 */
internal object PlaneColors {

    private const val FEET_PER_METER = 3.28084

    private val WHITE = intArrayOf(0xFF, 0xFF, 0xFF)
    private val RED = intArrayOf(0xE5, 0x34, 0x3B) // brand red #E5343B

    fun colorFor(plane: Plane, preferences: TrackingPreferences?): Int =
        if (preferences == null) {
            altitudeColor(plane)
        } else {
            proximityColor(preferences.proximityOf(plane) ?: 0.0)
        }

    /** White at 0, brand red at 1, linear in RGB in between. */
    private fun proximityColor(proximity: Double): Int {
        val t = proximity.toFloat().coerceIn(0f, 1f)
        return Color.rgb(
            lerp(WHITE[0], RED[0], t),
            lerp(WHITE[1], RED[1], t),
            lerp(WHITE[2], RED[2], t),
        )
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).roundToInt()

    // ----- tar1090 altitude palette (html/defaults.js ColorByAlt,
    // html/planeObject.js altitudeColor) -----

    private class Stop(val at: Float, val value: Float)

    /** Altitude in feet → hue. */
    private val HUE_STOPS = listOf(
        Stop(0f, 20f), // orange
        Stop(2000f, 32.5f), // yellow
        Stop(4000f, 43f),
        Stop(6000f, 54f),
        Stop(8000f, 72f),
        Stop(9000f, 85f), // green yellow
        Stop(11000f, 140f), // light green
        Stop(40000f, 300f), // magenta
        Stop(51000f, 360f), // red
    )

    /** Hue → lightness, evening out how bright the different hues look. */
    private val LIGHTNESS_STOPS = listOf(
        Stop(0f, 53f), Stop(20f, 50f), Stop(32f, 54f), Stop(40f, 52f),
        Stop(46f, 51f), Stop(50f, 46f), Stop(60f, 43f), Stop(80f, 41f),
        Stop(100f, 41f), Stop(120f, 41f), Stop(140f, 41f), Stop(160f, 40f),
        Stop(180f, 40f), Stop(190f, 44f), Stop(198f, 50f), Stop(200f, 58f),
        Stop(220f, 58f), Stop(240f, 58f), Stop(255f, 55f), Stop(266f, 55f),
        Stop(270f, 58f), Stop(280f, 58f), Stop(290f, 47f), Stop(300f, 43f),
        Stop(310f, 48f), Stop(320f, 48f), Stop(340f, 52f), Stop(360f, 53f),
    )

    private const val AIR_SATURATION = 88f
    private val UNKNOWN_HSL = floatArrayOf(0f, 0f, 75f)
    private val GROUND_HSL = floatArrayOf(220f, 0f, 30f)

    private fun altitudeColor(plane: Plane): Int {
        val hsl = when {
            plane.onGround -> GROUND_HSL
            plane.altitudeMeters == null -> UNKNOWN_HSL
            else -> {
                val feet = (plane.altitudeMeters!! * FEET_PER_METER).toFloat()
                // tar1090 rounds to limit the number of distinct colors.
                val step = if (feet < 8000f) 50f else 500f
                val rounded = step * (feet / step).roundToInt()
                val hue = interpolate(HUE_STOPS, rounded)
                floatArrayOf(hue, AIR_SATURATION, interpolate(LIGHTNESS_STOPS, hue))
            }
        }
        val hue = ((hsl[0] % 360f) + 360f) % 360f
        return ColorUtils.HSLToColor(
            floatArrayOf(
                hue,
                hsl[1].coerceIn(0f, 95f) / 100f,
                hsl[2].coerceIn(0f, 95f) / 100f,
            ),
        )
    }

    /**
     * tar1090's piecewise-linear lookup: the first stop's value below the
     * range, the last stop's value above it.
     */
    private fun interpolate(stops: List<Stop>, x: Float): Float {
        for (i in stops.indices.reversed()) {
            if (x > stops[i].at) {
                if (i == stops.lastIndex) return stops[i].value
                val a = stops[i]
                val b = stops[i + 1]
                return a.value + (b.value - a.value) * (x - a.at) / (b.at - a.at)
            }
        }
        return stops.first().value
    }
}
