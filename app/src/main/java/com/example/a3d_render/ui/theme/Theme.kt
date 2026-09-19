package com.example.a3d_render.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IndowingsDarkColorScheme = darkColorScheme(
    primary = IndowingsGreen,
    onPrimary = Color.White,
    primaryContainer = IndowingsGreenDark,
    onPrimaryContainer = Color.White,
    secondary = TextWhiteMuted,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextWhiteMuted,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFCF6679),
    errorContainer = Color(0xFF93000A)
)

@Composable
fun _3d_renderTheme(
    darkTheme: Boolean = true, // Always dark to match INDOWINGS desktop
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IndowingsDarkColorScheme,
        typography = Typography,
        content = content
    )
}
