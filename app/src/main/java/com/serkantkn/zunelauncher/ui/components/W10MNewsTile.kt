package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
            val lead = shown[index.coerceIn(0, shown.lastIndex)]
            TileFace(span) { m ->
                if (m.isLarge) {
                    // The large tile is a front page: the story's picture over the top half, its
                    // headline under it, and the next two stories beneath.
                    val others = (1..2).map { shown[(index + it) % shown.size] }.filter { it != lead }.distinct()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Crossfade(
                            targetState = lead,
                            animationSpec = tween(durationMillis = 700),
                            label = "news_tile_lead",
                            modifier = Modifier.fillMaxWidth().weight(0.55f)
                        ) { story -> StoryPicture(story) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.45f)
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(start = m.pad, end = m.pad, top = m.dp(10f), bottom = m.dp(6f))
                        ) {
                            Text(
                                text = lead.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = m.text(20f), lineHeight = m.text(24f), fontWeight = FontWeight.SemiBold),
                                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = lead.source,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(13f)),
                                color = Color.White.copy(alpha = 0.7f), maxLines = 1,
                                modifier = Modifier.padding(top = m.dp(2f), bottom = m.dp(8f))
                            )
                            others.forEach { story ->
                                Text(
                                    text = story.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f), lineHeight = m.text(18f)),
                                    color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = m.dp(5f))
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(13f)),
                                color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                    return@TileFace
                }
                Crossfade(
                    targetState = lead,
                    animationSpec = tween(durationMillis = 700),
                    label = "news_tile_story",
                    modifier = Modifier.fillMaxSize()
                ) { story ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        StoryPicture(story)
                        if (story.imageUrl != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.3f to Color.Transparent,
                                            0.6f to Color.Black.copy(alpha = 0.35f),
                                            1f to Color.Black.copy(alpha = 0.6f)
                                        )
                                    )
                            )
                        }
                        if (m.isSmall) return@Crossfade
                        // The headline sits on a band of its own: a news picture often carries big
                        // lettering of its own, and a fade alone lets the two run into each other.
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .then(if (story.imageUrl != null) Modifier.background(Color.Black.copy(alpha = 0.78f)) else Modifier)
                                .padding(start = m.pad, end = m.pad, top = m.dp(6f), bottom = m.dp(6f)),
                            verticalArrangement = Arrangement.spacedBy(m.dp(2f))
                        ) {
                            Text(
                                text = story.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = m.text(if (m.shape == TileShape.WIDE) 17f else 15f),
                                    lineHeight = m.text(if (m.shape == TileShape.WIDE) 20f else 18f),
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White,
                                maxLines = if (m.shape == TileShape.WIDE) 2 else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (m.shape == TileShape.WIDE) "$title · ${story.source}" else title,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = m.text(12f)),
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}

/** A story's picture filling its space; nothing at all for a story that came without one. */
@Composable
private fun StoryPicture(story: NewsItem) {
    if (story.imageUrl != null) {
        AsyncImage(
            model = story.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private const val STORIES_SHOWN = 6
private const val STORY_MILLIS = 6_000L
