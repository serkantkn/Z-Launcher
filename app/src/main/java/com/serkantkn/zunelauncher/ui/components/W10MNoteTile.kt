package com.serkantkn.zunelauncher.ui.components

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay

/**
 * Windows 10 Mobile live tile for a single pinned note (or, when [note] is null, the
 * "hızlı not" quick-capture tile). Shares the size model, press/drag scale table, flip
 * animation and edit-mode overlay of [W10MHubTile].
 *
 * Front: note icon + title. Back (flips every 6-9 s when the note has content): first lines
 * of the body or the first checklist rows. Locked notes never show their body.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun W10MNoteTile(
    note: Note?,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
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
        label = "w10m_note_tile_scale"
    )

    val strokeColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.55f)

    val tileColor = remember(note?.colorHex, zuneColors.accentColor) {
        val hex = note?.colorHex
        if (hex.isNullOrBlank()) zuneColors.accentColor
        else try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            zuneColors.accentColor
        }
    }

    val backText: String? = remember(note) {
        when {
            note == null || note.isLocked -> null
            note.isChecklist -> note.items.take(4).joinToString("\n") { (if (it.isChecked) "☑ " else "☐ ") + it.text }.ifBlank { null }
            note.content.isNotBlank() -> note.content.trim().take(160)
            else -> null
        }
    }

    var showBack by remember { mutableStateOf(false) }
    LaunchedEffect(backText, isEditing) {
        if (isEditing || backText == null) {
            showBack = false
            return@LaunchedEffect
        }
        while (true) {
            delay((6000..9000).random().toLong())
            showBack = !showBack
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (!isEditing && showBack) -180f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "w10m_note_tile_flip"
    )

    val tileShape = remember(cornerStyle) {
        when (cornerStyle) {
            TileCornerStyle.SHARP -> RoundedCornerShape(0.dp)
            TileCornerStyle.ROUNDED -> RoundedCornerShape(8.dp)
        }
    }

    val iconSize = if (span == 1) 22.dp else if (gridColumns >= 8) 24.dp else 28.dp
    val labelSize = if (span == 1) 11.sp else 14.sp
    val label = note?.displayTitle?.lowercase() ?: "hızlı not"
    val icon = when {
        note == null -> Icons.Default.Add
        note.isLocked -> Icons.Default.Lock
        note.isChecklist -> Icons.Default.Checklist
        else -> Icons.Default.StickyNote2
    }

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
                .background(tileColor.copy(alpha = if (note == null) 0.4f else 0.55f))
                .border(0.5.dp, if (isEditing) zuneColors.accentColor else strokeColor, tileShape)
        ) {
            if (isEditing) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }

            if (kotlin.math.abs(rotation) <= 90f) {
                // Front
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(if (span == 1) Alignment.Center else Alignment.TopStart)
                            .padding(if (span == 1) 0.dp else 10.dp)
                            .size(iconSize)
                    )
                    if (span > 1 || note == null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = labelSize,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White,
                            maxLines = if (span >= 4) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                        )
                    }
                }
            } else {
                // Back (mirrored so the text reads correctly after the 180° flip)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationX = 180f }
                        .padding(10.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = backText ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = Color.White,
                        maxLines = if (span >= 4) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Edit Mode Overlay Buttons (same as W10MHubTile)
        if (isEditing) {
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
