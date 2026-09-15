package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Zune HD transition: the word you touched becomes the title of the page it opens.
 *
 * On the Zune HD, tapping an item in a list did not slide a new page in from the side. The item's
 * own name lifted off the list, came toward you as it grew, overshot very slightly, and settled
 * into place as the heading of what you had just opened — so you could see where the new page had
 * come from rather than being told.
 *
 * It is done with one piece of text drawn above everything else. The word is laid out once at the
 * size it will end at and then scaled down to the size it started at, because scaling a laid-out
 * line is cheap and re-laying it out sixty times a second is not. The bulge toward the viewer is a
 * sine over the flight — nothing at either end, most in the middle — which is what makes it read
 * as coming forward rather than merely getting bigger.
 */

/** Where a word sits on screen, so the flight knows where to start or end. */
@Stable
class ZuneZoomAnchor {
    internal var bounds: Rect? = null
}

@Composable
fun rememberZuneZoomAnchor(): ZuneZoomAnchor = remember { ZuneZoomAnchor() }

/** Keeps an anchor told where its word is. */
fun Modifier.zuneZoomAnchor(anchor: ZuneZoomAnchor): Modifier = this.onGloballyPositioned { coords ->
    val position = coords.positionInWindow()
    anchor.bounds = Rect(
        left = position.x,
        top = position.y,
        right = position.x + coords.size.width,
        bottom = position.y + coords.size.height
    )
}

/** One flight in progress. */
internal data class Flight(
    val label: String,
    val from: Rect,
    val fromFontSize: TextUnit,
    val to: Rect,
    val toFontSize: TextUnit
)

@Stable
class ZuneTitleZoomState internal constructor(
    private val animation: Animatable<Float, AnimationVector1D>
) {
    private var flight by mutableStateOf<Flight?>(null)
    private var titleAnchor: ZuneZoomAnchor? = null
    private var titleFontSize: TextUnit = TextUnit.Unspecified

    /** Whether a word is in the air. */
    val isFlying: Boolean get() = flight != null

    /**
     * What the real title's opacity should be: nothing while its word is still in the air, so the
     * two are never on screen at once.
     */
    val titleAlpha: Float get() = if (flight == null) 1f else 0f

    /** Tells the state where the page's own title sits and how big it is. */
    fun bindTitle(anchor: ZuneZoomAnchor, fontSize: TextUnit) {
        titleAnchor = anchor
        titleFontSize = fontSize
    }

    internal fun current(): Pair<Flight, Float>? = flight?.let { it to animation.value }

    /**
     * Sends a word from where it is up into the title.
     *
     * Returns false when there is nothing to fly — the row was never measured, or the title has
     * not been laid out yet — so the caller can simply navigate without the animation rather than
     * wait for one that will not happen.
     */
    suspend fun flyToTitle(label: String, from: ZuneZoomAnchor, fromFontSize: TextUnit): Boolean =
        flyToTitle(label, from.bounds ?: return false, fromFontSize)

    /**
     * The same, from a rectangle read earlier.
     *
     * The caller usually navigates first and flies second, by which time the row that was touched
     * is gone from the list — so its position has to be taken before it leaves, not looked up
     * afterwards.
     */
    suspend fun flyToTitle(label: String, fromBounds: Rect, fromFontSize: TextUnit): Boolean {
        val end = titleAnchor?.bounds ?: return false
        if (titleFontSize == TextUnit.Unspecified) return false
        return fly(Flight(label, fromBounds, fromFontSize, end, titleFontSize))
    }

    /** The same flight backwards: the title shrinks down to where its row will be. */
    suspend fun flyToRow(label: String, to: ZuneZoomAnchor, toFontSize: TextUnit): Boolean =
        flyToRowBounds(label, to.bounds ?: return false, toFontSize)

    /**
     * Backwards, to a rectangle the caller worked out.
     *
     * Going up a folder, the row the word belongs in does not exist yet — the list it is landing
     * in has not been read. So the caller says roughly where it will be instead of waiting.
     */
    suspend fun flyToRowBounds(label: String, toBounds: Rect, toFontSize: TextUnit): Boolean {
        val start = titleAnchor?.bounds ?: return false
        if (titleFontSize == TextUnit.Unspecified) return false
        return fly(Flight(label, start, titleFontSize, toBounds, toFontSize))
    }

    /** Set from the theme; when motion is off a flight is skipped rather than rushed. */
    internal var animationsEnabled: Boolean = true

    private suspend fun fly(next: Flight): Boolean {
        if (!animationsEnabled) return false
        flight = next
        animation.snapTo(0f)
        animation.animateTo(1f, tween(DURATION_MS, easing = FLIGHT_EASING))
        flight = null
        return true
    }
}

@Composable
fun rememberZuneTitleZoomState(): ZuneTitleZoomState {
    val animation = remember { Animatable(0f) }
    val state = remember { ZuneTitleZoomState(animation) }
    state.animationsEnabled = LocalAnimationsEnabled.current
    return state
}

/**
 * The word in flight. Put it above everything else in the hub's outermost Box; it draws nothing
 * at all while nothing is flying.
 */
@Composable
fun ZuneTitleZoomOverlay(
    state: ZuneTitleZoomState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    fontWeight: FontWeight = FontWeight.Light,
    letterSpacing: TextUnit = (-1).sp
) {
    val (flight, progress) = state.current() ?: return
    val density = LocalDensity.current

    // The word is laid out at the size it ends at and scaled to the size it starts at, so the
    // glyphs are measured once for the whole flight.
    val endPx = with(density) { flight.toFontSize.toPx() }
    val startPx = with(density) { flight.fromFontSize.toPx() }
    val startScale = if (endPx > 0f) startPx / endPx else 1f

    val eased = progress
    val scale = lerp(startScale, 1f, eased) * (1f + BULGE * sin(eased * Math.PI).toFloat())
    val x = lerp(flight.from.left, flight.to.left, eased)
    val y = lerp(flight.from.top, flight.to.top, eased)

    Box(modifier = modifier.fillMaxSize()) {
        Text(
            text = flight.label,
            style = TextStyle(
                fontSize = flight.toFontSize,
                fontWeight = fontWeight,
                letterSpacing = letterSpacing
            ),
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // Scaling about the top-left keeps the point we are interpolating put.
                    transformOrigin = TransformOrigin(0f, 0f)
                    cameraDistance = CAMERA_DISTANCE * density.density
                }
        )
    }
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/** Long enough to follow, short enough not to be in the way. The Zune HD was about this quick. */
const val ZuneTitleZoomDurationMillis = 420

private const val DURATION_MS = ZuneTitleZoomDurationMillis

/** How far past its final size the word swells on the way — the "toward you" part. */
private const val BULGE = 0.16f

private const val CAMERA_DISTANCE = 24f

/**
 * Quick off the mark, soft landing.
 *
 * The first curve tried here — 0.15, 0.85 — reached three quarters of the distance in the first
 * fifth of the time, so on a recording the word simply appeared at the top: all the motion was
 * over in about a tenth of a second and the rest of the flight was an invisible settle. This one
 * accelerates properly, which is what makes the jump readable.
 */
private val FLIGHT_EASING: Easing = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)
