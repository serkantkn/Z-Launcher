package com.serkantkn.zunelauncher.ui.screens.music

import com.serkantkn.zunelauncher.util.toUserMessage
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.data.model.AlbumModel
import com.serkantkn.zunelauncher.data.model.ArtistModel
import com.serkantkn.zunelauncher.data.model.Playlist
import com.serkantkn.zunelauncher.data.model.StartFolders
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.repository.MusicBridge
import com.serkantkn.zunelauncher.data.model.SongModel
import com.serkantkn.zunelauncher.util.Lyrics
import com.serkantkn.zunelauncher.util.MusicSort
import com.serkantkn.zunelauncher.util.hasNetwork
import com.serkantkn.zunelauncher.util.hasUnmeteredNetwork
import com.serkantkn.zunelauncher.util.sortAlbumTracks
import com.serkantkn.zunelauncher.util.sortSongs
import com.serkantkn.zunelauncher.data.service.ThirdPartyMediaController
import com.serkantkn.zunelauncher.data.service.LocalMediaController
import com.serkantkn.zunelauncher.data.service.LocalPlaybackState
import com.serkantkn.zunelauncher.data.service.QueueEntry
import androidx.media3.common.Player
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicHubViewModel(application: Application) : AndroidViewModel(application) {

    private val musicRepository = application.appContainer.musicRepository
    val globalMediaController = ThirdPartyMediaController(application)
    val localMediaController = LocalMediaController(application)

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _localSongs = MutableStateFlow<List<SongModel>>(emptyList())
    val localSongs: StateFlow<List<SongModel>> = _localSongs.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumModel>>(emptyList())
    val albums: StateFlow<List<AlbumModel>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistModel>>(emptyList())
    val artists: StateFlow<List<ArtistModel>> = _artists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Whether the phone has let the launcher read its music.
     *
     * The hub used to ask the media store without ever asking for the permission, so on any
     * modern phone the query came back empty and the songs page said there was no music. There is
     * a difference between "no music" and "not allowed to look", and the hub now says which.
     */
    private val _hasPermission = MutableStateFlow(checkAudioPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) loadLocalSongs()
    }

    fun refreshPermissionState() {
        val granted = checkAudioPermission()
        if (granted != _hasPermission.value) onPermissionResult(granted) else if (granted) loadLocalSongs()
    }

    private fun checkAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val _dominantColor = MutableStateFlow<Color?>(null)
    val dominantColor: StateFlow<Color?> = _dominantColor.asStateFlow()

    val mediaState = globalMediaController.mediaState
        .stateIn(viewModelScope, SharingStarted.Lazily, globalMediaController.mediaState.value)

    private val musicDataStore = application.appContainer.musicDataStore

    /**
     * What is playing, from wherever it is playing.
     *
     * The hub's own player wins whenever it has a queue. Anything else on the phone — Spotify, a
     * browser tab — only shows through when the launcher itself is silent, and even then only if
     * notification access has been given, since that is the only way the system will say.
     */
    val playback: StateFlow<HubPlayback> = combine(
        localMediaController.state,
        globalMediaController.mediaState
    ) { local, external ->
        if (local.hasQueue) {
            HubPlayback(
                source = PlaybackSource.LOCAL,
                isPlaying = local.isPlaying,
                title = local.title,
                artist = local.artist,
                album = local.album,
                artworkUri = local.artworkUri,
                positionMillis = local.positionMillis,
                durationMillis = local.durationMillis,
                shuffleEnabled = local.shuffleEnabled,
                repeatMode = local.repeatMode,
                queue = local.queue,
                currentIndex = local.currentIndex
            )
        } else if (external.title.isNotBlank() || external.albumArt != null) {
            HubPlayback(
                source = PlaybackSource.EXTERNAL,
                isPlaying = external.isPlaying,
                title = external.title,
                artist = external.artist,
                externalArt = external.albumArt
            )
        } else {
            HubPlayback()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubPlayback())

    // ── Transport, routed to whatever is actually playing ────────────────────

    fun playPause() {
        if (playback.value.source == PlaybackSource.LOCAL) {
            localMediaController.playPause()
        } else {
            globalMediaController.playPause()
        }
    }

    fun skipToNext() {
        if (playback.value.source == PlaybackSource.LOCAL) {
            localMediaController.skipToNext()
        } else {
            globalMediaController.skipToNext()
        }
    }

    fun skipToPrevious() {
        if (playback.value.source == PlaybackSource.LOCAL) {
            localMediaController.skipToPrevious()
        } else {
            globalMediaController.skipToPrevious()
        }
    }

    /** Only our own player can be scrubbed; another app's session may refuse to seek. */
    fun seekTo(positionMillis: Long) {
        if (playback.value.source == PlaybackSource.LOCAL) localMediaController.seekTo(positionMillis)
    }

    fun toggleShuffle() {
        localMediaController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        localMediaController.cycleRepeatMode()
    }

    fun playQueueIndex(index: Int) = localMediaController.playQueueIndex(index)

    fun removeFromQueue(index: Int) = localMediaController.removeFromQueue(index)

    fun moveInQueue(from: Int, to: Int) = localMediaController.moveInQueue(from, to)

    init {
        globalMediaController.startListening()
        localMediaController.onPlaybackMoved = { ids, index, position ->
            viewModelScope.launch { musicDataStore.rememberQueue(ids, index, position) }
        }
        localMediaController.start()
        viewModelScope.launch {
            // The order the list was left in is the order it comes back in.
            val remembered = runCatching { musicDataStore.sortName.first() }.getOrNull().orEmpty()
            MusicSort.entries.firstOrNull { it.name == remembered }?.let { _sort.value = it }
            if (_hasPermission.value) loadLocalSongs()
            restoreRememberedQueue()
        }

        
        // The accent follows whatever is playing, from either player.
        viewModelScope.launch {
            playback.collect { state ->
                val art = state.externalArt ?: state.artworkUri?.let { loadArtwork(it) }
                extractDominantColor(art)
            }
        }
    }

    // ── What the pages are looking at ───────────────────────────────────────

    private val _selectedAlbum = MutableStateFlow<AlbumModel?>(null)
    val selectedAlbum: StateFlow<AlbumModel?> = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<ArtistModel?>(null)
    val selectedArtist: StateFlow<ArtistModel?> = _selectedArtist.asStateFlow()

    fun selectAlbum(album: AlbumModel?) {
        _selectedAlbum.value = album
    }

    fun selectArtist(artist: ArtistModel?) {
        _selectedArtist.value = artist
    }

    /** The tracks on one album, in the order the record was pressed. */
    fun tracksOf(album: AlbumModel): List<SongModel> =
        sortAlbumTracks(_localSongs.value.filter { it.albumId == album.id })

    /** Everything by one artist, album by album. */
    fun tracksOf(artist: ArtistModel): List<SongModel> =
        _localSongs.value.filter { it.artistId == artist.id && it.artist == artist.name }
            .let { sortSongs(it, MusicSort.ALBUM) }

    fun albumsOf(artist: ArtistModel): List<AlbumModel> =
        _albums.value.filter { album -> tracksOf(artist).any { it.albumId == album.id } }

    // ── Keeping a record on the Start screen ────────────────────────────────

    private val settingsDataStore = application.appContainer.settingsDataStore

    /** The records already on the board, so the menu can say "pin" or "unpin" truthfully. */
    val pinnedAlbums: StateFlow<Set<Long>> = settingsDataStore.startTiles
        .map { tiles -> tiles.mapNotNull { it.musicAlbumId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun togglePinAlbum(album: AlbumModel) {
        viewModelScope.launch {
            val tiles = settingsDataStore.startTiles.first()
            val id = "${StartTileItem.MUSIC_ALBUM_PREFIX}${album.id}"
            val updated = if (tiles.any { it.id == id }) {
                StartFolders.removeTile(tiles, id)
            } else {
                tiles + StartTileItem.fromMusicAlbum(album.id, album.title)
            }
            settingsDataStore.setStartTiles(updated)
        }
    }

    /** Plays the record a pinned tile asked for, once the library has been read. */
    private fun playPendingAlbum() {
        viewModelScope.launch {
            MusicBridge.pendingAlbum.collect { albumId ->
                if (albumId == null) return@collect
                var waited = 0L
                while (_localSongs.value.isEmpty() && waited < RESTORE_TIMEOUT_MS) {
                    kotlinx.coroutines.delay(RESTORE_POLL_MS)
                    waited += RESTORE_POLL_MS
                }
                val album = _albums.value.firstOrNull { it.id == albumId }
                if (album != null) {
                    _selectedAlbum.value = album
                    playSongs(tracksOf(album), 0)
                }
                MusicBridge.consume()
            }
        }
    }

    // ── Covers and words ────────────────────────────────────────────────────

    private val lyricsRepository = application.appContainer.lyricsRepository
    private val artworkRepository = application.appContainer.artworkRepository

    private val _lyrics = MutableStateFlow(Lyrics(emptyList()))
    /** The words to whatever is playing, timed when anyone has them timed. */
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()

    /**
     * Whether the hub may go looking online for what it cannot find on the phone.
     *
     * Two settings, both of which have to agree: the feature itself, and whether this connection
     * is one to spend.
     */
    private fun mayFetchOnline(): Boolean {
        if (!onlineExtras.value) return false
        val context = getApplication<Application>()
        return if (onlineWifiOnly.value) context.hasUnmeteredNetwork() else context.hasNetwork()
    }

    private val onlineExtras: StateFlow<Boolean> = settingsDataStore.musicOnlineExtras
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** What the hub shows the setting as, so the empty lyrics page can explain itself. */
    val onlineExtrasEnabled: StateFlow<Boolean> get() = onlineExtras

    private val onlineWifiOnly: StateFlow<Boolean> = settingsDataStore.musicOnlineWifiOnly
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var lyricsJob: kotlinx.coroutines.Job? = null

    /** Looks up the words for whatever is playing, whenever that changes. */
    private fun followLyrics() {
        viewModelScope.launch {
            playback.map { it.title to it.artist }.distinctUntilChanged().collect { (title, artist) ->
                lyricsJob?.cancel()
                if (title.isBlank()) {
                    _lyrics.value = Lyrics(emptyList())
                    return@collect
                }
                val song = _localSongs.value.firstOrNull { it.title == title && it.artist == artist }
                if (song == null) {
                    // Something else on the phone is playing; there is no file here to read.
                    _lyrics.value = Lyrics(emptyList())
                    return@collect
                }
                lyricsJob = launch {
                    _lyricsLoading.value = true
                    _lyrics.value = runCatching {
                        lyricsRepository.lyricsFor(song, mayFetchOnline())
                    }.getOrElse { Lyrics(emptyList()) }
                    _lyricsLoading.value = false
                }
            }
        }
    }

    /**
     * Fills in the covers the media store does not have.
     *
     * One album at a time and off the main thread, updating the lists as each answer arrives, so
     * a library with a hundred records fills in gradually rather than making everybody wait.
     */
    private fun resolveArtwork() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val allowNetwork = mayFetchOnline()
            _albums.value.forEach { album ->
                val sample = _localSongs.value.firstOrNull { it.albumId == album.id }
                val cover = runCatching {
                    artworkRepository.coverFor(album, sample, allowNetwork)
                }.getOrNull() ?: return@forEach

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _albums.value = _albums.value.map {
                        if (it.id == album.id) it.copy(artUri = cover) else it
                    }
                    _localSongs.value = _localSongs.value.map {
                        if (it.albumId == album.id) it.copy(albumArtUri = cover) else it
                    }
                    _artists.value = _artists.value.map { artist ->
                        if (artist.artUri == null || artist.artUri == album.artUri) {
                            val theirs = _localSongs.value.any {
                                it.artistId == artist.id && it.albumId == album.id
                            }
                            if (theirs) artist.copy(artUri = cover) else artist
                        } else artist
                    }
                }
            }
        }
    }

    // ── Sleep timer and sound ───────────────────────────────────────────────

    private val _sleepMinutesLeft = MutableStateFlow(0)
    /** Minutes until the music stops itself, or zero when nothing is set. */
    val sleepMinutesLeft: StateFlow<Int> = _sleepMinutesLeft.asStateFlow()

    private var sleepJob: kotlinx.coroutines.Job? = null

    /**
     * Stops the music after a while.
     *
     * Kept in the view model rather than booked with the alarm manager on purpose: a sleep timer
     * is for falling asleep to music that is playing now, and if the launcher is gone the music is
     * gone with it. Waking the phone later to stop something that already stopped would be worse
     * than useless.
     */
    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _sleepMinutesLeft.value = minutes
        sleepJob = viewModelScope.launch {
            var left = minutes
            while (left > 0) {
                kotlinx.coroutines.delay(60_000L)
                left--
                _sleepMinutesLeft.value = left
            }
            if (localMediaController.state.value.isPlaying) localMediaController.playPause()
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepMinutesLeft.value = 0
    }

    /**
     * Opens the phone's own equaliser.
     *
     * Android has had a system sound-effects panel for years and every phone maker puts their own
     * behind it; building another one here would be a worse version of something already there.
     */
    fun openEqualizer(context: android.content.Context) {
        runCatching {
            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(
                    android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE,
                    android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC
                )
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            ZuneLog.w("MusicHubViewModel", "this phone has no sound-effects panel", it)
        }
    }

    // ── Playlists ───────────────────────────────────────────────────────────

    val playlists: StateFlow<List<Playlist>> = musicDataStore.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedPlaylist.asStateFlow()

    fun selectPlaylist(id: String?) {
        _selectedPlaylist.value = id
    }

    /** The tracks in a playlist, in the order they were put there; deleted ones simply gone. */
    fun tracksOf(playlist: Playlist): List<SongModel> {
        val byId = _localSongs.value.associateBy { it.id }
        return playlist.songIds.mapNotNull { byId[it] }
    }

    fun createPlaylist(name: String, songIds: List<String> = emptyList()) {
        if (name.isBlank()) return
        viewModelScope.launch {
            musicDataStore.createPlaylist(name, songIds, System.currentTimeMillis())
        }
    }

    fun renamePlaylist(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { musicDataStore.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            musicDataStore.deletePlaylist(id)
            if (_selectedPlaylist.value == id) _selectedPlaylist.value = null
        }
    }

    fun addToPlaylist(id: String, songIds: List<String>) {
        viewModelScope.launch { musicDataStore.addToPlaylist(id, songIds) }
    }

    fun removeFromPlaylist(id: String, songId: String) {
        viewModelScope.launch { musicDataStore.removeFromPlaylist(id, songId) }
    }

    // ── Order and searching ─────────────────────────────────────────────────

    private val _sort = MutableStateFlow(MusicSort.TITLE)
    val sort: StateFlow<MusicSort> = _sort.asStateFlow()

    fun setSort(sort: MusicSort) {
        _sort.value = sort
        _localSongs.value = sortSongs(_localSongs.value, sort)
        viewModelScope.launch { musicDataStore.setSortName(sort.name) }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadLocalSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val songs = musicRepository.getLocalSongs()
                _localSongs.value = sortSongs(songs, _sort.value)
                _albums.value = musicRepository.albumsOf(songs)
                _artists.value = musicRepository.artistsOf(songs)
                _errorMessage.value = null
                resolveArtwork()
            } catch (e: Exception) {
                ZuneLog.e("MusicHubViewModel", "loadLocalSongs failed", e)
                _errorMessage.value = e.toUserMessage(getApplication())
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playLocalSong(index: Int) = playSongs(_localSongs.value, index)

    /** Plays a list of tracks from one of them — a whole album, an artist, a search result. */
    fun playSongs(songs: List<SongModel>, index: Int) {
        if (songs.isEmpty() || index !in songs.indices) return
        localMediaController.playMediaItems(songs.map(::mediaItemOf), index)
        rememberPlayed(songs[index])
    }

    /** Plays everything in the list in a random order, starting from a random track. */
    fun shuffleSongs(songs: List<SongModel>) {
        if (songs.isEmpty()) return
        val start = songs.indices.random()
        localMediaController.playMediaItems(songs.map(::mediaItemOf), start)
        rememberPlayed(songs[start])
        if (!localMediaController.state.value.shuffleEnabled) localMediaController.toggleShuffle()
    }

    fun playNext(song: SongModel) = localMediaController.playNext(mediaItemOf(song))

    /** The record this track is on goes to the front of the Zune list's quickplay. */
    private fun rememberPlayed(song: SongModel) {
        if (song.albumId == 0L) return
        viewModelScope.launch {
            settingsDataStore.noteAlbumPlayed(
                com.serkantkn.zunelauncher.data.model.RecentAlbum(
                    id = song.albumId,
                    title = song.album,
                    artist = song.artist,
                    artUri = song.albumArtUri?.toString()
                )
            )
        }
    }

    fun addToQueue(songs: List<SongModel>) = localMediaController.addToQueue(songs.map(::mediaItemOf))

    /**
     * A cover, small, for reading a colour out of.
     *
     * A thumbnail is all this needs — the picture is never shown at this size — and asking for one
     * keeps a full-resolution cover from being decoded just to average its pixels.
     */
    private suspend fun loadArtwork(uri: android.net.Uri): Bitmap? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            // An album row is not a media item, and some Android builds refuse to thumbnail one.
            // Reading the cover as a plain stream always works, so it is the fallback rather than
            // the accent quietly going back to the launcher's own colour.
            val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { resolver.loadThumbnail(uri, android.util.Size(320, 320), null) }.getOrNull()
            } else null

            thumbnail ?: runCatching {
                resolver.openInputStream(uri)?.use { stream ->
                    // Sampled down: only the colours are wanted, never the picture itself.
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }
            }.getOrNull()
        }

    private fun extractDominantColor(bitmap: Bitmap?) {
        if (bitmap == null) {
            _dominantColor.value = null
            return
        }
        
        Palette.from(bitmap).generate { palette ->
            // Zune HD prioritized vibrant accent colors.
            // We fall back to dominant if vibrant is unavailable.
            val colorInt = palette?.vibrantSwatch?.rgb 
                ?: palette?.lightVibrantSwatch?.rgb 
                ?: palette?.dominantSwatch?.rgb 
                ?: palette?.mutedSwatch?.rgb

            if (colorInt != null) {
                val hsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(colorInt, hsl)
                
                // Ensure lightness is at least 0.5 for readability on dark backgrounds
                if (hsl[2] < 0.5f) {
                    hsl[2] = 0.5f
                }
                
                val adjustedColorInt = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                _dominantColor.value = Color(adjustedColorInt)
            } else {
                _dominantColor.value = null
            }
        }
    }

    /**
     * Puts back the queue the launcher was last playing, paused where it left off.
     *
     * It waits for both the player to connect and the library to be read, because a remembered
     * queue is a list of ids and nothing else — the titles, artists and covers have to be looked
     * up again. Tracks deleted since are simply gone from it.
     */
    private fun restoreRememberedQueue() {
        viewModelScope.launch {
            val remembered = runCatching { musicDataStore.currentQueue() }.getOrNull() ?: return@launch
            if (remembered.isEmpty) return@launch

            // Wait, briefly, for the pieces this needs; a cold start has neither yet.
            var waited = 0L
            while ((!localMediaController.isReady.value || _localSongs.value.isEmpty()) && waited < RESTORE_TIMEOUT_MS) {
                kotlinx.coroutines.delay(RESTORE_POLL_MS)
                waited += RESTORE_POLL_MS
            }
            if (!localMediaController.isReady.value) return@launch
            if (localMediaController.state.value.hasQueue) return@launch

            val byId = _localSongs.value.associateBy { it.id }
            val items = remembered.mediaIds.mapNotNull { byId[it] }.map(::mediaItemOf)
            if (items.isEmpty()) {
                musicDataStore.forgetQueue()
                return@launch
            }
            val index = remembered.index.coerceIn(0, items.lastIndex)
            localMediaController.restoreQueue(items, index, remembered.positionMillis)
        }
    }

    private fun mediaItemOf(song: SongModel): androidx.media3.common.MediaItem =
        androidx.media3.common.MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri)
                    .build()
            )
            .build()

    override fun onCleared() {
        globalMediaController.stopListening()
        localMediaController.onPlaybackMoved = null
        localMediaController.stop()
        super.onCleared()
    }

    /**
     * The watchers that need the whole view model built first.
     *
     * Kotlin runs a class body top to bottom, so anything started from the first `init` runs while
     * the properties below it are still null. These two collect from flows that can emit at once,
     * so they wait here at the end where everything they touch exists.
     */
    init {
        playPendingAlbum()
        followLyrics()
    }

    private companion object {
        const val RESTORE_TIMEOUT_MS = 5_000L
        const val RESTORE_POLL_MS = 100L
    }
}

/** Where the sound is coming from. */
enum class PlaybackSource { NONE, LOCAL, EXTERNAL }

/**
 * One view of playback for the hub to draw, whichever player is behind it.
 *
 * [externalArt] is a bitmap because that is all another app's media session hands over; our own
 * player gives a [android.net.Uri], which loads without holding a second copy of the picture in
 * memory.
 */
data class HubPlayback(
    val source: PlaybackSource = PlaybackSource.NONE,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: android.net.Uri? = null,
    val externalArt: Bitmap? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = 0
) {
    val hasSomething: Boolean get() = source != PlaybackSource.NONE
    /** Only our own player can be scrubbed, shuffled or have its queue shown. */
    val isOurs: Boolean get() = source == PlaybackSource.LOCAL
}
