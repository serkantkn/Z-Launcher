package com.serkantkn.zunelauncher.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.util.formatTrackDuration
import androidx.compose.foundation.clickable

/**
 * The music hub's own controls.
 *
 * Metro rather than Material: a progress line instead of a slider with a knob, square buttons,
 * type doing the work an icon would do elsewhere.
 */

/**
 * The line under the cover: how far through the track we are, and how long is left.
 *
 * It can be dragged. While a finger is on it the line follows the finger rather than the player,
 * because a line that keeps snapping back to where playback actually is cannot be aimed.
 */
@Composable
fun MusicProgressLine(
    positionMillis: Long,
    durationMillis: Long,
    accent: Color,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val playedFraction = if (durationMillis > 0L) {
        (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    } else 0f
    val shown = dragFraction ?: playedFraction

    fun fractionAt(x: Float): Float = (x / widthPx).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .then(
                    if (!enabled || durationMillis <= 0L) Modifier else Modifier
                        .pointerInput(durationMillis) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    dragFraction?.let { onSeek((it * durationMillis).toLong()) }
                                    dragFraction = null
                                },
                                onDragCancel = { dragFraction = null }
                            ) { change, _ -> dragFraction = fractionAt(change.position.x) }
                        }
                        .pointerInput(durationMillis) {
                            detectTapGestures { offset -> onSeek((fractionAt(offset.x) * durationMillis).toLong()) }
                        }
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.Center)
                    .background(Color.White.copy(alpha = 0.28f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .height(2.dp)
                    .align(Alignment.CenterStart)
                    .background(accent)
            )
            if (enabled && durationMillis > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 0.dp)
                        .offsetForFraction(shown, widthPx)
                        .size(10.dp)
                        .background(accent)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTrackDuration(
                    if (dragFraction != null) (dragFraction!! * durationMillis).toLong() else positionMillis
                ),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = if (durationMillis > 0L) formatTrackDuration(durationMillis) else "",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/** Puts the handle where the line is filled to, in pixels rather than in a percentage of width. */
private fun Modifier.offsetForFraction(fraction: Float, widthPx: Float): Modifier =
    this.then(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                val x = (fraction * widthPx - placeable.width / 2f).toInt()
                placeable.placeRelative(x.coerceIn(0, (widthPx - placeable.width).toInt().coerceAtLeast(0)), 0)
            }
        }
    )

/** Shuffle and repeat: small, off to the side, lit in the accent when they are on. */
@Composable
fun ShuffleRepeatRow(
    shuffleEnabled: Boolean,
    repeatMode: Int,
    accent: Color,
    enabled: Boolean,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        MusicToggleButton(
            icon = Icons.Default.Shuffle,
            on = shuffleEnabled,
            accent = accent,
            enabled = enabled,
            onClick = onToggleShuffle
        )
        MusicToggleButton(
            icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            on = repeatMode != Player.REPEAT_MODE_OFF,
            accent = accent,
            enabled = enabled,
            onClick = onCycleRepeat
        )
    }
}

@Composable
private fun MusicToggleButton(
    icon: ImageVector,
    on: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = when {
            !enabled -> Color.White.copy(alpha = 0.25f)
            on -> accent
            else -> Color.White.copy(alpha = 0.6f)
        },
        modifier = Modifier
            .wpTilt(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(6.dp)
            .size(22.dp)
    )
}

/** One track in a list: number or cover, title, artist, length. */
@Composable
fun MusicSectionHeader(letter: String, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = 26.sp
        ),
        color = accent,
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp)
    )
}

/** The search field, over the hub, with whatever is behind it filtered as you type. */
@Composable
fun MusicSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        com.serkantkn.zunelauncher.ui.components.MetroTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = androidx.compose.ui.res.stringResource(
                com.serkantkn.zunelauncher.R.string.music_search_hint
            )
        )
    }
}

/** What is lined up to play: jump to any of it, or take something out. */
@Composable
fun MusicQueueSheet(
    playback: HubPlayback,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = com.serkantkn.zunelauncher.ui.theme.LocalZuneColors.current.accentColor
    com.serkantkn.zunelauncher.ui.screens.messaging.MessagingSheet(
        title = androidx.compose.ui.res.stringResource(com.serkantkn.zunelauncher.R.string.music_queue_title),
        onDismiss = onDismiss
    ) {
        if (playback.queue.isEmpty()) {
            com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction(
                label = androidx.compose.ui.res.stringResource(com.serkantkn.zunelauncher.R.string.music_queue_empty),
                onClick = onDismiss
            )
            return@MessagingSheet
        }

        // A long queue is a scroll of its own; the sheet never grows past half the screen.
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.heightIn(max = 340.dp)
        ) {
            itemsIndexed(
                playback.queue,
                key = { index, entry -> "$index:${entry.mediaId}" }
            ) { index, entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPlayIndex(index)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title.ifBlank { entry.mediaId },
                            style = MaterialTheme.typography.bodyLarge,
                            // The one playing is the one in the accent, as everywhere else.
                            color = if (index == playback.currentIndex) accent else MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (entry.artist.isNotBlank()) {
                            Text(
                                text = entry.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = com.serkantkn.zunelauncher.ui.theme.LocalZuneColors.current.textDim,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = androidx.compose.ui.res.stringResource(
                            com.serkantkn.zunelauncher.R.string.music_queue_remove
                        ),
                        tint = com.serkantkn.zunelauncher.ui.theme.LocalZuneColors.current.textMuted,
                        modifier = Modifier
                            .clickable { onRemove(index) }
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
            }
        }
    }
}


/**
 * The letter grid.
 *
 * Windows Phone's lists all had this: tap a heading and the whole alphabet appears as tiles, with
 * the letters nothing files under greyed out, and picking one drops you straight there. On a phone
 * holding a thousand songs it is the difference between finding something and scrolling for it.
 */
@Composable
fun LetterJumpOverlay(
    available: Set<String>,
    accent: Color,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // The Turkish alphabet, with the ones English shares in their usual places, and "#" for
    // everything that is not a letter at all.
    val letters = remember {
        listOf(
            "A", "B", "C", "Ç", "D", "E", "F", "G", "Ğ", "H", "I", "İ", "J", "K", "L", "M",
            "N", "O", "Ö", "P", "Q", "R", "S", "Ş", "T", "U", "Ü", "V", "W", "X", "Y", "Z", "#"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(letters, key = { it }) { letter ->
                val enabled = letter in available
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(if (enabled) accent else Color.White.copy(alpha = 0.07f))
                        .then(
                            if (enabled) Modifier.clickable { onPick(letter) } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 22.sp
                        ),
                        color = if (enabled) Color.White else Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

/**
 * The strip above the bottom bar: what is playing, wherever you are in the hub.
 *
 * Without it, starting a song on the songs page and then wanting to pause it means finding your
 * way back to the first pivot. It is the one control that should never be more than a tap away.
 */
@Composable
fun MusicMiniPlayer(
    playback: HubPlayback,
    accent: Color,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Solid, not tinted: the list scrolls underneath and a see-through strip lets the
            // rows read straight through the track that is playing.
            .background(Color(0xFF101010))
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                playback.externalArt != null -> androidx.compose.foundation.Image(
                    bitmap = playback.externalArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                playback.artworkUri != null -> coil.compose.AsyncImage(
                    model = playback.artworkUri,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                else -> Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = playback.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = playback.artist,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = if (playback.isPlaying) {
                androidx.compose.material.icons.Icons.Default.Pause
            } else {
                androidx.compose.material.icons.Icons.Default.PlayArrow
            },
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .clickable(onClick = onPlayPause)
                .padding(8.dp)
                .size(26.dp)
        )
    }
}

/**
 * The artist's name, huge and faint, behind everything.
 *
 * This is the Zune HD's one visual signature: the name of whoever you are listening to set in
 * enormous light type across the background, cropped by the edge of the screen, with the actual
 * interface sitting on top of it.
 */
@Composable
fun ZuneArtistBackdrop(artist: String, modifier: Modifier = Modifier) {
    if (artist.isBlank()) return
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Text(
            text = artist.lowercase(java.util.Locale.getDefault()),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 120.sp,
                lineHeight = 120.sp,
                letterSpacing = (-6).sp
            ),
            color = Color.White.copy(alpha = 0.06f),
            maxLines = 2,
            softWrap = true
        )
    }
}

/**
 * The words, scrolling with the music.
 *
 * The line being sung is bright and centred; the ones around it are dim. When nobody has timed the
 * words this becomes a plain block to read, which is still better than nothing - and when there
 * are no words at all it says where it looked rather than sitting blank.
 */
@Composable
fun LyricsPanel(
    lyrics: com.serkantkn.zunelauncher.util.Lyrics,
    positionMillis: Long,
    loading: Boolean,
    accent: Color,
    onlineAllowed: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val current = remember(lyrics, positionMillis) {
        com.serkantkn.zunelauncher.util.currentLyricIndex(lyrics.lines, positionMillis)
    }

    // The line being sung is kept in the middle of the panel rather than at the top, so there is
    // always as much to come as has just gone.
    androidx.compose.runtime.LaunchedEffect(current) {
        if (current >= 0) {
            runCatching { listState.animateScrollToItem(maxOf(0, current - 2)) }
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            loading -> Text(
                text = androidx.compose.ui.res.stringResource(
                    com.serkantkn.zunelauncher.R.string.music_lyrics_loading
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f)
            )

            lyrics.isEmpty -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        com.serkantkn.zunelauncher.R.string.music_lyrics_none
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
                if (!onlineAllowed) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            com.serkantkn.zunelauncher.R.string.music_lyrics_offline_hint
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            else -> androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isNow = lyrics.isSynced && index == current
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isNow) FontWeight.Normal else FontWeight.Light,
                            fontSize = if (isNow) 20.sp else 17.sp
                        ),
                        color = when {
                            !lyrics.isSynced -> Color.White.copy(alpha = 0.8f)
                            isNow -> accent
                            else -> Color.White.copy(alpha = 0.35f)
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}
