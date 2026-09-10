package com.serkantkn.zunelauncher.ui.components

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import kotlinx.coroutines.delay

private fun getHubIcon(hubType: HubType): ImageVector {
    return when (hubType) {
        HubType.PHONE -> Icons.Default.Call
        HubType.MESSAGING -> Icons.Default.Email
        HubType.PEOPLE -> Icons.Default.People
        HubType.PICTURES -> Icons.Default.Image
        HubType.MUSIC -> Icons.Default.MusicNote
        HubType.INTERNET -> Icons.Default.Language
        HubType.FILES -> Icons.Default.Folder
        HubType.NOTES -> Icons.Default.StickyNote2
        HubType.EMAIL -> Icons.Default.Mail
        HubType.CALCULATOR -> Icons.Default.Calculate
        HubType.WEATHER -> Icons.Default.WbSunny
        HubType.SETTINGS -> Icons.Default.Settings
        HubType.CLOCK -> Icons.Default.Schedule
        HubType.CALENDAR -> Icons.Default.CalendarMonth
        HubType.HOME -> Icons.Default.Home
    }
}

/**
 * The everyday hub tile: glyph in the middle, hub name in the bottom-left corner and the count in
 * the top-right, the way a Windows Phone flip tile carried them. When the hub has something to
 * say — unread mail, the last note, the last sum — the tile turns over to a text face.
 *
 * The Pictures hub is the exception: it has no back face and instead cross-fades through the
 * favourite photos, as the Photos tile did.
 */
@Composable
fun W10MHubTile(
    hubType: HubType,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    liveTitle: String? = null,
    liveSubtitle: String? = null,
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
    photoUris: List<Uri> = emptyList(),
    spacing: Dp = 8.dp
) {
    val icon = remember(hubType) { getHubIcon(hubType) }
    val title = stringResource(hubType.titleRes)
    val isPicturesLive = hubType == HubType.PICTURES && photoUris.isNotEmpty()
    val hasBack = !isPicturesLive && (badgeCount > 0 || !liveSubtitle.isNullOrBlank() || !liveTitle.isNullOrBlank())

    W10MTileSurface(
        liveKey = "hub:${hubType.name}",
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
        // A photo fills the whole tile, so it is drawn opaque no matter how see-through tiles are.
        opacity = if (isPicturesLive) 1f else null,
        back = if (hasBack) {
            {
                TileTextBack(
                    icon = icon,
                    title = title,
                    headline = liveTitle,
                    body = liveSubtitle,
                    badgeCount = badgeCount,
                    span = span,
                    gridColumns = gridColumns
                )
            }
        } else null,
        front = {
            if (isPicturesLive) {
                PhotoCycleFace(photoUris = photoUris, title = title, span = span, gridColumns = gridColumns, isEditing = isEditing)
            } else {
                HubGlyphFace(
                    icon = icon,
                    title = title,
                    badgeCount = badgeCount,
                    span = span,
                    gridColumns = gridColumns
                )
            }
        }
    )
}

// ════════════════════════════════════════════════════════════
// FACES
// ════════════════════════════════════════════════════════════

/** Front of a plain tile: glyph, name, count. */
@Composable
internal fun BoxScope.HubGlyphFace(
    icon: ImageVector,
    title: String,
    badgeCount: Int,
    span: Int,
    gridColumns: Int
) {
    val fg = tileForegroundColor()
    val compact = isCompactTile(span, gridColumns)
    val iconSize = when {
        span == 1 -> 26.dp
        span == 2 -> if (compact) 34.dp else 46.dp
        span == 4 -> if (compact) 44.dp else 54.dp
        else -> 62.dp
    }

    Icon(
        imageVector = icon,
        contentDescription = title,
        tint = fg,
        modifier = Modifier
            .align(Alignment.Center)
            // Windows Phone sat the glyph a touch above the middle to leave the name room.
            .offset(y = if (span > 1) (-5).dp else 0.dp)
            .size(iconSize)
    )

    TileBadge(badgeCount, span, gridColumns, fg)
    TileLabel(title, span, gridColumns, fg)
}

/**
 * Back of a flip tile: the hub's name in small type at the top, then whatever it wants to say. A
 * count with no words of its own becomes the headline, as on the Windows Phone message tile.
 */
@Composable
internal fun BoxScope.TileTextBack(
    icon: ImageVector,
    title: String,
    headline: String?,
    body: String?,
    badgeCount: Int,
    span: Int,
    gridColumns: Int
) {
    val fg = tileForegroundColor()
    val compact = isCompactTile(span, gridColumns)

    if (span == 1) {
        // A small tile has no room for words: the count alone is the message.
        Text(
            text = if (badgeCount > 0) badgeCount.toString() else "",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Light),
            color = fg,
            modifier = Modifier.align(Alignment.Center)
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg.copy(alpha = 0.75f),
                modifier = Modifier.size(if (compact) 12.dp else 15.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 9.sp else 11.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = fg.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val heading = headline ?: badgeCount.takeIf { it > 0 }?.toString()
            if (!heading.isNullOrBlank()) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = when {
                            headline == null && span >= 4 -> 34.sp
                            headline == null -> 26.sp
                            compact -> 11.sp
                            else -> 13.sp
                        },
                        fontWeight = if (headline == null) FontWeight.Light else FontWeight.SemiBold
                    ),
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 9.sp else 11.sp),
                    color = fg.copy(alpha = 0.9f),
                    maxLines = if (span >= 4) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The Photos tile: one favourite after another, cross-fading every few seconds. */
@Composable
private fun BoxScope.PhotoCycleFace(
    photoUris: List<Uri>,
    title: String,
    span: Int,
    gridColumns: Int,
    isEditing: Boolean
) {
    var index by remember(photoUris) { mutableIntStateOf(0) }
    LaunchedEffect(photoUris, isEditing) {
        if (isEditing || photoUris.size <= 1) return@LaunchedEffect
        while (true) {
            delay(PHOTO_CYCLE_MILLIS)
            index = (index + 1) % photoUris.size
        }
    }
    Crossfade(
        targetState = photoUris[index.coerceIn(0, photoUris.lastIndex)],
        animationSpec = tween(durationMillis = 900),
        label = "w10m_photo_cycle",
        modifier = Modifier.fillMaxSize()
    ) { uri ->
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.tile_favorite_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
    )
    TileLabel(title, span, gridColumns, Color.White)
}

private const val PHOTO_CYCLE_MILLIS = 5_000L

// ════════════════════════════════════════════════════════════
// SHARED BITS
// ════════════════════════════════════════════════════════════

/** True when the tile is too small for full-size type: 1×1, or 2×2 on the eight-column grid. */
internal fun isCompactTile(span: Int, gridColumns: Int): Boolean =
    span == 1 || (span == 2 && gridColumns >= 8)

/** Hub or app name in the bottom-left corner. Small tiles carry no name, as on Windows Phone. */
@Composable
internal fun BoxScope.TileLabel(text: String, span: Int, gridColumns: Int, color: Color) {
    if (span <= 1) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = if (isCompactTile(span, gridColumns)) 9.sp else 12.sp,
            fontWeight = FontWeight.Normal
        ),
        color = color.copy(alpha = 0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 6.dp, bottom = 4.dp, end = 24.dp)
    )
}

/** The count, in the top-right corner where a Windows Phone flip tile kept it. */
@Composable
internal fun BoxScope.TileBadge(count: Int, span: Int, gridColumns: Int, color: Color) {
    if (count <= 0) return
    Text(
        text = if (count > 99) "99+" else count.toString(),
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Light,
            fontSize = when {
                span == 1 -> 12.sp
                isCompactTile(span, gridColumns) -> 16.sp
                span >= 4 -> 26.sp
                else -> 22.sp
            }
        ),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = 7.dp, top = 3.dp)
    )
}

/**
 * Grid geometry: a tile is as wide as its column span and, except for the wide 2×4, just as tall.
 */
fun Modifier.w10mTileSize(
    span: Int,
    gridColumns: Int,
    spacing: Dp
): Modifier = this.layout { measurable, constraints ->
    val spacingPx = spacing.roundToPx()
    val width = constraints.maxWidth
    val height = when {
        span == 1 -> width
        span == 2 -> width
        span == 4 -> ((width - spacingPx) / 2).coerceAtLeast(1)
        span >= 8 -> width   // large 4x4: square
        else -> width
    }
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = width,
            maxWidth = width,
            minHeight = height,
            maxHeight = height
        )
    )
    layout(width, height) {
        placeable.placeRelative(0, 0)
    }
}
