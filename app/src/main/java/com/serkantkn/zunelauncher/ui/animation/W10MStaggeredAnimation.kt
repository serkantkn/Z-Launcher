package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled

/**
 * Authentic Windows Phone Turnstile transition.
 *
 * Both Entrance and Exit share the EXACT same screen-left-edge axis (x = 0):
 *
 * • Entrance (-90° → 0°):
 *   Entire screen swings in smoothly from in front of the screen (+Z plane)
 *   around the screen's left edge axis in a top-to-bottom cascading wave.
 *   (UNTOUCHED ORIGINAL).
 *
 * • Exit (0° → +90°) — Windows Phone "turnstile feather out":
 *   Entire screen swings into the depth of the screen (-Z plane) around the
 *   exact same screen-left axis:
 *   1. Non-clicked tiles leave in a FEATHER ordered by their real on-screen position
 *      (top-left first, bottom-right last), each with WP's exponential ease-in, so the
 *      wave reads as a sweep regardless of how many tiles are visible.
 *   2. The clicked/selected tile stays exactly where it is while the others leave, then —
 *      only after the last of them is gone — turns out on its own, straight into the app launch.
 *   3. At the end, all tiles reach exactly 90° (perpendicular in Z-axis) so they are completely invisible.
 *   - Strictly NO fade (alpha = 1f).
 *
 * @param progress Global animation envelope:
 *                 0f → 1f (Turnstile In)
 *                 1f       (Settled / Idle)
 *                 1f → 2f  (Turnstile Out)
 * @param index    Tile index for cascading stagger delay.
 * @param isClicked True for the tile that was tapped (stays visible, exits LAST).
 */
fun Modifier.w10mStaggeredAnimation(
    progress: Float,
    index: Int,
    isClicked: Boolean = false,
    /** 0f..1f position of this tile in the feather order (0 = leaves first). Negative = derive from screen position. */
    exitOrder: Float = -1f
): Modifier = composed {
    // Motion switched off: the tiles are simply in place, neither turning in nor out.
    if (!LocalAnimationsEnabled.current) return@composed this
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val screenWidthPx = (configuration.screenWidthDp * density).coerceAtLeast(1f)
    val screenHeightPx = (configuration.screenHeightDp * density).coerceAtLeast(1f)
    // Track this tile's position (px) relative to the window's top-left corner
    var tileScreenX by remember { mutableFloatStateOf(0f) }
    var tileScreenY by remember { mutableFloatStateOf(-1f) }
    var tileWidthPx by remember { mutableFloatStateOf(1f) }

    val effectiveX = if (tileScreenX > 0f) tileScreenX else (index * 60f * density)

    // Dynamic 3D depth layering:
    // Entrance: right tiles on top (effectiveX)
    // Exit: clicked tile topmost (10000f) so nothing sweeps over it, then left-to-right hierarchy (5000f - effectiveX)
    val dynamicZIndex = when {
        progress > 1f -> if (isClicked) 10000f else (5000f - effectiveX)
        progress < 1f -> effectiveX
        else -> 0f
    }

    // Entrance easing: smooth deceleration into resting position
    val entranceEasing = CubicBezierEasing(0.20f, 0.80f, 0.20f, 1.00f)
    // Exit easing (WP ExponentialEase, EaseIn): barely moves at first, then snaps away
    val featherEasing = CubicBezierEasing(0.55f, 0.00f, 1.00f, 0.45f)
    // Selected tile: a fuller, more deliberate turn (ease-in-out) so it reads as "chosen"
    val selectedExitEasing = CubicBezierEasing(0.60f, 0.00f, 0.30f, 1.00f)

    // ── Exit timeline (fractions of the 1f → 2f envelope) ──
    // Feather: start offset comes from the tile's real position; top-left leaves first.
    val featherSpan = 0.42f           // the last tile starts this late after the first
    val featherDuration = 0.28f       // each tile's own turn (ease-in: visible in its last ~40%)
    val selectedHoldEnd = 0.66f       // the chosen tile starts turning as the last others vanish
    val selectedDuration = 1f - selectedHoldEnd

    val exitProgress = if (progress > 1f) (progress - 1f).coerceIn(0f, 1f) else 0f

    // 0f..1f eased turn amount for THIS tile during exit
    val exitEased: Float
    // Extra scale for the selected tile (lift while waiting, grow while leaving)
    val selectedScale: Float
    if (exitProgress > 0f) {
        if (isClicked) {
            val t = ((exitProgress - selectedHoldEnd) / selectedDuration).coerceIn(0f, 1f)
            exitEased = selectedExitEasing.transform(t)
            // Stays exactly in place while the others leave (no scale: the shared screen-left
            // pivot would push it sideways), then turns away like the rest
            selectedScale = 1f
        } else {
            val order = when {
                exitOrder >= 0f -> exitOrder.coerceIn(0f, 1f)
                tileScreenY >= 0f -> {
                    // No explicit order: sweep by real position, top-left first
                    val yFrac = (tileScreenY / screenHeightPx).coerceIn(0f, 1f)
                    val xFrac = (tileScreenX / screenWidthPx).coerceIn(0f, 1f)
                    (yFrac * 0.9f + xFrac * 0.1f).coerceIn(0f, 1f)
                }
                else -> index.coerceAtMost(15) / 15f
            }
            val itemStart = order * featherSpan
            val t = ((exitProgress - itemStart) / featherDuration).coerceIn(0f, 1f)
            exitEased = featherEasing.transform(t)
            selectedScale = 1f
        }
    } else {
        exitEased = 0f
        selectedScale = 1f
    }

    this
        .onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            tileScreenX = pos.x
            tileScreenY = pos.y
            tileWidthPx = coords.size.width.toFloat().coerceAtLeast(1f)
        }
        .zIndex(dynamicZIndex)
        .graphicsLayer {
            // Idle: zero-cost pass-through
            if (progress == 1f) return@graphicsLayer

            // ── Common Pivot: Exact Screen Left Edge (x = 0) for BOTH Entrance & Exit ──
            val pivotX = -(tileScreenX / tileWidthPx)
            transformOrigin = TransformOrigin(pivotX, 0.5f)
            cameraDistance = 12f * density
            alpha = 1f
            clip = false

            if (progress < 1f) {
                // ═══ TURNSTILE IN: -90° → 0° (UNTOUCHED ORIGINAL) ═══
                val safeIndex = index.coerceAtMost(15)
                val staggerFraction = 0.035f
                val tileAnimDuration = 0.52f

                val itemStart = safeIndex * staggerFraction
                val itemEnd = (itemStart + tileAnimDuration).coerceAtMost(1f)
                val rawT = if (itemEnd > itemStart) {
                    ((progress - itemStart) / (itemEnd - itemStart)).coerceIn(0f, 1f)
                } else 1f
                val eased = entranceEasing.transform(rawT)

                rotationY = -90f * (1f - eased)
                translationX = 0f
            } else {
                // ═══ TURNSTILE FEATHER OUT: 0° → +90° around the EXACT same screen-left axis ═══
                // Full 90° rotation makes tiles stand perpendicular in Z-axis (edge-on) and completely invisible
                rotationY = 90f * exitEased
                translationX = 0f
            }
        }
}

/**
 * Subtle asymmetrical floating drift and wiggle animation for tiles in edit mode.
 * Smoothly oscillates and reverses (RepeatMode.Reverse) without any jumping or stuttering.
 */
@Composable
fun Modifier.w10mEditWiggle(
    isEditing: Boolean,
    isDragging: Boolean,
    index: Int
): Modifier {
    if (!isEditing || isDragging) return this

    val infiniteTransition = rememberInfiniteTransition(label = "edit_wiggle_$index")
    val duration = remember(index) { 1200 + (index % 5) * 160 }
    val wiggleFraction by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edit_wiggle_fraction_$index"
    )

    // Sign and factor variation for organic asymmetry based on tile index
    val rotationFactor = remember(index) { if (index % 2 == 0) 1.2f else -1.2f }
    val offsetXFactor = remember(index) { if ((index / 2) % 2 == 0) 1.8f else -1.8f }
    val offsetYFactor = remember(index) { if (index % 3 == 0) 2.2f else -2.2f }

    val rotation = wiggleFraction * rotationFactor
    val offsetX = wiggleFraction * offsetXFactor
    val offsetY = wiggleFraction * offsetYFactor

    return this.graphicsLayer {
        rotationZ = rotation
        translationX = offsetX * density
        translationY = offsetY * density
    }
}
