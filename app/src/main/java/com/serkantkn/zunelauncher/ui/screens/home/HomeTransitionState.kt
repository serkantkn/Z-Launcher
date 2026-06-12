package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

class HomeTransitionState(
    val maxTransitionPx: Float
) {
    var transitionOffset by mutableStateOf(0f) // 0f (Hubs) to maxTransitionPx (Favorites)
    
    val progress: Float
        get() = if (maxTransitionPx > 0) (transitionOffset / maxTransitionPx).coerceIn(0f, 1f) else 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            
            // Dragging DOWN (delta > 0) -> We want to collapse Favorites BEFORE Hubs/Favorites scroll up
            if (delta > 0 && transitionOffset > 0f) {
                val consumed = delta.coerceAtMost(transitionOffset)
                transitionOffset -= consumed
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val delta = available.y
            
            // Dragging UP (delta < 0) -> Hubs/Favorites couldn't scroll further, so we expand Favorites
            if (delta < 0 && transitionOffset < maxTransitionPx) {
                val remaining = maxTransitionPx - transitionOffset
                val consumedActually = (-delta).coerceAtMost(remaining)
                transitionOffset += consumedActually
                return Offset(0f, -consumedActually)
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberHomeTransitionState(
    maxTransitionPx: Float
): HomeTransitionState {
    return remember(maxTransitionPx) { HomeTransitionState(maxTransitionPx) }
}
