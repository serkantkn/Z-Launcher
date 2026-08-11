package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Extension to apply Windows Phone / Zune staggered tile entry/exit animations.
 *
 * @param progress Global progress: 
 *                 0f (Offscreen Start) -> 1f (Idle) -> 2f (Offscreen End)
 * @param index Index of the item for stagger delay.
 */
fun Modifier.w10mStaggeredAnimation(
    progress: Float,
    index: Int,
    isClicked: Boolean = false
): Modifier = this.graphicsLayer {
    // If we are perfectly idle, no transformations needed.
    if (progress == 1f) return@graphicsLayer

    // Maximum items to consider for staggering to prevent out-of-bounds progress
    val safeIndex = index.coerceAtMost(15)
    
    val staggerDelay = 0.04f // 4% delay per index
    val itemDuration = 0.40f  // Each item takes 40% of the total time
    
    val effectIntensity = when {
        progress < 1f -> {
            // ENTERING (0f -> 1f)
            val itemStart = safeIndex * staggerDelay
            val itemEnd = itemStart + itemDuration
            val p = (progress - itemStart) / (itemEnd - itemStart)
            1f - p.coerceIn(0f, 1f) // 1f (gone) -> 0f (normal)
        }
        else -> {
            // EXITING (1f -> 2f)
            val normalizedProgress = progress - 1f
            if (isClicked) {
                // Clicked item stays 100% visible while all other elements exit first (0.0 -> 0.50), then plays exit animation LAST (0.50 -> 1.0)
                val itemStart = 0.50f
                val itemEnd = 1.0f
                val p = (normalizedProgress - itemStart) / (itemEnd - itemStart)
                p.coerceIn(0f, 1f)
            } else {
                val itemStart = safeIndex * staggerDelay
                val itemEnd = (itemStart + itemDuration).coerceAtMost(0.70f)
                val p = (normalizedProgress - itemStart) / (itemEnd - itemStart)
                p.coerceIn(0f, 1f) // 0f (normal) -> 1f (gone)
            }
        }
    }

    // Transformations: Door hinge effect (Turnstile)
    alpha = (1f - (effectIntensity * 1.5f)).coerceIn(0f, 1f) // Fade out slightly faster
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) // Hinge on the left edge
    rotationY = -90f * effectIntensity // Swing backward like a door
    scaleX = 1f - (0.3f * effectIntensity) // Shrink
    scaleY = 1f - (0.3f * effectIntensity)

    cameraDistance = 16f * density
}
