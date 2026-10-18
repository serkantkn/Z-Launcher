package com.serkantkn.zunelauncher.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Zune layout's way into its main hubs: the name touched in the list lifts off, grows and
 * thickens as it rises, and settles at the top of the screen as the hub's own title, while the
 * hub's content comes in under it.
 *
 * The word flies over everything - the list going away and the hub arriving - so it is drawn once
 * at the launcher's top ([ZuneHubFlightOverlay]) and told where to go by the hub's title
 * ([ZuneHubFlightTitle]), which stays hidden until its word has landed on it.
 */
@Stable
class ZuneHubFlight {
    internal data class Pending(
        val key: String,
        val label: String,
        val from: Rect,
        val fromSize: TextUnit,
        val fromColor: Color
    )

    internal var pending by mutableStateOf<Pending?>(null)
    internal var target by mutableStateOf<Rect?>(null)
    internal val progress = Animatable(0f)
    private val known = HashMap<String, Rect>()

    /** Whether the title [key] is still in the air, so the hub's own title keeps out of sight. */
    fun hides(key: String): Boolean = pending?.key == key

    /** Whether any title is in the air. */
    val isFlying: Boolean get() = pending != null

    /** Sends [label], from where it stands in the list, up to the title of hub [key]. */
    fun launch(key: String, label: String, from: Rect, fromSize: TextUnit, fromColor: Color) {
        target = null
        pending = Pending(key, label, from, fromSize, fromColor)
    }

    /** Where hub [key]'s title has been laid out; the word in the air for it lands there. */
    internal fun land(key: String, bounds: Rect) {
        known[key] = bounds
        if (pending?.key == key) target = bounds
    }

    internal fun lastKnown(key: String): Rect? = known[key]

    internal fun finish() {
        pending = null
        target = null
    }
}

/** The flight under way, if the launcher keeps one; null where there is none (a preview). */
val LocalZuneHubFlight = staticCompositionLocalOf<ZuneHubFlight?> { null }

@Composable
fun rememberZuneHubFlight(): ZuneHubFlight = remember { ZuneHubFlight() }

/** How big a main hub's title stands at the top in the Zune layout. */
val ZUNE_HUB_TITLE_SIZE = 54.sp

/** The room over a main hub's title: clear of the status bar, as the small title's was. */
val ZUNE_HUB_TITLE_TOP = 44.dp

/** How far into the flight the hub is opened under the word. */
const val ZUNE_FLIGHT_OPEN_AT_MILLIS = 60L

private const val FLIGHT_MILLIS = 560
private const val WAIT_FOR_TITLE_MILLIS = 700L
private const val BULGE = 0.07f
private val FlightEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/** The big title of a main hub in the Zune layout: where its word lands. */
@Composable
fun ZuneHubFlightTitle(title: String, key: String, modifier: Modifier = Modifier) {
    val flight = LocalZuneHubFlight.current
    val ink = if (LocalZuneColors.current.isDark) Color.White else Color.Black
    Text(
        text = title,
        style = zuneHubTitleStyle(FontWeight.SemiBold),
        color = ink,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .onGloballyPositioned { flight?.land(key, it.boundsInWindow()) }
            .graphicsLayer { alpha = if (flight?.hides(key) == true) 0f else 1f }
    )
}

@Composable
private fun zuneHubTitleStyle(weight: FontWeight): TextStyle =
    MaterialTheme.typography.displayLarge.copy(
        fontWeight = weight,
        fontSize = ZUNE_HUB_TITLE_SIZE,
        lineHeight = ZUNE_HUB_TITLE_SIZE * 1.15f,
        letterSpacing = (-1.5).sp
    )

/**
 * The word in the air. Put over everything at the launcher's top; it draws nothing while no word
 * is flying. A hub that never lays its title out (opened somewhere else meanwhile) lets the word
 * go after a moment rather than keeping its own title hidden.
 */
@Composable
fun ZuneHubFlightOverlay(state: ZuneHubFlight) {
    val pending = state.pending ?: return
    LaunchedEffect(pending) {
        val to = withTimeoutOrNull(WAIT_FOR_TITLE_MILLIS) {
            snapshotFlow { state.target }.filterNotNull().first()
        } ?: state.lastKnown(pending.key)
        if (to == null) {
            state.finish()
            return@LaunchedEffect
        }
        state.target = to
        state.progress.snapTo(0f)
        state.progress.animateTo(1f, tween(FLIGHT_MILLIS, easing = FlightEasing))
        state.finish()
    }

    val density = LocalDensity.current
    val ink = if (LocalZuneColors.current.isDark) Color.White else Color.Black
    val to = state.target
    // Laid out once at the size it lands at, and scaled down to where it starts.
    val endPx = with(density) { ZUNE_HUB_TITLE_SIZE.toPx() }
    val startScale = with(density) { pending.fromSize.toPx() } / endPx
    val t = if (to == null) 0f else state.progress.value
    val x = if (to == null) pending.from.left else lerp(pending.from.left, to.left, t)
    val y = if (to == null) pending.from.top else lerp(pending.from.top, to.top, t)
    val scale = lerp(startScale, 1f, t) * (1f + BULGE * sin(t * Math.PI).toFloat())
    // Thin as it was in the list, heavy as it lands: the two weights cross over mid-flight.
    val heavy = ((t - 0.2f) / 0.6f).coerceIn(0f, 1f)
    val colour = lerp(pending.fromColor, ink, t)

    Box(modifier = Modifier.fillMaxSize()) {
        listOf(FontWeight.Light to 1f - heavy, FontWeight.SemiBold to heavy).forEach { (weight, alpha) ->
            if (alpha <= 0f) return@forEach
            Text(
                text = pending.label,
                style = zuneHubTitleStyle(weight),
                color = colour,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            )
        }
    }
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
