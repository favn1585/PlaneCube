package com.plane.cube.features.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Builds a marker bitmap for a plane: an SVG paper-airplane silhouette rotated
 * to heading, colored on a red↔green gradient by altitude, with the altitude
 * printed below in upright text.
 *
 * Coloring rule:
 *  - altitude unknown or ≥ 2000 m → fully green
 *  - 0 m → fully red
 *  - in between → linear RGB interpolation between red (0) and green (2000)
 * All planes get a 2 dp black border for contrast against satellite imagery.
 *
 * The plane sits in the upper portion of the bitmap, label hangs below.
 * Anchor with `(0.5, PlaneIcon.anchorY)` on the marker so the plane center
 * lands on the lat/lng position.
 */
internal object PlaneIcon {

    private const val PLANE_DP = 28f
    // Square area around the plane that contains it at any rotation. Needs
    // to be at least PLANE_DP * sqrt(2) ≈ 1.414 × PLANE_DP so the corners
    // don't get clipped when the icon is rotated 45°.
    private const val PLANE_BOX_DP = 42f
    private const val LABEL_DP = 14f
    private const val LABEL_GAP_DP = 2f

    // SVG viewBox is 0 0 122.88 122.88; the path's tip points to the upper-
    // right (45° CW from up), so we counter-rotate by NATURAL_ROTATION_DEG so
    // heading=0 (north) makes the nose point straight up on screen.
    private const val SVG_SIZE = 122.88f
    private const val NATURAL_ROTATION_DEG = 45f

    private const val SVG_PATH_DATA =
        "M16.63,105.75c0.01-4.03,2.3-7.97,6.03-12.38" +
            "L1.09,79.73c-1.36-0.59-1.33-1.42-0.54-2.4" +
            "l4.57-3.9c0.83-0.51,1.71-0.73,2.66-0.47" +
            "l26.62,4.5l22.18-24.02" +
            "L4.8,18.41c-1.31-0.77-1.42-1.64-0.07-2.65" +
            "l7.47-5.96l67.5,18.97" +
            "L99.64,7.45c6.69-5.79,13.19-8.38,18.18-7.15" +
            "c2.75,0.68,3.72,1.5,4.57,4.08" +
            "c1.65,5.06-0.91,11.86-6.96,18.86" +
            "L94.11,43.18l18.97,67.5" +
            "l-5.96,7.47c-1.01,1.34-1.88,1.23-2.65-0.07" +
            "L69.43,66.31L45.41,88.48" +
            "l4.5,26.62c0.26,0.94,0.05,1.82-0.47,2.66" +
            "l-3.9,4.57c-0.97,0.79-1.81,0.82-2.4-0.54" +
            "l-13.64-21.57c-4.43,3.74-8.37,6.03-12.42,6.03" +
            "C16.71,106.24,16.63,106.11,16.63,105.75" +
            "L16.63,105.75z"

    private val sourcePath = PathParser.createPathFromPathData(SVG_PATH_DATA)

    /**
     * Altitude → color ramp (piecewise linear in RGB):
     *   ≥ 4000 m         → white
     *   3000 m … 4000 m  → yellow → white
     *   2000 m … 3000 m  → orange → yellow
     *      0 m … 2000 m  → red → orange
     */
    private val WHITE = intArrayOf(0xFF, 0xFF, 0xFF)
    private val YELLOW = intArrayOf(0xFF, 0xEB, 0x3B) // Material yellow 500
    private val ORANGE = intArrayOf(0xFF, 0x98, 0x00) // Material orange 500
    private val RED = intArrayOf(0xE5, 0x39, 0x35) // Material red 600

    /** Anchor for the produced bitmap so the plane's center lands on the point. */
    val anchorY: Float = (PLANE_BOX_DP / 2f) / (PLANE_BOX_DP + LABEL_GAP_DP + LABEL_DP)

    fun create(
        headingDegrees: Float,
        altitudeMeters: Int?,
        density: Float,
    ): BitmapDescriptor {
        val planeSizePx = PLANE_DP * density
        val planeBoxPx = PLANE_BOX_DP * density
        val labelHeightPx = LABEL_DP * density
        val labelGapPx = LABEL_GAP_DP * density

        // Bitmap = a square big enough to hold the rotated plane + label below.
        val width = planeBoxPx.toInt()
        val height = (planeBoxPx + labelGapPx + labelHeightPx).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillColor = colorForAltitude(altitudeMeters)
        val borderColor = Color.BLACK
        val borderDp = 1f
        val borderPx = borderDp * density

        // Plane sits centered in the upper square (planeBoxPx × planeBoxPx).
        val centerX = width / 2f
        val planeCenterY = planeBoxPx / 2f

        // ----- SVG-based plane silhouette, rotated to heading -----
        canvas.save()
        canvas.translate(centerX, planeCenterY)
        canvas.rotate(headingDegrees - NATURAL_ROTATION_DEG)
        val scale = planeSizePx / SVG_SIZE
        canvas.scale(scale, scale)
        canvas.translate(-SVG_SIZE / 2f, -SVG_SIZE / 2f)
        canvas.drawPath(sourcePath, fillPaint(fillColor))
        // Stroke width is given in screen pixels (borderPx). Because we're
        // drawing in pre-scaled path units, divide by `scale` so the stroke
        // ends up the requested screen thickness.
        canvas.drawPath(sourcePath, strokePaint(borderColor, borderPx / scale))
        canvas.restore()

        // ----- altitude label, upright, just below the rotation box -----
        if (altitudeMeters != null) {
            val text = "${altitudeMeters} m"
            val textY = planeBoxPx + labelGapPx + labelHeightPx * 0.82f
            canvas.drawText(
                text,
                centerX,
                textY,
                textOutlinePaint(Color.BLACK, 11f * density, 2.5f * density),
            )
            canvas.drawText(text, centerX, textY, textFillPaint(Color.WHITE, 11f * density))
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /**
     * Piecewise color ramp by altitude. Boundary values use the "upper" color
     * of the segment they fall into (e.g. exactly 4000 m → white).
     * Null altitude defaults to white (treated as cruise/unknown).
     */
    private fun colorForAltitude(altitudeMeters: Int?): Int {
        if (altitudeMeters == null) return rgb(WHITE)
        val a = altitudeMeters
        return when {
            a >= 4000 -> rgb(WHITE)
            a >= 3000 -> lerpColor(YELLOW, WHITE, (a - 3000f) / 1000f)
            a >= 2000 -> lerpColor(ORANGE, YELLOW, (a - 2000f) / 1000f)
            a >= 0 -> lerpColor(RED, ORANGE, a / 2000f)
            else -> rgb(RED)
        }
    }

    private fun lerpColor(from: IntArray, to: IntArray, tRaw: Float): Int {
        val t = tRaw.coerceIn(0f, 1f)
        return Color.rgb(
            lerp(from[0], to[0], t),
            lerp(from[1], to[1], t),
            lerp(from[2], to[2], t),
        )
    }

    private fun rgb(c: IntArray): Int = Color.rgb(c[0], c[1], c[2])

    private fun lerp(a: Int, b: Int, t: Float): Int =
        (a + (b - a) * t).toInt().coerceIn(0, 255)

    private fun fillPaint(color: Int) = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private fun strokePaint(color: Int, width: Float) = Paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private fun textFillPaint(color: Int, size: Float) = Paint().apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private fun textOutlinePaint(color: Int, size: Float, width: Float) = Paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeJoin = Paint.Join.ROUND
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
}
