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
            NamedFace(span, shownLabel.takeUnless { tileHasPicture() && span == 1 }, fg) { size, glyphModifier ->
                if (tileHasPicture()) return@NamedFace
                if (face != null) {
                    TileIconImage(face = face, size = size, ink = fg, contentDescription = null, modifier = glyphModifier.size(size))
                } else {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = fg, modifier = glyphModifier.size(size))
                }
            }
        }
    )
}
