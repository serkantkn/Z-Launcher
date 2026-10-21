package com.serkantkn.zeta.ui.components

import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.di.appContainer
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneColors
import com.serkantkn.zeta.util.WallpaperImages
import kotlinx.coroutines.launch

enum class BackgroundMode {
    GRADIENT,
    WALLPAPER,
    BLURRED_WALLPAPER,
    SOLID,
    /** The Zune layout's glow of colour ([ZuneBackground]'s `glow`). */
    GLOW
}

private const val TAG = "ZuneBackground"

/**
 * Composable background renderer that supports multiple modes:
 * - GRADIENT: Dark gradient with subtle accent color
 * - WALLPAPER: System wallpaper with dark overlay
 * - BLURRED_WALLPAPER: System wallpaper with blur + dark overlay
 * - SOLID: Pure black
 *
 * Transitions between modes use Crossfade animation.
 */

@Composable
fun ZuneBackground(
    mode: BackgroundMode = BackgroundMode.GRADIENT,
    accentColor: Color = ZuneColors.Pink,
    customWallpaperPathOverride: String? = null,
    forceModeOverride: BackgroundMode? = null,
    modifier: Modifier = Modifier,
    /** True for the background under Start: it drifts a little as the Zune list scrolls. */
    followsStart: Boolean = false,
    /**
     * True to draw the phone's own wallpaper whatever picture the launcher has been given. Said
     * outright because a null override means "use the launcher's", not "use the system's".
     */
    systemWallpaper: Boolean = false,
    /**
     * How frosted the wallpaper behind see-through tiles is, 0-100; 0 leaves it sharp. Only a
     * background with tiles over it asks.
     */
    tileBlur: Int = 0,
    /** Which colours a [BackgroundMode.GLOW] ground glows in. */
    glow: com.serkantkn.zeta.data.model.ZuneGlow = com.serkantkn.zeta.data.model.ZuneGlow.ZUNE,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val parallaxPx by if (followsStart) {
        com.serkantkn.zeta.data.repository.StartParallax.shiftPx.collectAsState()
    } else {
        remember { mutableStateOf(0f) }
    }
    var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blurredWallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Track wallpaper changes so blur surfaces stay up-to-date
    var wallpaperVersion by remember { mutableStateOf(0) }

    val settingsDataStore = remember { context.appContainer.settingsDataStore }
    val customWallpaperPath by settingsDataStore.customWallpaperPath.collectAsState(initial = null)
    val solidBackgroundEnabled by settingsDataStore.solidBackgroundEnabled.collectAsState(initial = false)

    val coroutineScope = rememberCoroutineScope()
    
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                ZuneLog.d(TAG, "Wallpaper changed, reloading…")
                if (System.currentTimeMillis() - ZuneWallpaperManager.lastInternalWallpaperChangeTime > 5000) {
                    // External wallpaper change: clear our internal custom wallpaper
                    coroutineScope.launch {
                        settingsDataStore.setCustomWallpaperPath(null)
                    }
                }
                wallpaperVersion++
            }
        }
        context.registerReceiver(
            receiver,
            android.content.IntentFilter(android.content.Intent.ACTION_WALLPAPER_CHANGED)
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val effectiveCustomPath = if (systemWallpaper) null else customWallpaperPathOverride ?: customWallpaperPath

    // The picture itself is read by WallpaperImages, which keeps one copy for the whole launcher:
    // several of these backgrounds are alive at once — the Start screen, the hub in front of it,
    // the settings preview — and they were each decoding the same wallpaper for themselves.
    val wantsBlur = (forceModeOverride ?: mode) == BackgroundMode.BLURRED_WALLPAPER
    val generation by WallpaperImages.generation.collectAsState()
    // A wallpaper that could not be read is tried again each time the launcher comes back to the
    // front: access to it is given in the system's settings, and nothing else says it was.
    var retries by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (wallpaperBitmap == null) retries++
    }
    LaunchedEffect(wallpaperVersion, effectiveCustomPath, wantsBlur, generation, retries) {
        val wallpapers = WallpaperImages.load(context, effectiveCustomPath, wallpaperVersion, wantsBlur)
        if (wallpapers.full != null) {
            wallpaperBitmap = wallpapers.full
            blurredWallpaperBitmap = wallpapers.blurred
        }
    }

    val shownMode = forceModeOverride ?: if (solidBackgroundEnabled) BackgroundMode.SOLID else mode
    val backdrop = rememberTileBackdrop(
        wallpaper = if (shownMode == BackgroundMode.WALLPAPER) wallpaperBitmap else null,
        level = tileBlur
    )
    backdrop.drift = {
        if (parallaxPx != 0f) parallaxPx.coerceIn(0f, backdrop.hostSize.height * (PARALLAX_ZOOM - 1f) / 2f) else 0f
    }
    backdrop.driftZoom = { if (parallaxPx != 0f) PARALLAX_ZOOM else 1f }

    Box(modifier = modifier.fillMaxSize().tileBackdropHost(backdrop)) {
        Crossfade(
            targetState = forceModeOverride ?: if (solidBackgroundEnabled) BackgroundMode.SOLID else mode,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Drawn a touch larger than the screen so the drift never shows an edge.
                    if (parallaxPx != 0f) {
                        scaleX = PARALLAX_ZOOM
                        scaleY = PARALLAX_ZOOM
                        translationY = -parallaxPx.coerceIn(0f, size.height * (PARALLAX_ZOOM - 1f) / 2f)
                    }
                },
            label = "background_transition"
        ) { currentMode ->
            when (currentMode) {
                BackgroundMode.GRADIENT -> GradientBackground(accentColor = accentColor)
                BackgroundMode.WALLPAPER -> WallpaperBackground(
                    bitmapToDraw = wallpaperBitmap,
                    fallbackAccentColor = accentColor
                )
                BackgroundMode.BLURRED_WALLPAPER -> WallpaperBackground(
                    bitmapToDraw = blurredWallpaperBitmap ?: wallpaperBitmap,
                    fallbackAccentColor = accentColor
                )
                BackgroundMode.SOLID -> SolidBackground()
                BackgroundMode.GLOW -> ZuneGlowBackground(glow = glow)
            }
        }
        // Offered only over a wallpaper: a plain background has nothing to frost, and Start keeps
        // its shade there.
        CompositionLocalProvider(
            LocalTileBackdrop provides backdrop.takeIf { tileBlur > 0 && shownMode == BackgroundMode.WALLPAPER }
        ) {
            content()
        }
    }
}

/** Room for the wallpaper to drift: three percent of its height either way. */
private const val PARALLAX_ZOOM = 1.06f

object ZuneWallpaperManager {
    var lastInternalWallpaperChangeTime: Long = 0L
}

@Composable
private fun GradientBackground(accentColor: Color) {
    val isDark = LocalZuneColors.current.isDark
    val bg1 = if (isDark) ZuneColors.Black else ZuneColors.LightBackground
    val bg2 = if (isDark) ZuneColors.DarkNavy else ZuneColors.LightSurface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        bg1,
                        bg2,
                        accentColor.copy(alpha = if (isDark) 0.12f else 0.08f),
                        bg1
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    )
}

@Composable
private fun WallpaperBackground(
    bitmapToDraw: Bitmap?,
    fallbackAccentColor: Color
) {
    bitmapToDraw?.let { bitmap ->
        val isDark = LocalZuneColors.current.isDark
        
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    } ?: SystemWallpaperFallback(accentColor = fallbackAccentColor)
}

@Composable
private fun SystemWallpaperFallback(accentColor: Color) {
    val isDark = LocalZuneColors.current.isDark
    val overlayColor = if (isDark) Color(0x66000000) else Color(0x55FFFFFF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = if (isDark) 0.10f else 0.07f),
                        Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    )
}

/**
 * The Zune's warm light: a blur of magenta running into orange from the top right, fading into
 * the dark, and a little of it again low on the left - the ground of the Zune HD and of the Zune
 * software. Still, so Start costs nothing to keep on screen; it drifts only as the list scrolls.
 * In the accent's colours it is the same light, the accent and a warmer neighbour of it.
 */
@Composable
private fun ZuneGlowBackground(glow: com.serkantkn.zeta.data.model.ZuneGlow) {
    val isDark = LocalZuneColors.current.isDark
    // The theme's own accent - the one the running marks and the tiles wear - not the
    // magenta-to-orange that Start's background is otherwise handed.
    val accentColor = LocalZuneColors.current.accentColor
    val (first, second) = remember(glow, accentColor) { zuneGlowColours(glow, accentColor) }
    val base = if (isDark) Color(0xFF0A0507) else Color(0xFFF8F3F5)
    // On white the same light would drown the black type; it is kept to a tint there.
    val strength = if (isDark) 1f else 0.5f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val reach = maxOf(w, h)
                val main = Brush.radialGradient(
                    0f to first.copy(alpha = 0.9f * strength),
                    0.35f to first.copy(alpha = 0.42f * strength),
                    0.7f to first.copy(alpha = 0.1f * strength),
                    1f to Color.Transparent,
                    center = Offset(w * 1.05f, -h * 0.02f),
                    radius = reach * 0.62f
                )
                val warm = Brush.radialGradient(
                    0f to second.copy(alpha = 0.7f * strength),
                    0.45f to second.copy(alpha = 0.22f * strength),
                    1f to Color.Transparent,
                    center = Offset(w * 0.86f, h * 0.17f),
                    radius = reach * 0.34f
                )
                val low = Brush.radialGradient(
                    0f to first.copy(alpha = 0.26f * strength),
                    1f to Color.Transparent,
                    center = Offset(-w * 0.15f, h * 1.05f),
                    radius = reach * 0.45f
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(main)
                    drawRect(warm)
                    drawRect(low)
                }
            }
    )
}

/** The two colours of a glow: its main light and the warmer one inside it. */
internal fun zuneGlowColours(glow: com.serkantkn.zeta.data.model.ZuneGlow, accent: Color): Pair<Color, Color> =
    when (glow) {
        com.serkantkn.zeta.data.model.ZuneGlow.ACCENT -> {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
            // A neighbour towards yellow, lighter: the way the Zune's orange sits beside its magenta.
            val warmer = floatArrayOf((hsv[0] + 35f) % 360f, hsv[1] * 0.9f, minOf(1f, hsv[2] * 1.15f + 0.05f))
            accent to Color(android.graphics.Color.HSVToColor(warmer))
        }
        else -> ZUNE_MAGENTA to ZUNE_ORANGE
    }

/** The Zune's own two colours, from its mark. */
private val ZUNE_MAGENTA = Color(0xFFEC008C)
private val ZUNE_ORANGE = Color(0xFFF7941D)

@Composable
private fun SolidBackground() {
    val isDark = LocalZuneColors.current.isDark
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color.White)
    )
}

@Composable
fun ZuneWallpaperOverlay(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    isHubOverlay: Boolean = false
) {
    if (alpha <= 0f) return
    val isDark = LocalZuneColors.current.isDark
    
    if (isHubOverlay) {
        val tintColor = if (isDark) Color(0xFF0A0A0A).copy(alpha = alpha) else Color(0xFFF5F5F5).copy(alpha = alpha)
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(tintColor)
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isDark) listOf(
                            Color(0xFF0A0A0A).copy(alpha = 0.85f),
                            Color(0xFF202020).copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.12f * 0.85f),
                            Color(0xFF0A0A0A).copy(alpha = 0.85f)
                        ) else listOf(
                            Color(0xFFF5F5F5).copy(alpha = 0.85f),
                            Color(0xFFFFFFFF).copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.08f * 0.85f),
                            Color(0xFFF5F5F5).copy(alpha = 0.85f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        )
    }
}
