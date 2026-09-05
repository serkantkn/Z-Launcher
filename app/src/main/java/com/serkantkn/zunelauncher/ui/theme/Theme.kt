package com.serkantkn.zunelauncher.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.ThemeMode

fun AccentColor.toColor(): Color = when (this) {
    AccentColor.DYNAMIC -> Color.Transparent
    AccentColor.CUSTOM -> Color.Transparent
    AccentColor.MAGENTA -> ZuneColors.Magenta
    AccentColor.PINK -> ZuneColors.Pink
    AccentColor.ORANGE -> ZuneColors.Orange
    AccentColor.PURPLE -> ZuneColors.Purple
    AccentColor.BLUE -> ZuneColors.Blue
    AccentColor.GREEN -> ZuneColors.Green
    AccentColor.TEAL -> ZuneColors.Teal
    AccentColor.RED -> ZuneColors.Red
    AccentColor.YELLOW -> ZuneColors.Yellow
    AccentColor.LIME -> ZuneColors.Lime
    AccentColor.CRIMSON -> ZuneColors.Crimson
    AccentColor.COBALT -> ZuneColors.Cobalt
    AccentColor.AMBER -> ZuneColors.Amber
    AccentColor.BROWN -> ZuneColors.Brown
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
    val isDark: Boolean = true,
    val backgroundSolid: Boolean = false
)

val LocalZuneColors = staticCompositionLocalOf { ZuneExtendedColors() }
val LocalIsWideScreen = staticCompositionLocalOf { false }

@Composable
fun ZuneLauncherTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.MAGENTA,
    dynamicThemeColor: Int? = null,
    customThemeColor: Int? = null,
    solidBackgroundEnabled: Boolean = false,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val context = LocalContext.current
    val resolvedAccentColor = when (accentColor) {
        AccentColor.DYNAMIC -> {
            if (dynamicThemeColor != null) {
                Color(dynamicThemeColor)
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val dynamicScheme = if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dynamicScheme.primary
            } else {
                ZuneColors.Magenta // Fallback
            }
        }
        AccentColor.CUSTOM -> {
            if (customThemeColor != null) {
                Color(customThemeColor)
            } else {
                ZuneColors.Magenta // Fallback
            }
        }
        else -> {
            accentColor.toColor()
        }
    }

    val colorScheme = if (isDarkTheme) {
        if (solidBackgroundEnabled) {
            ZuneDarkColorScheme.copy(background = Color.Black)
        } else {
            ZuneDarkColorScheme
        }
    } else {
        if (solidBackgroundEnabled) {
            ZuneLightColorScheme.copy(background = Color.White)
        } else {
            ZuneLightColorScheme
        }
    }

    val zuneColors = if (isDarkTheme) {
        ZuneExtendedColors(
            isDark = true,
            accentColor = resolvedAccentColor,
            textMuted = if (solidBackgroundEnabled) Color(0xFFB5B5B5) else ZuneColors.WhiteMuted,
            textDim = if (solidBackgroundEnabled) Color(0xFF8E8E8E) else ZuneColors.WhiteDim,
            backgroundSolid = solidBackgroundEnabled
        )
    } else {
        ZuneExtendedColors(
            textMuted = if (solidBackgroundEnabled) Color(0xFF4A4A4A) else ZuneColors.LightTextMuted,
            textDim = if (solidBackgroundEnabled) Color(0xFF7A7A7A) else Color(0xFF999999),
            overlay = Color(0x1A000000),
            darkOverlay = Color(0xCCFFFFFF),
            isDark = false,
            accentColor = resolvedAccentColor,
            backgroundSolid = solidBackgroundEnabled
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // The theme is also used inside system overlay windows (WpToastOverlay) whose context is
            // the Application, not an Activity; only touch system bars when an Activity is present.
            val window = view.context.findActivity()?.window ?: return@SideEffect
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

    CompositionLocalProvider(
        LocalZuneColors provides zuneColors,
        LocalIsWideScreen provides isWideScreen
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZuneTypography,
            content = content
        )
    }
}
/** Walks ContextWrapper chain to the hosting Activity, or null for service/application contexts. */
private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
