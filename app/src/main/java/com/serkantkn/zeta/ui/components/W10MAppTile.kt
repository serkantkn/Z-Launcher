package com.serkantkn.zeta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.util.TileIconFace

/**
 * A pinned app on Start: its icon, its name in the corner and the number of notifications waiting.
 * When a notification carries text the tile turns over to show who it was from and what it said.
 */
@Composable
fun W10MAppTile(
    label: String,
    face: TileIconFace,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileKey: String = label,
    notificationCount: Int = 0,
    notificationTitle: String? = null,
    notificationText: String? = null,
    /** Several notifications, newest first, gone round one at each turn; in place of the two above. */
    notifications: List<TileLine> = emptyList(),
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
    spacing: Dp = 8.dp
) {
    val look = LocalTileLook.current
    val preview = LocalTileIsPreview.current
    // An icon the tile was given wins over the one the app (or the icon pack) brought.
    val face = customTileFace() ?: face
    val label = look.name?.takeIf { it.isNotBlank() } ?: label
    val said = notifications.ifEmpty {
        if (notificationTitle.isNullOrBlank() && notificationText.isNullOrBlank()) emptyList()
        else listOf(TileLine(title = notificationTitle?.takeIf { it.isNotBlank() } ?: label, body = notificationText?.takeIf { it.isNotBlank() }))
    }
    val hasNotifications = look.notifications && notificationCount > 0 && said.isNotEmpty()
    val notices = if (hasNotifications) said else emptyList()
    // Every size turns over to what its app has to say, as live tiles did: the icon on the front,
    // the words on the back.
    val hasBack = hasNotifications

    W10MTileSurface(
        liveKey = "app:$tileKey",
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
                val fg = tileForegroundColor()
                TileFace(span) { m ->
                    if (m.isSmall) {
                        // A small tile has no room for words: its back is the icon and the count.
                        GlyphCount(
                            count = notificationCount,
                            iconSize = m.width * 0.36f * tileIconScale(),
                            ink = fg,
                            maxWidth = m.width * 0.84f,
                            modifier = Modifier.align(Alignment.Center)
                        ) { size, glyphModifier ->
                            if (face != TileIconFace.None) {
                                TileIconImage(face, size = size, ink = fg, contentDescription = null, modifier = glyphModifier)
                            }
                        }
                        return@TileFace
                    }
                    // One notification at each turn, the newest first; a large tile has room for them all.
                    val turn = LocalTileTurn.current
                    val shown = if (m.shape == TileShape.LARGE) notices else listOf(notices[turn % notices.size])
                    Column(modifier = Modifier.fillMaxSize().padding(m.pad), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (face != TileIconFace.None) {
                                TileIconImage(face, size = m.dp(18f), ink = fg, contentDescription = null)
                                Spacer(modifier = Modifier.width(m.dp(6f)))
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(12f)),
                                color = fg.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (notificationCount > 1) {
                                Text(
                                    text = "+$notificationCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(12f), fontWeight = FontWeight.Normal),
                                    // The same ink as the rest of the tile: this used to consult the
                                    // theme on its own, which left one number the wrong colour once
                                    // the ink could be chosen.
                                    color = fg
                                )
                            }
                        }
                        TileLineList(
                            shown, m, fg,
                            maxItems = shown.size,
                            bodyLines = when (m.shape) {
                                TileShape.WIDE -> 2
                                TileShape.LARGE -> 3
                                else -> 4
                            }
                        )
                    }
                }
            }
        },
        front = {
            val fg = tileForegroundColor()
            if (isEditing && !preview) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            if (!tileHasPicture()) {
                TileFace(span) { m ->
                    // The icon takes the same share of every tile of a shape, as the hub glyphs do.
                    val iconSize = when (m.shape) {
                        TileShape.SMALL -> m.width * 0.44f
                        TileShape.MEDIUM -> m.width * 0.32f
                        TileShape.WIDE -> m.height * 0.36f
                        TileShape.LARGE -> m.width * 0.26f
                    } * tileIconScale()
                    // The icon and its count, centred together, as Windows Phone wrote them.
                    GlyphCount(
                        count = notificationCount,
                        iconSize = iconSize,
                        ink = fg,
                        maxWidth = m.width * 0.84f,
                        modifier = Modifier.align(Alignment.Center).offset(y = if (m.isSmall) 0.dp else -m.dp(6f))
                    ) { size, glyphModifier ->
                        TileIconImage(face = face, size = size, ink = fg, contentDescription = label, modifier = glyphModifier)
                    }
                }
            } else {
                // A picture fills the tile; its count keeps the corner.
                TileBadge(notificationCount, span, gridColumns, fg)
            }
            TileLabel(label, span, gridColumns, fg)
        }
    )
}
