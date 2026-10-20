package com.serkantkn.zunelauncher.ui.screens.music

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.serkantkn.zunelauncher.ui.components.ZuneHub
import com.serkantkn.zunelauncher.ui.components.ZuneHubBar
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import androidx.compose.foundation.lazy.itemsIndexed
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect

@Composable
fun MusicHubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: MusicHubViewModel = viewModel()
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    val statusMessage by viewModel.statusMessage.collectAsState()
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearStatus()
        }
    }

    // Coming back from the system's permission page has to be noticed: the hub is still standing
    // where it was, and nothing else would tell it that the answer changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionState()
        viewModel.refreshVideoPermission()
    }

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
    val selectedGenre by viewModel.selectedGenre.collectAsState()

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
        Box(modifier = modifier.fillMaxSize()) {
            // The cover behind the hub is whatever the hub is playing — its own track first, as
            // the rest of the screen already does — and the scrim over it matches the theme: a
            // black one under black titles made the light theme unreadable.
            // Xbox Music on the dashboard's greys had no cover behind it, nor a word: just the hub.
            val xboxTv = com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current && !com.serkantkn.zunelauncher.ui.tv.LocalVista.current
            val hasCover = !xboxTv && (playback.externalArt != null || playback.artworkUri != null)
            val backgroundColor = when {
                !hasCover -> Color.Transparent
                currentZuneColors.isDark -> Color.Black.copy(alpha = 0.8f)
                else -> Color.White.copy(alpha = 0.7f)
            }
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
                // The Zune HD's own signature: the artist's name, enormous and faint, behind the hub.
                if (playback.artist.isNotBlank() && !xboxTv) {
                    ZuneArtistBackdrop(
                        artist = playback.artist,
                        modifier = Modifier.padding(top = 120.dp)
                    )
                }
                when {
                    xboxTv -> Unit
                    playback.externalArt != null -> Image(
                        bitmap = playback.externalArt!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f
                    )
                    playback.artworkUri != null -> AsyncImage(
                        model = playback.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f
                    )
                }
            }

            val isWideScreen = LocalIsWideScreen.current
            val albumsLabel = stringResource(R.string.music_tab_albums)
            val artistsLabel = stringResource(R.string.music_tab_artists)
            val playlistsLabel = stringResource(R.string.music_tab_playlists)
            val openPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
            // The Zune layout has genres too, as the Zune HD did; and on a phone, instead of a pivot,
            // its menu of words, each opening its list under its own name (ZuneMusicMenu).
            val zuneLayout = com.serkantkn.zunelauncher.ui.animation.LocalZuneMotion.current
            val zuneMenu = zuneLayout && !isWideScreen
            val genresLabel = stringResource(R.string.music_tab_genres)
            val pages = listOf(
                stringResource(R.string.music_tab_now_playing),
                selectedAlbum?.title?.lowercase(Locale.getDefault()) ?: albumsLabel,
                selectedArtist?.name?.lowercase(Locale.getDefault()) ?: artistsLabel,
                stringResource(R.string.music_tab_songs),
                openPlaylist?.name?.lowercase(Locale.getDefault()) ?: playlistsLabel,
                stringResource(R.string.music_tab_videos)
            ) + if (zuneLayout) listOf(selectedGenre?.lowercase(Locale.getDefault()) ?: genresLabel) else emptyList()
            val pager = rememberLoopingPagerState(pageCount = pages.size)

            // The Zune menu: which list is open (null: the menu itself), and how far its page has
            // come in.
            var zuneSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Int?>(null) }
            val sectionIn = remember { androidx.compose.animation.core.Animatable(1f) }
            val animationsOn = com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled.current
            val sectionSlidePx = with(LocalDensity.current) { 96.dp.toPx() }
            fun openSection(index: Int, animate: Boolean = true) {
                zuneSection = index
                scope.launch {
                    pager.scrollToPage(index)
                    if (animate && animationsOn) {
                        sectionIn.snapTo(0f)
                        sectionIn.animateTo(1f, tween(SECTION_IN_MILLIS, easing = androidx.compose.animation.core.LinearEasing))
                    } else {
                        sectionIn.snapTo(1f)
                    }
                }
            }

            // The Zune title: what it says, and which page of the hub it is the title of - the hub,
            // one of its lists, or what is open in one. Going back up, the title flies back down to
            // the name it came from (ZuneHubFlight).
            val hubFlight = com.serkantkn.zunelauncher.ui.animation.LocalZuneHubFlight.current
            val zuneDetail = when (zuneSection) {
                ALBUMS_TAB -> selectedAlbum?.let { "album:${it.id}" }
                ARTISTS_TAB -> selectedArtist?.let { "artist:${it.name}" }
                GENRES_TAB -> selectedGenre?.let { "genre:$it" }
                PLAYLISTS_TAB -> openPlaylist?.let { "playlist:${it.id}" }
                else -> null
            }
            val zuneTitleKey = musicTitleKey(zuneSection, zuneDetail)
            val zuneTitleText = zuneSection?.let { pages.getOrNull(it) } ?: stringResource(R.string.zune_row_music)
            var shownTitle by remember { mutableStateOf(zuneTitleKey to zuneTitleText) }
            LaunchedEffect(zuneTitleKey) {
                val (before, beforeText) = shownTitle
                if (zuneMenu && before.startsWith("$zuneTitleKey:")) hubFlight?.closePage(before, beforeText)
                shownTitle = zuneTitleKey to zuneTitleText
            }

            // "videos" in the Zune list opens the hub here.
            val showVideos by com.serkantkn.zunelauncher.data.repository.MusicBridge.showVideos.collectAsState()
            LaunchedEffect(showVideos) {
                if (!showVideos) return@LaunchedEffect
                com.serkantkn.zunelauncher.data.repository.MusicBridge.consumeVideos()
                if (zuneMenu) openSection(VIDEOS_TAB, animate = false) else pager.scrollToPage(VIDEOS_TAB)
            }
            // Vista's "play all": everything shuffled, the hub on what is playing, once the songs are read.
            val shuffleAll by com.serkantkn.zunelauncher.data.repository.MusicBridge.pendingShuffleAll.collectAsState()
            LaunchedEffect(shuffleAll, songs.isNotEmpty()) {
                if (!shuffleAll || songs.isEmpty()) return@LaunchedEffect
                com.serkantkn.zunelauncher.data.repository.MusicBridge.consumeShuffleAll()
                viewModel.shuffleSongs(songs)
                if (zuneMenu) openSection(0, animate = false) else pager.scrollToPage(0)
            }
            // A record played from quickplay or a pinned tile opens on that record.
            val pendingAlbum by com.serkantkn.zunelauncher.data.repository.MusicBridge.pendingAlbum.collectAsState()
            LaunchedEffect(pendingAlbum) {
                if (zuneMenu && pendingAlbum != null) openSection(ALBUMS_TAB, animate = false)
            }

            /** Opens an album, sending its name up into the pivot. */
            fun openAlbum(album: AlbumModel, from: Rect?) {
                viewModel.selectAlbum(album)
                if (zuneMenu) {
                    // The album's name flies up from its tile and becomes the title.
                    hubFlight?.openPage(
                        musicTitleKey(ALBUMS_TAB, "album:${album.id}"),
                        album.title.lowercase(Locale.getDefault()),
                        musicAlbumItem(album.id)
                    )
                    openSection(ALBUMS_TAB)
                    return
                }
                scope.launch {
                    pager.scrollToPage(ALBUMS_TAB)
                    // A tablet has no pivot for the name to fly into; its heading changes instead.
                    if (from != null && !isWideScreen) {
                        albumZoom.flyToTitle(
                            album.title.lowercase(Locale.getDefault()),
                            from,
                            ALBUM_TILE_NAME_SIZE
                        )
                    }
                }
            }

            // In the Zune menu, Back from a list goes back to the menu; anything open inside the
            // list closes first (the handler after this one).
            BackHandler(enabled = zuneMenu && zuneSection != null) { zuneSection = null }

            // Back steps out of an album or an artist before it leaves the hub.
            BackHandler(
                enabled = selectedAlbum != null || selectedArtist != null || selectedGenre != null || isSearchOpen ||
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
                    selectedGenre != null -> viewModel.selectGenre(null)
                    else -> viewModel.selectPlaylist(null)
                }
            }


            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp.dp
            val density = LocalDensity.current
            val screenWidthPx = with(density) { screenWidthDp.toPx() }
            val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
            val overflowYPx = with(density) { (-24).dp.toPx() }

            // ── The bottom bar ─────────────────────────────────────────────
            val visibleSongs = when {
                selectedAlbum != null -> viewModel.tracksOf(selectedAlbum!!)
                selectedArtist != null -> viewModel.tracksOf(selectedArtist!!)
                else -> songs
            }

            // What is playing, on every page but the one that already shows it.
            val currentPage = (pager.currentPage % pages.size).let { if (it < 0) it + pages.size else it }

            val onVideos = currentPage == VIDEOS_TAB
            val barActions = if (onVideos) {
                listOf(
                    WpBarAction(Icons.Default.Search, stringResource(R.string.music_action_search)) {
                        isSearchOpen = true
                    },
                    WpBarAction(Icons.Default.Refresh, stringResource(R.string.music_action_refresh)) {
                        viewModel.loadVideos()
                    }
                )
            } else {
                listOf(
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
                )
            }
            val barMenu = if (onVideos) emptyList() else buildList {
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
                add(WpBarMenuItem(stringResource(R.string.music_sleep_timer)) { showSleepTimer = true })
                add(WpBarMenuItem(stringResource(R.string.music_equalizer)) { viewModel.openEqualizer(context) })
                add(WpBarMenuItem(stringResource(R.string.music_action_refresh)) { viewModel.loadLocalSongs() })
            }

            ZuneHub(
                title = stringResource(R.string.music_hub),
                tabs = pages,
                pager = pager,
                bar = ZuneHubBar(barActions, barMenu),
                // The music hub's own header on a phone: its name giant and sliding with the
                // pivot, and the album heading an album's name flies into.
                phoneHeader = if (zuneMenu) {
                    {
                        ZuneMusicTitle(
                            title = zuneTitleText,
                            key = zuneTitleKey
                        )
                    }
                } else { {
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
                } },
                phonePagePadding = PaddingValues(0.dp),
                showTabs = !zuneMenu,
                swipeEnabled = !zuneMenu,
                // One list at a time in the Zune menu: nothing of the next one to peek in.
                phonePeek = !zuneMenu,
                takeover = if (zuneMenu && zuneSection == null) {
                    {
                        val entries = buildList {
                            if (playback.hasSomething) add(ZuneMusicEntry(0, stringResource(R.string.music_tab_now_playing)))
                            add(ZuneMusicEntry(ARTISTS_TAB, artistsLabel))
                            add(ZuneMusicEntry(ALBUMS_TAB, albumsLabel))
                            add(ZuneMusicEntry(SONGS_TAB, stringResource(R.string.music_tab_songs)))
                            add(ZuneMusicEntry(GENRES_TAB, genresLabel))
                            add(ZuneMusicEntry(PLAYLISTS_TAB, playlistsLabel))
                            add(ZuneMusicEntry(VIDEOS_TAB, stringResource(R.string.music_tab_videos)))
                        }
                        ZuneMusicMenu(entries = entries, onPick = { page ->
                            // The word touched flies up and becomes the list's title.
                            entries.firstOrNull { it.page == page }?.let { entry ->
                                hubFlight?.openPage(musicTitleKey(page), entry.label, musicMenuItem(page))
                            }
                            // A list opens at its top, not inside whatever was open in it before.
                            viewModel.selectAlbum(null)
                            viewModel.selectArtist(null)
                            viewModel.selectGenre(null)
                            viewModel.selectPlaylist(null)
                            openSection(page)
                        })
                    }
                } else null,
                wideOnSelected = { index ->
                    if (index != ALBUMS_TAB) viewModel.selectAlbum(null)
                    if (index != ARTISTS_TAB) viewModel.selectArtist(null)
                    if (index != PLAYLISTS_TAB) viewModel.selectPlaylist(null)
                    if (index != GENRES_TAB) viewModel.selectGenre(null)
                },
                overlay = {
                    // What is playing, on every page but the one that already shows it.
                    val onNowPlaying = if (zuneMenu) zuneSection == 0 else currentPage == 0
                    if (playback.hasSomething && !onNowPlaying) {
                        MusicMiniPlayer(
                            playback = playback,
                            accent = LocalZuneColors.current.accentColor,
                            onOpen = {
                                if (zuneMenu) openSection(0) else scope.launch { pager.animateScrollToPage(0) }
                            },
                            onPlayPause = viewModel::playPause,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = com.serkantkn.zunelauncher.ui.components.bottomBarHeight())
                        )
                    }
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
            ) { index ->
                androidx.compose.foundation.layout.Box(
                    modifier = if (zuneMenu) Modifier.zuneMusicSection({ sectionIn.value }, sectionSlidePx) else Modifier
                ) {
                when {
                    index == VIDEOS_TAB -> VideosPage(viewModel)
                    !hasPermission -> ZunePermissionRequest(
                        title = stringResource(R.string.music_permission_title).lowercase(Locale.getDefault()),
                        message = stringResource(R.string.music_permission_message),
                        buttonLabel = stringResource(R.string.grant_permission).lowercase(Locale.getDefault()),
                        onRequest = { requestAudioPermission() }
                    )
                    else -> MusicPage(
                        page = index,
                        viewModel = viewModel,
                        selectedAlbum = selectedAlbum,
                        selectedArtist = selectedArtist,
                        onOpenAlbum = ::openAlbum,
                        onOpenArtist = { artist ->
                            if (zuneMenu) {
                                hubFlight?.openPage(
                                    musicTitleKey(ARTISTS_TAB, "artist:${artist.name}"),
                                    artist.name.lowercase(Locale.getDefault()),
                                    musicArtistItem(artist.name)
                                )
                            }
                            viewModel.selectArtist(artist)
                            if (zuneMenu) openSection(ARTISTS_TAB) else scope.launch { pager.scrollToPage(ARTISTS_TAB) }
                        },
                        onTrackLongPress = { trackSheetTarget = it },
                        openPlaylist = openPlaylist,
                        onOpenPlaylist = { playlist ->
                            if (zuneMenu) {
                                hubFlight?.openPage(
                                    musicTitleKey(PLAYLISTS_TAB, "playlist:${playlist.id}"),
                                    playlist.name.lowercase(Locale.getDefault()),
                                    musicPlaylistItem(playlist.id)
                                )
                            }
                            viewModel.selectPlaylist(playlist.id)
                            scope.launch { pager.scrollToPage(PLAYLISTS_TAB) }
                        },
                        onNewPlaylist = { showNewPlaylist = true },
                        selectedGenre = selectedGenre,
                        onOpenGenre = { genre ->
                            if (zuneMenu) {
                                hubFlight?.openPage(
                                    musicTitleKey(GENRES_TAB, "genre:$genre"),
                                    genre.lowercase(Locale.getDefault()),
                                    musicGenreItem(genre)
                                )
                            }
                            viewModel.selectGenre(genre)
                            if (zuneMenu) openSection(GENRES_TAB)
                        }
                    )
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
private const val VIDEOS_TAB = MUSIC_VIDEOS_PAGE
/** Only in the Zune layout, after the rest so they keep their places. */
private const val GENRES_TAB = 6

/** How long a list takes to come in under its word in the Zune menu. */
private const val SECTION_IN_MILLIS = 560

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
    onNewPlaylist: () -> Unit,
    selectedGenre: String?,
    onOpenGenre: (String) -> Unit
) {
    when (page) {
        0 -> if (com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen.current) {
            // Across a whole tablet the cover and its controls keep to a column in the middle.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = 560.dp).fillMaxHeight()) { NowPlayingPage(viewModel) }
            }
        } else {
            NowPlayingPage(viewModel)
        }
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
        GENRES_TAB -> GenresPage(viewModel, selectedGenre, onOpenGenre, onTrackLongPress)
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
    // Its words are white: on the Xbox's light grey it stands on the dark of Xbox Music's player.
    val xbox = com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current && !com.serkantkn.zunelauncher.ui.tv.LocalVista.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (xbox) Modifier.background(Color(0xF0262A2B)) else Modifier)
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
