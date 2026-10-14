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
    ZuneHubEntranceLayout(
        modifier = modifier,
        state = entrance,
        progressScale = entranceScale,
        offscreenCompositing = offscreenCompositing
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
                    phoneHeader, phoneAboveContent, phonePagePadding, phonePeek, swipeEnabled, page
                )
            }
            overlay?.invoke(this, barModifier)
        }
    }
}

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
    swipeEnabled: Boolean,
    page: @Composable (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (phoneHeader != null) {
            phoneHeader()
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
