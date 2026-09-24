package com.plane.cube.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * PlaneCube brand palette.
 *
 * The blues are lifted straight from the launcher-icon gradient
 * (`drawable/ic_launcher_background.xml`), so app chrome — buttons, FABs,
 * top-bar accents — reads as the same material as the icon. Reds mirror the
 * icon's tracking-cube face and mark the aircraft closest to the tracked area.
 */
// Icon-gradient anchors, top-left → bottom-right.
val BrandCobalt = Color(0xFF003B97) // gradient start; primary accent
val BrandDeepCobalt = Color(0xFF002766) // gradient midpoint
val BrandNavy = Color(0xFF001740) // gradient end; ground/background
// A lighter cobalt for `primaryContainer` (tonal step above BrandCobalt).
val BrandSkyBlue = Color(0xFF3A78D6)

// Red pulled from the cube's right-hand face; container tone is a pale wash.
val BrandRed = Color(0xFFE5343B)
val BrandCoral = Color(0xFFFF6B6B)
val BrandBlush = Color(0xFFFFD6D6)

@Deprecated(
    "Replaced by BrandCobalt (matches the launcher-icon gradient start).",
    ReplaceWith("BrandCobalt"),
)
val BrandBlue: Color get() = BrandCobalt
