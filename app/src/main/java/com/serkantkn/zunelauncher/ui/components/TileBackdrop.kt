package com.serkantkn.zunelauncher.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.serkantkn.zunelauncher.util.SimpleBlur
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The wallpaper as a see-through tile shows it when the tiles are frosted: blurred, as glass
 * would, under whatever tint the tile itself carries.
 *
 * The background draws the wallpaper; it also bakes one small, blurred copy of the part of it on
 * screen, and every tile draws its own piece of that copy behind its fill - the piece that lies
 * under it, found from where the tile is on the screen. Nothing is blurred while drawing, so a
 * board of tiles costs one picture drawn per tile, on any phone.
 */
@Stable
class TileBackdrop internal constructor() {
    /** The blurred copy of the screen's wallpaper, at [scale] of its size; null when off. */
    internal var image by mutableStateOf<ImageBitmap?>(null)
    /** Where the background is on the screen, and how big. */
    internal var hostOrigin by mutableStateOf(Offset.Zero)
    internal var hostSize by mutableStateOf(IntSize.Zero)
    /** How far the wallpaper has drifted up with the list, and how much it is magnified for it. */
    internal var drift: () -> Float = { 0f }
    internal var driftZoom: () -> Float = { 1f }
    /** The shade laid over the wallpaper, drawn again over each tile's piece of it. */
    internal var shade: (DrawScope.(origin: Offset, host: Size) -> Unit)? = null
}

/** The frosted wallpaper behind the tiles, when the background under them offers one. */
val LocalTileBackdrop = staticCompositionLocalOf<TileBackdrop?> { null }

/**
 * A backdrop for the tiles over a background, baked again whenever the wallpaper, the size of
 * the background or the strength of the blur changes. [level] runs 0-100; 0 turns it off.
 */
@Composable
internal fun rememberTileBackdrop(wallpaper: Bitmap?, level: Int): TileBackdrop {
    val backdrop = remember { TileBackdrop() }
    val size = backdrop.hostSize
    LaunchedEffect(wallpaper, level, size) {
        if (wallpaper == null || level <= 0 || size.width <= 0 || size.height <= 0) {
            backdrop.image = null
            return@LaunchedEffect
        }
        backdrop.image = withContext(Dispatchers.Default) { bake(wallpaper, size, level) }
    }
    return backdrop
}

/** Marks the background the backdrop is the wallpaper of, so tiles can find their piece. */
internal fun Modifier.tileBackdropHost(backdrop: TileBackdrop): Modifier = onGloballyPositioned { coordinates ->
    backdrop.hostOrigin = coordinates.positionInWindow()
    backdrop.hostSize = coordinates.size
}

/**
 * Draws, behind a tile, the frosted piece of the wallpaper under it. Nothing is drawn when the
 * blur is off or the tile is not over a background that offers it.
 */
fun Modifier.frostedBackdrop(): Modifier = composed {
    val backdrop = LocalTileBackdrop.current ?: return@composed Modifier
    var origin by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind {
            val image = backdrop.image ?: return@drawBehind
            val host = backdrop.hostSize
            if (host.width <= 0 || host.height <= 0) return@drawBehind
            // The tile's corner, in the background's own space.
            val at = origin - backdrop.hostOrigin
            // The wallpaper is drawn magnified about its middle and lifted with the list; its
            // copy is laid out the same way.
            val zoom = backdrop.driftZoom()
            val lift = backdrop.drift()
            val width = host.width * zoom
            val height = host.height * zoom
            val left = (host.width - width) / 2f - at.x
            val top = (host.height - height) / 2f - lift - at.y
            drawImage(
                image = image,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                filterQuality = FilterQuality.Low
            )
            backdrop.shade?.invoke(this, -at, Size(host.width.toFloat(), host.height.toFloat()))
        }
}

/**
 * The part of the wallpaper the background shows - cropped to fill it as the picture is - at a
 * fraction of its size, blurred. Small on purpose: it is drawn stretched, and stretching a
 * blurred picture only blurs it further.
 */
private fun bake(wallpaper: Bitmap, host: IntSize, level: Int): ImageBitmap? = runCatching {
    val width = (host.width / BAKE_DIVISOR).coerceAtLeast(1)
    val height = (host.height / BAKE_DIVISOR).coerceAtLeast(1)
    val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // ContentScale.Crop: as large as it takes to fill, centred.
    val scale = maxOf(width / wallpaper.width.toFloat(), height / wallpaper.height.toFloat())
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate((width - wallpaper.width * scale) / 2f, (height - wallpaper.height * scale) / 2f)
    }
    Canvas(small).drawBitmap(wallpaper, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    SimpleBlur.blur(small, radius = blurRadius(level), iterations = 3).asImageBitmap()
}.onFailure { ZuneLog.w("TileBackdrop", "the wallpaper could not be frosted", it) }.getOrNull()

/**
 * The blur's radius on the small copy for a strength of 0-100, up to [MAX_RADIUS] pixels. It
 * grows slowly at first: the low end softens the wallpaper, the high end turns it to frost.
 */
internal fun blurRadius(level: Int): Int {
    val strength = level.coerceIn(0, 100) / 100f
    return (MAX_RADIUS * strength * kotlin.math.sqrt(strength)).roundToInt().coerceAtLeast(1)
}

/** The small copy is a quarter of the screen across: enough to keep what a light blur leaves. */
private const val BAKE_DIVISOR = 4
/** At full strength the blur reaches sixteen small pixels - a sixth of a phone's width. */
private const val MAX_RADIUS = 16

/** The shade the Start screen lays over its wallpaper, for a backdrop to draw again. */
internal fun startShade(isDark: Boolean): DrawScope.(Offset, Size) -> Unit = { origin, host ->
    drawRect(
        brush = Brush.linearGradient(
            colors = startShadeColors(isDark),
            start = origin,
            end = origin + Offset(host.width, host.height)
        )
    )
}

internal fun startShadeColors(isDark: Boolean): List<Color> = if (isDark) listOf(
    Color(0xFF0A0A0A).copy(alpha = 0.85f),
    Color(0xFF202020).copy(alpha = 0.85f),
    Color.White.copy(alpha = 0.12f * 0.85f),
    Color(0xFF0A0A0A).copy(alpha = 0.85f)
) else listOf(
    Color(0xFFF5F5F5).copy(alpha = 0.85f),
    Color(0xFFFFFFFF).copy(alpha = 0.85f),
    Color.Black.copy(alpha = 0.08f * 0.85f),
    Color(0xFFF5F5F5).copy(alpha = 0.85f)
)
