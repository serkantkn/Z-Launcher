package com.serkantkn.zeta.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.ui.theme.LocalZuneColors

/**
 * A person pinned to Start — the tile Windows Phone was known for, a face on the board with the
 * name across the bottom. Somebody with no picture gets their initial instead.
 */
@Composable
fun W10MPersonTile(
    label: String,
    photoUri: String?,
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
        liveKey = "person:$label",
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
            val shownLabel = tileLabelText(label)
            TileFace(span) { m ->
                if (photoUri != null) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    // The name has to stay readable over whatever the photograph happens to be.
                    if (!m.isSmall) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(m.dp(if (m.isLarge) 90f else 56f))
                                .align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        )
                    }
                } else {
                    // Somebody with no picture: their initial, as large as the tile allows.
                    Text(
                        text = label.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = (minOf(m.width, m.height).value * if (m.isSmall) 0.5f else 0.42f).sp
                        ),
                        color = fg.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.Center).offset(y = if (m.isSmall) 0.dp else -m.dp(8f))
                    )
                }

                if (isEditing && !LocalTileIsPreview.current) {
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
                }

                if (!m.isSmall && shownLabel != null) {
                    Text(
                        text = shownLabel.lowercase(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = m.text(when (m.shape) { TileShape.MEDIUM -> 17f; TileShape.WIDE -> 22f; else -> 30f }),
                            fontWeight = FontWeight.Light
                        ),
                        color = if (photoUri != null) Color.White else fg,
                        maxLines = if (m.isLarge) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = m.pad, end = m.pad, bottom = m.dp(8f))
                    )
                }
            }
        }
    )
}
