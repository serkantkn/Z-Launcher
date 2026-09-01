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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex

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
 * • Exit (0° → +90°):
 *   Entire screen swings into the depth of the screen (-Z plane) around the
 *   exact same screen-left axis:
 *   1. Non-clicked tiles exit in a clear top-to-bottom cascading wave (index 0 → 15)
 *      with an accelerating ease-in departure.
 *   2. The clicked/selected tile stays stationary and prominent in the foreground,
 *      then executes its turnstile exit last into the app launch.
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
    isClicked: Boolean = false
): Modifier = composed {
    val density = LocalDensity.current.density
    // Track this tile's horizontal position (px) relative to the window's left edge
    var tileScreenX by remember { mutableFloatStateOf(0f) }
    var tileWidthPx by remember { mutableFloatStateOf(1f) }

    val effectiveX = if (tileScreenX > 0f) tileScreenX else (index * 60f * density)

    // Dynamic 3D depth layering:
    // Entrance: right tiles on top (effectiveX)
    // Exit: clicked tile topmost (10000f), followed by left-to-right hierarchy (5000f - effectiveX)
    val dynamicZIndex = when {
        progress > 1f -> if (isClicked) 10000f else (5000f - effectiveX)
        progress < 1f -> effectiveX
        else -> 0f
    }

    // Entrance easing: smooth deceleration into resting position
    val entranceEasing = CubicBezierEasing(0.20f, 0.80f, 0.20f, 1.00f)
    // Exit easing: gentle start accelerating smoothly into departure (yavaşça hızlanma)
    val exitEasing = CubicBezierEasing(0.40f, 0.00f, 0.90f, 0.40f)

    val exitEased = if (progress > 1f) {
        val exitProgress = progress - 1f
        if (isClicked) {
            // Selected tile waits while top-to-bottom wave ripples across other tiles, then exits last
            val itemStart = 0.52f
            val itemEnd = 1.00f
            val rawT = if (exitProgress > itemStart) {
                ((exitProgress - itemStart) / (itemEnd - itemStart)).coerceIn(0f, 1f)
            } else 0f
            exitEasing.transform(rawT)
        } else {
            // Natural top-to-bottom cascade wave (identical order to entrance)
            val safeIndex = index.coerceAtMost(15)
            val staggerFraction = 0.032f
            val otherTileDuration = 0.48f

            val itemStart = safeIndex * staggerFraction
            val itemEnd = (itemStart + otherTileDuration).coerceAtMost(1f)
            val rawT = if (itemEnd > itemStart) {
                ((exitProgress - itemStart) / (itemEnd - itemStart)).coerceIn(0f, 1f)
            } else 1f
            exitEasing.transform(rawT)
        }
    } else 0f

    this
        .onGloballyPositioned { coords ->
            tileScreenX = coords.positionInWindow().x
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
                // ═══ TURNSTILE OUT: 0° → +90° around the EXACT same screen-left axis ═══
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
