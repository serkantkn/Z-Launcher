package com.serkantkn.zeta.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.ui.components.WpBarAction
import com.serkantkn.zeta.ui.components.WpBarMenuItem
import com.serkantkn.zeta.ui.components.ZuneLoopingPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How every hub stands on a television: the doors the rest of the launcher goes through, each
 * opening onto the design chosen in settings - Media Center (Windows Vista's, [Vista]) or the
 * Xbox 360's dashboard ([Xbox]).
 *
 * These colours are what is left of Windows 7's Media Center, which the Xbox replaced; a few
 * places still read them for a blue that goes with either design.
 */
object MediaCenter {
    /** The blue it was known by, top and bottom of its gradient. */
    val Top = Color(0xFF0E3A78)
    val Bottom = Color(0xFF041531)
    /** The light band that crossed its background. */
    val Sheen = Color(0xFF5FA8F0)
    /** A word not chosen, a line of small print. */
    val Dim = Color(0xFF8FB3DD)
    /** The glow round whatever has the focus. */
    val Glow = Color(0xFF7CC8FF)
    /** The strip along the foot. */
    val Strip = Color(0xFF06204A)
}

/** A TV's ground: Media Center's blue with its rays, or the Xbox's greys. */
@Composable
fun MediaCenterBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    if (LocalVista.current) {
        VistaBackground(modifier = modifier, content = content)
        return
    }
    XboxBackground(modifier = modifier, content = content)
}

/**
 * A hub on a TV: [tabs] in one row the remote runs along (moving along turns the page), the page
 * below; [navExtra] (a hub's folders, albums) down the left of the page, [sidePane] down its
 * right, and [pane] beside it when something is open. Laid out by [VistaHub] or [XboxHub].
 */
@Composable
fun MediaCenterHub(
    title: String,
    tabs: List<String>,
    pager: ZuneLoopingPagerState,
    modifier: Modifier = Modifier,
    hiddenTabs: Set<Int> = emptySet(),
    onSelected: (Int) -> Unit = {},
    navExtra: (@Composable ColumnScope.() -> Unit)? = null,
    sidePaneWidth: Dp = 340.dp,
    sidePane: (@Composable () -> Unit)? = null,
    pane: (@Composable () -> Unit)? = null,
    paneKey: Any? = null,
    bottomRoom: Dp = 0.dp,
    /** The hub's commands, which Vista sets as links at the top rather than a strip at the foot. */
    links: List<VistaLink> = emptyList(),
    page: @Composable (index: Int) -> Unit
) {
    if (LocalVista.current) {
        VistaHub(
            title = title, tabs = tabs, pager = pager, modifier = modifier, hiddenTabs = hiddenTabs,
            onSelected = onSelected, links = links, navExtra = navExtra, sidePaneWidth = sidePaneWidth,
            sidePane = sidePane, pane = pane, paneKey = paneKey, bottomRoom = bottomRoom, page = page
        )
        return
    }
    XboxHub(
        title = title, tabs = tabs, pager = pager, modifier = modifier, hiddenTabs = hiddenTabs,
        onSelected = onSelected, navExtra = navExtra, sidePaneWidth = sidePaneWidth,
        sidePane = sidePane, pane = pane, paneKey = paneKey, bottomRoom = bottomRoom, page = page
    )
}

/** One word of a hub's row of ways in, as Vista or the Xbox writes it. */
@Composable
fun MediaCenterWord(
    text: String,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 34.sp,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    if (LocalVista.current) {
        VistaPivotWord(text = text, chosen = chosen, modifier = modifier, onFocused = onFocused, onClick = onClick)
        return
    }
    XboxPivotWord(text = text, chosen = chosen, modifier = modifier, onFocused = onFocused, onClick = onClick)
}

/**
 * A phone's bar on a TV: the same commands as Vista's links along the foot, or along the Xbox's
 * legend of buttons at the bottom left.
 */
@Composable
fun MediaCenterCommandStrip(
    actions: List<WpBarAction>,
    menuItems: List<WpBarMenuItem>,
    height: Dp,
    modifier: Modifier = Modifier
) {
    if (LocalVista.current) {
        VistaCommandLinks(
            links = actions.map { VistaLink(it.label, it.icon, it.onClick) } + menuItems.map { VistaLink(it.text, null, it.onClick) },
            height = height,
            modifier = modifier
        )
        return
    }
    XboxCommandLegend(actions, menuItems, height, modifier)
}

/** How a hub's content is laid out on a TV: Media Center's margins, inside the 5% a TV may crop. */
val MEDIA_CENTER_SIDE = 56.dp
private val SIDE = MEDIA_CENTER_SIDE
private val TOP = 28.dp
private val NAV_WIDTH = 220.dp
private const val FIRST_FOCUS_MILLIS = 250L
private const val FOCUS_TRIES = 15
private const val FOCUS_RETRY_MILLIS = 100L
private const val ARROW_RECENT_MILLIS = 400L
