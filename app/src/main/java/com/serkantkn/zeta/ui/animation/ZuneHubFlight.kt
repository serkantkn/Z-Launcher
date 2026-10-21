package com.serkantkn.zeta.ui.animation

import androidx.compose.ui.composed
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.ui.theme.LocalZuneColors
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
    /**
     * One word in the air: from a list's name up to a title ([down] false), or from a title back
     * down to the name it came from ([down] true). [key] is the title's; [itemId] the name's in
     * its list, kept out of sight until the word is back.
     */
    internal data class Pending(
        val key: String,
        val label: String,
        val from: Rect,
        val fromSize: TextUnit,
        val fromColor: Color,
        val down: Boolean = false,
        val to: Rect? = null,
        val toSize: TextUnit = ZUNE_HUB_TITLE_SIZE,
        val toColor: Color? = null,
        val itemId: String? = null,
        /** How the name reads in its list, where that differs from the title's lowercase. */
        val listText: String? = null,
        /**
         * The name is one of the Zune list's (a [com.serkantkn.zeta.ui.components.ZuneHubTitle]):
         * the word meets it on its baseline, not just centred in its line.
         */
        val inList: Boolean = false
    )

    /** A name in a list, as it was last laid out, for a word to lift off or land back on. */
    internal data class Label(val bounds: Rect, val size: TextUnit, val color: Color, val text: String?)

    internal var pending by mutableStateOf<Pending?>(null)
    internal var target by mutableStateOf<Rect?>(null)
    internal val progress = Animatable(0f)
    /** The word [progress] is for: until its flight sets off, a new one stands at its start. */
    internal var flown by mutableStateOf<Pending?>(null)
    private val known = HashMap<String, Rect>()
    private val labels = HashMap<String, Label>()
    /** The name each open page's title came from, so closing it can send the word back there. */
    private val origins = HashMap<String, Pair<String, Label>>()

    /** The hub opened from a name in the Zune list, and that name, for its closing to go back to. */
    private data class HubOrigin(val hub: String, val key: String, val itemId: String, val label: String, val name: Label)
    private var hubOrigin: HubOrigin? = null

    /** The titles drawn now, the latest last: a hub closing sends down whichever of its own shows. */
    private val shown = LinkedHashSet<String>()

    /**
     * The list's name a hub's title has just come back down to: it stands still in its place while
     * the rest of the list comes in round it, so the word lands on it rather than on a moving row.
     */
    private var landing by mutableStateOf<String?>(null)

    /** Whether the title [key] is still in the air, so the hub's own title keeps out of sight. */
    fun hides(key: String): Boolean = pending?.key == key

    /** Whether the list's name [itemId] is the one in the air. */
    fun hidesItem(itemId: String): Boolean = pending?.itemId == itemId

    /** Whether any title is in the air. */
    val isFlying: Boolean get() = pending != null

    /** Whether the list's name [itemId] is the one a title has just come back down to. */
    fun lands(itemId: String): Boolean = landing == itemId

    /** The list has come in again: the name a title came back to moves with it from now on. */
    fun landed() {
        landing = null
    }

    /**
     * Sends [label], from where it stands in the list, up to the title [key] of hub [hub]. The
     * list's name is [itemId]; the hub's closing sends the title back down to it ([closeHub]).
     */
    fun launch(key: String, label: String, from: Rect, fromSize: TextUnit, fromColor: Color, hub: String, itemId: String) {
        target = null
        landing = null
        hubOrigin = HubOrigin(hub, key, itemId, label, Label(from, fromSize, fromColor, null))
        pending = Pending(key, label, from, fromSize, fromColor, itemId = itemId, inList = true)
    }

    /**
     * Hub [hub] closing in the Zune layout: the title it shows goes back down to the name in the
     * list that opened it, as the hub goes and the list comes in. False when it was opened some
     * other way, or its title is nowhere to be seen, and the hub simply closes.
     */
    fun closeHub(hub: String): Boolean {
        val origin = hubOrigin?.takeIf { it.hub == hub } ?: return false
        hubOrigin = null
        // A page opened over the hub - a conversation, an album - stands over its title: the word
        // leaves from whatever of the hub's titles came last.
        val key = shown.lastOrNull { it == hub || it.startsWith("$hub:") } ?: return false
        val title = known[key] ?: return false
        landing = origin.itemId
        target = origin.name.bounds
        pending = Pending(
            key, origin.label, title, ZUNE_HUB_TITLE_SIZE, Color.Unspecified,
            down = true, to = origin.name.bounds, toSize = origin.name.size, toColor = origin.name.color,
            itemId = origin.itemId, inList = true
        )
        return true
    }

    /** Another hub has taken the open one's place: closing it has no name to go back to. */
    fun forgetHub() {
        hubOrigin = null
    }

    internal fun show(key: String) {
        shown.remove(key)
        shown.add(key)
    }

    internal fun unshow(key: String) {
        shown.remove(key)
    }

    /**
     * A page opening from a name in a list: the name [itemId] (marked with [zuneFlightLabel]) lifts
     * off and becomes the title [key] of the page. False when the name was never laid out.
     */
    fun openPage(key: String, label: String, itemId: String): Boolean {
        val from = labels[itemId] ?: return false
        origins[key] = itemId to from
        target = null
        pending = Pending(key, label, from.bounds, from.size, from.color, itemId = itemId, listText = from.text)
        return true
    }

    /**
     * The page [key] closing: its title goes back down to the name it came from. False when there
     * is nowhere to go back to - the page was not opened from a name, or its title was never laid
     * out - and the page simply closes.
     */
    fun closePage(key: String, label: String): Boolean {
        val (itemId, origin) = origins.remove(key) ?: return false
        val title = known[key] ?: return false
        target = origin.bounds
        pending = Pending(
            key, label, title, ZUNE_HUB_TITLE_SIZE, Color.Unspecified,
            down = true, to = origin.bounds, toSize = origin.size, toColor = origin.color, itemId = itemId,
            listText = origin.text
        )
        return true
    }

    /** Where hub [key]'s title has been laid out; the word in the air for it lands there. */
    internal fun land(key: String, bounds: Rect) {
        known[key] = bounds
        val now = pending
        if (now != null && now.key == key && !now.down) target = bounds
    }

    internal fun mark(itemId: String, label: Label) {
        labels[itemId] = label
    }

    internal fun lastKnown(key: String): Rect? = known[key]

    internal fun finish() {
        pending = null
        target = null
    }
}

/**
 * Marks a name in a list as one a page's title may lift off from and come back to, in the Zune
 * layout: where it is, at what size and in what colour. Out of sight while it is the word in the
 * air. Nothing at all in the other layouts.
 */
fun Modifier.zuneFlightLabel(
    itemId: String,
    fontSize: TextUnit,
    color: Color,
    /** The name as the list writes it, when the title writes it otherwise (lowercase). */
    text: String? = null
): Modifier = composed {
    val flight = LocalZuneHubFlight.current
    if (flight == null || !LocalZuneMotion.current) return@composed this
    this
        .onGloballyPositioned { coordinates ->
            val at = coordinates.positionInWindow()
            flight.mark(
                itemId,
                ZuneHubFlight.Label(
                    Rect(at.x, at.y, at.x + coordinates.size.width, at.y + coordinates.size.height),
                    fontSize,
                    color,
                    text
                )
            )
        }
        .graphicsLayer { alpha = if (flight.hidesItem(itemId)) 0f else 1f }
}

/** The flight under way, if the launcher keeps one; null where there is none (a preview). */
val LocalZuneHubFlight = staticCompositionLocalOf<ZuneHubFlight?> { null }

@Composable
fun rememberZuneHubFlight(): ZuneHubFlight = remember { ZuneHubFlight() }

/**
 * How big a main hub's title stands at the top in the Zune layout: across the whole top of the
 * screen and off its right edge, as the Zune HD's were.
 */
val ZUNE_HUB_TITLE_SIZE = 104.sp

/** The room over a main hub's title: just clear of the status bar. */
val ZUNE_HUB_TITLE_TOP = 26.dp

/** A little of what is behind shows through the title, as on the Zune HD. */
const val ZUNE_HUB_TITLE_ALPHA = 0.82f

/** The least the title moves for each page turned, when it all but fits the screen. */
private val TITLE_STEP_MIN = 40.dp

/** How far into the flight the hub is opened under the word. */
const val ZUNE_FLIGHT_OPEN_AT_MILLIS = 60L

private const val FLIGHT_MILLIS = 560
private const val WAIT_FOR_TITLE_MILLIS = 700L
private const val BULGE = 0.07f
private val FlightEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/**
 * The big title of a main hub in the Zune layout: where its word lands. It runs off the right of
 * the screen, and the pages turning carry it left - [cycle] is where the pages stand, 0 to
 * [pageCount] - so what was cut off comes into view, wrapping back to its start with the pages.
 * Touched, it goes back, as the Back key would.
 */
@Composable
fun ZuneHubFlightTitle(
    title: String,
    key: String,
    modifier: Modifier = Modifier,
    cycle: (() -> Float)? = null,
    pageCount: Int = 1
) {
    val flight = LocalZuneHubFlight.current
    val ink = if (LocalZuneColors.current.isDark) Color.White else Color.Black
    val density = LocalDensity.current
    val screenPx = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val minStepPx = with(density) { TITLE_STEP_MIN.toPx() }
    var right by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    if (flight != null) {
        androidx.compose.runtime.DisposableEffect(flight, key) {
            flight.show(key)
            onDispose { flight.unshow(key) }
        }
    }
    Text(
        text = title,
        style = zuneHubTitleStyle(FontWeight.Bold),
        color = ink,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            // As wide as the word is, not as the screen: the rest runs off to the right.
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .onGloballyPositioned { coordinates ->
                right = coordinates.positionInWindow().x + coordinates.size.width
            }
            .graphicsLayer {
                alpha = if (flight?.hides(key) == true) 0f else ZUNE_HUB_TITLE_ALPHA
                val turn = cycle ?: return@graphicsLayer
                if (pageCount < 2) return@graphicsLayer
                val last = (pageCount - 1).toFloat()
                val overflow = (right - screenPx).coerceAtLeast(0f)
                val step = maxOf(overflow / last, minStepPx)
                val c = turn()
                translationX = if (c <= last) -c * step else -step * last * (1f - (c - last))
            }
            // Measured where it is drawn, the pages' turn included, so a hub opened on another
            // page has its word land where the title stands. Not clipped to the window: the title
            // may start off its left edge.
            .onGloballyPositioned { coordinates ->
                val at = coordinates.positionInWindow()
                flight?.land(key, Rect(at.x, at.y, at.x + coordinates.size.width, at.y + coordinates.size.height))
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { back?.onBackPressed() }
    )
}

@Composable
private fun zuneHubTitleStyle(weight: FontWeight): TextStyle =
    MaterialTheme.typography.displayLarge.copy(
        fontWeight = weight,
        fontSize = ZUNE_HUB_TITLE_SIZE,
        lineHeight = ZUNE_HUB_TITLE_SIZE,
        letterSpacing = (-3).sp
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
        state.flown = pending
        state.progress.animateTo(1f, tween(FLIGHT_MILLIS, easing = FlightEasing))
        state.finish()
    }

    val density = LocalDensity.current
    val ink = if (LocalZuneColors.current.isDark) Color.White else Color.Black
    val titleColour = ink.copy(alpha = ZUNE_HUB_TITLE_ALPHA)
    val to = state.target
    // Laid out once at the title's size, and scaled to the name's at the other end.
    val titlePx = with(density) { ZUNE_HUB_TITLE_SIZE.toPx() }
    val fromScale = with(density) { pending.fromSize.toPx() } / titlePx
    val toScale = with(density) { pending.toSize.toPx() } / titlePx
    val t = if (to == null || state.flown !== pending) 0f else state.progress.value
    // A list's line and the title's sit their glyphs differently in their boxes; at the name's
    // end the word is put on the Zune list's baseline, or centred on a page's name, and at the
    // title's it lies exactly on it.
    val measurer = rememberTextMeasurer()
    val titleStyle = zuneHubTitleStyle(FontWeight.Bold)
    val listStyle = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light)
    fun drop(box: Rect, size: TextUnit, scale: Float): Float = if (pending.inList) {
        val listBaseline = measurer.measure(pending.listText ?: pending.label, listStyle.copy(fontSize = size)).firstBaseline
        val titleBaseline = measurer.measure(pending.label, titleStyle).firstBaseline
        listBaseline - titleBaseline * scale
    } else {
        (box.height - titlePx * scale) / 2f
    }
    val fromDrop = if (pending.down) 0f else drop(pending.from, pending.fromSize, fromScale)
    val toDrop = if (!pending.down || to == null) 0f else drop(to, pending.toSize, toScale)
    val x = if (to == null) pending.from.left else lerp(pending.from.left, to.left, t)
    val y = if (to == null) pending.from.top + fromDrop else lerp(pending.from.top + fromDrop, to.top + toDrop, t)
    val scale = lerp(fromScale, toScale, t) * (1f + BULGE * sin(t * Math.PI).toFloat())
    // Thin in the list, heavy as the title: the two weights cross over mid-flight.
    val crossing = ((t - 0.2f) / 0.6f).coerceIn(0f, 1f)
    val heavy = if (pending.down) 1f - crossing else crossing
    val colour = if (pending.down) {
        lerp(titleColour, pending.toColor ?: titleColour, t)
    } else {
        lerp(pending.fromColor, titleColour, t)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The thin word reads as the list writes it, the heavy one as the title does: the name
        // turns lowercase as it thickens, not in the instant it is touched.
        listOf(
            Triple(FontWeight.Light, 1f - heavy, pending.listText ?: pending.label),
            Triple(FontWeight.Bold, heavy, pending.label)
        ).forEach { (weight, alpha, words) ->
            if (alpha <= 0f) return@forEach
            Text(
                text = words,
                style = zuneHubTitleStyle(weight),
                color = colour,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
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
