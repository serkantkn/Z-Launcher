package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The finger on the tall page that Start and the app list make together on a tablet.
 *
 * [reveal] is how far the list has risen: 0 is Start, 1 is the list. On Start ([opening]) a
 * drag upwards lifts the list with the finger; a drag downwards from a Start that is standing
 * still is the Windows Phone pull for the shade instead. On the list a drag downwards lowers it
 * again. Letting go settles to whichever end is nearer — or the end the finger was going towards,
 * when it was thrown. Sideways drags are left to the boards, which scroll that way themselves.
 */
internal fun Modifier.win8SwipeReveal(
    reveal: Animatable<Float, AnimationVector1D>,
    heightPx: () -> Float,
    opening: Boolean,
    enabled: Boolean,
    animate: Boolean,
    onSettled: (open: Boolean) -> Unit,
    onPullDown: () -> Unit = {}
): Modifier = if (!enabled) this else pointerInput(opening, animate) {
    coroutineScope {
        val velocity = VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            velocity.resetTracking()
            velocity.addPosition(down.uptimeMillis, down.position)
            var travel = 0f
            var shadePulled = false
            val startedAt = reveal.value

            fun follow(change: PointerInputChange, dy: Float) {
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                travel += dy
                val height = heightPx().takeIf { it > 0f } ?: return
                if (opening) {
                    if (travel > 0f && startedAt == 0f) {
                        // Below the top of Start there is nothing to reveal: this is the shade.
                        if (!shadePulled && travel > SHADE_PULL_PX * density) {
                            shadePulled = true
                            onPullDown()
                        }
                        return
                    }
                    val wanted = (startedAt - travel / height).coerceIn(0f, 1f)
                    launch { reveal.snapTo(wanted) }
                } else {
                    val wanted = (startedAt - travel / height).coerceIn(0f, 1f)
                    launch { reveal.snapTo(wanted) }
                }
            }

            val first = awaitVerticalTouchSlopOrCancellation(down.id) { change, over -> follow(change, over) }
                ?: return@awaitEachGesture
            verticalDrag(first.id) { change -> follow(change, change.positionChange().y) }

            // Let go: a throw decides, otherwise the nearer end does.
            val thrown = velocity.calculateVelocity().y
            val target = when {
                thrown < -FLING_PX_S * density -> 1f
                thrown > FLING_PX_S * density -> 0f
                reveal.value == startedAt -> startedAt
                opening -> if (reveal.value > OPEN_PAST) 1f else 0f
                else -> if (reveal.value < CLOSE_PAST) 0f else 1f
            }
            launch {
                if (animate && reveal.value != target) {
                    reveal.animateTo(target, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
                } else {
                    reveal.snapTo(target)
                }
                onSettled(target == 1f)
            }
        }
    }
}

/** How long the page takes to finish its slide once the finger is off, or from the arrow. */
internal const val REVEAL_MILLIS = 340

/** A drag downwards on a still Start this long opens the shade. */
private const val SHADE_PULL_PX = 48f

/** Faster than this, in dp per second, and the direction of the throw wins over the distance. */
private const val FLING_PX_S = 900f

/** Past this much of the screen the list keeps rising; before it, Start comes back. */
private const val OPEN_PAST = 0.28f

/** Below this much the list keeps going down; above it, it comes back up. */
private const val CLOSE_PAST = 0.72f
