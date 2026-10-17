package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled

/**
 * The Zune HD's own motion for its home list, used by the Zune layout in place of Windows Phone's
 * turnstile. It runs on the same envelope as the turnstile, so Start drives both alike:
 *
 * - 0 → 1, coming in: the words slide in from the left, one after another, as the list came back
 *   on the Zune HD when you left a menu.
 * - 1 → 2, going out: the other words slide away to the left and fade, and the one you touched
 *   zooms towards you and dissolves - the Zune's "zoom through" - while the hub appears behind it.
 *
 * Nothing here turns in depth: the Zune moved flat, in slides and zooms.
 */
fun Modifier.zuneListMotion(
    progress: () -> Float,
    index: Int,
    isClicked: Boolean = false
): Modifier = composed {
    if (!LocalAnimationsEnabled.current) return@composed this
    val slidePx = with(LocalDensity.current) { SLIDE.toPx() }
    graphicsLayer {
        val value = progress()
        if (value == 1f) return@graphicsLayer
        val order = index.coerceIn(0, MAX_STAGGERED)
        if (value < 1f) {
            val start = order * ENTER_STAGGER
            val t = ((value - start) / ENTER_SPAN).coerceIn(0f, 1f)
            val eased = EnterEasing.transform(t)
            translationX = -slidePx * (1f - eased)
            alpha = eased
        } else {
            val leaving = (value - 1f).coerceIn(0f, 1f)
            if (isClicked) {
                // Grows from its own left edge, past the screen, fading as it goes by.
                val eased = ZoomEasing.transform(leaving)
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = 1f + ZOOM * eased
                scaleY = scaleX
                // Whole until well past twice its size, then gone in the last stretch.
                alpha = (1f - eased * eased * eased).coerceIn(0f, 1f)
            } else {
                val start = order * EXIT_STAGGER
                val t = ((leaving - start) / EXIT_SPAN).coerceIn(0f, 1f)
                val eased = ExitEasing.transform(t)
                translationX = -slidePx * EXIT_DISTANCE * eased
                alpha = 1f - eased
            }
        }
    }
}

/** How long the list takes to come in, and to leave, in the Zune layout. */
const val ZUNE_ENTER_MILLIS = 640
const val ZUNE_EXIT_MILLIS = 520

/** How far into the leaving the hub is opened, so it appears behind the word's zoom. */
const val ZUNE_OPEN_AT_MILLIS = 320L

/** How long a hub takes to slide in or out over the Zune list. */
const val ZUNE_HUB_MILLIS = 340

/** How far a hub slides as it appears over the Zune list, and back as it goes. */
val ZUNE_HUB_SLIDE = 56.dp

private val SLIDE = 96.dp
private const val EXIT_DISTANCE = 3f
private const val ZOOM = 4.5f
private const val MAX_STAGGERED = 18
private const val ENTER_STAGGER = 0.03f
private const val ENTER_SPAN = 0.46f
private const val EXIT_STAGGER = 0.025f
private const val EXIT_SPAN = 0.5f

private val EnterEasing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
private val ExitEasing = CubicBezierEasing(0.55f, 0f, 0.9f, 0.6f)
private val ZoomEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
