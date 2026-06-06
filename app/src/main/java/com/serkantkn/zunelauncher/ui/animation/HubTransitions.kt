package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

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

/**
 * Applies a 3D hinge rotation around the LEFT edge of the composable.
 *
 * @param rotationDegrees Y-axis rotation angle.
 *   Negative values rotate the surface backward (away from viewer).
 *   Positive values rotate the surface forward (toward viewer).
 * @param density Screen density for camera distance scaling.
 * @param alpha Content opacity.
 */
fun Modifier.hingeRotation(
    rotationDegrees: Float,
    density: Float,
    alpha: Float = 1f
): Modifier = this.graphicsLayer {
    rotationY = rotationDegrees
    transformOrigin = TransformOrigin(0f, 0.5f)
    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density
    this.alpha = alpha
}
