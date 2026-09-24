package com.plane.cube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Both schemes share the same primary — the launcher icon's cobalt gradient
// start — so the FAB / action buttons / accented text always feel like a piece
// of the icon. Only the neutrals shift with light/dark mode.
private val DarkColorScheme = darkColorScheme(
    primary = BrandCobalt,
    onPrimary = Color.White,
    primaryContainer = BrandDeepCobalt,
    onPrimaryContainer = Color.White,
    secondary = BrandSkyBlue,
    onSecondary = Color.White,
    // Tracking cube + closest-aircraft accents.
    tertiary = BrandCoral,
    onTertiary = BrandNavy,
    tertiaryContainer = BrandRed,
    onTertiaryContainer = Color.White,
    background = BrandNavy,
    onBackground = Color.White,
    surface = BrandNavy,
    onSurface = Color.White,
    surfaceVariant = BrandDeepCobalt,
    onSurfaceVariant = BrandBlush,
    error = BrandCoral,
    onError = BrandNavy,
    errorContainer = BrandRed,
    onErrorContainer = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandCobalt,
    onPrimary = Color.White,
    primaryContainer = BrandSkyBlue,
    onPrimaryContainer = Color.White,
    secondary = BrandSkyBlue,
    onSecondary = Color.White,
    tertiary = BrandRed,
    onTertiary = Color.White,
    tertiaryContainer = BrandBlush,
    onTertiaryContainer = BrandNavy,
    background = Color.White,
    onBackground = BrandNavy,
    surface = Color.White,
    onSurface = BrandNavy,
    surfaceVariant = Color(0xFFE6ECF7),
    onSurfaceVariant = BrandNavy,
    error = BrandRed,
    onError = Color.White,
    errorContainer = BrandBlush,
    onErrorContainer = BrandNavy,
)

@Composable
fun PlaneCubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
