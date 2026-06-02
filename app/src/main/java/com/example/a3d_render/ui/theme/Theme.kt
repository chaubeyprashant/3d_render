package com.example.a3d_render.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MonoOnBackground,
    onPrimary = MonoBlack,
    secondary = MonoOnSurfaceMuted,
    onSecondary = MonoOnBackground,
    background = MonoBackground,
    onBackground = MonoOnBackground,
    surface = MonoSurface,
    onSurface = MonoOnSurface,
    surfaceVariant = MonoSurfaceVariant,
    onSurfaceVariant = MonoOnSurfaceMuted,
    surfaceContainerHigh = MonoSurfaceElevated,
    outline = MonoOutline,
    outlineVariant = Color(0xFF2E2E2E)
)

private val LightColorScheme = lightColorScheme(
    primary = MonoLightOnBackground,
    onPrimary = Color.White,
    secondary = MonoLightOnSurfaceMuted,
    onSecondary = MonoLightOnBackground,
    background = MonoLightBackground,
    onBackground = MonoLightOnBackground,
    surface = MonoLightSurface,
    onSurface = MonoLightOnBackground,
    surfaceVariant = MonoLightSurfaceVariant,
    onSurfaceVariant = MonoLightOnSurfaceMuted,
    surfaceContainerHigh = MonoLightSurfaceVariant,
    outline = Color(0xFFD0D0D0),
    outlineVariant = Color(0xFFE5E5E5)
)

@Composable
fun _3d_renderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
