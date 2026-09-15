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
 * State holder for a hub pager, looping or not.
 *
 * Looping is the Windows Phone panorama behaviour and the default: the underlying [pagerState]
 * has `ZUNE_PAGER_LOOP_COUNT * pageCount` pages and starts in the middle of that range, so the
 * pivot can be swiped both ways without ever reaching an edge.
 *
 * A pivot that sits inside another pager wants the opposite. If it loops, it swallows the swipe
 * for ever and the screen behind it can never be reached; with [isLooping] off it stops at its two
 * ends and hands the rest of the drag to whatever is carrying it.
 *
 * All public helpers speak in *logical* pages (`0 until pageCount`); use [pagerState] directly
 * only for raw scroll-driven effects (parallax headers etc.).
 */
@Stable
class ZuneLoopingPagerState internal constructor(
    val pagerState: PagerState,
    private val pageCountState: State<Int>,
    /** Whether the pivot wraps around, or stops at its first and last page. */
    val isLooping: Boolean = true
) {
    /** Number of logical pages (the real pivot count). */
    val pageCount: Int get() = pageCountState.value

    /** Current logical page, normalized to `0 until pageCount`. */
    val currentPage: Int get() = logicalPage(pagerState.currentPage)

    /** Maps a raw pager index to its logical page (`0 until pageCount`). */
    fun logicalPage(rawPage: Int): Int {
        val size = pageCount
        if (size <= 0) return 0
        if (!isLooping) return rawPage.coerceIn(0, size - 1)
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
        // Without the loop there is only one of each page, and it is where it says it is.
        if (!isLooping) return logical.coerceIn(0, size - 1)
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
 * [initialPage]. [pageCount] may change over time (e.g. dynamic tag pivots); the underlying pager
 * re-reads it, and normalization always uses the latest value.
 *
 * Pass `looping = false` for a pivot that lives inside another pager, so its two ends give the
 * swipe back rather than going round for ever.
 */
@Composable
fun rememberLoopingPagerState(
    pageCount: Int,
    initialPage: Int = 0,
    looping: Boolean = true
): ZuneLoopingPagerState {
    val pageCountState = rememberUpdatedState(pageCount)
    val pagerState = rememberPagerState(
        initialPage = if (looping) {
            (ZUNE_PAGER_LOOP_COUNT / 2) * pageCount + initialPage
        } else {
            initialPage
        },
        pageCount = {
            if (looping) ZUNE_PAGER_LOOP_COUNT * pageCountState.value else pageCountState.value
        }
    )
    return remember(pagerState, looping) {
        ZuneLoopingPagerState(pagerState, pageCountState, looping)
    }
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
