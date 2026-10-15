package com.serkantkn.zunelauncher.ui.components

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalScreenAwake
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.material.icons.filled.Newspaper
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
import com.serkantkn.zunelauncher.util.TileIconFace
import kotlinx.coroutines.delay

internal fun getHubIcon(hubType: HubType): ImageVector {
    return when (hubType) {
        HubType.PHONE -> Icons.Default.Call
        HubType.MESSAGING -> WpMessagingGlyph
        HubType.PEOPLE -> Icons.Default.People
        HubType.PICTURES -> Icons.Default.Image
        HubType.MUSIC -> Icons.Default.MusicNote
        HubType.INTERNET -> Icons.Default.Language
        HubType.FILES -> Icons.Default.Folder
        HubType.NOTES -> Icons.Default.StickyNote2
        HubType.EMAIL -> Icons.Default.Mail
        HubType.CAMERA -> Icons.Default.PhotoCamera
        HubType.CALCULATOR -> Icons.Default.Calculate
        HubType.WEATHER -> Icons.Default.WbSunny
        HubType.NEWS -> Icons.Default.Newspaper
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
    isMergeTarget: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    liveTitle: String? = null,
    liveSubtitle: String? = null,
    /**
     * Everything new, newest first, for the wide and large tiles, which have room to list it; the
     * medium tile turns over to the first. Without it, [liveTitle] and [liveSubtitle] are the one.
     */
    liveItems: List<TileLine> = emptyList(),
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
    photoUris: List<Uri> = emptyList(),
    spacing: Dp = 8.dp
) {
    val icon = remember(hubType) { getHubIcon(hubType) }
    val look = LocalTileLook.current
    val title = look.name?.takeIf { it.isNotBlank() } ?: stringResource(hubType.titleRes)
    val customFace = customTileFace()
    val isPicturesLive = hubType == HubType.PICTURES && photoUris.isNotEmpty() && look.picture == null
    val lines = liveItems.ifEmpty {
        val heading = liveTitle ?: liveSubtitle
        if (heading.isNullOrBlank()) emptyList()
        else listOf(TileLine(title = heading, body = liveSubtitle.takeIf { liveTitle != null }))
    }
    val shape = TileShape.of(span)
    // The wide and large tiles say what is new on their front, so they have nothing to turn to.
    // A small tile's back holds nothing but the count, so without one it has no back either: it
    // used to flip over to a blank face every twenty seconds.
    val hasBack = !isPicturesLive && when (shape) {
        TileShape.SMALL -> badgeCount > 0
        TileShape.MEDIUM -> badgeCount > 0 || lines.isNotEmpty()
        else -> false
    }

    W10MTileSurface(
        liveKey = "hub:${hubType.name}",
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
        // A photo fills the whole tile, so it is drawn opaque no matter how see-through tiles are.
        opacity = if (isPicturesLive) 1f else null,
        back = if (hasBack) {
            {
                TileTextBack(
                    icon = icon,
                    title = title,
                    headline = lines.firstOrNull()?.title,
                    // A missed call has no words of its own: the count's sentence stands in.
                    body = lines.firstOrNull()?.body ?: liveSubtitle.takeIf { liveTitle != null },
                    badgeCount = badgeCount,
                    span = span,
                    gridColumns = gridColumns
                )
            }
        } else null,
        front = {
            when {
                isPicturesLive -> PhotoCycleFace(photoUris = photoUris, title = title, span = span, gridColumns = gridColumns, isEditing = isEditing)
                shape.ordinal >= TileShape.WIDE.ordinal && lines.isNotEmpty() && !tileHasPicture() -> IconicListFace(
                    icon = icon,
                    face = customFace,
                    title = title,
                    count = if (look.notifications) badgeCount else 0,
                    lines = lines,
                    span = span,
                    gridColumns = gridColumns
                )
                else -> HubGlyphFace(
                    icon = icon,
                    title = title,
                    badgeCount = badgeCount,
                    span = span,
                    gridColumns = gridColumns,
                    face = customFace
                )
            }
        }
    )
}

/**
 * The front of a wide or large tile with something to say, after Windows Phone's iconic tile: the
 * glyph with the count large beside it, and what is new written out. On the wide tile the words
 * stand to the right of the glyph; the large tile puts the glyph on top and lists underneath.
 */
@Composable
internal fun BoxScope.IconicListFace(
    icon: ImageVector?,
    face: TileIconFace?,
    title: String,
    count: Int,
    lines: List<TileLine>,
    span: Int,
    gridColumns: Int
) {
    val fg = tileForegroundColor()
    TileFace(span) { m ->
        TileBody(m) {
            if (m.shape == TileShape.WIDE) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    IconicCount(
                        icon = icon,
                        face = face,
                        count = count,
                        metrics = m,
                        ink = fg,
                        iconSize = m.dp(44f) * tileIconScale(),
                        modifier = Modifier.padding(end = m.dp(14f))
                    )
                    TileLineList(
                        lines = lines,
                        metrics = m,
                        ink = fg,
                        maxItems = 3,
                        bodyLines = when (lines.size) { 1 -> 4; 2 -> 2; else -> 1 },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    IconicCount(
                        icon = icon,
                        face = face,
                        count = count,
                        metrics = m,
                        ink = fg,
                        iconSize = m.dp(52f) * tileIconScale()
                    )
                    TileLineList(
                        lines = lines,
                        metrics = m,
                        ink = fg,
                        maxItems = 5,
                        bodyLines = when (lines.size) { 1 -> 10; 2 -> 4; 3 -> 2; else -> 1 },
                        modifier = Modifier.padding(top = m.dp(16f))
                    )
                }
            }
        }
    }
    TileLabel(title, span, gridColumns, fg)
}

// ════════════════════════════════════════════════════════════
// FACES
// ════════════════════════════════════════════════════════════

/**
 * Front of a plain tile: glyph, name, count. A [face] the tile was given stands in for the hub's
 * own glyph; a picture the tile was given leaves no room for either.
 */
@Composable
internal fun BoxScope.HubGlyphFace(
    icon: ImageVector,
    title: String,
    badgeCount: Int,
    span: Int,
    gridColumns: Int,
    face: TileIconFace? = null
) {
    val fg = tileForegroundColor()
    if (!tileHasPicture()) {
        TileFace(span) { m ->
            // The glyph takes the same share of every tile of a shape, whatever the grid: a fixed
            // size that suited the four-column grid sat lost in the middle of the big tile.
            val iconSize = when (m.shape) {
                TileShape.SMALL -> m.width * 0.42f
                TileShape.MEDIUM -> m.width * 0.30f
                TileShape.WIDE -> m.height * 0.34f
                TileShape.LARGE -> m.width * 0.24f
            } * tileIconScale()
            // The glyph and its count, centred together - Windows Phone sat them a touch above
            // the middle to leave the name room.
            GlyphCount(
                count = badgeCount,
                iconSize = iconSize,
                ink = fg,
                maxWidth = m.width * 0.84f,
                modifier = Modifier.align(Alignment.Center).offset(y = if (m.isSmall) 0.dp else -m.dp(6f))
            ) { size, glyphModifier ->
                if (face != null) {
                    TileIconImage(face = face, size = size, ink = fg, contentDescription = title, modifier = glyphModifier)
                } else {
                    Icon(imageVector = icon, contentDescription = title, tint = fg, modifier = glyphModifier)
                }
            }
        }
    } else {
        // A picture fills the tile; its count keeps the corner.
        TileBadge(badgeCount, span, gridColumns, fg)
    }

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
    TileFace(span) { m ->
        if (m.isSmall) {
            // A small tile has no room for words: the count alone is the message.
            Text(
                text = if (badgeCount > 0) badgeCount.toString() else "",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = m.figure(30f), fontWeight = FontWeight.Light),
                color = fg,
                modifier = Modifier.align(Alignment.Center)
            )
            return@TileFace
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(m.pad),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TileCaption(icon, title, m, fg)
            Column(verticalArrangement = Arrangement.spacedBy(m.dp(2f))) {
                val heading = headline ?: badgeCount.takeIf { it > 0 }?.toString()
                if (!heading.isNullOrBlank()) {
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = if (headline == null) m.figure(44f) else m.text(16f),
                            fontWeight = if (headline == null) FontWeight.Light else FontWeight.Normal
                        ),
                        color = fg,
                        maxLines = if (body.isNullOrBlank()) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!body.isNullOrBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = m.text(13f), lineHeight = m.text(16f)),
                        color = fg.copy(alpha = 0.9f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * An album pinned to Start: the same living photo face as the pictures tile, with the album's own
 * name across the bottom instead of the hub's.
 */
@Composable
fun W10MAlbumTile(
    label: String,
    photoUris: List<Uri>,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
    spacing: Dp = 8.dp
) {
    W10MTileSurface(
        liveKey = "album:$label",
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
        opacity = if (photoUris.isNotEmpty()) 1f else null,
        front = {
            if (photoUris.isEmpty()) {
                // An album whose pictures have not been read yet, or that has been emptied.
                HubGlyphFace(
                    icon = Icons.Default.Image,
                    title = label,
                    badgeCount = 0,
                    span = span,
                    gridColumns = gridColumns
                )
            } else {
                PhotoCycleFace(
                    photoUris = photoUris,
                    title = label,
                    span = span,
                    gridColumns = gridColumns,
                    isEditing = isEditing
                )
            }
        }
    )
}

/** The Photos tile: one favourite after another, cross-fading every few seconds. */
@Composable
internal fun BoxScope.PhotoCycleFace(
    photoUris: List<Uri>,
    title: String,
    span: Int,
    gridColumns: Int,
    isEditing: Boolean
) {
    var index by remember(photoUris) { mutableIntStateOf(0) }
    // Behind an opaque hub the pictures stop changing: nobody can see them change.
    val awake = LocalScreenAwake.current
    LaunchedEffect(photoUris, isEditing, awake) {
        if (isEditing || !awake || photoUris.size <= 1) return@LaunchedEffect
        while (true) {
            delay(PHOTO_CYCLE_MILLIS)
            index = (index + 1) % photoUris.size
        }
    }

    // Windows Phone's photo tile never sat still: each picture drifted slowly across the tile
    // while it was up, so the tile looked alive even between changes. The drift alternates
    // direction so two pictures in a row do not pull the same way.
    val animate = LocalAnimationsEnabled.current
    val pan = remember { Animatable(0f) }
    LaunchedEffect(index, animate, isEditing) {
        pan.snapTo(0f)
        if (!animate || isEditing) return@LaunchedEffect
        pan.animateTo(1f, tween(durationMillis = PHOTO_PAN_MILLIS, easing = LinearEasing))
    }

    Crossfade(
        targetState = photoUris[index.coerceIn(0, photoUris.lastIndex)],
        animationSpec = tween(durationMillis = 900),
        label = "w10m_photo_cycle",
        modifier = Modifier.fillMaxSize()
    ) { uri ->
        val towardsLeft = index % 2 == 0
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.tile_favorite_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Zoomed a little so the drift never uncovers an edge.
                    val zoom = PHOTO_PAN_ZOOM + pan.value * 0.04f
                    scaleX = zoom
                    scaleY = zoom
                    val travel = size.width * 0.05f * pan.value
                    translationX = if (towardsLeft) -travel else travel
                }
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

/** How long one picture takes to drift from one side of the tile to the other. */
private const val PHOTO_PAN_MILLIS = 6_000

/** How far in the picture starts, so the drift has room to move without showing an edge. */
private const val PHOTO_PAN_ZOOM = 1.06f

// ════════════════════════════════════════════════════════════
// SHARED BITS
// ════════════════════════════════════════════════════════════

/** True when the tile is too small for full-size type: 1×1, or 2×2 on the eight-column grid. */
internal fun isCompactTile(span: Int, gridColumns: Int): Boolean =
    span == 1 || (span == 2 && gridColumns >= 8)

/**
 * Hub or app name in the bottom-left corner. Small tiles carry no name, as on Windows Phone, and
 * neither does a tile whose name was switched off; one given another name shows that.
 */
@Composable
internal fun BoxScope.TileLabel(text: String, span: Int, gridColumns: Int, color: Color) {
    if (span <= 1) return
    val shown = tileLabelText(text) ?: return
    TileFace(span) { m ->
        Text(
            text = shown,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(13f), fontWeight = FontWeight.Normal),
            color = color.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = m.dp(8f), bottom = m.dp(6f), end = m.dp(28f))
        )
    }
}

/** The count, in the top-right corner where a Windows Phone flip tile kept it. */
@Composable
internal fun BoxScope.TileBadge(count: Int, span: Int, gridColumns: Int, color: Color) {
    if (count <= 0 || !LocalTileLook.current.notifications) return
    TileFace(span) { m ->
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Light,
                fontSize = when (m.shape) {
                    TileShape.SMALL -> m.figure(15f)
                    TileShape.MEDIUM -> m.figure(26f)
                    TileShape.WIDE -> m.figure(30f)
                    TileShape.LARGE -> m.figure(40f)
                }
            ),
            color = color,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = m.dp(9f), top = m.dp(4f))
        )
    }
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
