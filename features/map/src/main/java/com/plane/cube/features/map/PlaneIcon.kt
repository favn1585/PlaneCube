package com.plane.cube.features.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlin.math.ceil
import kotlin.math.hypot

/** A rendered marker bitmap plus the anchor that puts the plane's center on its position. */
internal class PlaneMarkerIcon(val descriptor: BitmapDescriptor, val anchorY: Float)

/**
 * Builds a marker bitmap for a plane: its tar1090 type silhouette (see
 * [AircraftIcons]) rotated to heading and filled with the color picked by
 * [PlaneColors], with the altitude printed below in upright text.
 * All planes get a 1 dp black border for contrast against satellite imagery.
 *
 * The plane sits in the upper square of the bitmap, label hangs below.
 * Anchor with `(0.5, PlaneMarkerIcon.anchorY)` so the plane center lands on
 * the lat/lng position.
 */
internal object PlaneIcon {

    private const val LABEL_DP = 14f
    private const val LABEL_GAP_DP = 2f
    private const val BORDER_DP = 1f

    /** Draws every silhouette this much larger than tar1090's base size. */
    private const val ICON_SCALE = 1.25f

    /** tar1090 strokes accent lines at 60% of the outline width. */
    private const val ACCENT_RATIO = 0.6f

    fun create(
        icon: AircraftIcon,
        headingDegrees: Float,
        altitudeMeters: Int?,
        fillColor: Int,
        density: Float,
    ): PlaneMarkerIcon {
        val shape = icon.shape
        val body = shape.body
        if (body == null && icon.fallback().shape !== shape) {
            return create(icon.fallback(), headingDegrees, altitudeMeters, fillColor, density)
        }
        val shapeWidthPx = shape.width * icon.scale * ICON_SCALE * density
        val shapeHeightPx = shape.height * icon.scale * ICON_SCALE * density
        // Square that holds the shape at any rotation, plus room for the border.
        val borderPx = BORDER_DP * density
        val boxPx = ceil(hypot(shapeWidthPx, shapeHeightPx) + 2 * borderPx)
        val labelHeightPx = LABEL_DP * density
        val labelGapPx = LABEL_GAP_DP * density

        val width = boxPx.toInt()
        val height = (boxPx + labelGapPx + labelHeightPx).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ----- type silhouette, nose up at heading 0, rotated to heading -----
        val (minX, minY, viewWidth, viewHeight) = shape.viewBox
        var scaleX = shapeWidthPx / viewWidth
        var scaleY = shapeHeightPx / viewHeight
        if (!shape.noAspect) {
            // SVG's default "xMidYMid meet": uniform scale, centered.
            scaleX = minOf(scaleX, scaleY)
            scaleY = scaleX
        }
        canvas.save()
        canvas.translate(width / 2f, boxPx / 2f)
        if (!shape.noRotate) canvas.rotate(headingDegrees)
        canvas.scale(scaleX, scaleY)
        canvas.translate(-(minX + viewWidth / 2f), -(minY + viewHeight / 2f))
        // Stroke widths are in path units, so divide the screen width by the
        // scale to get the requested on-screen thickness.
        val pathScale = (scaleX + scaleY) / 2f
        // Like tar1090 (paint-order="stroke"): a double-width stroke under the
        // fill leaves a border of exactly borderPx outside the silhouette.
        if (body != null) {
            canvas.drawPath(body, strokePaint(Color.BLACK, 2 * borderPx / pathScale))
            canvas.drawPath(body, fillPaint(fillColor))
        }
        shape.accents?.let { accents ->
            canvas.drawPath(
                accents,
                strokePaint(Color.BLACK, ACCENT_RATIO * shape.accentMult * borderPx / pathScale),
            )
        }
        canvas.restore()

        // ----- altitude label, upright, just below the rotation box -----
        if (altitudeMeters != null) {
            val text = "${altitudeMeters} m"
            val textY = boxPx + labelGapPx + labelHeightPx * 0.82f
            canvas.drawText(
                text,
                width / 2f,
                textY,
                textOutlinePaint(Color.BLACK, 11f * density, 2.5f * density),
            )
            canvas.drawText(text, width / 2f, textY, textFillPaint(Color.WHITE, 11f * density))
        }

        return PlaneMarkerIcon(
            descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
            anchorY = (boxPx / 2f) / height,
        )
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
