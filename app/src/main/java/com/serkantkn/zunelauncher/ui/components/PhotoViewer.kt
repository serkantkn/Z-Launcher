package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import kotlin.math.abs

/**
 * Full-screen photo viewer overlay with grow-from-thumbnail enter animation,
 * shrink-to-thumbnail exit animation, dynamic drag-to-shrink scaling, and pinch-to-zoom.
 */
@Composable
fun PhotoViewer(
    photo: MediaImage?,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    AnimatedVisibility(
        visible = photo != null,
        enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.2f, animationSpec = tween(350, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.15f, animationSpec = tween(280, easing = FastOutSlowInEasing))
    ) {
        if (photo == null) return@AnimatedVisibility

        key(photo.uri) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var swipeOffset by remember { mutableFloatStateOf(0f) }

            val dragScaleFactor = remember(swipeOffset) {
                (1f - (abs(swipeOffset) / 750f)).coerceIn(0.35f, 1f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - abs(swipeOffset) / 800f).coerceIn(0.15f, 1f)))
                    .pointerInput(photo.uri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .pointerInput(photo.uri) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (abs(swipeOffset) > 200f) {
                                    onDismiss()
                                }
                                swipeOffset = 0f
                            }
                        ) { change, dragAmount ->
                            if (scale == 1f) {
                                swipeOffset += dragAmount
                                change.consume()
                            }
                        }
                    }
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale * dragScaleFactor
                            scaleY = scale * dragScaleFactor
                            translationX = offset.x
                            translationY = offset.y + swipeOffset
                        }
                        .clickable(enabled = scale == 1f) {
                            onDismiss()
                        }
                )

                // Top Controls Bar (Close & Favorite)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) ZuneColors.Pink else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
