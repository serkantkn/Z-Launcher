package com.serkantkn.zunelauncher.ui.screens.music

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import com.serkantkn.zunelauncher.data.model.AlbumModel
import com.serkantkn.zunelauncher.data.model.ArtistModel
import com.serkantkn.zunelauncher.data.model.SongModel
import com.serkantkn.zunelauncher.ui.animation.ZuneTitleZoomOverlay
import com.serkantkn.zunelauncher.ui.animation.rememberZuneTitleZoomState
import com.serkantkn.zunelauncher.ui.animation.rememberZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.components.MetroTextField
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.screens.messaging.MessagingSheet
import com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction
import com.serkantkn.zunelauncher.util.MusicSort
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import java.util.Locale
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import java.util.concurrent.TimeUnit

@Composable
fun MusicHubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: MusicHubViewModel = viewModel()
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    // Coming back from the system's permission page has to be noticed: the hub is still standing
    // where it was, and nothing else would tell it that the answer changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissionState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> viewModel.onPermissionResult(granted) }
    )

    val albums by viewModel.albums.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val songs by viewModel.localSongs.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val pinnedAlbums by viewModel.pinnedAlbums.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isSearchOpen by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var trackSheetTarget by remember { mutableStateOf<SongModel?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var addToPlaylistTarget by remember { mutableStateOf<SongModel?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    /** The track waiting to go into a list that does not exist yet. */
    var pendingPlaylistSong by remember { mutableStateOf<String?>(null) }

    // The album's name flies from its tile up into the pivot, as a Zune HD title did.
    val albumZoom = rememberZuneTitleZoomState()
    val albumTabAnchor = rememberZuneZoomAnchor()
    albumZoom.bindTitle(albumTabAnchor, MUSIC_PIVOT_FONT_SIZE)

    fun requestAudioPermission() {
        permissionLauncher.launch(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_AUDIO
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )
    }
    
    // Animate accent color transition
    val animatedAccent by animateColorAsState(
        targetValue = dominantColor ?: LocalZuneColors.current.accentColor,
        animationSpec = tween(durationMillis = 1000),
        label = "AccentColorAnimation"
    )

    // Override theme for this hub
    val currentZuneColors = LocalZuneColors.current
    val musicHubColors = currentZuneColors.copy(
        accentColor = animatedAccent
    )

    CompositionLocalProvider(LocalZuneColors provides musicHubColors) {
        ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
            val backgroundColor = if (mediaState.albumArt != null) Color.Black.copy(alpha = 0.8f) else Color.Transparent
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            
            // The Zune HD's own signature: the artist's name, enormous and faint, behind the hub.
            if (playback.artist.isNotBlank()) {
                ZuneArtistBackdrop(
                    artist = playback.artist,
                    modifier = Modifier.padding(top = 120.dp)
                )
            }

            // Dynamic Album Art Background
            mediaState.albumArt?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f // Dim the background
                )
            }

            val albumsLabel = stringResource(R.string.music_tab_albums)
            val artistsLabel = stringResource(R.string.music_tab_artists)
            val playlistsLabel = stringResource(R.string.music_tab_playlists)
            val openPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
            val pages = listOf(
                stringResource(R.string.music_tab_now_playing),
                selectedAlbum?.title?.lowercase(Locale.getDefault()) ?: albumsLabel,
                selectedArtist?.name?.lowercase(Locale.getDefault()) ?: artistsLabel,
                stringResource(R.string.music_tab_songs),
                openPlaylist?.name?.lowercase(Locale.getDefault()) ?: playlistsLabel
            )
            val pager = rememberLoopingPagerState(pageCount = pages.size)

            /** Opens an album, sending its name up into the pivot. */
            fun openAlbum(album: AlbumModel, from: Rect?) {
                viewModel.selectAlbum(album)
                scope.launch {
                    pager.scrollToPage(ALBUMS_TAB)
                    if (from != null) {
                        albumZoom.flyToTitle(
                            album.title.lowercase(Locale.getDefault()),
                            from,
                            ALBUM_TILE_NAME_SIZE
                        )
                    }
                }
            }

            // Back steps out of an album or an artist before it leaves the hub.
            BackHandler(
                enabled = selectedAlbum != null || selectedArtist != null || isSearchOpen ||
                    showQueue || showSortSheet || trackSheetTarget != null ||
                    selectedPlaylistId != null || showNewPlaylist || addToPlaylistTarget != null
            ) {
                when {
                    addToPlaylistTarget != null -> addToPlaylistTarget = null
                    showNewPlaylist -> showNewPlaylist = false
                    trackSheetTarget != null -> trackSheetTarget = null
                    showSortSheet -> showSortSheet = false
                    showQueue -> showQueue = false
                    isSearchOpen -> {
                        isSearchOpen = false
                        viewModel.setSearchQuery("")
                    }
                    selectedAlbum != null -> viewModel.selectAlbum(null)
                    selectedArtist != null -> viewModel.selectArtist(null)
                    else -> viewModel.selectPlaylist(null)
                }
            }

            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp.dp
            val density = LocalDensity.current
            val screenWidthPx = with(density) { screenWidthDp.toPx() }
            val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
            val overflowYPx = with(density) { (-24).dp.toPx() }
            val isWideScreen = LocalIsWideScreen.current
            
            Column(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    ZuneWideHubTitle(text = stringResource(R.string.music_hub))

                    if (!hasPermission) {
                        ZunePermissionRequest(
                            title = stringResource(R.string.music_permission_title).lowercase(Locale.getDefault()),
                            message = stringResource(R.string.music_permission_message),
                            buttonLabel = stringResource(R.string.grant_permission).lowercase(Locale.getDefault()),
                            onRequest = { requestAudioPermission() }
                        )
                    } else {
                        ZuneWidePanorama(tabs = pages) { index ->
                            MusicPage(
                                page = index,
                                viewModel = viewModel,
                                selectedAlbum = selectedAlbum,
                                selectedArtist = selectedArtist,
                                onOpenAlbum = { album, _ -> viewModel.selectAlbum(album) },
                                onOpenArtist = { artist -> viewModel.selectArtist(artist) },
                                onTrackLongPress = { trackSheetTarget = it },
                                openPlaylist = openPlaylist,
                                onOpenPlaylist = { playlist -> viewModel.selectPlaylist(playlist.id) },
                                onNewPlaylist = { showNewPlaylist = true }
                            )
                        }
                    }
                } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 28.dp,
                            bottom = 4.dp,
                            start = ZuneDimens.ScreenPaddingHorizontal
                        )
                ) {
                    val pageCount = pager.pageCount
                    val cycle = (pager.pagerState.currentPage + pager.pagerState.currentPageOffsetFraction) % pageCount
                    val actualCycle = if (cycle < 0) cycle + pageCount else cycle
                    val threshold = (pageCount - 1).toFloat()

                    val translationX1: Float
                    val translationX2: Float

                    if (actualCycle <= threshold) {
                        translationX1 = -actualCycle * parallaxMultiplierPx
                        translationX2 = screenWidthPx
                    } else {
                        val fraction = actualCycle - threshold
                        translationX1 = -threshold * parallaxMultiplierPx - fraction * screenWidthPx
                        translationX2 = screenWidthPx - fraction * screenWidthPx
                    }

                    Text(
                        text = stringResource(R.string.music_hub),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 96.sp,
                            letterSpacing = (-4).sp,
                            lineHeight = 96.sp
                        ),
                        color = if (LocalZuneColors.current.isDark) Color.White else Color.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            translationX = translationX1
                            translationY = overflowYPx
                        }
                    )
                    Text(
                        text = stringResource(R.string.music_hub),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 96.sp,
                            letterSpacing = (-4).sp,
                            lineHeight = 96.sp
                        ),
                        color = if (LocalZuneColors.current.isDark) Color.White else Color.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            translationX = translationX2
                            translationY = overflowYPx
                        }
                    )
                }

                ZunePivotTabs(
                    tabs = pages,
                    state = pager,
                    fontSize = MUSIC_PIVOT_FONT_SIZE,
                    firstTabAnchor = albumTabAnchor,
                    firstTabAlpha = albumZoom.titleAlpha,
                    zoomTabIndex = ALBUMS_TAB,
                    onSelected = { index ->
                        // Stepping away from an album or an artist closes it, so the tab goes back
                        // to saying what it is.
                        if (index != ALBUMS_TAB) viewModel.selectAlbum(null)
                        if (index != ARTISTS_TAB) viewModel.selectArtist(null)
                        if (index != PLAYLISTS_TAB) viewModel.selectPlaylist(null)
                    },
                    modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                )

                if (!hasPermission) {
                    ZunePermissionRequest(
                        title = stringResource(R.string.music_permission_title).lowercase(Locale.getDefault()),
                        message = stringResource(R.string.music_permission_message),
                        buttonLabel = stringResource(R.string.grant_permission).lowercase(Locale.getDefault()),
                        onRequest = { requestAudioPermission() }
                    )
                } else {
                    ZuneLoopingPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        MusicPage(
                            page = page,
                            viewModel = viewModel,
                            selectedAlbum = selectedAlbum,
                            selectedArtist = selectedArtist,
                            onOpenAlbum = ::openAlbum,
                            onOpenArtist = { artist ->
                                viewModel.selectArtist(artist)
                                scope.launch { pager.scrollToPage(ARTISTS_TAB) }
                            },
                            onTrackLongPress = { trackSheetTarget = it },
                            openPlaylist = openPlaylist,
                            onOpenPlaylist = { playlist ->
                                viewModel.selectPlaylist(playlist.id)
                                scope.launch { pager.scrollToPage(PLAYLISTS_TAB) }
                            },
                            onNewPlaylist = { showNewPlaylist = true }
                        )
                    }
                }
                }
            }

            // ── The bottom bar ─────────────────────────────────────────────
            val visibleSongs = when {
                selectedAlbum != null -> viewModel.tracksOf(selectedAlbum!!)
                selectedArtist != null -> viewModel.tracksOf(selectedArtist!!)
                else -> songs
            }

            // What is playing, on every page but the one that already shows it.
            val currentPage = (pager.currentPage % pages.size).let { if (it < 0) it + pages.size else it }
            if (playback.hasSomething && currentPage != 0) {
                MusicMiniPlayer(
                    playback = playback,
                    accent = LocalZuneColors.current.accentColor,
                    onOpen = { scope.launch { pager.animateScrollToPage(0) } },
                    onPlayPause = viewModel::playPause,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 56.dp + WindowInsets.navigationBars
                            .asPaddingValues().calculateBottomPadding())
                )
            }

            WindowsPhoneBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
                actions = listOf(
                    WpBarAction(Icons.Default.Shuffle, stringResource(R.string.music_action_shuffle)) {
                        viewModel.shuffleSongs(visibleSongs)
                    },
                    WpBarAction(Icons.Default.Search, stringResource(R.string.music_action_search)) {
                        isSearchOpen = true
                    },
                    WpBarAction(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.music_action_queue)) {
                        showQueue = true
                    },
                    WpBarAction(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.music_action_sort)) {
                        showSortSheet = true
                    }
                ),
                menuItems = buildList {
                    selectedAlbum?.let { album ->
                        val pinned = album.id in pinnedAlbums
                        add(
                            WpBarMenuItem(
                                stringResource(
                                    if (pinned) R.string.music_unpin_album else R.string.music_pin_album
                                )
                            ) { viewModel.togglePinAlbum(album) }
                        )
                    }
                    addAll(listOf(
                    WpBarMenuItem(stringResource(R.string.music_sleep_timer)) { showSleepTimer = true },
                    WpBarMenuItem(stringResource(R.string.music_equalizer)) { viewModel.openEqualizer(context) },
                    WpBarMenuItem(stringResource(R.string.music_action_refresh)) { viewModel.loadLocalSongs() }
                    ))
                }
            )

            // ── Searching ──────────────────────────────────────────────────
            if (isSearchOpen) {
                MusicSearchOverlay(
                    query = searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = {
                        isSearchOpen = false
                        viewModel.setSearchQuery("")
                    }
                )
            }

            if (showSortSheet) {
                MessagingSheet(
                    title = stringResource(R.string.music_action_sort),
                    onDismiss = { showSortSheet = false }
                ) {
                    MusicSort.entries.forEach { option ->
                        SheetAction(
                            label = stringResource(musicSortLabel(option)),
                            color = if (option == sort) LocalZuneColors.current.accentColor else null
                        ) {
                            viewModel.setSort(option)
                            showSortSheet = false
                        }
                    }
                }
            }

            if (showQueue) {
                MusicQueueSheet(
                    playback = playback,
                    onPlayIndex = { viewModel.playQueueIndex(it) },
                    onRemove = { viewModel.removeFromQueue(it) },
                    onDismiss = { showQueue = false }
                )
            }

            trackSheetTarget?.let { song ->
                MessagingSheet(title = song.title, onDismiss = { trackSheetTarget = null }) {
                    SheetAction(label = stringResource(R.string.music_play_next)) {
                        viewModel.playNext(song)
                        trackSheetTarget = null
                    }
                    SheetAction(label = stringResource(R.string.music_add_to_queue)) {
                        viewModel.addToQueue(listOf(song))
                        trackSheetTarget = null
                    }
                    SheetAction(label = stringResource(R.string.music_add_to_playlist)) {
                        addToPlaylistTarget = song
                        trackSheetTarget = null
                    }
                }
            }

            addToPlaylistTarget?.let { song ->
                MessagingSheet(
                    title = stringResource(R.string.music_add_to_playlist),
                    onDismiss = { addToPlaylistTarget = null }
                ) {
                    SheetAction(
                        label = stringResource(R.string.music_new_playlist),
                        color = LocalZuneColors.current.accentColor
                    ) {
                        addToPlaylistTarget = null
                        showNewPlaylist = true
                        newPlaylistName = ""
                        pendingPlaylistSong = song.id
                    }
                    playlists.forEach { playlist ->
                        SheetAction(label = playlist.name) {
                            viewModel.addToPlaylist(playlist.id, listOf(song.id))
                            addToPlaylistTarget = null
                        }
                    }
                }
            }

            if (showSleepTimer) {
                val sleepMinutes by viewModel.sleepMinutesLeft.collectAsState()
                MessagingSheet(
                    title = stringResource(R.string.music_sleep_timer),
                    onDismiss = { showSleepTimer = false }
                ) {
                    if (sleepMinutes > 0) {
                        SheetAction(
                            label = stringResource(R.string.music_sleep_cancel, sleepMinutes),
                            color = LocalZuneColors.current.accentColor
                        ) {
                            viewModel.cancelSleepTimer()
                            showSleepTimer = false
                        }
                    }
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        SheetAction(label = stringResource(R.string.music_sleep_minutes, minutes)) {
                            viewModel.startSleepTimer(minutes)
                            showSleepTimer = false
                        }
                    }
                }
            }

            if (showNewPlaylist) {
                MessagingSheet(
                    title = stringResource(R.string.music_new_playlist),
                    onDismiss = {
                        showNewPlaylist = false
                        pendingPlaylistSong = null
                    }
                ) {
                    Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        MetroTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = stringResource(R.string.music_playlist_name)
                        )
                    }
                    SheetAction(
                        label = stringResource(R.string.music_create),
                        color = LocalZuneColors.current.accentColor
                    ) {
                        viewModel.createPlaylist(
                            newPlaylistName,
                            listOfNotNull(pendingPlaylistSong)
                        )
                        newPlaylistName = ""
                        pendingPlaylistSong = null
                        showNewPlaylist = false
                    }
                }
            }
        }
    }
}

    // The word in flight, over everything.
    ZuneTitleZoomOverlay(state = albumZoom, letterSpacing = (-1).sp)
}

/** Which page of the pivot is which. */
private const val ALBUMS_TAB = 1
private const val ARTISTS_TAB = 2
private const val SONGS_TAB = 3
private const val PLAYLISTS_TAB = 4

/** The pivot's type size, so a word flying into it lands at the right scale. */
private val MUSIC_PIVOT_FONT_SIZE = 36.sp

/** The size an album's name is written at on its tile: where the flight begins. */
private val ALBUM_TILE_NAME_SIZE = 15.sp

@StringRes
private fun musicSortLabel(sort: MusicSort): Int = when (sort) {
    MusicSort.TITLE -> R.string.music_sort_title
    MusicSort.ARTIST -> R.string.music_sort_artist
    MusicSort.ALBUM -> R.string.music_sort_album
    MusicSort.RECENT -> R.string.music_sort_recent
    MusicSort.DURATION -> R.string.music_sort_duration
}

/** One page of the hub, whichever of the four it is and whatever is open inside it. */
@Composable
private fun MusicPage(
    page: Int,
    viewModel: MusicHubViewModel,
    selectedAlbum: AlbumModel?,
    selectedArtist: ArtistModel?,
    onOpenAlbum: (AlbumModel, Rect?) -> Unit,
    onOpenArtist: (ArtistModel) -> Unit,
    onTrackLongPress: (SongModel) -> Unit,
    openPlaylist: com.serkantkn.zunelauncher.data.model.Playlist?,
    onOpenPlaylist: (com.serkantkn.zunelauncher.data.model.Playlist) -> Unit,
    onNewPlaylist: () -> Unit
) {
    when (page) {
        0 -> NowPlayingPage(viewModel)
        ALBUMS_TAB -> if (selectedAlbum != null) {
            AlbumDetailPage(viewModel, selectedAlbum)
        } else {
            AlbumsPage(viewModel, onOpenAlbum)
        }
        ARTISTS_TAB -> if (selectedArtist != null) {
            ArtistDetailPage(viewModel, selectedArtist, onOpenAlbum)
        } else {
            ArtistsPage(viewModel, onOpenArtist)
        }
        SONGS_TAB -> SongsPage(viewModel, onTrackLongPress)
        else -> if (openPlaylist != null) {
            PlaylistDetailPage(viewModel, openPlaylist, onTrackLongPress)
        } else {
            PlaylistsPage(viewModel, onOpenPlaylist, onNewPlaylist)
        }
    }
}

@Composable
fun NowPlayingPage(viewModel: MusicHubViewModel) {
    val playback by viewModel.playback.collectAsState()
    val accent = LocalZuneColors.current.accentColor

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    val lyrics by viewModel.lyrics.collectAsState()
    val lyricsLoading by viewModel.lyricsLoading.collectAsState()
    val onlineAllowed by viewModel.onlineExtrasEnabled.collectAsState()
    var showLyrics by remember { mutableStateOf(false) }

        Spacer(modifier = Modifier.weight(1f))

        // ── The cover, or the words in its place ──
        if (showLyrics) {
            // The words get the whole width; a square would wrap every other line.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clickable { showLyrics = false },
                contentAlignment = Alignment.Center
            ) {
                LyricsPanel(
                    lyrics = lyrics,
                    positionMillis = playback.positionMillis,
                    loading = lyricsLoading,
                    accent = accent,
                    onlineAllowed = onlineAllowed,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable { showLyrics = !showLyrics },
            contentAlignment = Alignment.Center
        ) {
            when {
                playback.externalArt != null -> Image(
                    bitmap = playback.externalArt!!.asImageBitmap(),
                    contentDescription = stringResource(R.string.music_album_art),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                playback.artworkUri != null -> AsyncImage(
                    model = playback.artworkUri,
                    contentDescription = stringResource(R.string.music_album_art),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                else -> Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (!playback.hasSomething) {
            Text(
                text = stringResource(R.string.music_nothing_playing),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                text = stringResource(R.string.music_nothing_playing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalZuneColors.current.textDim,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Text(
                text = playback.title.ifBlank { stringResource(R.string.music_unknown_song) },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 26.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playback.artist.ifBlank { stringResource(R.string.music_unknown_artist) },
                style = MaterialTheme.typography.bodyLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (playback.album.isNotBlank()) {
                Text(
                    text = playback.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZuneColors.current.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Only worth saying when there is something to turn over to.
        if (playback.hasSomething && (!lyrics.isEmpty || lyricsLoading)) {
            Text(
                text = stringResource(
                    if (showLyrics) R.string.music_show_cover else R.string.music_lyrics
                ),
                style = MaterialTheme.typography.labelSmall,
                color = LocalZuneColors.current.textDim,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable { showLyrics = !showLyrics }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── How far through ──
        MusicProgressLine(
            positionMillis = playback.positionMillis,
            durationMillis = playback.durationMillis,
            accent = accent,
            // Another app's session may refuse to be scrubbed; only our own player is dragged.
            enabled = playback.isOurs,
            onSeek = viewModel::seekTo
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Transport ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::skipToPrevious, enabled = playback.hasSomething) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.music_previous),
                    tint = if (playback.hasSomething) Color.White else Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(42.dp)
                )
            }
            IconButton(onClick = viewModel::playPause, enabled = playback.hasSomething) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.music_play_pause),
                    tint = if (playback.hasSomething) accent else Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(64.dp)
                )
            }
            IconButton(onClick = viewModel::skipToNext, enabled = playback.hasSomething) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.music_next),
                    tint = if (playback.hasSomething) Color.White else Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        ShuffleRepeatRow(
            shuffleEnabled = playback.shuffleEnabled,
            repeatMode = playback.repeatMode,
            accent = accent,
            enabled = playback.isOurs,
            onToggleShuffle = viewModel::toggleShuffle,
            onCycleRepeat = viewModel::cycleRepeatMode,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}
