package com.autoomstudio.mp3studio.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.autoomstudio.mp3studio.data.settings.ThemeMode
import com.autoomstudio.mp3studio.data.settings.ThemeSettings
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal val NeonDarkColors = darkColorScheme(
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

internal val NeonLightColors = lightColorScheme(
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

/**
 * Applies the user's theme settings and matches the edge-to-edge system bar icons to them, so a
 * forced light or dark theme doesn't leave unreadable status bar icons. [forceDark] overrides the
 * settings for screens that are always dark, such as sign-in.
 */
@Composable
fun MPlayAppTheme(
    activity: ComponentActivity,
    settings: ThemeSettings,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = forceDark || when (settings.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    LaunchedEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { darkTheme },
        )
    }
    MPlayTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor, content = content)
}

/** Gradient used for the "M" of the MPlay wordmark and other accent highlights. */
val NeonBrush = Brush.linearGradient(listOf(NeonPurpleDeep, NeonPurple, NeonViolet))

@Composable
fun MPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Wallpaper colors; ignored below Android 12, where they don't exist. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NeonDarkColors
        else -> NeonLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MPlayTypography,
        content = content,
    )
}
