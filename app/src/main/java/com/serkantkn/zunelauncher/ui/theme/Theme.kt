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
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
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
    /**
     * The accent for small words that are links — "close all", "play". On a dark ground the
     * accent itself; on the light theme a quarter darker, because a bright accent at 14sp on
     * white is barely there.
     */
    val accentLink: Color = ZuneColors.Magenta,
    val isDark: Boolean = true,
    val backgroundSolid: Boolean = false
)

/** The accent, darkened for the light theme's link words. */
private fun linkShadeOf(accent: Color, isDark: Boolean): Color =
    if (isDark) accent else Color(
        red = accent.red * LIGHT_LINK_DARKEN,
        green = accent.green * LIGHT_LINK_DARKEN,
        blue = accent.blue * LIGHT_LINK_DARKEN,
        alpha = accent.alpha
    )

private const val LIGHT_LINK_DARKEN = 0.75f

val LocalZuneColors = staticCompositionLocalOf { ZuneExtendedColors() }
val LocalIsWideScreen = staticCompositionLocalOf { false }

/**
 * Whether this is a television: nothing to touch, a remote's arrows and a sofa's distance. Start
 * is drawn for it instead of a phone's or a tablet's (ui/tv), and everything must be reachable
 * by focus alone.
 */
val LocalIsTv = staticCompositionLocalOf { false }

/** How much larger a television's type is set than a phone's at the same font-size setting. */
private const val TV_FONT_SCALE = 1.15f

/** Whether [context] runs on a television, as Android itself reports it. */
fun isTelevision(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager)
        ?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

/**
 * Whether the launcher's own motion is wanted.
 *
 * The setting existed and was stored for a long time without a single animation consulting it.
 * Everything shared reads it now — the hub entrance, the turnstile, the page hinge, the title
 * flight, the press lean and the page transition — so switching it off actually stills the
 * launcher instead of only being remembered.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/**
 * Whether the content below is actually being looked at.
 *
 * A hub covers the Start screen completely, but the board underneath it stays composed — its
 * scroll position and its place in the turning ripple have to survive being covered. False here
 * says "you are behind something opaque": nothing needs to be drawn, and the tiles stop turning
 * over until the hub is closed. On a cheap phone that is the difference between one screen's
 * worth of work and two.
 */
val LocalScreenAwake = staticCompositionLocalOf { true }

/**
 * App theme. [fontScale] is the user's font-size preference (SettingsDataStore.fontScale). It is
 * multiplied into [LocalDensity].fontScale, so every sp-based size (ZuneTypography and raw `.sp`
 * values) scales on top of the system font size. 1.0f leaves the platform density untouched.
 */
@Composable
fun ZuneLauncherTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColor = AccentColor.MAGENTA,
    dynamicThemeColor: Int? = null,
    customThemeColor: Int? = null,
    solidBackgroundEnabled: Boolean = false,
    fontScale: Float = 1.0f,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val context = LocalContext.current
    val isTv = remember(context) { isTelevision(context) }
    // A TV may wear Vista's Media Center: its type is Selawik and its colour Media Center's blue.
    val vista = if (isTv) {
        remember(context) { com.serkantkn.zunelauncher.ui.tv.TvDesign.attach(context) }
        com.serkantkn.zunelauncher.ui.tv.TvDesign.vista.collectAsState().value
    } else false
    // Media Center - Windows 7's and Vista's - was blue and white whatever Windows was set to: a
    // light theme put dark words on its blue, where nobody could read them.
    val isDarkTheme = isTv || when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val chosenAccentColor = when (accentColor) {
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
    val resolvedAccentColor = if (vista) com.serkantkn.zunelauncher.ui.tv.Vista.Link else chosenAccentColor

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
            accentLink = linkShadeOf(resolvedAccentColor, isDark = true),
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
            accentLink = linkShadeOf(resolvedAccentColor, isDark = false),
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

    val platformDensity = LocalDensity.current
    // Read from across a room: a television's type is set a size up, whatever the user chose.
    val effectiveFontScale = if (isTv) fontScale * TV_FONT_SCALE else fontScale
    val scaledDensity = remember(platformDensity, effectiveFontScale) {
        if (effectiveFontScale == 1.0f) platformDensity
        else Density(density = platformDensity.density, fontScale = platformDensity.fontScale * effectiveFontScale)
    }

    CompositionLocalProvider(
        LocalZuneColors provides zuneColors,
        LocalIsWideScreen provides isWideScreen,
        LocalIsTv provides isTv,
        com.serkantkn.zunelauncher.ui.tv.LocalVista provides vista,
        LocalAnimationsEnabled provides animationsEnabled,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (vista) remember { com.serkantkn.zunelauncher.ui.tv.vistaTypography(ZuneTypography) } else ZuneTypography,
            content = content
        )
    }
}
/** Walks ContextWrapper chain to the hosting Activity, or null for service/application contexts. */
fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
