package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable

@Composable
fun TestDispatchRawDelta() {
    val state = rememberLazyGridState()
    val delta = 10f
    state.dispatchRawDelta(delta)
}
