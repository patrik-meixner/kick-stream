package com.kickstream.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.darkColorScheme

private val DarkColorScheme = darkColorScheme(
    primary = KickGreen,
    onPrimary = DarkBackground,
    background = DarkBackground,
    onBackground = OnDarkSurface,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    error = ErrorRed,
)

val KickStreamShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

// Extra colors not in the standard color scheme, accessible via composition local
data class ExtendedColors(
    val kickGreenAlpha: Color = KickGreenAlpha,
    val darkSurfaceElevated: Color = DarkSurfaceElevated,
    val liveRed: Color = LiveRed,
)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors() }

@Composable
fun KickStreamTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides ExtendedColors()) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = KickStreamTypography,
            shapes = KickStreamShapes,
            content = content,
        )
    }
}
