package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.animation.ZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.animation.zuneZoomAnchor
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

/**
 * Pivot header bound to an infinite [ZuneLoopingPagerState]: the highlighted tab follows the
 * normalized logical page and tapping a tab animates the pager to the nearest instance of that
 * page. [onSelected] is invoked after the scroll is launched for any extra per-hub side effect.
 */
@Composable
fun ZunePivotTabs(
    tabs: List<String>,
    state: ZuneLoopingPagerState,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 72.sp,
    startPadding: Dp = ZuneDimens.ScreenPaddingHorizontal,
    firstTabAnchor: ZuneZoomAnchor? = null,
    firstTabAlpha: Float = 1f,
    zoomTabIndex: Int = 0,
    onSelected: (Int) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    ZunePivotTabs(
        tabs = tabs,
        firstTabAnchor = firstTabAnchor,
        firstTabAlpha = firstTabAlpha,
        zoomTabIndex = zoomTabIndex,
        pagerState = state.pagerState,
        onSelected = { index ->
            scope.launch { state.animateScrollToPage(index) }
            onSelected(index)
        },
        modifier = modifier,
        fontSize = fontSize,
        startPadding = startPadding
    )
}

@Composable
fun ZunePivotTabs(
    tabs: List<String>,
    pagerState: PagerState,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 72.sp,
    /**
     * How far in from the left the first tab starts. The default is the screen's own margin, for
     * a pivot placed against the edge; pass 0 when the pivot already sits inside something padded,
     * or the two margins add up and the words stand further in than the title above them.
     */
    startPadding: Dp = ZuneDimens.ScreenPaddingHorizontal,
    /**
     * Lets the first tab's word be used as the landing place of a [ZuneTitleZoomOverlay] flight:
     * the anchor learns where it is, and the alpha hides it while its double is still in the air.
     */
    firstTabAnchor: ZuneZoomAnchor? = null,
    firstTabAlpha: Float = 1f,
    /**
     * Which tab the flight lands on. The first one by default — but a hub whose sub-lists live
     * behind a later tab (the pictures hub opens albums from its second) needs the word that
     * changes, not the word that happens to be leftmost.
     */
    zoomTabIndex: Int = 0
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(pagerState, tabs.size) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .collect { currentFloatPage ->
                val size = tabs.size
                if (size > 0) {
                    val actualFloatPage = (currentFloatPage % size).let { if (it < 0) it + size else it }
                    val clampedPage = actualFloatPage.coerceIn(0f, (size - 1).toFloat())
                    val index = clampedPage.toInt()
                    val fraction = clampedPage - index
                    val spacingPx = with(density) { 24.dp.toPx() }
                    
                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                    val itemWidth = itemInfo?.size?.toFloat() ?: 0f
                    val offsetPixels = (fraction * (itemWidth + spacingPx)).toInt()
                    
                    listState.scrollToItem(index, offsetPixels)
                }
            }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = startPadding, end = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(tabs) { index, tabTitle ->
            val actualCurrentPage = (pagerState.currentPage % tabs.size).let { if (it < 0) it + tabs.size else it }
            val isSelected = index == actualCurrentPage
            
            val alpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.45f,
                label = "settings_tab_alpha"
            )
            
            Text(
                text = tabTitle,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = fontSize,
                    letterSpacing = if (fontSize > 40.sp) (-3).sp else (-1).sp
                ),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    LocalZuneColors.current.textMuted
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .alpha(if (index == zoomTabIndex) alpha * firstTabAlpha else alpha)
                    .then(
                        if (index == zoomTabIndex && firstTabAnchor != null) {
                            Modifier.zuneZoomAnchor(firstTabAnchor)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelected(index) }
                    )
            )
        }
    }
}
