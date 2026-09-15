package com.serkantkn.zunelauncher.ui.screens.pictures

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The pieces the pictures hub is built from.
 *
 * Everything here is Metro rather than Material: square corners, no cards, no shadows, the accent
 * doing the work a border would do elsewhere. A tile is a picture and nothing else until it has
 * something to say — a video says how long it runs, a favourite shows its heart, a chosen one is
 * pulled back and ticked.
 */

/** The side of a tile that is left blank while its picture loads. */
private val PlaceholderDark = Color(0xFF1B1B1B)
private val PlaceholderLight = Color(0xFFDDDDDD)

/**
 * One picture or video in the grid.
 *
 * Choosing several is a mode, and a mode has to be visible: a chosen tile shrinks back from the
 * grid and takes an accent frame with a tick in the corner, so a glance says how many are in hand
 * without counting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    item: MediaImage,
    isFavorite: Boolean,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.84f else 1f,
        animationSpec = tween(160),
        label = "pics_tile_scale"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .background(if (zuneColors.isDark) PlaceholderDark else PlaceholderLight)
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (item.isVideo) {
                // A clip reads as a still until it says otherwise, so it says so twice: the play
                // glyph for the eye, the running time for anyone deciding whether to watch it.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 5.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.pics_video_badge),
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = formatDuration(item.durationMillis),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        color = Color.White
                    )
                }
            }

            if (isFavorite && !selectionMode) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(14.dp)
                        .alpha(0.9f)
                )
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, zuneColors.accentColor)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(22.dp)
                    .background(zuneColors.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * An album as a Metro tile: the newest picture in it, its name across the bottom, and how much is
 * inside. The name is the word that flies up into the title when the album opens, so the tile is
 * also where that flight starts.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumTile(
    album: MediaAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val name = album.bucketName.ifBlank { stringResource(R.string.pics_unsorted_album) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(if (zuneColors.isDark) PlaceholderDark else PlaceholderLight)
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onClick
            )
    ) {
        AsyncImage(
            model = album.coverUri,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = name.lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (album.videoCount > 0) {
                    stringResource(
                        R.string.pics_album_photos_videos,
                        album.photoCount,
                        album.videoCount
                    )
                } else {
                    stringResource(R.string.pics_album_photos, album.photoCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1
            )
        }
    }
}

/**
 * The date a run of pictures belongs to.
 *
 * Grouping by day is what turns an endless square of thumbnails back into a roll somebody can find
 * their way around: "bugün", "dün", then the date itself.
 */
@Composable
fun DayHeader(dayMillis: Long, count: Int, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ZuneDimens.SpacingMd, bottom = 6.dp)
    ) {
        Text(
            text = dayLabel(dayMillis),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            color = zuneColors.accentColor
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.textDim,
            modifier = Modifier.padding(bottom = 3.dp)
        )
    }
}

/** "bugün", "dün", or the date written out in the phone's language. */
@Composable
private fun dayLabel(dayMillis: Long): String {
    val today = stringResource(R.string.pics_today)
    val yesterday = stringResource(R.string.pics_yesterday)
    return remember(dayMillis, today, yesterday) {
        val locale = Locale.getDefault()
        val now = Calendar.getInstance()
        val startOfToday = now.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayBefore = startOfToday - 24L * 60L * 60L * 1000L
        when {
            dayMillis >= startOfToday -> today
            dayMillis >= dayBefore -> yesterday
            else -> {
                // The year is only worth the room once the pictures are from another one.
                val sameYear = Calendar.getInstance().apply { timeInMillis = dayMillis }
                    .get(Calendar.YEAR) == now.get(Calendar.YEAR)
                val pattern = if (sameYear) "d MMMM" else "d MMMM yyyy"
                SimpleDateFormat(pattern, locale).format(Date(dayMillis))
            }
        }
    }
}

/** Nothing to show, and why — with a line underneath saying what would put something here. */
@Composable
fun PicturesEmpty(
    message: String,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = modifier.fillMaxWidth().padding(top = ZuneDimens.SpacingLg)) {
        Text(
            text = message.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textMuted
        )
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textDim,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
