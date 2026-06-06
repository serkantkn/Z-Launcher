package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

class GridDragDropState(
    val state: LazyGridState,
    val isEditMode: Boolean,
    val canSwap: (Int, Int) -> Boolean,
    val onMove: (Int, Int) -> Unit
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
        if (draggingItemIndex == null) return
        totalDragAmount += dragAmount

        val startOffset = draggingItemInitialOffset + totalDragAmount
        val centerOffset = startOffset + Offset(draggingItemInitialSize.width / 2f, draggingItemInitialSize.height / 2f)

        var targetItemIndex: Int? = null

        // İlk olarak doğrudan üzerine geldiğimiz bir karo var mı kontrol et
        val directTarget = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != draggingItemIndex && canSwap(draggingItemIndex!!, item.index) &&
                    centerOffset.x.toInt() in item.offset.x..(item.offset.x + item.size.width) &&
                    centerOffset.y.toInt() in item.offset.y..(item.offset.y + item.size.height)
        }

        if (directTarget != null) {
            targetItemIndex = directTarget.index
        } else {
            // Eğer doğrudan bir karonun üzerinde değilsek, boş bir alanda (satır sonu gibi) olabiliriz.
            // İşaretçinin solunda kalan aynı satırdaki en son karoyu bulalım:
            val leftItem = state.layoutInfo.visibleItemsInfo.lastOrNull { item ->
                item.index != draggingItemIndex && canSwap(draggingItemIndex!!, item.index) &&
                        centerOffset.y.toInt() in item.offset.y..(item.offset.y + item.size.height) &&
                        centerOffset.x.toInt() > item.offset.x + item.size.width
            }
            if (leftItem != null) {
                val potentialTarget = leftItem.index + 1
                if (canSwap(draggingItemIndex!!, potentialTarget)) {
                    targetItemIndex = potentialTarget
                }
            }
        }

        if (targetItemIndex != null && targetItemIndex != draggingItemIndex) {
            onMove(draggingItemIndex!!, targetItemIndex)
            draggingItemIndex = targetItemIndex
            // Not: draggingItemInitialOffset ve totalDragAmount sıfırlanmıyor!
            // Bu sayede parmağın mutlak konumu korunur ve layout animasyonlarından etkilenmez.
        }
    }
}

@Composable
fun rememberGridDragDropState(
    gridState: LazyGridState,
    isEditMode: Boolean,
    canSwap: (Int, Int) -> Boolean,
    onMove: (Int, Int) -> Unit
): GridDragDropState {
    return remember(gridState, isEditMode) {
        GridDragDropState(
            state = gridState,
            isEditMode = isEditMode,
            canSwap = canSwap,
            onMove = onMove
        )
    }
}
