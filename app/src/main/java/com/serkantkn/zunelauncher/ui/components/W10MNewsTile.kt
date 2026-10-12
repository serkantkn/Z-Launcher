package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.theme.LocalScreenAwake
import kotlinx.coroutines.delay

/**
 * The news tile: the front page's stories one after another, each a picture with its headline
 * across the bottom, as the Bing News tile was. A small tile, or no stories yet, is the plain
 * glyph tile. A story's picture fills the tile, so the transparency setting gives way to it.
 */
@Composable
fun W10MNewsTile(
    stories: List<NewsItem>,
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(HubType.NEWS.titleRes)
    // The stories with a picture first: a tile of words alone is kept for when there are none.
    val shown = remember(stories) {
        val pictured = stories.filter { it.imageUrl != null }
        (pictured.ifEmpty { stories }).take(STORIES_SHOWN)
    }
    val live = span > 1 && shown.isNotEmpty()
    var index by remember(shown) { mutableIntStateOf(0) }
    val awake = LocalScreenAwake.current
    LaunchedEffect(shown, isEditing, awake) {
        if (!live || isEditing || !awake || shown.size < 2) return@LaunchedEffect
        while (true) {
            delay(STORY_MILLIS)
            index = (index + 1) % shown.size
        }
    }
    val compact = isCompactTile(span, gridColumns)

    W10MTileSurface(
        liveKey = "hub:${HubType.NEWS.name}",
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
        opacity = if (live && shown.any { it.imageUrl != null }) 1f else null,
        front = {
            if (!live) {
                HubGlyphFace(
                    icon = Icons.Default.Newspaper,
                    title = title,
                    badgeCount = 0,
                    span = span,
                    gridColumns = gridColumns
                )
                return@W10MTileSurface
            }
            Crossfade(
                targetState = shown[index.coerceIn(0, shown.lastIndex)],
                animationSpec = tween(durationMillis = 700),
                label = "news_tile_story",
                modifier = Modifier.fillMaxSize()
            ) { story ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (story.imageUrl != null) {
                        AsyncImage(
                            model = story.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.35f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.82f)
                                    )
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = story.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (compact) 11.sp else 14.sp,
                                lineHeight = if (compact) 13.sp else 17.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                            maxLines = if (span >= 4 && !compact) 2 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 9.sp else 11.sp),
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    )
}

private const val STORIES_SHOWN = 6
private const val STORY_MILLIS = 6_000L
