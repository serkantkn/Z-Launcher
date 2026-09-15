package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.background
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
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

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

            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                // The name has to stay readable over whatever the photograph happens to be.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                )
            } else {
                Text(
                    text = label.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = if (span == 1) 22.sp else 40.sp
                    ),
                    color = fg.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (isEditing) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }

            if (span == 1) {
                if (photoUri == null) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.align(Alignment.Center).size(22.dp)
                    )
                }
            } else {
                Text(
                    text = label.lowercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 14.sp,
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
    )
}
