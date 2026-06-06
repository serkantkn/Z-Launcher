package com.serkantkn.zunelauncher.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.ThemeMode

fun AccentColor.toColor(): Color = when (this) {
    AccentColor.MAGENTA -> ZuneColors.Magenta
    AccentColor.PINK -> ZuneColors.Pink
    AccentColor.ORANGE -> ZuneColors.Orange
    AccentColor.PURPLE -> ZuneColors.Purple
    AccentColor.BLUE -> ZuneColors.Blue
    AccentColor.GREEN -> ZuneColors.Green
    AccentColor.TEAL -> ZuneColors.Teal
    AccentColor.RED -> ZuneColors.Red
}

private val ZuneDarkColorScheme = darkColorScheme(
    primary = ZuneColors.Pink,
    secondary = ZuneColors.Magenta,
    tertiary = ZuneColors.Orange,
    background = ZuneColors.Black,
    surface = ZuneColors.DarkNavy,
    surfaceVariant = ZuneColors.DarkCard,
    onPrimary = ZuneColors.White,
    onSecondary = ZuneColors.White,
    onTertiary = ZuneColors.White,
    onBackground = ZuneColors.White,
    onSurface = ZuneColors.White,
    onSurfaceVariant = ZuneColors.WhiteMuted
)

private val ZuneLightColorScheme = lightColorScheme(
    primary = ZuneColors.Magenta,
    secondary = ZuneColors.Pink,
    tertiary = ZuneColors.Orange,
    background = ZuneColors.LightBackground,
    surface = ZuneColors.LightSurface,
    surfaceVariant = Color(0xFFE8E8E8),
    onPrimary = ZuneColors.White,
    onSecondary = ZuneColors.White,
    onTertiary = ZuneColors.White,
    onBackground = ZuneColors.LightText,
    onSurface = ZuneColors.LightText,
    onSurfaceVariant = ZuneColors.LightTextMuted
)

/**
 * Extended Zune-specific colors not covered by Material3 color scheme.
 * Accessed via LocalZuneColors.current throughout the app.
 */
data class ZuneExtendedColors(
    val accentPink: Color = ZuneColors.Pink,
    val accentMagenta: Color = ZuneColors.Magenta,
    val accentOrange: Color = ZuneColors.Orange,
    val textMuted: Color = ZuneColors.WhiteMuted,
    val textDim: Color = ZuneColors.WhiteDim,
    val overlay: Color = ZuneColors.SurfaceOverlay,
    val darkOverlay: Color = ZuneColors.DarkOverlay,
    val accentColor: Color = ZuneColors.Magenta,
    val isDark: Boolean = true
)

val LocalZuneColors = staticCompositionLocalOf { ZuneExtendedColors() }

@Composable
fun ZuneLauncherTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.MAGENTA,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDarkTheme) ZuneDarkColorScheme else ZuneLightColorScheme

    val zuneColors = if (isDarkTheme) {
        ZuneExtendedColors(
            isDark = true,
            accentColor = accentColor.toColor()
        )
    } else {
        ZuneExtendedColors(
            textMuted = ZuneColors.LightTextMuted,
            textDim = Color(0xFF999999),
            overlay = Color(0x1A000000),
            darkOverlay = Color(0xCCFFFFFF),
            isDark = false,
            accentColor = accentColor.toColor()
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalZuneColors provides zuneColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZuneTypography,
            content = content
        )
    }
}