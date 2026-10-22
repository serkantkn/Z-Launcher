package com.serkantkn.zeta.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * How see-through the pop-up cards - a notification, the volume bar - are. They are frosted as the
 * tiles are, when the tiles are: the same share of the colour, over the same blur. 1 is solid.
 * The launcher provides it for the cards it draws itself, each overlay window for its own.
 */
val LocalBannerAlpha = staticCompositionLocalOf { 1f }

/** The share of a tile's colour a card keeps, for a tile opacity of 0-100 and a blur of 0-100. */
fun bannerAlpha(tileBlur: Int, tileOpacity: Int): Float =
    if (tileBlur > 0 && tileOpacity < 100) tileOpacity.coerceIn(0, 100) / 100f else 1f

/**
 * The blur, in screen pixels, a window behind a card gets for a tile blur of 0-100: what the tiles'
 * frost amounts to at full size (their blur is worked on a quarter-size copy).
 */
fun bannerBlurPx(tileBlur: Int): Int = if (tileBlur <= 0) 0 else blurRadius(tileBlur) * 4

/** Where a card stands as it is pushed up and away, in pixels above its place; 0 at rest. */
@Stable
class SwipeAwayState {
    internal val offset = Animatable(0f)
    internal var height by mutableIntStateOf(0)
    internal var gone by mutableStateOf(false)

    /** How much of the card is still there, 1 at rest and 0 once it is gone over the top. */
    val remaining: Float
        get() = if (height <= 0) 1f else (1f + offset.value / height).coerceIn(0f, 1f)

    /**
     * How much of the blur behind the card's window to keep. The window stays where the card was
     * while the card moves, so the blur would linger where the card has left: it thins quickly as
     * the card is pushed and is gone the moment the card is let go to fly away.
     */
    val blurShare: Float
        get() = if (gone) 0f else remaining * remaining * remaining

    /** Back in its place, at once: for a card shown again after it was swiped away. */
    suspend fun reset() {
        gone = false
        offset.snapTo(0f)
    }
}

@Composable
fun rememberSwipeAwayState(vararg keys: Any?): SwipeAwayState = remember(*keys) { SwipeAwayState() }

/**
 * A card that a swipe up puts away: it follows the finger up, and once it has gone far enough -
 * or is thrown - it slides out over the top and [onSwipedAway] is called; otherwise it springs
 * back. A pull down calls [onPulledDown] (a notification opens its quick reply with it).
 */
fun Modifier.swipeAway(
    state: SwipeAwayState,
    enabled: Boolean = true,
    onPulledDown: (() -> Unit)? = null,
    onSwipedAway: () -> Unit
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val awayPx = with(density) { SWIPE_AWAY_DISTANCE.toPx() }
    val pullPx = with(density) { PULL_DOWN_DISTANCE.toPx() }
    var pulled by remember(state) { mutableStateOf(0f) }
    this
        .onSizeChanged { state.height = it.height }
        .graphicsLayer { translationY = state.offset.value }
        .draggable(
            orientation = Orientation.Vertical,
            enabled = enabled && !state.gone,
            state = rememberDraggableState { delta ->
                val next = state.offset.value + delta
                if (next > 0f) {
                    // Down: the card stays put; far enough, and it opens out.
                    pulled += delta
                    if (onPulledDown != null && pulled > pullPx) {
                        pulled = Float.NEGATIVE_INFINITY
                        onPulledDown()
                    }
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { state.offset.snapTo(0f) }
                } else {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { state.offset.snapTo(next) }
                }
            },
            onDragStopped = { velocity ->
                pulled = 0f
                if (state.offset.value < -awayPx || velocity < -THROW_VELOCITY) {
                    state.gone = true
                    state.offset.animateTo(
                        -(state.height.toFloat() + awayPx),
                        tween(SWIPE_AWAY_MILLIS, easing = FastOutSlowInEasing)
                    )
                    onSwipedAway()
                } else {
                    state.offset.animateTo(0f, spring())
                }
            }
        )
}

/** How far up a card has to be pushed to go. */
private val SWIPE_AWAY_DISTANCE = 48.dp

/** How far down a card has to be pulled to open out. */
private val PULL_DOWN_DISTANCE = 24.dp

/** Pixels a second upwards that put a card away however short the push. */
private const val THROW_VELOCITY = 1200f

private const val SWIPE_AWAY_MILLIS = 200
