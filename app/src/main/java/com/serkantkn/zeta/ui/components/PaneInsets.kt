package com.serkantkn.zeta.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen

/**
 * Which edges of the screen the space a hub is drawn in touches. A hub on its own touches all of
 * them; in a split, the top pane does not reach the navigation bar and the bottom one does not
 * reach the status bar, so neither leaves room for a bar that is not there.
 */
data class PaneEdges(val top: Boolean = true, val bottom: Boolean = true)

val LocalPaneEdges = staticCompositionLocalOf { PaneEdges() }

/** The room the status bar takes at the top of the space being drawn in: none away from the top. */
@Composable
fun statusBarTop(): Dp =
    if (LocalPaneEdges.current.top) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp

/** The room the navigation bar takes at the bottom of the space being drawn in: none away from it. */
@Composable
fun navigationBarBottom(): Dp =
    if (LocalPaneEdges.current.bottom) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp

/**
 * Draws [content] as a screen of its own of [widthDp] × [heightDp] - a pane of a split. Whatever
 * in it asks how big the screen is (the configuration's sizes, whether it is wide) is told the
 * pane's size; the bars it does not touch ([edges]) take no room in it.
 */
@Composable
fun PaneScope(
    widthDp: Int,
    heightDp: Int,
    edges: PaneEdges,
    wide: Boolean = widthDp >= WIDE_FROM_DP,
    content: @Composable (Modifier) -> Unit
) {
    val base = LocalConfiguration.current
    val config = remember(base, widthDp, heightDp) {
        Configuration(base).apply {
            screenWidthDp = widthDp
            screenHeightDp = heightDp
            smallestScreenWidthDp = minOf(widthDp, heightDp)
            orientation = if (widthDp > heightDp) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
    }
    // The bars it does not reach are taken off for every padding modifier inside.
    val consumed = when {
        !edges.top && !edges.bottom -> Modifier
            .consumeWindowInsets(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        !edges.top -> Modifier.consumeWindowInsets(WindowInsets.statusBars.only(WindowInsetsSides.Top))
        !edges.bottom -> Modifier.consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        else -> Modifier
    }
    CompositionLocalProvider(
        LocalConfiguration provides config,
        LocalIsWideScreen provides wide,
        LocalPaneEdges provides edges
    ) {
        content(consumed)
    }
}

/** From this width a hub lays itself out as on a tablet: headings at the side, a pane beside. */
const val WIDE_FROM_DP = 600
