package com.serkantkn.zunelauncher.ui.components

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.serkantkn.zunelauncher.ui.theme.LocalBackgroundSize
import com.serkantkn.zunelauncher.ui.theme.LocalBlurredWallpaperBitmap
import com.serkantkn.zunelauncher.ui.theme.LocalHazeState
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.LocalWallpaperBitmap
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.util.SimpleBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val hazeState = rememberHazeState()
    var backgroundSize by remember { mutableStateOf(IntSize.Zero) }
    var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var blurredWallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Track wallpaper changes so blur surfaces stay up-to-date
    var wallpaperVersion by remember { mutableStateOf(0) }

    val settingsDataStore = remember { com.serkantkn.zunelauncher.data.datastore.SettingsDataStore(context) }
    val customWallpaperPath by settingsDataStore.customWallpaperPath.collectAsState(initial = null)

    val coroutineScope = rememberCoroutineScope()
    
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                Log.d(TAG, "Wallpaper changed, reloading…")
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

    // Reload wallpaper whenever version or custom path changes
    LaunchedEffect(wallpaperVersion, customWallpaperPath) {
        val wallpapers = withContext(Dispatchers.IO) {
            try {
                val bitmap = if (customWallpaperPath != null) {
                    val file = java.io.File(customWallpaperPath!!)
                    if (file.exists()) {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        val wallpaperManager = WallpaperManager.getInstance(context)
                        loadWallpaperBitmap(wallpaperManager, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
                    }
                } else {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    loadWallpaperBitmap(wallpaperManager, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
                }

                bitmap?.let { b ->
                    val blurred = try {
                        val scaleDown = Bitmap.createScaledBitmap(
                            b,
                            (b.width / 6).coerceAtLeast(1),
                            (b.height / 6).coerceAtLeast(1),
                            true
                        )
                        SimpleBlur.blur(scaleDown, radius = 3, iterations = 2)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        b
                    }
                    b to blurred
                }
            } catch (e: Exception) {
                null
            }
        }

        wallpapers?.let { (wallpaper, blurredWallpaper) ->
            wallpaperBitmap = wallpaper
            blurredWallpaperBitmap = blurredWallpaper
        }
    }

    var backgroundCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { backgroundSize = it }
            .onGloballyPositioned { backgroundCoordinates = it }
    ) {
        Crossfade(
            targetState = mode,
            modifier = Modifier
                .fillMaxSize(),
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
        CompositionLocalProvider(
            LocalWallpaperBitmap provides wallpaperBitmap,
            LocalBlurredWallpaperBitmap provides blurredWallpaperBitmap,
            LocalBackgroundSize provides backgroundSize,
            com.serkantkn.zunelauncher.ui.theme.LocalBackgroundCoordinates provides backgroundCoordinates,
            LocalHazeState provides hazeState
        ) {
            content()
        }
    }
}

object ZuneWallpaperManager {
    var lastInternalWallpaperChangeTime: Long = 0L
}

private fun loadWallpaperBitmap(
    wallpaperManager: WallpaperManager,
    fallbackWidth: Int,
    fallbackHeight: Int
): Bitmap? {
    try {
        wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor)?.let { bitmap ->
                return bitmap
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "getWallpaperFile failed; trying drawable fallback", e)
    }

    val drawable = runCatching { wallpaperManager.drawable }.getOrNull()
        ?: runCatching { wallpaperManager.peekDrawable() }.getOrNull()
        ?: return null

    val width = drawable.intrinsicWidth.takeIf { it > 1 }
        ?: wallpaperManager.desiredMinimumWidth.takeIf { it > 1 }
        ?: fallbackWidth
    val height = drawable.intrinsicHeight.takeIf { it > 1 }
        ?: wallpaperManager.desiredMinimumHeight.takeIf { it > 1 }
        ?: fallbackHeight

    return Bitmap.createBitmap(
        width.coerceAtLeast(1),
        height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    ).also { bitmap ->
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
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
        val overlayColor = Color.Transparent

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dark/Light overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor)
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
            .background(if (isDark) ZuneColors.Black else ZuneColors.LightBackground)
    )
}
