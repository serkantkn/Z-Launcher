package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.ui.theme.LocalBackgroundSize
import com.serkantkn.zunelauncher.ui.theme.LocalBlurredWallpaperBitmap
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.roundToInt

@Composable
fun ZuneGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    tintColor: Color,
    fallbackColor: Color,
    borderColor: Color = Color.Transparent,
    content: @Composable () -> Unit
) {
    var positionInRoot by remember { mutableStateOf(IntOffset.Zero) }
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(cornerRadius)
    val backgroundCoordinates = com.serkantkn.zunelauncher.ui.theme.LocalBackgroundCoordinates.current

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val position = if (backgroundCoordinates != null && backgroundCoordinates.isAttached && coordinates.isAttached) {
                    backgroundCoordinates.localPositionOf(coordinates, androidx.compose.ui.geometry.Offset.Zero)
                } else {
                    coordinates.positionInRoot()
                }
                positionInRoot = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                surfaceSize = coordinates.size
            }
            .clip(shape)
            .border(0.5.dp, borderColor, shape)
    ) {
        ZuneBackdropBlur(
            positionInRoot = positionInRoot,
            surfaceSize = surfaceSize,
            tintColor = tintColor,
            fallbackColor = fallbackColor,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

@Composable
private fun ZuneBackdropBlur(
    positionInRoot: IntOffset,
    surfaceSize: IntSize,
    tintColor: Color,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    val blurredWallpaper = LocalBlurredWallpaperBitmap.current
    val backgroundSize = LocalBackgroundSize.current
    val wallpaperImage = remember(blurredWallpaper) { blurredWallpaper?.asImageBitmap() }

    Canvas(
        modifier = modifier.background(fallbackColor.copy(alpha = 0.35f))
    ) {
        val image = wallpaperImage
        if (
            image != null &&
            backgroundSize.width > 0 &&
            backgroundSize.height > 0
        ) {
            val containerWidth = backgroundSize.width.toFloat()
            val containerHeight = backgroundSize.height.toFloat()
            
            // This logic perfectly mimics ContentScale.Crop for the FULL background size
            val scale = maxOf(
                containerWidth / image.width.toFloat(),
                containerHeight / image.height.toFloat()
            )
            val scaledImageWidth = image.width * scale
            val scaledImageHeight = image.height * scale
            val imageLeft = (containerWidth - scaledImageWidth) / 2f
            val imageTop = (containerHeight - scaledImageHeight) / 2f

            // Translate the canvas in the opposite direction of this surface's position.
            // This means we draw the full blurred background image relative to the screen root,
            // but because the Canvas is clipped to the surface size, we only see the correct portion!
            translate(
                left = -positionInRoot.x.toFloat(), 
                top = -positionInRoot.y.toFloat()
            ) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset(imageLeft.roundToInt(), imageTop.roundToInt()),
                    dstSize = IntSize(scaledImageWidth.roundToInt(), scaledImageHeight.roundToInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )
            }
        }

        drawRect(color = tintColor)
    }
}
