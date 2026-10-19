package com.serkantkn.zunelauncher.ui.tv

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
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Windows Media Center, as every hub stands on a television.
 *
 * Media Center was Windows' ten-foot interface: deep blue, a library's name small in the corner,
 * its ways in (albums, artists, genres...) as one row of words across the top that the remote
 * runs along, and what the chosen one holds filling the rest. Whatever the remote is on glows.
 * The commands a phone keeps in its bar stand in a strip along the foot, words beside their
 * icons, the way Media Center's own transport and task strips read.
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

/**
 * Media Center's ground: the deep blue, darker towards the foot, with the soft band of light
 * that ran across it.
 */
@Composable
fun MediaCenterBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MediaCenter.Top, MediaCenter.Bottom)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The band: a wide, faint sweep of light from the left, as on Media Center's own.
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.45f to MediaCenter.Sheen.copy(alpha = 0.16f),
                    0.55f to MediaCenter.Sheen.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    start = Offset(0f, size.height * 0.25f),
                    end = Offset(size.width, size.height * 0.75f)
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    listOf(MediaCenter.Sheen.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                    radius = size.width * 0.6f
                )
            )
        }
        content()
    }
}

/**
 * A hub as a Media Center library: [title] small at the top left, [tabs] in one row under it,
 * the chosen page below. Moving along the row turns the page as the remote passes, without a
 * press; down goes into the page. [navExtra] (a hub's folders, albums) stands down the left of
 * the page, [sidePane] down its right, and [pane] beside it when something is open.
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
    page: @Composable (index: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val shown = tabs.indices.filter { it !in hiddenTabs }
    val focus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    val rowState = rememberLazyListState()

    // The remote starts on the word of the page showing, read when the hub has settled on it.
    LaunchedEffect(Unit) {
        delay(FIRST_FOCUS_MILLIS)
        repeat(FOCUS_TRIES) {
            val at = pager.currentPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
            if (tabs.isEmpty() || runCatching { focus[at].requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(FOCUS_RETRY_MILLIS)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(start = SIDE, end = SIDE, top = TOP)) {
        Text(
            text = title.lowercase(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontSize = 22.sp),
            color = MediaCenter.Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (shown.isNotEmpty()) {
            LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                itemsIndexed(shown) { position, index ->
                    MediaCenterWord(
                        text = tabs[index],
                        chosen = pager.currentPage == index,
                        modifier = Modifier.focusRequester(focus[index]),
                        onFocused = {
                            // Only a move of the remote turns the page; focus handed back does not.
                            val byArrow = android.os.SystemClock.uptimeMillis() - TvFocus.lastArrowAt < ARROW_RECENT_MILLIS
                            if (byArrow && pager.currentPage != index) {
                                onSelected(index)
                                scope.launch { pager.scrollToPage(index) }
                            }
                            scope.launch { rowState.animateScrollToItem((position - 1).coerceAtLeast(0)) }
                        },
                        onClick = {
                            onSelected(index)
                            scope.launch { pager.scrollToPage(index) }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (navExtra != null) {
                Column(
                    modifier = Modifier
                        .width(NAV_WIDTH)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 24.dp, bottom = bottomRoom),
                    content = navExtra
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = bottomRoom)) {
                // The hub's own pager, still: it is what turns the pages, only no swipe moves it.
                com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0,
                    userScrollEnabled = false
                ) { index -> page(index) }
            }
            if (sidePane != null) {
                Box(modifier = Modifier.width(sidePaneWidth).fillMaxHeight().padding(start = 24.dp, bottom = bottomRoom)) { sidePane() }
            }
            if (pane != null) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp)) {
                    AnimatedContent(
                        targetState = paneKey,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(90)) },
                        label = "media_center_pane",
                        modifier = Modifier.fillMaxSize()
                    ) { pane() }
                }
            }
        }
    }
}

/** One word of a Media Center row: white when chosen, pale blue when not, a little larger with the remote on it. */
@Composable
fun MediaCenterWord(
    text: String,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 34.sp,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(160), label = "mcWord")
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = size),
        color = when {
            focused || chosen -> Color.White
            else -> MediaCenter.Dim.copy(alpha = 0.75f)
        },
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    )
}

/**
 * The strip along the foot in place of a phone's bar: the same commands, each its icon with its
 * name beside it, the menu's lines after them, on a band of dark blue that fades into the page.
 */
@Composable
fun MediaCenterCommandStrip(
    actions: List<WpBarAction>,
    menuItems: List<WpBarMenuItem>,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(Color.Transparent, MediaCenter.Strip.copy(alpha = 0.92f))))
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = SIDE),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                MediaCenterCommand(label = action.label, onClick = action.onClick) {
                    Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            menuItems.forEach { item -> MediaCenterCommand(label = item.text, onClick = item.onClick) }
        }
    }
}

@Composable
private fun MediaCenterCommand(label: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        icon?.invoke()
        Text(
            text = label.lowercase(),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1
        )
    }
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
