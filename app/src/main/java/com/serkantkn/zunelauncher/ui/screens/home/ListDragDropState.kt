package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

class ListDragDropState(
    val state: LazyListState,
    val isEditMode: Boolean,
    val canSwap: (Int, Int) -> Boolean,
    val onMove: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
    var draggingItemInitialOffset by mutableStateOf(Offset.Zero)
    var draggingItemInitialSize by mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    var totalDragAmount by mutableStateOf(Offset.Zero)

    internal val draggingItem: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    fun startDrag(index: Int) {
        val itemInfo = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (itemInfo != null) {
            draggingItemIndex = index
            draggingItemInitialOffset = Offset(0f, itemInfo.offset.toFloat())
            draggingItemInitialSize = androidx.compose.ui.unit.IntSize(itemInfo.size, itemInfo.size) // Size is height in list
            totalDragAmount = Offset.Zero
        }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        totalDragAmount = Offset.Zero
    }

    fun onDrag(dragAmount: Offset) {
        if (draggingItemIndex == null) return
        totalDragAmount += dragAmount

        val startOffset = draggingItemInitialOffset + totalDragAmount
        val centerOffset = startOffset.y + (draggingItemInitialSize.height / 2f)

        var targetItemIndex: Int? = null

        val directTarget = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != draggingItemIndex && canSwap(draggingItemIndex!!, item.index) &&
                    centerOffset.toInt() in item.offset..(item.offset + item.size)
        }

        if (directTarget != null) {
            targetItemIndex = directTarget.index
        }

        if (targetItemIndex != null && targetItemIndex != draggingItemIndex) {
            onMove(draggingItemIndex!!, targetItemIndex)
            draggingItemIndex = targetItemIndex
        }
    }
}

@Composable
fun rememberListDragDropState(
    listState: LazyListState,
    isEditMode: Boolean,
    canSwap: (Int, Int) -> Boolean,
    onMove: (Int, Int) -> Unit
): ListDragDropState {
    return remember(listState, isEditMode) {
        ListDragDropState(
            state = listState,
            isEditMode = isEditMode,
            canSwap = canSwap,
            onMove = onMove
        )
    }
}
