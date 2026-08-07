package com.serkantkn.zunelauncher.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.skydoves.cloudy.cloudy

@Composable
fun Modifier.metroGlassBlur(radiusDp: Float = 15f): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            val radiusPx = radiusDp * density
            if (radiusPx > 0f) {
                renderEffect = RenderEffect.createBlurEffect(
                    radiusPx,
                    radiusPx,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
        }
    } else {
        this.cloudy(radius = radiusDp.toInt().coerceAtLeast(1))
    }
}
