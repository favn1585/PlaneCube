package com.plane.cube.features.map

import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.util.Log
import androidx.core.graphics.PathParser
import com.plane.cube.domain.entity.Plane
import org.json.JSONArray
import org.json.JSONObject

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
    private val accentData: List<String>,
    /** SVG `transform` on the shape group, e.g. `scale(-0.01,0.01)`. */
    private val transform: String?,
    val accentMult: Float,
    /** Symmetric shapes (balloons, ground markers) are drawn upright. */
    val noRotate: Boolean,
    /** SVG `preserveAspectRatio="none"`: stretch the viewBox to width × height. */
    val noAspect: Boolean,
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
 * The data in `res/raw/tar1090_icons.json` is converted from tar1090's
 * `html/markers.js` (GPL-2.0-or-later) and tar1090-db's aircraft type table.
 */
internal class AircraftIcons private constructor(
    private val shapes: Map<String, AircraftShape>,
    private val typeDesignatorIcons: Map<String, Pair<String, Float>>,
    private val typeDescriptionIcons: Map<String, Pair<String, Float>>,
    private val categoryIcons: Map<String, Pair<String, Float>>,
    /** ICAO type designator → (description, wake turbulence category). */
    private val types: Map<String, Pair<String, String?>>,
) {
    private val cache = HashMap<Pair<String?, String?>, AircraftIcon>()

    fun iconFor(plane: Plane): AircraftIcon =
        cache.getOrPut(plane.typeDesignator to plane.category) {
            val (name, scale) = resolve(plane.typeDesignator, plane.category)
            val generic = shapes.getValue(UNKNOWN)
            // tar1090 shrinks every base marker by 4% (planeObject.js).
            AircraftIcon(shapes[name] ?: generic, scale * BASE_SCALE, generic)
        }

    private fun resolve(typeDesignator: String?, category: String?): Pair<String, Float> {
        typeDesignator?.let { designator ->
            typeDesignatorIcons[designator]?.let { return it }
            val (description, wtc) = types[designator] ?: (null to null)
            if (description != null && description.length == 3) {
                if (description == "L1P" && category == "B4") {
                    categoryIcons["B4"]?.let { return it }
                }
                if (wtc != null && wtc.length == 1) {
                    val withWtc = "$description-$wtc"
                    if (withWtc == "L2J-M" && category == "A2") return "jet_swept" to 1f
                    typeDescriptionIcons[withWtc]?.let { return it }
                }
                typeDescriptionIcons[description]?.let { return it }
                typeDescriptionIcons[description.substring(0, 1)]?.let { return it.first to 1f }
            }
        }
        category?.let { categoryIcons[it]?.let { icon -> return icon } }
        return UNKNOWN to 1f
    }

    companion object {
        private const val UNKNOWN = "unknown"
        private const val BASE_SCALE = 0.96f

        /** Parses the bundled tables. Takes a few tens of ms; call off the main thread. */
        fun load(context: Context): AircraftIcons {
            val text = context.resources.openRawResource(R.raw.tar1090_icons)
                .bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            return AircraftIcons(
                shapes = root.getJSONObject("shapes").mapValues { name, value ->
                    parseShape(name, value as JSONObject)
                },
                typeDesignatorIcons = root.getJSONObject("typeDesignatorIcons").mapValues { _, v -> iconRef(v) },
                typeDescriptionIcons = root.getJSONObject("typeDescriptionIcons").mapValues { _, v -> iconRef(v) },
                categoryIcons = root.getJSONObject("categoryIcons").mapValues { _, v -> iconRef(v) },
                types = root.getJSONObject("types").mapValues { _, value ->
                    val entry = value as JSONArray
                    entry.getString(0) to entry.optString(1).takeIf { it.isNotEmpty() }
                },
            )
        }

        private fun iconRef(value: Any): Pair<String, Float> {
            val entry = value as JSONArray
            return entry.getString(0) to entry.getDouble(1).toFloat()
        }

        private fun parseShape(name: String, json: JSONObject): AircraftShape {
            val viewBox = json.getJSONArray("viewBox")
            return AircraftShape(
                name = name,
                width = json.getDouble("w").toFloat(),
                height = json.getDouble("h").toFloat(),
                viewBox = FloatArray(4) { viewBox.getDouble(it).toFloat() },
                pathData = json.getJSONArray("path").strings(),
                accentData = json.optJSONArray("accent")?.strings().orEmpty(),
                transform = json.optString("transform").takeIf { it.isNotEmpty() },
                accentMult = json.optDouble("accentMult", 1.0).toFloat(),
                noRotate = json.optBoolean("noRotate"),
                noAspect = json.optBoolean("noAspect"),
            )
        }

        private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

        private fun <T> JSONObject.mapValues(transform: (String, Any) -> T): Map<String, T> {
            val out = HashMap<String, T>(length())
            keys().forEach { key -> out[key] = transform(key, get(key)) }
            return out
        }
    }
}
