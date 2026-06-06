package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import androidx.compose.foundation.layout.PaddingValues

@Composable
fun ZunePivotTabs(
    tabs: List<String>,
    pagerState: PagerState,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .collect { currentFloatPage ->
                val clampedPage = currentFloatPage.coerceIn(0f, (tabs.size - 1).toFloat())
                val index = clampedPage.toInt()
                val fraction = clampedPage - index
                val spacingPx = with(density) { 24.dp.toPx() }
                
                val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                val itemWidth = itemInfo?.size?.toFloat() ?: 0f
                val offsetPixels = (fraction * (itemWidth + spacingPx)).toInt()
                
                listState.scrollToItem(index, offsetPixels)
            }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = 48.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(tabs) { index, tabTitle ->
            val isSelected = index == pagerState.currentPage
            
            val alpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.45f,
                label = "settings_tab_alpha"
            )
            
            Text(
                text = tabTitle,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 56.sp,
                    letterSpacing = (-2).sp
                ),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    LocalZuneColors.current.textMuted
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .alpha(alpha)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelected(index) }
                    )
            )
        }
    }
}
