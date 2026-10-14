package com.serkantkn.zunelauncher.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle

/**
 * The Music tile. While something is playing it becomes the album cover with the track written
 * across the bottom, the way the Music+Videos tile did; the wide tile puts the cover at its left
 * and writes the track out beside it. The rest of the time it is the plain glyph tile. Cover art
 * fills the tile, so it ignores the transparency setting while it is up.
 */
@Composable
fun W10MMusicTile(
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle,
    albumArt: Bitmap?,
    trackTitle: String,
    trackArtist: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(HubType.MUSIC.titleRes)
    val hasTrack = trackTitle.isNotBlank()

    W10MTileSurface(
        liveKey = "hub:${HubType.MUSIC.name}",
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
        opacity = if (albumArt != null) 1f else null,
        front = {
            if (!hasTrack) {
                HubGlyphFace(icon = Icons.Default.MusicNote, title = title, badgeCount = 0, span = span, gridColumns = gridColumns)
                return@W10MTileSurface
            }
            TileFace(span) { m ->
                if (m.shape == TileShape.WIDE) {
                    // Beside the cover the words sit on the tile's own colour, in its own ink.
                    val ink = tileForegroundColor()
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f)) { Cover(albumArt, m) }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(m.pad),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            TileCaption(Icons.Default.MusicNote, stringResource(R.string.tile_now_playing), m, ink)
                            Column {
                                Text(
                                    text = trackTitle,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = m.text(19f), fontWeight = FontWeight.Normal, lineHeight = m.text(22f)),
                                    color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                if (trackArtist.isNotBlank()) {
                                    Text(trackArtist, style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f)),
                                        color = ink.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(13f)),
                                color = ink.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    return@TileFace
                }

                Cover(albumArt, m)
                if (m.isSmall) return@TileFace
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(m.dp(if (m.isLarge) 120f else 74f))
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = m.pad, end = m.pad, bottom = m.dp(7f))
                ) {
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = m.text(if (m.isLarge) 24f else 15f),
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (trackArtist.isNotBlank()) {
                        Text(trackArtist, style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(if (m.isLarge) 18f else 13f)),
                            color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (m.isLarge) {
                        Spacer(modifier = Modifier.height(m.dp(4f)))
                        Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(13f)),
                            color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
    )
}

/** The cover, or a note on a darker square while the playing app has not sent one. */
@Composable
private fun BoxScope.Cover(art: Bitmap?, m: TileMetrics) {
    Crossfade(
        targetState = art,
        animationSpec = tween(durationMillis = 700),
        label = "w10m_album_art",
        modifier = Modifier.matchParentSize()
    ) { cover ->
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = stringResource(R.string.tile_now_playing),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(m.dp(44f)))
            }
        }
    }
}
