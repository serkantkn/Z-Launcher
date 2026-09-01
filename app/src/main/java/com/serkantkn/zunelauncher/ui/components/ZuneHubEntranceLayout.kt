package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.CompositingStrategy

/**
 * Encapsulates the authentic Windows Phone signature 3D entrance animation:
 * - Entry starts from the left (-140dp)
 * - Arcs outward across 60% of the screen right (+80dp, TransformOrigin(0.6f, 0.5f))
 * - Ultra slow-motion decelerating landing to latch flat onto the left screen edge
 * - Provides a delayed bottomBarModifier to slide up bottom application bars when entrance completes
 * - Optimized with CompositingStrategy.Offscreen for 100% GPU hardware acceleration on 120Hz displays
 */
@Composable
fun ZuneHubEntranceLayout(
    modifier: Modifier = Modifier,
    content: @Composable (bottomBarModifier: Modifier) -> Unit
) {
    val density = LocalDensity.current
    val entranceAnim = remember { Animatable(0f) }
    val bottomBarSlideAnim = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        entranceAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 780,
                easing = CubicBezierEasing(0.05f, 0.95f, 0.08f, 1.0f)
            )
        )
        bottomBarSlideAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 350,
                easing = FastOutSlowInEasing
            )
        )
    }

    val leftStartPx = with(density) { (-140).dp.toPx() }
    val rightMaxPx = with(density) { 80.dp.toPx() }
    val bottomBarOffsetPx = with(density) { 90.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = entranceAnim.value
                val inv = 1f - p
                val currentX = leftStartPx * inv * inv + rightMaxPx * 3.5f * p * inv * inv

                rotationY = -48f * inv * inv
                translationX = currentX
                scaleX = 0.82f + (0.18f * p)
                scaleY = 0.82f + (0.18f * p)

                transformOrigin = TransformOrigin(0.6f, 0.5f)
                cameraDistance = 32f * density.density
                alpha = (p * 3f).coerceIn(0f, 1f)

                // Force GPU RenderNode Offscreen compositing during active entrance for 120Hz liquid smoothness
                compositingStrategy = if (p < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
    ) {
        val bottomBarModifier = Modifier.graphicsLayer {
            translationY = bottomBarSlideAnim.value * bottomBarOffsetPx
            alpha = 1f - bottomBarSlideAnim.value
        }
        content(bottomBarModifier)
    }
}
