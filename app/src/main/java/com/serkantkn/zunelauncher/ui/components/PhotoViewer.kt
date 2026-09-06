package com.serkantkn.zunelauncher.ui.components

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.MediaImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen photo viewer overlay supporting horizontal swiping between photos,
 * independent pinch-to-zoom without dismiss conflict, double-tap zoom, and smooth vertical drag-to-dismiss.
 */
@Composable
fun PhotoViewer(
    photos: List<MediaImage>?,
    initialIndex: Int = 0,
    favoritePhotoIds: Set<String>,
    onDismiss: () -> Unit,
    onToggleFavorite: (MediaImage) -> Unit,
    onEditPhoto: (MediaImage) -> Unit = {},
    onDeletePhoto: (MediaImage) -> Unit = {}
) {
    AnimatedVisibility(
        visible = !photos.isNullOrEmpty(),
        enter = fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.88f, animationSpec = tween(280, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 0.88f, animationSpec = tween(220, easing = FastOutSlowInEasing))
    ) {
        if (photos.isNullOrEmpty()) return@AnimatedVisibility

        val safeInitialIndex = initialIndex.coerceIn(0, photos.lastIndex)
        val pagerState = rememberPagerState(
            initialPage = safeInitialIndex,
            pageCount = { photos.size }
        )

        var currentScale by remember { mutableFloatStateOf(1f) }
        var currentDragOffsetY by remember { mutableFloatStateOf(0f) }
        var showControls by remember { mutableStateOf(true) }

        val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos[0]
        val isFavorite = currentPhoto.id.toString() in favoritePhotoIds

        val backgroundAlpha = (1f - (abs(currentDragOffsetY) / 600f)).coerceIn(0.2f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale == 1f && currentDragOffsetY == 0f,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = photos[page]
                val isCurrentPage = page == pagerState.currentPage

                ZoomablePhotoItem(
                    photo = photo,
                    isCurrentPage = isCurrentPage,
                    onScaleChanged = { scale ->
                        if (isCurrentPage) {
                            currentScale = scale
                        }
                    },
                    onDragOffsetYChanged = { dragY ->
                        if (isCurrentPage) {
                            currentDragOffsetY = dragY
                        }
                    },
                    onDismiss = onDismiss,
                    onToggleControls = { showControls = !showControls }
                )
            }

            // Top Info Bar (Photo Counter)
            AnimatedVisibility(
                visible = showControls && currentDragOffsetY == 0f && photos.size > 1,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Windows Phone Application Bar
            AnimatedVisibility(
                visible = showControls && currentDragOffsetY == 0f,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                WindowsPhoneBottomBar(
                    actions = listOf(
                        WpBarAction(
                            icon = Icons.Default.Edit,
                            label = stringResource(R.string.common_edit),
                            onClick = { onEditPhoto(currentPhoto) }
                        ),
                        WpBarAction(
                            icon = Icons.Default.Delete,
                            label = stringResource(R.string.common_delete),
                            onClick = { onDeletePhoto(currentPhoto) }
                        ),
                        WpBarAction(
                            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            label = stringResource(R.string.common_favorite),
                            onClick = { onToggleFavorite(currentPhoto) }
                        ),
                        WpBarAction(
                            icon = Icons.Default.Close,
                            label = stringResource(R.string.common_close),
                            onClick = onDismiss
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhotoItem(
    photo: MediaImage,
    isCurrentPage: Boolean,
    onScaleChanged: (Float) -> Unit,
    onDragOffsetYChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    onToggleControls: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val dragOffsetYAnim = remember { Animatable(0f) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            dragOffsetYAnim.snapTo(0f)
            onScaleChanged(1f)
            onDragOffsetYChanged(0f)
        }
    }

    val dragScaleFactor = (1f - (abs(dragOffsetYAnim.value) / 750f)).coerceIn(0.45f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = {
                        if (scale > 1.05f) {
                            coroutineScope.launch {
                                val scaleAnim = Animatable(scale)
                                val offsetAnimX = Animatable(offset.x)
                                val offsetAnimY = Animatable(offset.y)
                                launch { scaleAnim.animateTo(1f, tween(200)) { scale = value; onScaleChanged(value) } }
                                launch { offsetAnimX.animateTo(0f, tween(200)) { offset = offset.copy(x = value) } }
                                launch { offsetAnimY.animateTo(0f, tween(200)) { offset = offset.copy(y = value) } }
                            }
                        } else {
                            onToggleControls()
                        }
                    },
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            val targetScale = if (scale > 1.2f) 1f else 2.5f
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val targetOffset = if (targetScale == 1f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (centerX - tapOffset.x) * (targetScale - 1f),
                                    y = (centerY - tapOffset.y) * (targetScale - 1f)
                                )
                            }

                            val scaleAnim = Animatable(scale)
                            val offsetAnimX = Animatable(offset.x)
                            val offsetAnimY = Animatable(offset.y)

                            launch {
                                scaleAnim.animateTo(targetScale, tween(250, easing = FastOutSlowInEasing)) {
                                    scale = value
                                    onScaleChanged(value)
                                }
                            }
                            launch {
                                offsetAnimX.animateTo(targetOffset.x, tween(250, easing = FastOutSlowInEasing)) {
                                    offset = offset.copy(x = value)
                                }
                            }
                            launch {
                                offsetAnimY.animateTo(targetOffset.y, tween(250, easing = FastOutSlowInEasing)) {
                                    offset = offset.copy(y = value)
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(photo.id) {
                awaitEachGesture {
                    var isZooming = false
                    var isVerticalDrag = false
                    var isHorizontalSwipe = false

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            // Two or more fingers: Pure pinch-to-zoom & pan. Dismiss gesture is NEVER active.
                            isZooming = true
                            isVerticalDrag = false
                            if (dragOffsetYAnim.value != 0f) {
                                coroutineScope.launch { dragOffsetYAnim.snapTo(0f) }
                                onDragOffsetYChanged(0f)
                            }

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            onScaleChanged(newScale)

                            if (newScale > 1f) {
                                val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                offset = Offset.Zero
                            }

                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1) {
                            val change = event.changes[0]

                            if (scale > 1.05f && !isVerticalDrag) {
                                // Zoomed in: 1-finger panning within image bounds
                                val pan = change.position - change.previousPosition
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                                change.consume()
                            } else if (scale <= 1.05f && !isZooming) {
                                // At 1x scale: separate vertical dismiss drag from horizontal pager swipe
                                val deltaY = change.position.y - change.previousPosition.y
                                val deltaX = change.position.x - change.previousPosition.x

                                if (!isVerticalDrag && !isHorizontalSwipe) {
                                    if (abs(deltaY) > 8f && abs(deltaY) > abs(deltaX) * 1.35f) {
                                        isVerticalDrag = true
                                    } else if (abs(deltaX) > 8f) {
                                        isHorizontalSwipe = true
                                    }
                                }

                                if (isVerticalDrag) {
                                    val newDragY = dragOffsetYAnim.value + deltaY
                                    coroutineScope.launch {
                                        dragOffsetYAnim.snapTo(newDragY)
                                    }
                                    onDragOffsetYChanged(newDragY)
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // All pointers released
                    if (isVerticalDrag) {
                        if (abs(dragOffsetYAnim.value) > 170f) {
                            onDismiss()
                        } else {
                            coroutineScope.launch {
                                dragOffsetYAnim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                onDragOffsetYChanged(0f)
                            }
                        }
                    }

                    if (scale < 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                        onScaleChanged(1f)
                    }
                }
            },
        contentAlignment = Alignment.Center
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
                    translationY = offset.y + dragOffsetYAnim.value
                }
        )
    }
}
