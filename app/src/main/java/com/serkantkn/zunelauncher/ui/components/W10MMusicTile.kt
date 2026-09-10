package com.serkantkn.zunelauncher.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle

/**
 * The Music tile. While something is playing it becomes the album cover with the track written
 * across the bottom, the way the Music+Videos tile did; the rest of the time it is the plain glyph
 * tile. Cover art fills the tile, so it ignores the transparency setting while it is up.
 */
@Composable
fun W10MMusicTile(
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
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
    val compact = isCompactTile(span, gridColumns)

    W10MTileSurface(
        liveKey = "hub:${HubType.MUSIC.name}",
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
        opacity = if (albumArt != null) 1f else null,
        front = {
            if (!hasTrack) {
                HubGlyphFace(
                    icon = Icons.Default.MusicNote,
                    title = title,
                    badgeCount = 0,
                    span = span,
                    gridColumns = gridColumns
                )
                return@W10MTileSurface
            }

            Crossfade(
                targetState = albumArt,
                animationSpec = tween(durationMillis = 700),
                label = "w10m_album_art",
                modifier = Modifier.fillMaxSize()
            ) { art ->
                if (art != null) {
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = stringResource(R.string.tile_now_playing),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (span >= 4) 62.dp else 48.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            )

            if (span > 1) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = if (compact) 10.sp else 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (trackArtist.isNotBlank()) {
                        Text(
                            text = trackArtist,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 10.sp),
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}
