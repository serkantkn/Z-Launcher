package com.serkantkn.zunelauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.skydoves.cloudy.cloudy

@Composable
fun Modifier.metroGlassBlur(radiusDp: Float = 15f): Modifier {
    return this.cloudy(radius = radiusDp.toInt().coerceAtLeast(1))
}
