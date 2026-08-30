package com.niklauncher.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NikDarkColors = darkColorScheme(
    primary = NikViolet,
    onPrimary = Color.White,
    primaryContainer = NikVioletDeep,
    onPrimaryContainer = Color.White,
    secondary = NikAmber,
    onSecondary = Color(0xFF2A1B00),
    secondaryContainer = NikAmberDeep,
    onSecondaryContainer = Color.White,
    background = NikBackgroundDark,
    onBackground = NikOnSurfaceDark,
    surface = NikSurfaceDark,
    onSurface = NikOnSurfaceDark,
    surfaceVariant = NikSurfaceVariantDark,
    onSurfaceVariant = NikOnSurfaceVariantDark,
    outline = NikOutlineDark,
    error = NikError,
    onError = Color(0xFF2A0000),
)

private val NikLightColors = lightColorScheme(
    primary = NikVioletDeep,
    onPrimary = Color.White,
    primaryContainer = NikVioletBright,
    onPrimaryContainer = Color(0xFF1A0B4A),
    secondary = NikAmberDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3B0),
    onSecondaryContainer = Color(0xFF2A1B00),
    background = NikBackgroundLight,
    onBackground = NikOnSurfaceLight,
    surface = NikSurfaceLight,
    onSurface = NikOnSurfaceLight,
    surfaceVariant = NikSurfaceVariantLight,
    onSurfaceVariant = NikOnSurfaceVariantLight,
    outline = NikOutlineLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * Dynamic colour is deliberately not used. NikLauncher has an identity of its
 * own, and letting the wallpaper recolour it would dissolve that.
 */
@Composable
fun NikLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NikDarkColors else NikLightColors,
        typography = NikTypography,
        content = content,
    )
}
