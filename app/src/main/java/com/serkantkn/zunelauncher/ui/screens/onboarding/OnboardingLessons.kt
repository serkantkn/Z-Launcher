package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * The lessons, played on a full-size copy of the launcher.
 *
 * Each one reports back the moment it is done, and the tour moves on by itself a couple of seconds
 * later - long enough to see the tick appear and understand what was just achieved, short enough
 * that nobody is left wondering whether they have to press something.
 *
 * None of them has to be done: "next" is there throughout. A tour that will not let you past until
 * you have performed a gesture is a test, and nobody asked to sit one.
 */

// -- Swiping between the three screens --------------------------------------

@Composable
fun SwipeLessonPage(onCompleted: () -> Unit) {
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    var reachedLeft by remember { mutableStateOf(false) }
    var reachedRight by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            if (page == 0) reachedLeft = true
            if (page == 2) reachedRight = true
        }
    }

    LaunchedEffect(reachedLeft, reachedRight) {
        if (reachedLeft && reachedRight) onCompleted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SideScreenClone(stringResource(R.string.social_hub))
                1 -> StartScreenClone(editingTile = null, onTileLongPress = { })
                else -> SideScreenClone(stringResource(R.string.apps_hub))
            }
        }

        LessonInstruction(
            title = stringResource(R.string.onboarding_swipe_title),
            line = stringResource(R.string.onboarding_swipe_line),
            ticks = listOf(
                stringResource(R.string.onboarding_swipe_left) to reachedLeft,
                stringResource(R.string.onboarding_swipe_right) to reachedRight
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** The screens either side of start, as their name at the top of an empty page. */
@Composable
private fun SideScreenClone(label: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 28.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 46.sp,
                letterSpacing = (-1).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// -- Holding a tile ---------------------------------------------------------

@Composable
fun TileLessonPage(onCompleted: () -> Unit) {
    var held by remember { mutableStateOf<HubType?>(null) }

    LaunchedEffect(held) {
        if (held != null) onCompleted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The real board with the real tiles: what is learnt here is what happens out there.
        StartScreenClone(editingTile = held, onTileLongPress = { held = it })

        LessonInstruction(
            title = stringResource(R.string.onboarding_tile_title),
            line = stringResource(R.string.onboarding_tile_line),
            ticks = listOf(
                stringResource(
                    if (held != null) R.string.onboarding_tile_done else R.string.onboarding_tile_try
                ) to (held != null)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// -- The pivot --------------------------------------------------------------

@Composable
fun PivotLessonPage(onCompleted: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val tabs = listOf(
        stringResource(R.string.onboarding_pivot_one),
        stringResource(R.string.onboarding_pivot_two)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    var moved by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> if (page != 0) moved = true }
    }

    LaunchedEffect(moved) {
        if (moved) onCompleted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // A hub, at the size a hub really is: the eyebrow, the pivot, the page under it.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_pivot_hub),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                ),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(start = ZuneDimens.ScreenPaddingHorizontal, bottom = 6.dp)
            )
            ZunePivotTabs(tabs = tabs, state = pager, modifier = Modifier.padding(bottom = 18.dp))
            ZuneLoopingPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = 48.dp
                ),
                pageSpacing = 24.dp
            ) { page ->
                Text(
                    text = stringResource(
                        if (page == 0) R.string.onboarding_pivot_body_one
                        else R.string.onboarding_pivot_body_two
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textMuted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        LessonInstruction(
            title = stringResource(R.string.onboarding_pivot_title),
            line = stringResource(R.string.onboarding_pivot_line),
            ticks = listOf(stringResource(R.string.onboarding_pivot_try) to moved),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
