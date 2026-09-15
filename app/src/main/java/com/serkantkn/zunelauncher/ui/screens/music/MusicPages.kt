package com.serkantkn.zunelauncher.ui.screens.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AlbumModel
import com.serkantkn.zunelauncher.data.model.ArtistModel
import com.serkantkn.zunelauncher.data.model.SongModel
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.formatTotalDuration
import com.serkantkn.zunelauncher.util.formatTrackDuration
import com.serkantkn.zunelauncher.util.groupByLetter
import com.serkantkn.zunelauncher.util.matchesMusicQuery
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The library pages: albums, artists and songs.
 *
 * Two of these used to say "coming soon" in the middle of an empty screen. The shapes are the ones
 * Windows Phone used — albums as covers in a grid, artists and songs as lettered lists you can
 * jump around — because that is what this hub is pretending to be.
 */

private val PlaceholderInk = Color.White.copy(alpha = 0.08f)

// ── Albums ──────────────────────────────────────────────────────────────────

@Composable
fun AlbumsPage(
    viewModel: MusicHubViewModel,
    onOpenAlbum: (AlbumModel, Rect?) -> Unit
) {
    val albums by viewModel.albums.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val shown = remember(albums, query) { albums.filter { it.matchesMusicQuery(query) } }

    if (shown.isEmpty()) {
        MusicEmpty(
            message = stringResource(if (query.isBlank()) R.string.music_no_albums else R.string.music_no_match),
            hint = if (query.isBlank()) stringResource(R.string.music_no_albums_hint) else null
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 120.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        items(shown, key = { it.id }) { album ->
            AlbumTile(album = album, onOpen = onOpenAlbum)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTile(album: AlbumModel, onOpen: (AlbumModel, Rect?) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    // Where the album's name sits is where its flight starts.
    var nameBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(PlaceholderInk)
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onOpen(album, nameBounds) },
                onLongClick = { onOpen(album, nameBounds) }
            )
            .onGloballyPositioned { coords ->
                val box = coords.boundsInRoot()
                nameBounds = Rect(box.left + 12f, box.bottom - 62f, box.right, box.bottom - 28f)
            }
    ) {
        AlbumArt(album.artUri, Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = album.title.lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Inside one album: the cover, what it is, and its running order. */
@Composable
fun AlbumDetailPage(viewModel: MusicHubViewModel, album: AlbumModel) {
    val zuneColors = LocalZuneColors.current
    val tracks = remember(album, viewModel.localSongs.collectAsState().value) { viewModel.tracksOf(album) }
    val playback by viewModel.playback.collectAsState()
    val hourLabel = stringResource(R.string.music_hour_short)
    val minuteLabel = stringResource(R.string.music_minute_short)

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 120.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            Row(modifier = Modifier.padding(bottom = 18.dp)) {
                AlbumArt(album.artUri, Modifier.size(120.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = zuneColors.accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.music_track_count, album.trackCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = zuneColors.textDim,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = formatTotalDuration(album.totalDuration, hourLabel, minuteLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = zuneColors.textDim
                    )
                    if (album.year > 0) {
                        Text(
                            text = album.year.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = zuneColors.textDim
                        )
                    }
                }
            }
        }

        itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
            TrackRow(
                song = song,
                leading = song.trackNumber.takeIf { it > 0 }?.toString() ?: (index + 1).toString(),
                isPlaying = playback.isOurs && playback.title == song.title && playback.isPlaying,
                onClick = { viewModel.playSongs(tracks, index) }
            )
        }
    }
}

// ── Artists ─────────────────────────────────────────────────────────────────

@Composable
fun ArtistsPage(viewModel: MusicHubViewModel, onOpenArtist: (ArtistModel) -> Unit) {
    val artists by viewModel.artists.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val shown = remember(artists, query) { artists.filter { it.matchesMusicQuery(query) } }

    if (shown.isEmpty()) {
        MusicEmpty(
            message = stringResource(if (query.isBlank()) R.string.music_no_artists else R.string.music_no_match),
            hint = if (query.isBlank()) stringResource(R.string.music_no_artists_hint) else null
        )
        return
    }

    val groups = remember(shown) { groupByLetter(shown) { it.name } }
    val zuneColors = LocalZuneColors.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var jumping by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = ZuneDimens.ScreenPaddingHorizontal,
                bottom = 120.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            groups.forEach { (letter, inLetter) ->
                item(key = "letter_$letter") {
                    MusicSectionHeader(
                        letter = letter,
                        accent = zuneColors.accentColor,
                        modifier = Modifier.clickable { jumping = true }
                    )
                }
                items(inLetter, key = { it.id.toString() + it.name }) { artist ->
                    ArtistRow(artist = artist, onClick = { onOpenArtist(artist) })
                }
            }
        }

        if (jumping) {
            LetterJumpOverlay(
                available = groups.map { it.first }.toSet(),
                accent = zuneColors.accentColor,
                onPick = { letter ->
                    jumping = false
                    scope.launch { listState.scrollToItem(headerIndexOf(groups, letter)) }
                },
                onDismiss = { jumping = false }
            )
        }
    }
}

/**
 * Where a letter's heading sits in the list.
 *
 * The list is headings and rows interleaved, so a heading's index is everything before it: one for
 * each earlier heading, plus each earlier row.
 */
private fun <T> headerIndexOf(groups: List<Pair<String, List<T>>>, letter: String): Int {
    var index = 0
    groups.forEach { (groupLetter, items) ->
        if (groupLetter == letter) return index
        index += 1 + items.size
    }
    return 0
}

@Composable
private fun ArtistRow(artist: ArtistModel, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(modifier = Modifier.size(56.dp).background(PlaceholderInk), contentAlignment = Alignment.Center) {
            if (artist.artUri != null) {
                AsyncImage(
                    model = artist.artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.music_artist_counts, artist.albumCount, artist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textDim
            )
        }
    }
}

/** Inside one artist: their records, then everything they have on this phone. */
@Composable
fun ArtistDetailPage(
    viewModel: MusicHubViewModel,
    artist: ArtistModel,
    onOpenAlbum: (AlbumModel, Rect?) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val songs = viewModel.localSongs.collectAsState().value
    val albums = remember(artist, songs) { viewModel.albumsOf(artist) }
    val tracks = remember(artist, songs) { viewModel.tracksOf(artist) }
    val playback by viewModel.playback.collectAsState()

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 120.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        if (albums.isNotEmpty()) {
            item(key = "albums_header") {
                MusicSectionHeader(
                    letter = stringResource(R.string.music_tab_albums),
                    accent = zuneColors.accentColor
                )
            }
            item(key = "albums_row") {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        Box(modifier = Modifier.size(132.dp)) {
                            AlbumTile(album = album, onOpen = onOpenAlbum)
                        }
                    }
                }
            }
        }

        item(key = "songs_header") {
            MusicSectionHeader(
                letter = stringResource(R.string.music_tab_songs),
                accent = zuneColors.accentColor
            )
        }
        itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
            TrackRow(
                song = song,
                leading = null,
                isPlaying = playback.isOurs && playback.title == song.title && playback.isPlaying,
                onClick = { viewModel.playSongs(tracks, index) }
            )
        }
    }
}

// ── Songs ───────────────────────────────────────────────────────────────────

@Composable
fun SongsPage(viewModel: MusicHubViewModel, onLongPress: (SongModel) -> Unit) {
    val songs by viewModel.localSongs.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val zuneColors = LocalZuneColors.current

    val shown = remember(songs, query) { songs.filter { it.matchesMusicQuery(query) } }

    if (shown.isEmpty()) {
        MusicEmpty(
            message = when {
                errorMessage != null -> stringResource(R.string.music_load_failed, errorMessage!!)
                query.isNotBlank() -> stringResource(R.string.music_no_match)
                else -> stringResource(R.string.music_not_found)
            },
            hint = if (errorMessage == null && query.isBlank()) stringResource(R.string.music_not_found_hint) else null
        )
        return
    }

    val groups = remember(shown) { groupByLetter(shown) { it.title } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var jumping by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = ZuneDimens.ScreenPaddingHorizontal,
                bottom = 120.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            groups.forEach { (letter, inLetter) ->
                item(key = "letter_$letter") {
                    MusicSectionHeader(
                        letter = letter,
                        accent = zuneColors.accentColor,
                        modifier = Modifier.clickable { jumping = true }
                    )
                }
                items(inLetter, key = { it.id }) { song ->
                    TrackRow(
                        song = song,
                        leading = null,
                        isPlaying = playback.isOurs && playback.title == song.title && playback.isPlaying,
                        onClick = { viewModel.playSongs(shown, shown.indexOf(song).coerceAtLeast(0)) },
                        onLongClick = { onLongPress(song) }
                    )
                }
            }
        }

        if (jumping) {
            LetterJumpOverlay(
                available = groups.map { it.first }.toSet(),
                accent = zuneColors.accentColor,
                onPick = { letter ->
                    jumping = false
                    scope.launch { listState.scrollToItem(headerIndexOf(groups, letter)) }
                },
                onDismiss = { jumping = false }
            )
        }
    }
}

// ── Shared pieces ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    song: SongModel,
    leading: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = onClick
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp)
    ) {
        if (leading != null) {
            Text(
                text = leading,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) zuneColors.accentColor else zuneColors.textDim,
                modifier = Modifier.size(width = 28.dp, height = 20.dp)
            )
        } else {
            Box(modifier = Modifier.size(48.dp).background(PlaceholderInk)) {
                AlbumArt(song.albumArtUri, Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.size(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                // The track that is playing is the one in the accent; nothing else marks it.
                color = if (isPlaying) zuneColors.accentColor else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatTrackDuration(song.duration),
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.textDim,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/** A cover, with a note behind it for the albums that have none. */
@Composable
private fun AlbumArt(uri: android.net.Uri?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(PlaceholderInk), contentAlignment = Alignment.Center) {
        // The note sits underneath: an album with no cover, or one still loading, is never a
        // blank grey square.
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.18f),
            modifier = Modifier.size(20.dp)
        )
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun MusicEmpty(message: String, hint: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal)) {
        MetroEmpty(message = message)
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = LocalZuneColors.current.textDim,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ── Playlists ───────────────────────────────────────────────────────────────

/** The lists somebody has made here, and a way to start another. */
@Composable
fun PlaylistsPage(
    viewModel: MusicHubViewModel,
    onOpenPlaylist: (com.serkantkn.zunelauncher.data.model.Playlist) -> Unit,
    onNewPlaylist: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    val zuneColors = LocalZuneColors.current

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 120.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "new") {
            Text(
                text = stringResource(R.string.music_new_playlist),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewPlaylist)
                    .padding(vertical = 14.dp)
            )
        }

        if (playlists.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.music_no_playlists_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            val interactionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wpTilt(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onOpenPlaylist(playlist) }
                    )
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.music_track_count, playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textDim
                )
            }
        }
    }
}

/** Inside one playlist. */
@Composable
fun PlaylistDetailPage(
    viewModel: MusicHubViewModel,
    playlist: com.serkantkn.zunelauncher.data.model.Playlist,
    onTrackLongPress: (SongModel) -> Unit
) {
    val songs = viewModel.localSongs.collectAsState().value
    val tracks = remember(playlist, songs) { viewModel.tracksOf(playlist) }
    val playback by viewModel.playback.collectAsState()

    if (tracks.isEmpty()) {
        MusicEmpty(
            message = stringResource(R.string.music_playlist_empty),
            hint = stringResource(R.string.music_playlist_empty_hint)
        )
        return
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 120.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
            TrackRow(
                song = song,
                leading = null,
                isPlaying = playback.isOurs && playback.title == song.title && playback.isPlaying,
                onClick = { viewModel.playSongs(tracks, index) },
                onLongClick = { onTrackLongPress(song) }
            )
        }
    }
}
