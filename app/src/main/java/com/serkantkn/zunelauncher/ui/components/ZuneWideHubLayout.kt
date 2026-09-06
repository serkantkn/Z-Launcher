package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/** Left inset of hub content in wide-screen (tablet / desktop split) mode. */
val ZuneWideHubStartPadding: Dp = 72.dp

/** Right inset of hub content in wide-screen mode (lets the next column peek in). */
val ZuneWideHubEndPadding: Dp = 48.dp

/**
 * Giant 96sp panorama title used at the top of every hub in wide-screen mode. The text is lifted
 * by 24dp via a graphics layer so its ascender overflow hugs the status area exactly like the
 * phone-mode parallax header.
 */
@Composable
fun ZuneWideHubTitle(
    text: String,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 24.dp
) {
    val zuneColors = LocalZuneColors.current
    val overflowYPx = with(LocalDensity.current) { (-24).dp.toPx() }
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = 96.sp,
            letterSpacing = (-4).sp,
            lineHeight = 96.sp
        ),
        color = if (zuneColors.isDark) Color.White else Color.Black,
        modifier = modifier
            .padding(start = ZuneWideHubStartPadding, top = 4.dp, bottom = bottomPadding)
            .graphicsLayer { translationY = overflowYPx }
    )
}

/**
 * Wide-screen replacement for the pivot pager: every logical page is laid out side by side as a
 * 360dp column headed by its pivot title in accent color, inside a horizontally scrolling row.
 *
 * @param fillPageHeight When true each column stretches to the row height and the page body is
 * wrapped in a weighted [Box] (pictures / people hubs); when false the column wraps its content
 * (music / settings hubs).
 */
@Composable
fun ZuneWidePanorama(
    tabs: List<String>,
    modifier: Modifier = Modifier.fillMaxSize(),
    fillPageHeight: Boolean = false,
    page: @Composable (index: Int) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = ZuneWideHubStartPadding,
            end = ZuneWideHubEndPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        items(tabs.size) { index ->
            val columnModifier = if (fillPageHeight) {
                Modifier.width(360.dp).fillMaxHeight()
            } else {
                Modifier.width(360.dp)
            }
            Column(modifier = columnModifier) {
                Text(
                    text = tabs[index],
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.accentColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (fillPageHeight) {
                    Box(modifier = Modifier.weight(1f)) {
                        page(index)
                    }
                } else {
                    page(index)
                }
            }
        }
    }
}
