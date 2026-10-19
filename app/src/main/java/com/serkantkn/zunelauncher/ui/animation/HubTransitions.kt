package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.serkantkn.zunelauncher.ui.tv.tvTake
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

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
    // The Zune layout's pages slide rather than swing, and a slide is over sooner.
    val zune = LocalZuneMotion.current
    return androidx.compose.runtime.remember(enabled, zune) {
        androidx.compose.animation.core.tween(
            durationMillis = when {
                !enabled -> 0
                zune -> ZUNE_PAGE_MILLIS
                else -> HingeAnimation.DURATION_MS
            },
            easing = HingeAnimation.EASING
        )
    }
}

/**
 * A hub turning away as a page of it opens: [progress] runs 0 (the hub in front) -> 1 (the page).
 * On its left edge into the screen, as Windows Phone does; in the Zune layout it slides off to
 * the left instead.
 */
fun Modifier.pageHingeAway(progress: () -> Float): Modifier = tvHubBehindPage(progress).composed {
    val density = LocalDensity.current
    val zune = LocalZuneMotion.current
    val slidePx = with(density) { ZUNE_PAGE_SLIDE.toPx() }
    graphicsLayer {
        val p = progress()
        if (zune) {
            translationX = zunePageAway(p, slidePx)
        } else {
            rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * p
            transformOrigin = TransformOrigin(0f, 0.5f)
            cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
        }
        alpha = (1f - p * 1.5f).coerceIn(0f, 1f)
    }
}

/**
 * The page coming in over its hub: [progress] runs 0 (not yet) -> 1 (in front). Out of the
 * screen on the same left edge, as Windows Phone does; in from the right in the Zune layout.
 */
fun Modifier.pageHingeIn(
    /**
     * True where the hub has gone before the page starts in (messaging, people) rather than both
     * moving at once: the Zune layout's slide then fades over the whole of it, or the screen
     * stood empty between the two.
     */
    sequential: Boolean = false,
    progress: () -> Float
): Modifier = tvPageInFront(progress).composed {
    val density = LocalDensity.current
    val zune = LocalZuneMotion.current
    val flight = LocalZuneHubFlight.current
    val slidePx = with(density) { ZUNE_PAGE_SLIDE.toPx() }
    graphicsLayer {
        val p = progress()
        if (zune) {
            // While a title is flying to or from the page it does not slide, only fades: the
            // title lands where the page's title is laid out, and that is where it must stay.
            translationX = if (flight?.isFlying == true) 0f else zunePageIn(p, slidePx)
        } else {
            rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
            transformOrigin = TransformOrigin(0f, 0.5f)
            cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
        }
        alpha = if (zune && sequential) p.coerceIn(0f, 1f) else (p * 1.5f - 0.2f).coerceIn(0f, 1f)
    }
}

/**
 * On a television, the hub behind an open page: out of the remote's reach while the page covers
 * it, and given the focus again when the page goes, rather than leaving it nowhere.
 */
private fun Modifier.tvHubBehindPage(progress: () -> Float): Modifier = composed {
    if (!com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current) return@composed Modifier
    // Some callers hand over a fresh lambda each composition, holding a plain value: the newest
    // is the one read.
    val current by androidx.compose.runtime.rememberUpdatedState(progress)
    val away by remember { derivedStateOf { current() > TV_COVERED } }
    val requester = remember { FocusRequester() }
    var wasAway by remember { mutableStateOf(away) }
    LaunchedEffect(away) {
        if (wasAway && !away) requester.tvTake()
        wasAway = away
    }
    // Back from the page the focus goes into the hub at its first stop - on a tablet layout the
    // heading of the page that was open. Handing it back to the very row that opened the page
    // (focusRestorer) did not take across the page's coming and going; that is still to do.
    Modifier
        .focusRequester(requester)
        .focusProperties { onEnter = { if (away) cancelFocusChange() } }
        .focusGroup()
}

/**
 * On a television, a page coming in over its hub takes the remote with it as soon as it is
 * mostly in front, and keeps it off while it is still out of sight.
 */
private fun Modifier.tvPageInFront(progress: () -> Float): Modifier = composed {
    if (!com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current) return@composed Modifier
    val current by androidx.compose.runtime.rememberUpdatedState(progress)
    val shown by remember { derivedStateOf { current() > TV_COVERED } }
    val requester = remember { FocusRequester() }
    LaunchedEffect(shown) {
        if (shown) requester.tvTake()
    }
    Modifier
        .focusRequester(requester)
        .focusProperties { onEnter = { if (!shown) cancelFocusChange() } }
        .focusGroup()
}

/** How far a page has to have come in before the TV's focus is given to it. */
private const val TV_COVERED = 0.5f

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

