package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.components.W10MHubTile
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * The lessons.
 *
 * Nothing here is a picture of the launcher: every one is the launcher, small. The swipe lesson is
 * a real pager, the tile is the real tile with its real edit chrome, the pivot is the real pivot.
 * Somebody who does the thing once in a box this size has done it, and will do it again on the
 * screen itself without being told twice.
 *
 * None of them has to be done. A tour that will not let you past until you have performed a
 * gesture is a test, and nobody asked to sit one.
 */

// -- Swiping between the three screens --------------------------------------

@Composable
fun SwipeLessonPage() {
    val zuneColors = LocalZuneColors.current
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    var reachedLeft by remember { mutableStateOf(false) }
    var reachedRight by remember { mutableStateOf(false) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            if (page == 0) reachedLeft = true
            if (page == 2) reachedRight = true
        }
    }

    OnboardingPage(
        title = stringResource(R.string.onboarding_swipe_title),
        line = stringResource(R.string.onboarding_swipe_line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .border(2.dp, zuneColors.textDim.copy(alpha = 0.4f))
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                MockScreen(
                    label = stringResource(
                        when (page) {
                            0 -> R.string.social_hub
                            1 -> R.string.onboarding_swipe_start
                            else -> R.string.apps_hub
                        }
                    ),
                    isStart = page == 1
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LessonTick(done = reachedLeft, label = stringResource(R.string.onboarding_swipe_left))
            LessonTick(done = reachedRight, label = stringResource(R.string.onboarding_swipe_right))
        }
    }
}

/** One of the three screens, as a name on a panel. */
@Composable
private fun MockScreen(label: String, isStart: Boolean) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isStart) zuneColors.accentColor.copy(alpha = 0.18f) else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 30.sp
            ),
            color = if (isStart) zuneColors.accentColor else zuneColors.textMuted,
            textAlign = TextAlign.Center
        )
    }
}

// -- Holding a tile ---------------------------------------------------------

@Composable
fun TileLessonPage() {
    var isEditing by remember { mutableStateOf(false) }

    OnboardingPage(
        title = stringResource(R.string.onboarding_tile_title),
        line = stringResource(R.string.onboarding_tile_line)
    ) {
        Box(modifier = Modifier.size(150.dp)) {
            // The real tile, with the real edit chrome on it: what is learnt here is what happens
            // on the start screen, not an impression of it.
            W10MHubTile(
                hubType = HubType.PICTURES,
                span = 2,
                gridColumns = 4,
                spacing = 8.dp,
                isEditing = isEditing,
                isDragging = false,
                cornerStyle = TileCornerStyle.ROUNDED,
                onClick = { },
                onLongClick = { isEditing = true },
                onRemoveClick = { isEditing = false },
                onResizeClick = { }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        LessonTick(
            done = isEditing,
            label = stringResource(
                if (isEditing) R.string.onboarding_tile_done else R.string.onboarding_tile_try
            )
        )
    }
}

// -- The pivot --------------------------------------------------------------

@Composable
fun PivotLessonPage() {
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

    OnboardingPage(
        title = stringResource(R.string.onboarding_pivot_title),
        line = stringResource(R.string.onboarding_pivot_line)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .border(2.dp, zuneColors.textDim.copy(alpha = 0.4f))
                .padding(vertical = 12.dp)
        ) {
            ZunePivotTabs(tabs = tabs, state = pager, fontSize = 34.sp, startPadding = 16.dp)
            ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = stringResource(
                            if (page == 0) R.string.onboarding_pivot_body_one
                            else R.string.onboarding_pivot_body_two
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        LessonTick(done = moved, label = stringResource(R.string.onboarding_pivot_try))
    }
}

// -- Shared -----------------------------------------------------------------

/** A small square that fills with the accent once the thing has been done. */
@Composable
private fun LessonTick(done: Boolean, label: String) {
    val zuneColors = LocalZuneColors.current
    val fill by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = tween(220),
        label = "lesson_tick"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (done) zuneColors.accentColor else zuneColors.textDim)
                .background(zuneColors.accentColor.copy(alpha = fill)),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) zuneColors.accentColor else zuneColors.textMuted,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
