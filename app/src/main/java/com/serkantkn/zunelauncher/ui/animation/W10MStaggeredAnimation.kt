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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
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
/** Where a tile is on screen. Deliberately not snapshot state: see [w10mStaggeredAnimation]. */
private class TilePlacement {
    var x = 0f
    var y = -1f
    var width = 1f
}

private const val PHASE_ENTERING = 0
private const val PHASE_IDLE = 1
private const val PHASE_LEAVING = 2

private fun phaseOf(progress: Float): Int = when {
    progress < 1f -> PHASE_ENTERING
    progress > 1f -> PHASE_LEAVING
    else -> PHASE_IDLE
}

// Entrance easing: smooth deceleration into resting position
private val EntranceEasing = CubicBezierEasing(0.20f, 0.80f, 0.20f, 1.00f)
// Exit easing (WP ExponentialEase, EaseIn): barely moves at first, then snaps away
private val FeatherEasing = CubicBezierEasing(0.55f, 0.00f, 1.00f, 0.45f)
// Selected tile: a fuller, more deliberate turn (ease-in-out) so it reads as "chosen"
private val SelectedExitEasing = CubicBezierEasing(0.60f, 0.00f, 0.30f, 1.00f)

// ── Exit timeline (fractions of the 1f → 2f envelope) ──
// Feather: start offset comes from the tile's real position; top-left leaves first.
private const val FEATHER_SPAN = 0.42f        // the last tile starts this late after the first
private const val FEATHER_DURATION = 0.28f    // each tile's own turn (ease-in: visible in its last ~40%)
private const val SELECTED_HOLD_END = 0.66f   // the chosen tile starts turning as the last others vanish

fun Modifier.w10mStaggeredAnimation(
    progress: () -> Float,
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

    // Where this tile is, kept outside the snapshot system on purpose. It used to be three
    // mutableFloatStateOf values written from onGloballyPositioned, which meant every tile
    // recomposed on every frame of every scroll — the position only matters to the turn itself,
    // and the turn reads it in the draw phase, where reading a plain field is free.
    val placement = remember { TilePlacement() }

    // The turn is read in the draw phase too, so the whole board no longer recomposes once per
    // frame for the length of the animation. Only the coarse phase — coming in, sitting still,
    // going out — is composition state, because the stacking order is decided while laying out.
    var phase by remember { mutableIntStateOf(phaseOf(progress())) }
    LaunchedEffect(Unit) {
        snapshotFlow { phaseOf(progress()) }.collect { phase = it }
    }

    val effectiveX = if (placement.x > 0f) placement.x else (index * 60f * density)

    // Dynamic 3D depth layering:
    // Entrance: right tiles on top (effectiveX)
    // Exit: clicked tile topmost (10000f) so nothing sweeps over it, then left-to-right hierarchy
    val dynamicZIndex = when (phase) {
        PHASE_LEAVING -> if (isClicked) 10000f else (5000f - effectiveX)
        PHASE_ENTERING -> effectiveX
        else -> 0f
    }

    this
        .onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            placement.x = pos.x
            placement.y = pos.y
            placement.width = coords.size.width.toFloat().coerceAtLeast(1f)
        }
        .zIndex(dynamicZIndex)
        .graphicsLayer {
            val value = progress()
            // Idle: zero-cost pass-through
            if (value == 1f) return@graphicsLayer

            // ── Common Pivot: Exact Screen Left Edge (x = 0) for BOTH Entrance & Exit ──
            val pivotX = -(placement.x / placement.width)
            transformOrigin = TransformOrigin(pivotX, 0.5f)
            cameraDistance = 12f * density
            alpha = 1f
            clip = false

            if (value < 1f) {
                // ═══ TURNSTILE IN: -90° → 0° (UNTOUCHED ORIGINAL) ═══
                val safeIndex = index.coerceAtMost(15)
                val staggerFraction = 0.035f
                val tileAnimDuration = 0.52f

                val itemStart = safeIndex * staggerFraction
                val itemEnd = (itemStart + tileAnimDuration).coerceAtMost(1f)
                val rawT = if (itemEnd > itemStart) {
                    ((value - itemStart) / (itemEnd - itemStart)).coerceIn(0f, 1f)
                } else 1f
                val eased = EntranceEasing.transform(rawT)

                rotationY = -90f * (1f - eased)
                translationX = 0f
            } else {
                // ═══ TURNSTILE FEATHER OUT: 0° → +90° around the EXACT same screen-left axis ═══
                // Full 90° rotation makes tiles stand perpendicular in Z-axis (edge-on) and
                // completely invisible.
                val exitProgress = (value - 1f).coerceIn(0f, 1f)
                val exitEased = if (isClicked) {
                    val t = ((exitProgress - SELECTED_HOLD_END) / (1f - SELECTED_HOLD_END))
                        .coerceIn(0f, 1f)
                    SelectedExitEasing.transform(t)
                } else {
                    val order = when {
                        exitOrder >= 0f -> exitOrder.coerceIn(0f, 1f)
                        placement.y >= 0f -> {
                            // No explicit order: sweep by real position, top-left first
                            val yFrac = (placement.y / screenHeightPx).coerceIn(0f, 1f)
                            val xFrac = (placement.x / screenWidthPx).coerceIn(0f, 1f)
                            (yFrac * 0.9f + xFrac * 0.1f).coerceIn(0f, 1f)
                        }
                        else -> index.coerceAtMost(15) / 15f
                    }
                    val itemStart = order * FEATHER_SPAN
                    val t = ((exitProgress - itemStart) / FEATHER_DURATION).coerceIn(0f, 1f)
                    FeatherEasing.transform(t)
                }
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
