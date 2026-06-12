package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.Composable

@Composable
fun TestLayout() {
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height * 0.5f).toInt()) {
            placeable.placeRelative(0, 0)
        }
    }
}
