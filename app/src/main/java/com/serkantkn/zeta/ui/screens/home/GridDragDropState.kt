package com.serkantkn.zeta.ui.screens.home

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Drag-to-reorder for the lazy favourites grid. [onMove] is called with grid item indices each
 * time the held tile passes over another one; the state keeps the finger's absolute position so
 * the tile follows it through the grid's own placement animations.
 */
class GridDragDropState(
    val state: LazyGridState,
    private val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
    var draggingItemInitialOffset by mutableStateOf(Offset.Zero)
    var draggingItemInitialSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    var totalDragAmount by mutableStateOf(Offset.Zero)

    internal val draggingItem: LazyGridItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    fun startDrag(index: Int) {
        val itemInfo = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (itemInfo != null) {
            draggingItemIndex = index
            draggingItemInitialOffset = Offset(itemInfo.offset.x.toFloat(), itemInfo.offset.y.toFloat())
            draggingItemInitialSize = itemInfo.size
            totalDragAmount = Offset.Zero
        }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        totalDragAmount = Offset.Zero
    }

    fun onDrag(dragAmount: Offset) {
        val dragging = draggingItemIndex ?: return
        totalDragAmount += dragAmount

        val startOffset = draggingItemInitialOffset + totalDragAmount
        val centerOffset = startOffset + Offset(draggingItemInitialSize.width / 2f, draggingItemInitialSize.height / 2f)

        var targetItemIndex: Int? = null

        // Is the finger's tile squarely over another tile?
        val directTarget = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != dragging &&
                    centerOffset.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                    centerOffset.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
        }

        if (directTarget != null) {
            targetItemIndex = directTarget.index
        } else {
            // Not over a tile, so it may be in the empty space at the end of a row: the slot
            // after the last tile on that row to the left of the finger.
            val leftItem = state.layoutInfo.visibleItemsInfo.lastOrNull { item ->
                item.index != dragging &&
                        centerOffset.y.toInt() in item.offset.y..(item.offset.y + item.size.height) &&
                        centerOffset.x.toInt() > item.offset.x + item.size.width
            }
            if (leftItem != null) targetItemIndex = leftItem.index + 1
        }

        if (targetItemIndex != null && targetItemIndex != dragging) {
            onMove(dragging, targetItemIndex)
            draggingItemIndex = targetItemIndex
            // The start offset and the drag total are deliberately not reset: the finger's
            // absolute position is what places the tile, so layout animations cannot move it.
        }
    }
}

/**
 * The state is kept across recompositions, but [onMove] is read fresh on every call: the one
 * captured when the grid was first composed closed over the favourites list of that moment and
 * wrote it back — stale, with every change made since undone — on the next drag.
 */
@Composable
fun rememberGridDragDropState(
    gridState: LazyGridState,
    onMove: (Int, Int) -> Unit
): GridDragDropState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(gridState) {
        GridDragDropState(state = gridState) { from, to -> currentOnMove(from, to) }
    }
}
