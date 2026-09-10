package com.serkantkn.zunelauncher.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.toImageBitmap

/**
 * A pinned app on Start: its icon, its name in the corner and the number of notifications waiting.
 * When a notification carries text the tile turns over to show who it was from and what it said.
 */
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
    tileKey: String = label,
    notificationCount: Int = 0,
    notificationTitle: String? = null,
    notificationText: String? = null,
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
    spacing: Dp = 8.dp
) {
    val zuneColors = LocalZuneColors.current
    val hasNotifications = notificationCount > 0 &&
        (!notificationTitle.isNullOrBlank() || !notificationText.isNullOrBlank())
    val bitmap = icon?.let { drawable -> remember(drawable) { drawable.toImageBitmap() } }

    W10MTileSurface(
        liveKey = "app:$tileKey",
        span = span,
        gridColumns = gridColumns,
        spacing = spacing,
        isEditing = isEditing,
        isDragging = isDragging,
        cornerStyle = cornerStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        onRemoveClick = onRemoveClick,
        onResizeClick = onResizeClick,
        modifier = modifier,
        back = if (hasNotifications) {
            {
                val fg = tileForegroundColor()
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        bitmap?.let {
                            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = fg.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (notificationCount > 1) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "+$notificationCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = if (zuneColors.isDark) Color.White else fg
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (!notificationTitle.isNullOrBlank()) {
                        Text(
                            text = notificationTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!notificationText.isNullOrBlank()) {
                        Text(
                            text = notificationText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = fg.copy(alpha = 0.85f),
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
        } else null,
        front = {
            val fg = tileForegroundColor()
            if (isEditing) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            bitmap?.let {
                val iconSize = when {
                    span == 1 -> 26.dp
                    span == 2 -> if (gridColumns >= 8) 36.dp else 46.dp
                    span == 4 -> if (gridColumns >= 8) 46.dp else 56.dp
                    else -> 62.dp
                }
                Image(
                    bitmap = it,
                    contentDescription = label,
                    modifier = Modifier.size(iconSize).align(Alignment.Center)
                )
            }
            TileBadge(notificationCount, span, gridColumns, fg)
            TileLabel(label, span, gridColumns, fg)
        }
    )
}
