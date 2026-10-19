package com.serkantkn.zunelauncher.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.serkantkn.zunelauncher.ui.tv.tvTake
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch

/** Width of the column of headings down the left of a hub on a wide screen. */
val ZuneWideNavWidth: Dp = 300.dp

/**
 * A hub on a wide screen: its headings stacked down the left, and the page for the chosen one
 * filling everything to their right.
 *
 * This replaces the sideways panorama of 360dp columns for hubs whose pages want room — a month,
 * a keypad, a grid of pictures. The headings are the pivot's, turned on their side: the same
 * light type, the chosen one in the accent and the rest stepped back. Nothing scrolls sideways;
 * a page scrolls within itself if it has to.
 *
 * The pivot's own pager state stays the single truth about which page is showing, so everything
 * a hub already does with it — the bar's actions, a jump to a page, a page asked for from
 * outside — goes on working unchanged. The pager is still there underneath; it just cannot be
 * dragged, because a page here is the width of the screen and a sideways drag belongs to what
 * is on it.
 *
 * @param hiddenTabs pages that have no heading here because the hub shows them some other way
 * @param navExtra anything else the hub wants under its headings (albums, say)
 * @param sidePane a strip kept open down the right of the page, whatever page that is
 */
@Composable
fun ZuneWideNavHub(
    title: String,
    tabs: List<String>,
    state: ZuneLoopingPagerState,
    modifier: Modifier = Modifier,
    hiddenTabs: Set<Int> = emptySet(),
    onSelected: (Int) -> Unit = {},
    navExtra: (@Composable ColumnScope.() -> Unit)? = null,
    sidePaneWidth: Dp = 340.dp,
    sidePane: (@Composable () -> Unit)? = null,
    page: @Composable (index: Int) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val scope = rememberCoroutineScope()
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val isTv = com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current

    Row(modifier = modifier.fillMaxSize()) {
        // ── The headings ──
        Column(
            modifier = Modifier
                .width(ZuneWideNavWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = 56.dp, end = 16.dp, top = 40.dp, bottom = 96.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                ),
                color = ink.copy(alpha = 0.9f),
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(20.dp))
            tabs.forEachIndexed { index, label ->
                if (index in hiddenTabs) return@forEachIndexed
                val chosen = state.currentPage == index
                val source = remember { MutableInteractionSource() }
                val colour by animateColorAsState(
                    targetValue = if (chosen) zuneColors.accentColor else ink.copy(alpha = 0.55f),
                    animationSpec = tween(180),
                    label = "wide_nav_heading"
                )
                // On a television the headings are the way into the hub: the chosen one takes the
                // focus as the hub opens, and moving onto another turns to its page, as Media
                // Center's did, without a press.
                val requester = remember { FocusRequester() }
                if (isTv && index == state.currentPage) {
                    LaunchedEffect(Unit) {
                        requester.tvTake()
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = colour,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wpTilt(interactionSource = source)
                        .then(
                            if (!isTv) Modifier
                            else Modifier
                                .focusRequester(requester)
                                .onFocusChanged { focus ->
                                    if (focus.isFocused && state.currentPage != index) {
                                        onSelected(index)
                                        scope.launch { state.scrollToPage(index) }
                                    }
                                }
                        )
                        .clickable(indication = null, interactionSource = source) {
                            onSelected(index)
                            scope.launch { state.scrollToPage(index) }
                        }
                        .padding(vertical = 7.dp)
                )
            }
            navExtra?.invoke(this)
        }

        // ── The page ──
        ZuneLoopingPager(
            state = state,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 40.dp),
            beyondViewportPageCount = 0,
            userScrollEnabled = false
        ) { index ->
            Box(modifier = Modifier.fillMaxSize().padding(end = 32.dp)) { page(index) }
        }

        // ── The strip beside it ──
        if (sidePane != null) {
            Box(
                modifier = Modifier
                    .width(sidePaneWidth)
                    .fillMaxHeight()
                    .padding(top = 40.dp, end = 32.dp)
            ) { sidePane() }
        }
    }
}

/** A small heading for something kept under the hub's headings or beside its page. */
@Composable
fun ZuneWideNavLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 1.sp
        ),
        color = LocalZuneColors.current.textMuted,
        maxLines = 1,
        modifier = modifier
    )
}
