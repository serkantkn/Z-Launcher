package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * A website pinned to Start.
 *
 * Windows Phone let any page become a tile, and on a launcher that is the browser's most natural
 * trick: the site sits on the board beside the apps and opens straight into the internet hub.
 */
@Composable
fun W10MWebTile(
    label: String,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 8,
    spacing: Dp = 8.dp
) {
    val zuneColors = LocalZuneColors.current
    val iconSize = if (span == 1) 22.dp else if (gridColumns >= 8) 24.dp else 28.dp

    W10MTileSurface(
        liveKey = "web:$label",
        span = span,
        gridColumns = gridColumns,
        spacing = spacing,
        isEditing = isEditing,
        isDragging = isDragging,
        highlighted = isMergeTarget,
        cornerStyle = cornerStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        onRemoveClick = onRemoveClick,
        onResizeClick = onResizeClick,
        modifier = modifier,
        front = {
            val fg = tileForegroundColor(zuneColors.accentColor)
            if (isEditing && !LocalTileIsPreview.current) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            val face = customTileFace()
            val shownLabel = tileLabelText(label)
            if (!tileHasPicture()) {
                val iconModifier = Modifier
                    .align(if (span == 1) Alignment.Center else Alignment.TopStart)
                    .padding(if (span == 1) 0.dp else 10.dp)
                    .size(iconSize * tileIconScale())
                if (face != null) {
                    TileIconImage(face = face, size = iconSize * tileIconScale(), ink = fg, contentDescription = null, modifier = iconModifier)
                } else {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = fg, modifier = iconModifier)
                }
            }
            if (span > 1 && shownLabel != null) {
                Text(
                    text = shownLabel.lowercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = fg,
                    maxLines = if (span >= 4) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                )
            }
        }
    )
}
