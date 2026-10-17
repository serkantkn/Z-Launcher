package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled

/**
 * True while the Zune layout is chosen: the launcher then moves the Zune HD's way throughout -
 * the list, the hubs coming in, their pages opening - and flat, in slides. Windows Phone and
 * Windows 8 keep their own turnstiles and hinges.
 */
val LocalZuneMotion = staticCompositionLocalOf { false }

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

/**
 * One layer of a hub coming in, in the Zune layout: the hub's name, its headings and its page
 * each slide in from the right, the name furthest and the page least, so the hub settles in
 * depth - the Zune HD's parallax - rather than turning in on a hinge. [entered] runs 0 -> 1;
 * [layer] is 0 for the name, 1 for the headings, 2 for the page. Nothing anywhere else.
 */
fun Modifier.zuneHubLayer(entered: () -> Float, layer: Int): Modifier = composed {
    if (!LocalZuneMotion.current || !LocalAnimationsEnabled.current) return@composed this
    val slidePx = with(LocalDensity.current) { HUB_LAYER_SLIDE.toPx() }
    graphicsLayer {
        val value = entered()
        if (value >= 1f) return@graphicsLayer
        val start = layer * HUB_LAYER_STAGGER
        val t = ((value - start) / (1f - start)).coerceIn(0f, 1f)
        val eased = EnterEasing.transform(t)
        translationX = slidePx * HUB_LAYER_DEPTH[layer.coerceIn(0, HUB_LAYER_DEPTH.lastIndex)] * (1f - eased)
        alpha = (t * 2.5f).coerceIn(0f, 1f)
    }
}

/**
 * A page opening over a hub, in the Zune layout: the hub slides off to the left as the page
 * slides in from the right - the Zune HD going down into a list - where Windows Phone hinges.
 * [opened] runs 0 (the hub) -> 1 (the page).
 */
internal fun zunePageAway(opened: Float, slidePx: Float): Float = -slidePx * ZUNE_PAGE_DISTANCE * opened

internal fun zunePageIn(opened: Float, slidePx: Float): Float = slidePx * (1f - opened)

/** How far a page slides in over a hub, and the hub away, in the Zune layout. */
val ZUNE_PAGE_SLIDE = 72.dp
private const val ZUNE_PAGE_DISTANCE = 1.5f

/** How long a page takes to open over a hub, or close, in the Zune layout. */
const val ZUNE_PAGE_MILLIS = 380

/** How long a hub's name, headings and page take to settle, in the Zune layout. */
const val ZUNE_HUB_ENTER_MILLIS = 620

private val HUB_LAYER_SLIDE = 120.dp
private val HUB_LAYER_DEPTH = floatArrayOf(1f, 0.7f, 0.4f)
private const val HUB_LAYER_STAGGER = 0.06f

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
