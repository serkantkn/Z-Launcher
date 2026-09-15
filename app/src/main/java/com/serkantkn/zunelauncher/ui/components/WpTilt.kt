package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled

/**
 * The Windows Phone tilt.
 *
 * Nothing on Windows Phone lit up or rippled when it was touched; it leaned. Press the left edge
 * of a row and the row pivots away on that side, as though it were a physical panel hinged behind
 * the glass. It is the single most recognisable thing about the platform's feel, and it is worth
 * having in one place rather than written out again beside every list.
 *
 * [interactionSource] must be the one given to the clickable, so the lean follows the real touch.
 */
fun Modifier.wpTilt(
    interactionSource: InteractionSource,
    maxDegrees: Float = MAX_TILT_DEGREES,
    pressedScale: Float = PRESSED_SCALE
): Modifier = composed {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val point = rememberPressPoint(interactionSource)
    val lean = rememberWpTiltAngles(point, size, maxDegrees)
    val scale by animateFloatAsState(
        targetValue = if (point != null) pressedScale else 1f,
        animationSpec = wpTiltSpring(),
        label = "wp_tilt_scale"
    )

    this
        .onSizeChanged { size = it }
        .graphicsLayer {
            rotationX = lean.rotationX
            rotationY = lean.rotationY
            scaleX = scale
            scaleY = scale
            cameraDistance = CAMERA_DISTANCE * density
            transformOrigin = TransformOrigin.Center
        }
}

/** How far a surface is leaning, in degrees about each axis. */
@Immutable
data class WpTiltAngles(val rotationX: Float, val rotationY: Float) {
    companion object {
        val Flat = WpTiltAngles(0f, 0f)
    }
}

/**
 * Where the finger is, or null when there is none.
 *
 * Every surface that leans needs this and none of them should write it out again: a press carries
 * the point it landed on, and a release or a cancel takes it away.
 */
@Composable
fun rememberPressPoint(interactionSource: InteractionSource): Offset? {
    var pressPoint by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            pressPoint = when (interaction) {
                is PressInteraction.Press -> interaction.pressPosition
                is PressInteraction.Release, is PressInteraction.Cancel -> null
                else -> pressPoint
            }
        }
    }
    return pressPoint
}

/**
 * The lean itself, sprung.
 *
 * Kept apart from [wpTilt] because not everything that leans can use a plain modifier: a Start
 * tile adds the lean to the rotation it is already flipping by, and a calculator key drives its
 * own press handling so the key fires the instant it is touched. Both want this arithmetic and
 * these springs, and neither wants a second graphics layer of its own.
 *
 * [maxDegrees] is the angle at the very edge of the surface, doubled from the centre outwards, so
 * a press halfway to the edge leans half as far.
 */
@Composable
fun rememberWpTiltAngles(
    pressPoint: Offset?,
    size: IntSize,
    maxDegrees: Float = MAX_TILT_DEGREES
): WpTiltAngles {
    // Nothing leans when the launcher has been asked to hold still.
    if (!LocalAnimationsEnabled.current) return WpTiltAngles.Flat
    val spec = wpTiltSpring()
    val rotationX by animateFloatAsState(
        targetValue = if (pressPoint != null) -fractionFromCentre(pressPoint.y, size.height) * maxDegrees else 0f,
        animationSpec = spec,
        label = "wp_tilt_x"
    )
    val rotationY by animateFloatAsState(
        targetValue = if (pressPoint != null) fractionFromCentre(pressPoint.x, size.width) * maxDegrees else 0f,
        animationSpec = spec,
        label = "wp_tilt_y"
    )
    return WpTiltAngles(rotationX, rotationY)
}

/** The one spring the whole platform leans and settles on. */
@Composable
fun wpTiltSpring(): SpringSpec<Float> =
    remember { spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow) }

/** Where the touch landed, as -0.5 at one edge through 0 in the middle to 0.5 at the other. */
private fun fractionFromCentre(position: Float, extent: Int): Float {
    if (extent <= 0) return 0f
    return ((position / extent) - 0.5f).coerceIn(-0.5f, 0.5f)
}

private const val MAX_TILT_DEGREES = 10f
private const val PRESSED_SCALE = 0.975f
private const val CAMERA_DISTANCE = 12f
