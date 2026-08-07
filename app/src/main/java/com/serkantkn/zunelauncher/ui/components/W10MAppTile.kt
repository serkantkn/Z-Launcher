package com.serkantkn.zunelauncher.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.toImageBitmap
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun W10MAppTile(
    label: String,
    icon: Drawable?,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    notificationCount: Int = 0,
    notificationTitle: String? = null,
    notificationText: String? = null
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
        label = "w10m_tile_scale"
    )

    val strokeColor = if (zuneColors.isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }

    val aspectRatio = when (span) {
        4 -> 2f
        else -> 1f
    }

    val hasNotifications = notificationCount > 0
    var showBack by remember { mutableStateOf(false) }

    LaunchedEffect(hasNotifications) {
        if (hasNotifications) {
            while (true) {
                delay((4000..7000).random().toLong())
                showBack = !showBack
            }
        } else {
            showBack = false
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (showBack) 180f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "w10m_tile_flip"
    )

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
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
                .clip(RoundedCornerShape(8.dp))
                .background(zuneColors.accentColor.copy(alpha = 0.4f))
                .border(0.5.dp, if (isEditing) zuneColors.accentColor else strokeColor, RoundedCornerShape(8.dp))
        ) {
            // Dim overlay in edit mode
            if (isEditing) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }

            if (rotation <= 90f) {
                // Front Side Content
                icon?.let { drawable ->
                    val bitmap = remember(drawable) { drawable.toImageBitmap() }
                    val iconSize = when (span) {
                        1 -> 24.dp
                        2 -> 40.dp
                        else -> 48.dp
                    }
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = label,
                            modifier = Modifier.size(iconSize)
                        )
                        
                        if (notificationCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = notificationCount.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (span == 1) 16.sp else 24.sp
                                ),
                                color = if (zuneColors.isDark) Color.White else Color.Black
                            )
                        }
                    }
                }

                // App label at bottom-left
                if (span > 1) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
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
            } else {
                // Back Side Content
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            rotationX = 180f
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            icon?.let { drawable ->
                                val bitmap = remember(drawable) { drawable.toImageBitmap() }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (zuneColors.isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (notificationCount > 1) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "+$notificationCount",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = zuneColors.accentColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (!notificationTitle.isNullOrBlank()) {
                            Text(
                                text = notificationTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = if (zuneColors.isDark) Color.White else Color.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!notificationText.isNullOrBlank()) {
                            Text(
                                text = notificationText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp
                                ),
                                color = if (zuneColors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
                                maxLines = when (span) {
                                    1 -> 1
                                    2 -> 2
                                    else -> 3
                                },
                                overflow = TextOverflow.Ellipsis
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
                    contentDescription = "Kaldır",
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
                    contentDescription = "Boyutlandır",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
