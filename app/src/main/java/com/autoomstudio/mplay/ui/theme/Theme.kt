package com.autoomstudio.mplay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val NeonDarkColors = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = NeonPurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = NeonViolet,
    onSecondary = MidnightBackground,
    secondaryContainer = MidnightSelected,
    onSecondaryContainer = MidnightOnSurface,
    tertiary = NeonViolet,
    background = MidnightBackground,
    onBackground = MidnightOnSurface,
    surface = MidnightBackground,
    onSurface = MidnightOnSurface,
    surfaceVariant = MidnightSelected,
    onSurfaceVariant = MidnightOnSurfaceVariant,
    surfaceContainerLowest = MidnightBackground,
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightSurfaceContainer,
    surfaceContainerHigh = MidnightSurfaceHigh,
    surfaceContainerHighest = MidnightSelected,
    outline = MidnightOutline,
    outlineVariant = MidnightOutline,
)

private val NeonLightColors = lightColorScheme(
    primary = NeonPurpleDeep,
    onPrimary = Color.White,
    primaryContainer = DaylightSelected,
    onPrimaryContainer = NeonPurpleDeep,
    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = DaylightSelected,
    onSecondaryContainer = DaylightOnSurface,
    tertiary = NeonPurple,
    background = DaylightBackground,
    onBackground = DaylightOnSurface,
    surface = DaylightBackground,
    onSurface = DaylightOnSurface,
    surfaceVariant = DaylightSelected,
    onSurfaceVariant = DaylightOnSurfaceVariant,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightSurface,
    surfaceContainer = DaylightSurfaceContainer,
    surfaceContainerHigh = DaylightSurfaceHigh,
    surfaceContainerHighest = DaylightSelected,
    outline = DaylightOutline,
    outlineVariant = DaylightOutline,
)

/** Gradient used for the "M" of the MPlay wordmark and other accent highlights. */
val NeonBrush = Brush.linearGradient(listOf(NeonPurpleDeep, NeonPurple, NeonViolet))

@Composable
fun MPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NeonDarkColors else NeonLightColors,
        typography = MPlayTypography,
        content = content,
    )
}
