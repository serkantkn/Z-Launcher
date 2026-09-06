package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Number of times the logical page set is repeated inside the underlying [PagerState] so that the
 * hub pivots can be swiped "forever" in both directions (Windows Phone panorama behaviour).
 */
const val ZUNE_PAGER_LOOP_COUNT = 1000

/**
 * State holder for an infinitely looping hub pager.
 *
 * The underlying [pagerState] has `ZUNE_PAGER_LOOP_COUNT * pageCount` pages and starts in the
 * middle of that range, so the user can swipe both ways without ever reaching an edge. All
 * public helpers speak in *logical* pages (`0 until pageCount`); use [pagerState] directly only
 * for raw scroll-driven effects (parallax headers etc.).
 */
@Stable
class ZuneLoopingPagerState internal constructor(
    val pagerState: PagerState,
    private val pageCountState: State<Int>
) {
    /** Number of logical pages (the real pivot count). */
    val pageCount: Int get() = pageCountState.value

    /** Current logical page, normalized to `0 until pageCount`. */
    val currentPage: Int get() = logicalPage(pagerState.currentPage)

    /** Maps a raw pager index to its logical page (`0 until pageCount`). */
    fun logicalPage(rawPage: Int): Int {
        val size = pageCount
        if (size <= 0) return 0
        return ((rawPage % size) + size) % size
    }

    /**
     * Raw pager index of the instance of [logical] nearest to the current raw page, so the
     * pivot header tap always scrolls the short way round (never more than half a loop).
     */
    fun nearestRawPage(logical: Int): Int {
        val size = pageCount
        val current = pagerState.currentPage
        if (size <= 0) return current
        var diff = logical - logicalPage(current)
        if (diff > size / 2) {
            diff -= size
        } else if (diff < -size / 2) {
            diff += size
        }
        return current + diff
    }

    /** Animates to the nearest instance of the logical page [logical]. */
    suspend fun animateScrollToPage(logical: Int) {
        pagerState.animateScrollToPage(nearestRawPage(logical))
    }

    /** Snaps (no animation) to the nearest instance of the logical page [logical]. */
    suspend fun scrollToPage(logical: Int) {
        pagerState.scrollToPage(nearestRawPage(logical))
    }
}

/**
 * Remembers a [ZuneLoopingPagerState] for [pageCount] logical pages, starting on logical page
 * [initialPage] in the middle of the loop range. [pageCount] may change over time (e.g. dynamic
 * tag pivots); the underlying pager re-reads it, and normalization always uses the latest value.
 */
@Composable
fun rememberLoopingPagerState(pageCount: Int, initialPage: Int = 0): ZuneLoopingPagerState {
    val pageCountState = rememberUpdatedState(pageCount)
    val pagerState = rememberPagerState(
        initialPage = (ZUNE_PAGER_LOOP_COUNT / 2) * pageCount + initialPage,
        pageCount = { ZUNE_PAGER_LOOP_COUNT * pageCountState.value }
    )
    return remember(pagerState) { ZuneLoopingPagerState(pagerState, pageCountState) }
}

/**
 * [HorizontalPager] bound to a [ZuneLoopingPagerState]; the [content] lambda receives the
 * *logical* page index. Scroll physics parameters are passed straight through to
 * [HorizontalPager] with its own defaults so each hub keeps its current feel.
 */
@Composable
fun ZuneLoopingPager(
    state: ZuneLoopingPagerState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 0.dp,
    beyondViewportPageCount: Int = PagerDefaults.BeyondViewportPageCount,
    flingBehavior: TargetedFlingBehavior = PagerDefaults.flingBehavior(state = state.pagerState),
    userScrollEnabled: Boolean = true,
    content: @Composable PagerScope.(logicalPage: Int) -> Unit
) {
    HorizontalPager(
        state = state.pagerState,
        modifier = modifier,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        beyondViewportPageCount = beyondViewportPageCount,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled
    ) { page ->
        content(state.logicalPage(page))
    }
}
