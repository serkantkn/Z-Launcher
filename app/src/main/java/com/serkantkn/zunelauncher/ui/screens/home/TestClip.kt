package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun TestClip() {
    val currentHeaderHeight = 150f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                clip = true
                shape = object : Shape {
                    override fun createOutline(
                        size: Size,
                        layoutDirection: LayoutDirection,
                        density: Density
                    ): Outline {
                        return Outline.Rectangle(
                            Rect(
                                left = 0f,
                                top = currentHeaderHeight,
                                right = size.width,
                                bottom = size.height
                            )
                        )
                    }
                }
            }
    )
}
