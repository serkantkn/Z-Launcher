package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/*
 * ════════════════════════════════════════════════════════════════════════════
 * THE HUB STANDARD
 * ════════════════════════════════════════════════════════════════════════════
 *
 * Every hub is the same thing twice:
 *
 *   phone  - the hub's name small at the top, the pivot's headings under it, the pages swiped
 *            sideways under those, and Windows Phone's bar along the bottom;
 *   tablet - the hub's name small at the top left, the same headings stacked down the left, the
 *            chosen page beside them, optionally a pane for whatever is open in the rest of the
 *            width, and Windows 8's bar across the bottom.
 *
 * A hub says only what it has - its name, its headings, what is on each page, the commands on its
 * bar, and what opens in the pane - and [ZuneHub] decides which of the two it is drawn as. Nothing
 * in a hub picks between a pivot and the headings, or between the two bars: a new hub written
 * against this gets both for nothing, and a change to how hubs stand is made here, once.
 *
 * The pager state is the one truth about which page is showing on both, so a hub's bar can read
 * `pager.currentPage` and be right on either.
 *
 * What stays in the hub: its sheets and dialogs, and on a phone the screens that hinge in over it
 * (a hub wraps its [ZuneHub] in the hinge's graphicsLayer, as before).
 */

/** The commands on a hub's bar: drawn as Windows Phone's bar on a phone and Windows 8's on a tablet. */
class ZuneHubBar(
    val actions: List<WpBarAction>,
    val menu: List<WpBarMenuItem> = emptyList()
)

/**
 * What a tablet hub keeps open in the rest of the width, beside its list.
 *
 * @param key what is open, or null for nothing; the pane cross-fades when it changes
 * @param ownsBar true when what is drawn there has a bar of its own (an editor, a card); false
 * when it has none (a conversation with its composer), and an empty bar is laid under it so the
 * band along the bottom still runs the whole width
 * @param content what is drawn for [key]; `barModifier` is the hub's entrance for a bar, to hand to
 * the pane's own bar so it rises with the hub's
 */
class ZuneHubPane(
    val key: Any?,
    val ownsBar: Boolean = true,
    val content: @Composable (key: Any?, barModifier: Modifier) -> Unit
)

/**
 * A hub, standing as a phone's pivot or a tablet's headings - see the standard above.
 *
 * @param title the hub's name, small, at the top (it can change: "3 selected")
 * @param tabs the pivot's headings, which are the tablet's headings too
 * @param page what is on a page; the same pages serve both, and a page that should differ on a
 * tablet asks [LocalIsWideScreen]
 * @param bar the bar's commands; null for a hub with no bar
 * @param showTabs false while the hub has no pages to move between (no account set up yet)
 * @param takeover drawn instead of the pages while it is set - asking for a permission, loading,
 * a search - with the name (and on a tablet the headings) still standing
 * @param titleTrailing drawn after the name on a phone (a sync spinner)
 * @param onTitleClick the name as a button on a phone (choosing an account)
 * @param phoneHeader replaces the name and pivot on a phone, for a hub whose header is its own (the
 * people hub's giant sliding name, the pictures' zooming album heading)
 * @param phoneAboveContent anything between the pivot and the pages on a phone (a banner, a search box)
 * @param phonePagePadding the room inside each page on a phone
 * @param phonePeek the edge of the next page showing at the right of this one on a phone, as a
 * Windows Phone pivot always had it, saying there is more to swipe to; false only for a hub whose
 * pages must have the whole width
 * @param phonePanorama Windows Phone's panorama on a phone instead of the pivot: the hub's name
 * giant across the top, sliding slower than the sections under it and round again at the end, and
 * each section under its own heading, the next one's edge showing at the right. Only the people
 * hub stands this way, as it did on Windows Phone 7; a tablet draws its headings either way
 * @param glideFrom when set, the hub opens on this page and glides on to its first, the way the
 * Windows Phone 7 people hub came in on "recent" and slid round to "all": it fades in where it
 * stands instead of swinging in, then moves. Played once, when the hub is opened; never with the
 * animations off
 * @param swipeEnabled false while the pages must hold still on a phone (choosing several)
 * @param wideHiddenTabs headings a tablet does not show, because it shows that page another way
 * @param wideOnSelected called when a heading is chosen on a tablet
 * @param wideNavExtra anything under the headings on a tablet (folders, albums, tags)
 * @param wideSidePane a strip kept open down the right of the page on a tablet, under the one bar
 * @param widePane the pane beside the list on a tablet; with it, the list is [wideListWidth] wide and
 * the list's commands stand at the left end of the band, the pane's at the right
 */
@Composable
fun ZuneHub(
    title: String,
    tabs: List<String>,
    pager: ZuneLoopingPagerState,
    modifier: Modifier = Modifier,
    bar: ZuneHubBar? = null,
    showTabs: Boolean = true,
    takeover: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    entrance: ZuneHubEntranceState = rememberZuneHubEntranceState(),
    entranceScale: () -> Float = { 1f },
    offscreenCompositing: Boolean = true,
    phoneHeader: (@Composable ColumnScope.() -> Unit)? = null,
    phoneAboveContent: (@Composable ColumnScope.() -> Unit)? = null,
    phonePagePadding: PaddingValues = PaddingValues(horizontal = ZuneDimens.ScreenPaddingHorizontal),
    phonePeek: Boolean = true,
    phonePanorama: Boolean = false,
    glideFrom: Int? = null,
    swipeEnabled: Boolean = true,
    wideHiddenTabs: Set<Int> = emptySet(),
    wideOnSelected: (Int) -> Unit = {},
    wideNavExtra: (@Composable ColumnScope.() -> Unit)? = null,
    wideSidePaneWidth: Dp = 340.dp,
    wideSidePane: (@Composable () -> Unit)? = null,
    widePane: ZuneHubPane? = null,
    wideListWidth: Dp = ZuneHubDefaults.WideListWidth,
    overlay: (@Composable BoxScope.(barModifier: Modifier) -> Unit)? = null,
    page: @Composable (index: Int) -> Unit
) {
    val animationsOn = LocalAnimationsEnabled.current
    val gliding = glideFrom != null && animationsOn
    // Read afresh while waiting: a hub kept running is first composed out of sight, where it
    // counts as in, and only then moved to where it is opened.
    val arrival by rememberUpdatedState(LocalHubArrival.current)
    val wide = LocalIsWideScreen.current
    val showingPages by rememberUpdatedState(takeover == null)
    // How much of a gliding hub's pages show: none while Start turns away - its ground alone, as
    // Windows Phone 7 left the screen dark for a moment - then a quick fade, then the glide.
    val reveal = remember { Animatable(if (gliding) 0f else 1f) }
    if (gliding) {
        LaunchedEffect(Unit) {
            // On the page it glides from - the hub's pager is best made starting there, so not
            // even a first frame shows another.
            pager.scrollToPage(glideFrom!!)
            // Shown once Start is nearly away and the hub has its pages (not a spinner, not a
            // question about permissions). The pages have been laid out unseen meanwhile.
            snapshotFlow { arrival() >= GLIDE_REVEAL_AT && showingPages }.first { it }
            // Slow to start, so the first frames shown are still turned well away, then flattening
            // fast - as the phone's tiles were deep in their first frame and flat by the third.
            val arriving = launch { reveal.animateTo(1f, tween(GLIDE_FADE_MILLIS, easing = FastOutSlowInEasing)) }
            if (wide) {
                arriving.join()
                // A tablet shows one heading's page at a time: no panorama to glide round, and
                // gliding there would pass through every page between - a search page opens its
                // keyboard on the way. It holds on the page a moment and then turns to the first,
                // which comes in the way a chosen heading's page does.
                delay(WIDE_GLIDE_HOLD_MILLIS)
                pager.scrollToPage(0)
                return@LaunchedEffect
            }
            // Carried on to the first - forward, the short way round, as one swipe would - as
            // soon as the pages have nearly come flat: on the phone they stood still for no more
            // than a frame or two.
            snapshotFlow { reveal.value >= GLIDE_FROM_ARRIVAL }.first { it }
            val target = pager.pagerState.currentPage + forwardStepsToFirst(glideFrom, pager.pageCount, pager.isLooping)
            pager.pagerState.animateScrollToPage(
                page = target,
                animationSpec = tween(GLIDE_MILLIS, easing = GlideEasing)
            )
        }
    }
    ZuneHubEntranceLayout(
        modifier = modifier,
        state = entrance,
        progressScale = entranceScale,
        offscreenCompositing = offscreenCompositing,
        swingIn = !gliding,
        fadeIn = { reveal.value }
    ) { barModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (LocalIsWideScreen.current) {
                WideHub(
                    title, tabs, pager, bar, showTabs, takeover, barModifier,
                    wideHiddenTabs, wideOnSelected, wideNavExtra, wideSidePaneWidth, wideSidePane,
                    widePane, wideListWidth, page
                )
            } else {
                PhoneHub(
                    title, tabs, pager, bar, showTabs, takeover, titleTrailing, onTitleClick, barModifier,
                    phoneHeader, phoneAboveContent, phonePagePadding, phonePeek, phonePanorama, swipeEnabled, page
                )
            }
            overlay?.invoke(this, barModifier)
        }
    }
}

/**
 * How far in the hub on show has come, 0-1, as whatever opens hubs moves it in: a hub that does
 * something once it is in (the people hub's glide) waits for 1. Hubs drawn anywhere else are in.
 */
val LocalHubArrival = staticCompositionLocalOf<() -> Float> { { 1f } }

object ZuneHubDefaults {
    /** How wide a tablet hub's list is when something opens in a pane beside it. */
    val WideListWidth: Dp = 440.dp

    /** The narrowest the list is let go on a narrow tablet. */
    val WideListMinWidth: Dp = 280.dp

    /** The least room the pane keeps beside the list. */
    val WidePaneMinWidth: Dp = 360.dp
}

/** The hub's name as a phone shows it, small above the pivot. */
@Composable
fun ZuneHubPhoneTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(
            top = 28.dp,
            bottom = 4.dp,
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal
        )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            ),
            color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        )
        trailing?.invoke(this)
    }
}

@Composable
private fun BoxScope.PhoneHub(
    title: String,
    tabs: List<String>,
    pager: ZuneLoopingPagerState,
    bar: ZuneHubBar?,
    showTabs: Boolean,
    takeover: (@Composable () -> Unit)?,
    titleTrailing: (@Composable RowScope.() -> Unit)?,
    onTitleClick: (() -> Unit)?,
    barModifier: Modifier,
    phoneHeader: (@Composable ColumnScope.() -> Unit)?,
    phoneAboveContent: (@Composable ColumnScope.() -> Unit)?,
    phonePagePadding: PaddingValues,
    phonePeek: Boolean,
    phonePanorama: Boolean,
    swipeEnabled: Boolean,
    page: @Composable (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (phoneHeader != null) {
            phoneHeader()
        } else if (phonePanorama) {
            PanoramaTitle(title = title, pager = pager)
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
                ZuneHubPhoneTitle(title = title, onClick = onTitleClick, trailing = titleTrailing)
                if (showTabs) ZunePivotTabs(tabs = tabs, state = pager, modifier = Modifier.padding(top = 4.dp))
            }
        }
        phoneAboveContent?.invoke(this)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                takeover != null -> takeover()
                phonePanorama -> {
                    // Each section under its own heading, the heading moving with it; the next
                    // section's edge shows at the right, as the pivot's does.
                    val direction = LocalLayoutDirection.current
                    val pagePadding = PaddingValues(
                        start = phonePagePadding.calculateStartPadding(direction),
                        top = phonePagePadding.calculateTopPadding(),
                        bottom = phonePagePadding.calculateBottomPadding()
                    )
                    ZuneLoopingPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = PIVOT_PEEK),
                        userScrollEnabled = swipeEnabled
                    ) { index ->
                        Column(modifier = Modifier.fillMaxSize().padding(pagePadding)) {
                            if (showTabs) PanoramaSectionHeading(tabs.getOrElse(index) { "" })
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { page(index) }
                        }
                    }
                }
                phonePeek -> {
                    // A page gives up its right margin to the next page's edge: what shows of the
                    // next page is its own left margin and the first [PIVOT_PEEK] - margin of
                    // what is on it. The page keeps the rest of the padding it asked for, so a
                    // hub that pads its own rows is not padded twice.
                    val direction = LocalLayoutDirection.current
                    val pagePadding = PaddingValues(
                        start = phonePagePadding.calculateStartPadding(direction),
                        top = phonePagePadding.calculateTopPadding(),
                        bottom = phonePagePadding.calculateBottomPadding()
                    )
                    ZuneLoopingPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = PIVOT_PEEK),
                        userScrollEnabled = swipeEnabled
                    ) { index ->
                        Box(modifier = Modifier.fillMaxSize().padding(pagePadding)) { page(index) }
                    }
                }
                else -> ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = swipeEnabled) { index ->
                    Box(modifier = Modifier.fillMaxSize().padding(phonePagePadding)) { page(index) }
                }
            }
        }
        if (bar != null) Spacer(modifier = Modifier.height(bottomBarHeight()))
    }
    if (bar != null) {
        WindowsPhoneBottomBar(
            actions = bar.actions,
            menuItems = bar.menu,
            modifier = Modifier.align(Alignment.BottomCenter).then(barModifier)
        )
    }
}

@Composable
private fun BoxScope.WideHub(
    title: String,
    tabs: List<String>,
    pager: ZuneLoopingPagerState,
    bar: ZuneHubBar?,
    showTabs: Boolean,
    takeover: (@Composable () -> Unit)?,
    barModifier: Modifier,
    hiddenTabs: Set<Int>,
    onSelected: (Int) -> Unit,
    navExtra: (@Composable ColumnScope.() -> Unit)?,
    sidePaneWidth: Dp,
    sidePane: (@Composable () -> Unit)?,
    pane: ZuneHubPane?,
    listWidth: Dp,
    page: @Composable (Int) -> Unit
) {
    // The headings and the page, the bar's room left under them.
    @Composable
    fun NavAndPage(modifier: Modifier) {
        Column(modifier = modifier) {
            ZuneWideNavHub(
                title = title,
                tabs = if (showTabs) tabs else emptyList(),
                state = pager,
                modifier = Modifier.weight(1f),
                hiddenTabs = hiddenTabs,
                onSelected = onSelected,
                navExtra = navExtra,
                sidePaneWidth = sidePaneWidth,
                sidePane = sidePane
            ) { index -> if (takeover != null) takeover() else page(index) }
            if (bar != null) Spacer(modifier = Modifier.height(bottomBarHeight()))
        }
    }

    if (pane == null) {
        NavAndPage(Modifier.fillMaxSize())
        if (bar != null) {
            WindowsPhoneBottomBar(
                actions = bar.actions,
                menuItems = bar.menu,
                modifier = Modifier.align(Alignment.BottomCenter).then(barModifier)
            )
        }
        return
    }

    // The list gives way before the pane does: on a narrow tablet (one held upright) the pane
    // keeps room enough to read what is open in it.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fittedList = minOf(listWidth, maxOf(ZuneHubDefaults.WideListMinWidth, maxWidth - ZuneWideNavWidth - ZuneHubDefaults.WidePaneMinWidth))
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(ZuneWideNavWidth + fittedList).fillMaxHeight()) {
                NavAndPage(Modifier.fillMaxSize())
                if (bar != null) {
                    WindowsPhoneBottomBar(
                        actions = bar.actions,
                        menuItems = bar.menu,
                        // The list's commands at the left end; whatever is open beside it has the right.
                        wideCommandsAtStart = true,
                        modifier = Modifier.align(Alignment.BottomCenter).then(barModifier)
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = pane.key,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 12 }) togetherWith fadeOut(tween(90))
                    },
                    label = "hub_pane",
                    modifier = Modifier.fillMaxSize()
                ) { key ->
                    if (pane.ownsBar) {
                        pane.content(key, barModifier)
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { pane.content(key, barModifier) }
                                Spacer(modifier = Modifier.height(bottomBarHeight()))
                            }
                            WindowsPhoneBottomBarStrip(modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }
}

/**
 * How much of the right of a phone's pivot page belongs to the next page: the next page's left
 * margin and a little of what it holds, as on Windows Phone. The weather hub, which lays out its
 * own pivot, keeps the same.
 */
internal val PIVOT_PEEK = ZuneDimens.ScreenPaddingHorizontal * 2

/**
 * The panorama's name, giant, across the top of a phone. It slides a little with every section
 * - slower than the sections, as the title of a Windows Phone panorama did - and, going from the
 * last section round to the first, it is carried off to the left while its next copy comes in
 * from the right, so the name always starts where the first section does.
 */
@Composable
private fun PanoramaTitle(title: String, pager: ZuneLoopingPagerState) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val travelPx = with(density) { PANORAMA_TITLE_TRAVEL.toPx() }
    val style = MaterialTheme.typography.displayLarge.copy(
        fontWeight = FontWeight.Light,
        fontSize = 96.sp,
        letterSpacing = (-4).sp,
        lineHeight = 96.sp
    )
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The name's next copy waits beyond the right edge for going round; kept out of sight
            // there even when the hub is turned in and the edge comes into view.
            .clipToBounds()
            .padding(top = 28.dp, start = ZuneDimens.ScreenPaddingHorizontal)
            // Lifted into the top and cut by it, as a panorama's name was - and the room it rises
            // out of given back, so the sections' headings sit right under it.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val lift = PANORAMA_TITLE_LIFT.roundToPx()
                layout(placeable.width, (placeable.height + lift).coerceAtLeast(0)) { placeable.place(0, lift) }
            }
    ) {
        // Where the sections are, in sections from the first, read as they move.
        fun cycle(): Float {
            val count = pager.pageCount.coerceAtLeast(1)
            val raw = (pager.pagerState.currentPage + pager.pagerState.currentPageOffsetFraction) % count
            return if (raw < 0) raw + count else raw
        }
        fun offsets(): Pair<Float, Float> {
            val last = (pager.pageCount - 1).coerceAtLeast(0).toFloat()
            // The name moves only a little over all the sections together; going round from the
            // last to the first is where it sweeps.
            val driftPx = if (last > 0f) travelPx / last else 0f
            val at = cycle()
            return if (at <= last) {
                -at * driftPx to screenWidthPx
            } else {
                val fraction = at - last
                (-last * driftPx - fraction * screenWidthPx) to (screenWidthPx - fraction * screenWidthPx)
            }
        }
        Text(
            text = title,
            style = style,
            color = ink,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer { translationX = offsets().first }
        )
        Text(
            text = title,
            style = style,
            color = ink,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer { translationX = offsets().second }
        )
    }
}

/** A panorama section's own heading, above what is in it and moving with it. */
@Composable
private fun PanoramaSectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 36.sp),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
    )
}

/** How many pages forward the first page is from [from]: round the end on a looping hub. */
internal fun forwardStepsToFirst(from: Int, pageCount: Int, looping: Boolean): Int = when {
    pageCount <= 0 || from <= 0 -> 0
    looping -> pageCount - from
    else -> -from
}

/**
 * How far the panorama's name slides over all its sections together. Windows Phone 7's name hardly
 * moved from section to section - on the last the whole of it still showed - and swept right
 * round only going from the last back to the first.
 */
private val PANORAMA_TITLE_TRAVEL = 32.dp
/** The name sits a little above the top, cut by it, as a panorama's did. */
private val PANORAMA_TITLE_LIFT = (-24).dp

/**
 * The glide into the people hub, measured off a Windows Phone 7 frame by frame: a third of the
 * way in its first tenth of a second, half in two, three quarters in three, and the last of it
 * eased out over half a second more - a cubic ease-out over eight tenths, near enough exactly.
 */
internal const val GLIDE_MILLIS = 800
/** How long a tablet shows the page it opens on before it turns to the first. */
private const val WIDE_GLIDE_HOLD_MILLIS = 700L
/**
 * How far in the hub has come when its pages are shown: Start gone and the hub's ground left bare
 * for about a quarter of a second, as the phone's screen was, and the ground itself already at
 * full strength, so the pages turn in solid.
 */
private const val GLIDE_REVEAL_AT = 0.96f
/**
 * The pages' arrival, turning flat as they show. Longer than the two or three frames the phone
 * filmed at thirty a second, so that at sixty or a hundred and twenty the turn is seen.
 */
private const val GLIDE_FADE_MILLIS = 200
/** How far the pages have turned flat when the glide sets off. */
private const val GLIDE_FROM_ARRIVAL = 0.75f
internal val GlideEasing = androidx.compose.animation.core.CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
