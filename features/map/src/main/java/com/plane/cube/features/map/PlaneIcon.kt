package com.plane.cube.features.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Builds a marker bitmap for a plane: a silhouette rotated to the heading,
 * with the altitude printed below in upright text. The combined bitmap is
 * meant to be used with `Marker(flat = true)` so the silhouette stays
 * correctly oriented to north as the user pans/rotates the map; the altitude
 * text will rotate with the map, which is the trade-off for keeping it to one
 * marker per plane.
 *
 * The plane's "center" sits at y = `planeSize / 2` from the top of the bitmap;
 * the rendered altitude label hangs below it. Use anchor `(0.5, ~0.33)` on
 * the `Marker` so the plane center lands on the lat/lng position.
 */
internal object PlaneIcon {

    private const val PLANE_DP = 40f
    private const val LABEL_DP = 14f
    private const val LABEL_GAP_DP = 2f

    /** Anchor for the produced bitmap so the plane's center lands on the point. */
    val anchorY: Float = (PLANE_DP / 2f) / (PLANE_DP + LABEL_GAP_DP + LABEL_DP)

    fun create(
        headingDegrees: Float,
        altitudeMeters: Int?,
        inside: Boolean,
        density: Float,
    ): BitmapDescriptor {
        val planeSizePx = PLANE_DP * density
        val labelHeightPx = LABEL_DP * density
        val labelGapPx = LABEL_GAP_DP * density

        val width = (planeSizePx * 1.4f).toInt()
        val height = (planeSizePx + labelGapPx + labelHeightPx).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillColor = if (inside) Color.RED else Color.WHITE
        val strokeColor = if (inside) Color.WHITE else Color.BLACK

        val centerX = width / 2f
        val planeCenterY = planeSizePx / 2f

        // ----- airliner silhouette (top-down view), rotated to heading -----
        canvas.save()
        canvas.rotate(headingDegrees, centerX, planeCenterY)
        val s = planeSizePx / 2f // half size; coordinates below are factors of s
        val path = Path().apply {
            // Start at the nose, walk clockwise around the airframe.
            moveTo(centerX,                  planeCenterY - 1.00f * s)  // nose tip
            lineTo(centerX + 0.06f * s,      planeCenterY - 0.55f * s)  // fuselage right, near nose
            // Main wing (right side): swept leading edge → tip → trailing edge → wing root.
            lineTo(centerX + 1.00f * s,      planeCenterY - 0.05f * s)  // right wingtip leading edge
            lineTo(centerX + 1.00f * s,      planeCenterY + 0.05f * s)  // right wingtip trailing edge
            lineTo(centerX + 0.08f * s,      planeCenterY + 0.18f * s)  // wing root trailing
            // Fuselage tapering toward the tail.
            lineTo(centerX + 0.06f * s,      planeCenterY + 0.55f * s)  // before tailplane
            // Horizontal stabilizer (right side).
            lineTo(centerX + 0.38f * s,      planeCenterY + 0.72f * s)  // right tailplane tip leading
            lineTo(centerX + 0.38f * s,      planeCenterY + 0.82f * s)  // right tailplane tip trailing
            lineTo(centerX + 0.06f * s,      planeCenterY + 0.90f * s)  // tailplane root trailing
            // Tail cone.
            lineTo(centerX + 0.05f * s,      planeCenterY + 1.00f * s)
            lineTo(centerX - 0.05f * s,      planeCenterY + 1.00f * s)
            // Mirror back up the left side.
            lineTo(centerX - 0.06f * s,      planeCenterY + 0.90f * s)
            lineTo(centerX - 0.38f * s,      planeCenterY + 0.82f * s)
            lineTo(centerX - 0.38f * s,      planeCenterY + 0.72f * s)
            lineTo(centerX - 0.06f * s,      planeCenterY + 0.55f * s)
            lineTo(centerX - 0.08f * s,      planeCenterY + 0.18f * s)
            lineTo(centerX - 1.00f * s,      planeCenterY + 0.05f * s)
            lineTo(centerX - 1.00f * s,      planeCenterY - 0.05f * s)
            lineTo(centerX - 0.06f * s,      planeCenterY - 0.55f * s)
            close()
        }
        canvas.drawPath(path, fillPaint(fillColor))
        canvas.drawPath(path, strokePaint(strokeColor, 1.4f * density))
        canvas.restore()

        // ----- altitude label, upright -----
        if (altitudeMeters != null) {
            val text = "${altitudeMeters} m"
            val textY = planeSizePx + labelGapPx + labelHeightPx * 0.82f

            canvas.drawText(text, centerX, textY, textOutlinePaint(strokeColor, 11f * density, 2.5f * density))
            canvas.drawText(text, centerX, textY, textFillPaint(fillColor, 11f * density))
        }

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

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
