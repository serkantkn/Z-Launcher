package com.serkantkn.zeta.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zeta.util.TileIconFace
import kotlin.math.roundToInt

/**
 * Windows 8's Start motion, for the tablet board; the phone keeps Windows Phone's turnstile.
 *
 * It runs on the same envelope as the turnstile, so Start drives both alike:
 *  - 0 → 1, coming in: the tiles slide in from the right and fade up, the left of the board a
 *    beat before the right, as Start did on Windows 8 every time it came back.
 *  - 1 → 2, launching: the chosen tile flips edge-on about its own upright axis, the others
 *    fall back a little and fade, and [Win8LaunchSplash] carries on the flip — the tile's colour
 *    and face, turning the rest of the way round while it grows from the tile to the screen.
 */
object Win8Motion {
    /** How long the launch takes, from the tap to the screen being covered. */
    const val LAUNCH_MILLIS = 560

    /** Where the tiles were, in root coordinates, so a launch can grow out of the chosen one. */
    class Bounds {
        val byKey = HashMap<String, Rect>()
    }

    // ── Coming in ──
    internal const val ENTER_SPREAD = 0.24f      // the rightmost tile starts this much later
    internal const val ENTER_DURATION = 0.40f    // each tile's own slide
    internal val ENTER_OFFSET = 140.dp
    internal val EnterEasing = CubicBezierEasing(0.10f, 0.90f, 0.20f, 1.00f)

    // ── Launching ──
    internal const val FLIP_END = 0.32f          // the chosen tile is edge-on here
    internal const val FALL_BACK_END = 0.55f     // the rest are gone here
    internal val FlipInEasing = CubicBezierEasing(0.55f, 0.00f, 1.00f, 0.60f)
    internal val GrowEasing = CubicBezierEasing(0.10f, 0.70f, 0.25f, 1.00f)
}

/**
 * A tile on the tablet board moving the Windows 8 way; see [Win8Motion]. [key] is the launch key
 * the tile is known by, under which its place is kept in [bounds].
 */
fun Modifier.win8TileMotion(
    progress: () -> Float,
    isClicked: Boolean,
    key: String,
    bounds: Win8Motion.Bounds
): Modifier = composed {
    if (!LocalAnimationsEnabled.current) return@composed this
    val density = LocalDensity.current.density
    val screenWidthPx = (LocalConfiguration.current.screenWidthDp * density).coerceAtLeast(1f)
    val offsetPx = Win8Motion.ENTER_OFFSET.value * density
    // Plain fields, read in the draw phase: a scroll must not recompose the board.
    val placement = remember { FloatArray(1) }

    this
        .onGloballyPositioned { coords ->
            val rect = coords.boundsInRoot()
            bounds.byKey[key] = rect
            placement[0] = rect.left
        }
        .graphicsLayer {
            val value = progress()
            if (value == 1f) return@graphicsLayer
            if (value < 1f) {
                val xFrac = (placement[0] / screenWidthPx).coerceIn(0f, 1f)
                val start = xFrac * Win8Motion.ENTER_SPREAD
                val t = ((value - start) / Win8Motion.ENTER_DURATION).coerceIn(0f, 1f)
                val eased = Win8Motion.EnterEasing.transform(t)
                translationX = (1f - eased) * offsetPx * (1f + xFrac * 0.5f)
                alpha = eased
            } else {
                val x = (value - 1f).coerceIn(0f, 1f)
                if (isClicked) {
                    val t = (x / Win8Motion.FLIP_END).coerceIn(0f, 1f)
                    rotationY = 90f * Win8Motion.FlipInEasing.transform(t)
                    cameraDistance = 14f * density
                    alpha = if (t >= 1f) 0f else 1f
                } else {
                    val t = (x / Win8Motion.FALL_BACK_END).coerceIn(0f, 1f)
                    alpha = 1f - t
                    scaleX = 1f - 0.08f * t
                    scaleY = 1f - 0.08f * t
                }
            }
        }
}

/** What the splash shows: the tile's colour and the face it wore. */
class Win8Splash(
    val from: Rect,
    val color: Color,
    val glyph: ImageVector? = null,
    val face: TileIconFace? = null
)

/**
 * The splash a launch turns into: it picks up the flip where the tile left off — from edge-on
 * back round to flat — while it grows from the tile's place to the whole screen. Drawn over the
 * board; the hub or the app then comes up over it.
 */
@Composable
fun Win8LaunchSplash(splash: Win8Splash, progress: () -> Float) {
    val density = LocalDensity.current.density
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val full = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                val s = stageOf(progress())
                val e = Win8Motion.GrowEasing.transform(s)
                val left = lerp(splash.from.left, full.left, e)
                val top = lerp(splash.from.top, full.top, e)
                val right = lerp(splash.from.right, full.right, e)
                val bottom = lerp(splash.from.bottom, full.bottom, e)
                val width = (right - left).roundToInt().coerceAtLeast(1)
                val height = (bottom - top).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(left.roundToInt(), top.roundToInt())
                }
            }
            .graphicsLayer {
                val s = stageOf(progress())
                alpha = if (s <= 0f) 0f else 1f
                // The second half of the turn: edge-on at the start, flat by the time it has
                // grown most of the way.
                val turn = (s / 0.6f).coerceIn(0f, 1f)
                rotationY = -90f * (1f - Win8Motion.GrowEasing.transform(turn))
                cameraDistance = 14f * density
            }
            .background(splash.color),
        contentAlignment = Alignment.Center
    ) {
        val size = 96.dp
        when {
            splash.glyph != null -> Icon(splash.glyph, contentDescription = null, tint = Color.White, modifier = Modifier.size(size))
            splash.face is TileIconFace.Glyph -> Image(
                bitmap = splash.face.bitmap,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(size)
            )
            splash.face is TileIconFace.Picture -> Image(splash.face.bitmap, contentDescription = null, modifier = Modifier.size(size))
            splash.face is TileIconFace.Letter -> Text(splash.face.text, color = Color.White, fontSize = 64.sp)
        }
    }
}

/** 0 until the tile has turned edge-on, then 0 → 1 as the splash turns flat and grows. */
private fun stageOf(value: Float): Float {
    val x = (value - 1f).coerceIn(0f, 1f)
    return ((x - Win8Motion.FLIP_END) / (1f - Win8Motion.FLIP_END)).coerceIn(0f, 1f)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * The launch envelope's "everything else" for screens other than the tile board: during 1 → 2
 * the thing falls back a little and fades, as the board's other tiles do.
 */
fun Modifier.win8FallBack(progress: () -> Float): Modifier = graphicsLayer {
    val x = (progress() - 1f).coerceIn(0f, 1f)
    if (x == 0f) return@graphicsLayer
    val t = (x / Win8Motion.FALL_BACK_END).coerceIn(0f, 1f)
    alpha = 1f - t
    scaleX = 1f - 0.08f * t
    scaleY = 1f - 0.08f * t
}

/** The launch envelope's chosen thing: it flips edge-on about its upright axis, as a tile does. */
fun Modifier.win8FlipAway(progress: () -> Float): Modifier = graphicsLayer {
    val x = (progress() - 1f).coerceIn(0f, 1f)
    if (x == 0f) return@graphicsLayer
    val t = (x / Win8Motion.FLIP_END).coerceIn(0f, 1f)
    rotationY = 90f * Win8Motion.FlipInEasing.transform(t)
    cameraDistance = 14f * density
    alpha = if (t >= 1f) 0f else 1f
}
