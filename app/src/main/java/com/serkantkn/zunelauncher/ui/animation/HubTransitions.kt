package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable

/**
 * Hinge-style animation for hub transitions.
 *
 * The LEFT edge of the screen acts as a hinge (menteşe):
 * - Opening: current screen hinges BACKWARD (away from viewer),
 *            new hub screen hinges FORWARD (toward viewer)
 * - Closing: the exact reverse
 *
 * Mimics a physical door/book-page turning around a left-edge pivot.
 */
/**
 * The spec a page hinge should swing on right now.
 *
 * Read it in composition and hand it to the Animatable: a hinge is started from a coroutine, which
 * cannot read a composition local of its own, and a door that still swings for six tenths of a
 * second after motion was switched off reads as the setting having done nothing.
 */
@Composable
fun rememberHingeSpec(): androidx.compose.animation.core.AnimationSpec<Float> {
    val enabled = com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled.current
    return androidx.compose.runtime.remember(enabled) {
        androidx.compose.animation.core.tween(
            durationMillis = if (enabled) HingeAnimation.DURATION_MS else 0,
            easing = HingeAnimation.EASING
        )
    }
}

object HingeAnimation {
    /** Total duration of the hinge animation in milliseconds */
    const val DURATION_MS = 600

    /** Easing curve — fast start, smooth deceleration */
    val EASING = FastOutSlowInEasing

    /**
     * Camera distance multiplier for 3D perspective.
     * Higher values reduce distortion during rotation.
     */
    const val CAMERA_DISTANCE_MULTIPLIER = 12f

    /** Maximum rotation angle (90° = fully edge-on / invisible) */
    const val MAX_ROTATION_DEGREES = 90f
}

