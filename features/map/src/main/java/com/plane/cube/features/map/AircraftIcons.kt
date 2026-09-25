package com.plane.cube.features.map

import android.graphics.Matrix
import android.graphics.Path
import android.util.Log
import androidx.core.graphics.PathParser
import com.plane.cube.domain.entity.Plane

private const val TAG = "AircraftIcons"

/**
 * One aircraft silhouette from tar1090's `markers.js`: SVG path data plus the
 * size (in CSS px, used here as dp) it is drawn at before per-type scaling.
 */
internal class AircraftShape(
    val name: String,
    val width: Float,
    val height: Float,
    /** SVG viewBox: minX, minY, width, height. */
    val viewBox: FloatArray,
    private val pathData: List<String>,
    private val accentData: List<String> = emptyList(),
    /** SVG `transform` on the shape group, e.g. `scale(-0.01,0.01)`. */
    private val transform: String? = null,
    val accentMult: Float = 1f,
    /** Symmetric shapes (balloons, ground markers) are drawn upright. */
    val noRotate: Boolean = false,
    /** SVG `preserveAspectRatio="none"`: stretch the viewBox to width × height. */
    val noAspect: Boolean = false,
) {
    /**
     * Filled outline, in viewBox units. Parsed on first use; null if the path
     * data can't be parsed, so the caller can fall back to another shape
     * instead of crashing the map.
     */
    val body: Path? by lazy { parseOrNull(pathData) }

    /** Detail lines (windows, engines) stroked on top of the fill. */
    val accents: Path? by lazy { if (accentData.isEmpty()) null else parseOrNull(accentData) }

    private fun parseOrNull(data: List<String>): Path? =
        runCatching { parse(data) }
            .onFailure { Log.w(TAG, "Unparseable path data in aircraft shape '$name'", it) }
            .getOrNull()

    private fun parse(data: List<String>): Path {
        val path = Path()
        data.forEach { path.addPath(PathParser.createPathFromPathData(it)) }
        transformMatrix()?.let(path::transform)
        return path
    }

    private fun transformMatrix(): Matrix? {
        val spec = transform ?: return null
        val args = spec.substringAfter('(').substringBefore(')')
            .split(',', ' ').filter { it.isNotBlank() }.map { it.toFloat() }
        return Matrix().apply {
            when {
                spec.startsWith("scale") -> setScale(args[0], args.getOrElse(1) { args[0] })
                spec.startsWith("rotate") ->
                    setRotate(args[0], args.getOrElse(1) { 0f }, args.getOrElse(2) { 0f })
                spec.startsWith("translate") -> setTranslate(args[0], args.getOrElse(1) { 0f })
            }
        }
    }
}

/**
 * The silhouette picked for a plane and how much to scale it. [fallback] is
 * the generic marker, used if [shape]'s path data turns out to be unusable.
 */
internal data class AircraftIcon(
    val shape: AircraftShape,
    val scale: Float,
    private val generic: AircraftShape,
) {
    fun fallback(): AircraftIcon = AircraftIcon(generic, 1f, generic)
}

/**
 * Picks the silhouette for an aircraft the same way tar1090 does
 * (`getBaseMarker` in `markers.js`): exact ICAO type designator first, then
 * the type's ICAO description (e.g. `L2J`, combined with its wake turbulence
 * category), then the ADS-B emitter category, then a generic marker.
 *
 * The shapes and tables live in [tar1090Shapes] and `Tar1090Tables.kt`.
 * Touching them builds a few thousand map entries, so the first call is best
 * made off the main thread.
 */
internal class AircraftIcons {

    private val shapes = tar1090Shapes
    private val generic = shapes.getValue(UNKNOWN)
    private val cache = HashMap<Pair<String?, String?>, AircraftIcon>()

    fun iconFor(plane: Plane): AircraftIcon =
        cache.getOrPut(plane.typeDesignator to plane.category) {
            val ref = resolve(plane.typeDesignator, plane.category)
            // tar1090 shrinks every base marker by 4% (planeObject.js).
            AircraftIcon(shapes[ref.shape] ?: generic, ref.scale * BASE_SCALE, generic)
        }

    private fun resolve(typeDesignator: String?, category: String?): IconRef {
        typeDesignator?.let { designator ->
            tar1090TypeDesignatorIcons[designator]?.let { return it }
            val (description, wtc) = tar1090Types[designator] ?: (null to null)
            if (description != null && description.length == 3) {
                if (description == "L1P" && category == "B4") {
                    tar1090CategoryIcons["B4"]?.let { return it }
                }
                if (wtc != null && wtc.length == 1) {
                    val withWtc = "$description-$wtc"
                    if (withWtc == "L2J-M" && category == "A2") return IconRef("jet_swept", 1f)
                    tar1090TypeDescriptionIcons[withWtc]?.let { return it }
                }
                tar1090TypeDescriptionIcons[description]?.let { return it }
                tar1090TypeDescriptionIcons[description.substring(0, 1)]?.let {
                    return it.copy(scale = 1f)
                }
            }
        }
        category?.let { tar1090CategoryIcons[it]?.let { icon -> return icon } }
        return IconRef(UNKNOWN, 1f)
    }

    /** Builds the lookup tables up front, so later [iconFor] calls stay cheap. */
    fun warmUp() {
        tar1090Types.size
    }

    private companion object {
        const val UNKNOWN = "unknown"
        const val BASE_SCALE = 0.96f
    }
}
