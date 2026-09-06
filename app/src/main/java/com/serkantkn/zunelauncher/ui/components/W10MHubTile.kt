package com.serkantkn.zunelauncher.ui.components

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay

private fun getHubIcon(hubType: HubType): ImageVector {
    return when (hubType) {
        HubType.PHONE -> Icons.Default.Call
        HubType.MESSAGING -> Icons.Default.Email
        HubType.PEOPLE -> Icons.Default.People
        HubType.PICTURES -> Icons.Default.Image
        HubType.MUSIC -> Icons.Default.MusicNote
        HubType.INTERNET -> Icons.Default.Language
        HubType.FILES -> Icons.Default.Folder
        HubType.NOTES -> Icons.Default.StickyNote2
        HubType.EMAIL -> Icons.Default.Mail
        HubType.SETTINGS -> Icons.Default.Settings
        HubType.CLOCK -> Icons.Default.Schedule
        HubType.CALENDAR -> Icons.Default.CalendarMonth
        HubType.HOME -> Icons.Default.Home
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun W10MHubTile(
    hubType: HubType,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    liveSubtitle: String? = null,
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
    photoUris: List<Uri> = emptyList(),
    spacing: Dp = 8.dp
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleTarget = when {
        isDragging -> 1.05f
        isPressed -> 0.93f
        isEditing -> 0.97f
        else -> 1f
    }

    val pressScale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "w10m_hub_tile_scale"
    )

    val strokeColor = if (zuneColors.isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    val isPicturesLive = hubType == HubType.PICTURES && photoUris.isNotEmpty()
    var currentPhoto by remember(photoUris) { mutableStateOf(photoUris.firstOrNull()) }
    var nextPhoto by remember(photoUris) { mutableStateOf(photoUris.getOrNull(1) ?: photoUris.firstOrNull()) }
    var isPhotoFlipped by remember { mutableStateOf(false) }

    // 5-second slideshow for favorite photos with 3D flip animation
    LaunchedEffect(isPicturesLive, photoUris, isEditing) {
        if (isPicturesLive && !isEditing) {
            var index = 0
            while (true) {
                delay(5000)
                if (isEditing) break
                index = (index + 1) % photoUris.size
                if (isPhotoFlipped) {
                    currentPhoto = photoUris[index]
                } else {
                    nextPhoto = photoUris[index]
                }
                isPhotoFlipped = !isPhotoFlipped
            }
        }
    }

    val hasBadge = badgeCount > 0
    var showBack by remember { mutableStateOf(false) }

    LaunchedEffect(hasBadge, liveSubtitle, isEditing, isPicturesLive) {
        if (isPicturesLive) return@LaunchedEffect
        if (isEditing) {
            showBack = false
            return@LaunchedEffect
        }
        if (hasBadge || liveSubtitle != null) {
            while (true) {
                delay((5000..8000).random().toLong())
                showBack = !showBack
            }
        } else {
            showBack = false
        }
    }

    val rotation by animateFloatAsState(
        targetValue = when {
            isEditing -> 0f
            isPicturesLive -> if (isPhotoFlipped) -180f else 0f
            showBack -> -180f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "w10m_hub_tile_flip"
    )

    val tileShape = remember(cornerStyle) {
        when (cornerStyle) {
            TileCornerStyle.SHARP -> RoundedCornerShape(0.dp)
            TileCornerStyle.ROUNDED -> RoundedCornerShape(8.dp)
        }
    }

    val icon = remember(hubType) { getHubIcon(hubType) }

    Box(
        modifier = modifier
            .w10mTileSize(span = span, gridColumns = gridColumns, spacing = spacing)
            .scale(pressScale)
            .alpha(if (isDragging) 0.8f else 1f)
            .graphicsLayer {
                rotationX = rotation
                cameraDistance = 8f * density.density
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (!isEditing) onClick() },
                onLongClick = if (isEditing) null else onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(tileShape)
                .background(zuneColors.accentColor.copy(alpha = if (isPicturesLive) 0.9f else 0.45f))
                .border(0.5.dp, if (isEditing) zuneColors.accentColor else strokeColor, tileShape)
        ) {
            if (isPicturesLive) {
                // Pictures slideshow Live Tile with 3D Flip
                if (kotlin.math.abs(rotation) <= 90f) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        currentPhoto?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = stringResource(R.string.tile_favorite_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Bottom gradient overlay for title legibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                    )
                                )
                        )
                        if (span > 1) {
                            Text(
                                text = stringResource(hubType.titleRes),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (span == 2 && gridColumns >= 8) 9.sp else 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 6.dp, bottom = 4.dp, end = 24.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationX = -180f }
                    ) {
                        nextPhoto?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = stringResource(R.string.tile_favorite_photo),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Bottom gradient overlay for title legibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                    )
                                )
                        )
                        if (span > 1) {
                            Text(
                                text = stringResource(hubType.titleRes),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (span == 2 && gridColumns >= 8) 9.sp else 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 6.dp, bottom = 4.dp, end = 24.dp)
                            )
                        }
                    }
                }
            } else if (kotlin.math.abs(rotation) <= 90f) {
                // Front Side Content
                Box(modifier = Modifier.fillMaxSize()) {
                    val iconSize = when (span) {
                        1 -> 26.dp
                        2 -> if (gridColumns >= 8) 36.dp else 48.dp
                        4 -> if (gridColumns >= 8) 46.dp else 58.dp
                        else -> 64.dp
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(hubType.titleRes),
                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier
                            .size(iconSize)
                            .align(Alignment.Center)
                    )

                    // Badge (Bottom-Right corner)
                    if (badgeCount > 0) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (span == 1 || (span == 2 && gridColumns >= 8)) 12.sp else 20.sp
                            ),
                            color = if (zuneColors.isDark) Color.White else Color.Black,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 2.dp)
                        )
                    }

                    // Label at bottom-left
                    if (span > 1) {
                        Text(
                            text = stringResource(hubType.titleRes),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (span == 2 && gridColumns >= 8) 9.sp else 12.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = if (zuneColors.isDark) {
                                Color.White.copy(alpha = 0.85f)
                            } else {
                                Color.Black.copy(alpha = 0.75f)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 6.dp, bottom = 4.dp, end = 24.dp)
                        )
                    }
                }
            } else {
                // Back Side Content (Live Tile flip)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationX = -180f }
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (zuneColors.isDark) Color.White else Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(hubType.titleRes),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (zuneColors.isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
                            )
                        }

                        if (!liveSubtitle.isNullOrBlank()) {
                            Text(
                                text = liveSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (badgeCount > 0) {
                            Text(
                                text = stringResource(R.string.tile_new_notifications, badgeCount),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (zuneColors.isDark) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }

        // Edit Mode Overlay Buttons
        if (isEditing) {
            // Remove Button (Top Right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onRemoveClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.notes_remove_cap),
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Resize Button (Bottom Right)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White, CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onResizeClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.notes_resize_cap),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

fun Modifier.w10mTileSize(
    span: Int,
    gridColumns: Int,
    spacing: Dp
): Modifier = this.layout { measurable, constraints ->
    val spacingPx = spacing.roundToPx()
    val width = constraints.maxWidth
    val height = when {
        span == 1 -> width
        span == 2 -> width
        span == 4 -> ((width - spacingPx) / 2).coerceAtLeast(1)
        span >= 8 -> ((width - 3 * spacingPx) / 4).coerceAtLeast(1)
        else -> width
    }
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = width,
            maxWidth = width,
            minHeight = height,
            maxHeight = height
        )
    )
    layout(width, height) {
        placeable.placeRelative(0, 0)
    }
}
