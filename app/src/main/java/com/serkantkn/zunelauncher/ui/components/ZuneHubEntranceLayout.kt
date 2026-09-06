package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Default entrance curve: ultra slow-motion decelerating landing onto the left screen edge. */
val ZuneHubEntranceEasing: Easing = CubicBezierEasing(0.05f, 0.95f, 0.08f, 1.0f)

/** Default entrance duration in milliseconds. */
const val ZuneHubEntranceDurationMillis = 780

/** Default delay-slide duration of the bottom application bar, in milliseconds. */
const val ZuneHubBottomBarDurationMillis = 350

/**
 * Progress of a hub's signature entrance: [progress] runs 0 -> 1 while the hub swings in, then
 * [bottomBarSlide] runs 1 -> 0 while the bottom application bar slides up. Created with
 * [rememberZuneHubEntranceState]; the animation is started where the state is remembered, so a
 * hub whose layout is conditionally composed (e.g. hidden behind a detail hinge) still plays its
 * entrance exactly once, at screen start.
 */
@Stable
class ZuneHubEntranceState internal constructor(
    internal val entranceAnim: Animatable<Float, AnimationVector1D>,
    internal val bottomBarSlideAnim: Animatable<Float, AnimationVector1D>
) {
    /** Entrance progress, 0f (off-screen left) .. 1f (latched flat on the left edge). */
    val progress: Float get() = entranceAnim.value

    /** Bottom bar slide fraction, 1f (hidden below) .. 0f (in place). */
    val bottomBarSlide: Float get() = bottomBarSlideAnim.value
}

/**
 * Remembers a [ZuneHubEntranceState] and plays the entrance once at first composition:
 * entrance tween ([entranceDurationMillis], [entranceEasing]) followed by the bottom bar tween
 * ([bottomBarDurationMillis], [bottomBarEasing]).
 */
@Composable
fun rememberZuneHubEntranceState(
    entranceDurationMillis: Int = ZuneHubEntranceDurationMillis,
    entranceEasing: Easing = ZuneHubEntranceEasing,
    bottomBarDurationMillis: Int = ZuneHubBottomBarDurationMillis,
    bottomBarEasing: Easing = FastOutSlowInEasing
): ZuneHubEntranceState {
    val state = remember {
        ZuneHubEntranceState(
            entranceAnim = Animatable(0f),
            bottomBarSlideAnim = Animatable(1f)
        )
    }
    LaunchedEffect(Unit) {
        state.entranceAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = entranceDurationMillis,
                easing = entranceEasing
            )
        )
        state.bottomBarSlideAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = bottomBarDurationMillis,
                easing = bottomBarEasing
            )
        )
    }
    return state
}

/**
 * Encapsulates the authentic Windows Phone signature 3D entrance animation:
 * - Entry starts from the left (-140dp), rotated -48 degrees and scaled to 82%, hinged on the
 *   screen-left edge (TransformOrigin(0f, 0.5f)) like the Start turnstile
 * - Ultra slow-motion decelerating landing to latch flat onto the left screen edge
 * - Provides a delayed bottomBarModifier to slide up bottom application bars when entrance completes
 * - Optimized with CompositingStrategy.Offscreen for 100% GPU hardware acceleration on 120Hz displays
 *
 * @param state Entrance progress; defaults to a state that plays the standard curve at first
 * composition. Hoist it with [rememberZuneHubEntranceState] to customise timing or to keep the
 * entrance running while the layout itself is temporarily out of composition.
 * @param progressScale Extra multiplier applied to the entrance progress on every frame, e.g. a
 * hub hinge value that folds the whole hub back out when a detail screen opens.
 * @param offscreenCompositing Force offscreen compositing while the entrance is in flight.
 */
@Composable
fun ZuneHubEntranceLayout(
    modifier: Modifier = Modifier,
    state: ZuneHubEntranceState = rememberZuneHubEntranceState(),
    progressScale: () -> Float = { 1f },
    offscreenCompositing: Boolean = true,
    content: @Composable (bottomBarModifier: Modifier) -> Unit
) {
    val density = LocalDensity.current

    val leftStartPx = with(density) { (-140).dp.toPx() }
    val bottomBarOffsetPx = with(density) { 90.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = state.entranceAnim.value * progressScale()
                val inv = 1f - p
                // Straight approach from the left/front, never drifting past x = 0
                rotationY = -48f * inv * inv
                translationX = leftStartPx * inv * inv
                scaleX = 0.82f + (0.18f * p)
                scaleY = 0.82f + (0.18f * p)

                // Hinged on the screen-left edge, same axis as the Start turnstile
                transformOrigin = TransformOrigin(0f, 0.5f)
                cameraDistance = 32f * density.density
                alpha = (p * 3f).coerceIn(0f, 1f)

                // Force GPU RenderNode Offscreen compositing during active entrance for 120Hz liquid smoothness
                if (offscreenCompositing) {
                    compositingStrategy = if (p < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }
            }
    ) {
        val bottomBarModifier = Modifier.graphicsLayer {
            translationY = state.bottomBarSlideAnim.value * bottomBarOffsetPx
            alpha = 1f - state.bottomBarSlideAnim.value
        }
        content(bottomBarModifier)
    }
}
