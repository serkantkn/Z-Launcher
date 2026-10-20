package com.serkantkn.zunelauncher.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A command as Vista put it in a library: a small arrow and a word ("▸ play slide show"). */
class VistaLink(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/**
 * A hub as one of Vista Media Center's libraries: its name huge and faint in the top right, its
 * commands as small links at the top left, its ways in as one row of words under them - the
 * chosen one white between chevrons, the others pale blue - and the page below. As in Windows 7's,
 * moving along the row turns the page.
 */
@Composable
fun VistaHub(
    title: String,
    tabs: List<String>,
    pager: ZuneLoopingPagerState,
    modifier: Modifier = Modifier,
    hiddenTabs: Set<Int> = emptySet(),
    onSelected: (Int) -> Unit = {},
    links: List<VistaLink> = emptyList(),
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

    // The remote starts on the word of the page showing. A request made while the hub is still
    // zooming in can come to nothing, and the launcher then hands the hub's first stop - a link
    // above the words - the focus; so this asks until a word really has it, or the remote moves.
    var wordFocused by remember { mutableStateOf(false) }
    // Keyed on whether there are words yet: a hub showing something else first (its loading, a
    // takeover) has none, and the remote is put on them when they come.
    LaunchedEffect(tabs.isEmpty()) {
        if (tabs.isEmpty()) return@LaunchedEffect
        val since = android.os.SystemClock.uptimeMillis()
        delay(FIRST_FOCUS_MILLIS)
        repeat(FOCUS_TRIES) {
            if (wordFocused || TvFocus.lastArrowAt > since) return@LaunchedEffect
            val at = pager.currentPage.coerceIn(0, tabs.size - 1)
            runCatching { focus[at].requestFocus() }
            delay(FOCUS_RETRY_MILLIS)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val start = maxWidth * START_FRACTION
        VistaLibraryTitle(title, modifier = Modifier.align(Alignment.TopEnd))
        Column(modifier = Modifier.fillMaxSize().padding(start = start, end = END, top = TOP)) {
            if (links.isNotEmpty()) {
                VistaLinkRow(links, modifier = Modifier.padding(bottom = 2.dp))
            } else {
                Spacer(modifier = Modifier.height(LINK_ROW_HEIGHT))
            }
            if (shown.isNotEmpty()) {
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(30.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(shown) { position, index ->
                        VistaPivotWord(
                            text = tabs[index],
                            chosen = pager.currentPage == index,
                            modifier = Modifier
                                .focusRequester(focus[index])
                                .onFocusChanged { if (it.isFocused) wordFocused = true },
                            onFocused = {
                                val byArrow = android.os.SystemClock.uptimeMillis() - TvFocus.lastArrowAt < ARROW_RECENT_MILLIS
                                if (byArrow && pager.currentPage != index) {
                                    onSelected(index)
                                    scope.launch { pager.scrollToPage(index) }
                                } else if (!byArrow && pager.currentPage != index) {
                                    // The focus fell here on its own - the row it was on went with a
                                    // dialog - and not onto the page showing: it goes to that page's word.
                                    runCatching { focus[pager.currentPage.coerceIn(0, focus.lastIndex)].requestFocus() }
                                    return@VistaPivotWord
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
            Spacer(modifier = Modifier.height(22.dp))
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
                            label = "vista_pane",
                            modifier = Modifier.fillMaxSize()
                        ) { pane() }
                    }
                }
            }
        }
    }
}

/** A library's name, huge and faint in the top right corner. */
@Composable
fun VistaLibraryTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.lowercase(),
        style = MaterialTheme.typography.displayMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.Light, fontSize = 40.sp),
        color = Vista.Faint,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.padding(top = 8.dp, end = END)
    )
}

/** One of a library's ways in: the chosen one white between chevrons, the rest pale blue. */
@Composable
fun VistaPivotWord(
    text: String,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(160), label = "vistaPivot")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .tvOwnFocus()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    ) {
        val lit = focused || chosen
        val style = MaterialTheme.typography.titleLarge.copy(
            fontFamily = SelawikFamily,
            fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = if (lit) 22.sp else 19.sp
        )
        val color = when {
            focused -> Color.White
            chosen -> Color(0xFFDCEBFA)
            else -> Vista.Link
        }
        if (lit) Text("‹ ", style = style, color = color)
        Text(text = text.lowercase(), style = style, color = color, maxLines = 1)
        if (lit) Text(" ›", style = style, color = color)
    }
}

/** A library's commands, small and pale blue at its top left, each a little arrow and a word. */
@Composable
fun VistaLinkRow(links: List<VistaLink>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(LINK_ROW_HEIGHT).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        links.forEach { link -> VistaLinkText(link) }
    }
}

@Composable
fun VistaLinkText(link: VistaLink, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(interactionSource = source, indication = null, onClick = link.onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = link.icon ?: Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (focused) Color.White else Vista.Link,
            modifier = Modifier.size(if (link.icon == null) 14.dp else 16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = link.label.lowercase(),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SelawikFamily, fontSize = 17.sp),
            color = if (focused) Color.White else Vista.Link,
            maxLines = 1
        )
    }
}

/**
 * The commands a phone keeps in its bar, on a page inside a library: Vista's links again, along
 * the foot this time, where they do not stand over the page's own top.
 */
@Composable
fun VistaCommandLinks(links: List<VistaLink>, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        if (links.isNotEmpty()) {
            VistaLinkRow(links, modifier = Modifier.align(Alignment.CenterEnd).padding(end = END))
        }
    }
}

/** Where a library's content starts across the screen, as Vista's did. */
private const val START_FRACTION = 0.115f
private val END = 44.dp
private val TOP = 68.dp
private val LINK_ROW_HEIGHT = 30.dp
private val NAV_WIDTH = 220.dp
private const val FIRST_FOCUS_MILLIS = 250L
private const val FOCUS_TRIES = 25
private const val FOCUS_RETRY_MILLIS = 100L
private const val ARROW_RECENT_MILLIS = 400L

/**
 * A choice as Vista Media Center put one up over a page: a pane of blue glass in the bottom right
 * corner, its name in small white capitals, the page dimmed behind it. It takes the remote as it
 * comes and keeps it until it goes.
 */
@Composable
fun VistaPanel(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tvTake() }
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(PANEL_MILLIS)) }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 44.dp, bottom = 40.dp)
                .width(minOf(maxWidth * 0.42f, 460.dp))
                .heightIn(max = maxHeight * 0.8f)
                .focusRequester(focus)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .graphicsLayer {
                    alpha = shown.value
                    val grow = 0.92f + 0.08f * shown.value
                    scaleX = grow
                    scaleY = grow
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                }
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Vista.PanelTop.copy(alpha = 0.95f), Vista.PanelBottom.copy(alpha = 0.95f))))
                .vistaGlass(strength = 0.45f)
                // A tap on the glass stays on it, without making the glass a stop for the remote.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }
}

private const val PANEL_MILLIS = 220
