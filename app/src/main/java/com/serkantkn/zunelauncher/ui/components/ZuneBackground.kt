package com.serkantkn.zunelauncher.ui.components

import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.util.WallpaperImages
import kotlinx.coroutines.launch

enum class BackgroundMode {
    GRADIENT,
    WALLPAPER,
    BLURRED_WALLPAPER,
    SOLID
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
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val parallaxPx by if (followsStart) {
        com.serkantkn.zunelauncher.data.repository.StartParallax.shiftPx.collectAsState()
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

    val effectiveCustomPath = customWallpaperPathOverride ?: customWallpaperPath

    // The picture itself is read by WallpaperImages, which keeps one copy for the whole launcher:
    // several of these backgrounds are alive at once — the Start screen, the hub in front of it,
    // the settings preview — and they were each decoding the same wallpaper for themselves.
    val wantsBlur = (forceModeOverride ?: mode) == BackgroundMode.BLURRED_WALLPAPER
    LaunchedEffect(wallpaperVersion, effectiveCustomPath, wantsBlur) {
        val wallpapers = WallpaperImages.load(context, effectiveCustomPath, wallpaperVersion, wantsBlur)
        if (wallpapers.full != null) {
            wallpaperBitmap = wallpapers.full
            blurredWallpaperBitmap = wallpapers.blurred
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
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
            }
        }
        content()
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
