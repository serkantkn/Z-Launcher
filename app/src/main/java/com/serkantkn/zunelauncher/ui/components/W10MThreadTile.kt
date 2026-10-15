package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
 * A conversation pinned to Start.
 *
 * The same idea as the person tile, one step further in: this opens not the address book entry but
 * the conversation itself, which is what somebody who writes to one person all day actually wants
 * on their home screen.
 */
@Composable
fun W10MThreadTile(
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
    spacing: Dp = 8.dp,
    /** Messages in the conversation not read yet. */
    unreadCount: Int = 0,
    /** The newest of them, while there is one; the tile says it. */
    lastMessage: String? = null
) {
    val zuneColors = LocalZuneColors.current
    val shape = TileShape.of(span)
    val news = unreadCount > 0 && !lastMessage.isNullOrBlank() && LocalTileLook.current.notifications
    val hasBack = news && (shape == TileShape.SMALL || shape == TileShape.MEDIUM)

    W10MTileSurface(
        liveKey = "sms:$label",
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
        back = if (!hasBack) null else {
            {
                TileTextBack(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = label,
                    headline = label,
                    body = lastMessage,
                    badgeCount = unreadCount,
                    span = span,
                    gridColumns = gridColumns
                )
            }
        },
        front = {
            val fg = tileForegroundColor(zuneColors.accentColor)
            if (isEditing && !LocalTileIsPreview.current) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            val face = customTileFace()
            val shownLabel = tileLabelText(label)
            if (news && !hasBack && !tileHasPicture()) {
                // The wide and large tiles say what was written, beside the glyph and the count.
                IconicListFace(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    face = face,
                    title = label,
                    count = unreadCount,
                    lines = listOf(TileLine(title = label, body = lastMessage)),
                    span = span,
                    gridColumns = gridColumns
                )
                return@W10MTileSurface
            }
            NamedFace(span, shownLabel, fg) { size, glyphModifier, room ->
                if (tileHasPicture()) return@NamedFace
                // The unread count beside the glyph, as Windows Phone wrote it.
                GlyphCount(
                    count = if (news) unreadCount else 0,
                    iconSize = size,
                    ink = fg,
                    maxWidth = room,
                    modifier = glyphModifier
                ) { glyphSize, sized ->
                    if (face != null) {
                        TileIconImage(face = face, size = glyphSize, ink = fg, contentDescription = null, modifier = sized)
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = fg, modifier = sized)
                    }
                }
            }
            if (news && tileHasPicture()) TileBadge(unreadCount, span, gridColumns, fg)
        }
    )
}
