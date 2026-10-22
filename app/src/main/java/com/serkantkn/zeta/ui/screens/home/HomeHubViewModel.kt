package com.serkantkn.zeta.ui.screens.home

import com.serkantkn.zeta.util.localizedString
import com.serkantkn.zeta.R
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.di.appContainer
import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zeta.data.model.AppInfo
import com.serkantkn.zeta.data.model.FavoriteAppItem
import com.serkantkn.zeta.data.model.HomeScreenLayout
import com.serkantkn.zeta.data.model.Alarm
import com.serkantkn.zeta.data.model.CalendarEvent
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.datastore.SettingsDataStore
import com.serkantkn.zeta.data.model.ContactModel
import com.serkantkn.zeta.data.datastore.FavoriteAppsDataStore
import com.serkantkn.zeta.data.model.SmsConversationModel
import com.serkantkn.zeta.data.model.SocialMessageModel
import com.serkantkn.zeta.data.model.TemperatureUnit
import com.serkantkn.zeta.data.model.WeatherSnapshot
import com.serkantkn.zeta.data.model.TileAnimation
import com.serkantkn.zeta.data.model.TileInk
import com.serkantkn.zeta.data.repository.SmsRepository
import com.serkantkn.zeta.data.service.ActiveMediaState
import com.serkantkn.zeta.data.service.ThirdPartyMediaController
import kotlinx.coroutines.flow.asStateFlow
import com.serkantkn.zeta.data.model.StartFolders
import com.serkantkn.zeta.data.model.StartTileItem
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.data.repository.SocialRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import android.net.Uri
import com.serkantkn.zeta.data.model.MediaImage
import com.serkantkn.zeta.data.model.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.serkantkn.zeta.data.model.TileIcon
import com.serkantkn.zeta.data.model.TileIconStyle
import com.serkantkn.zeta.data.model.TileLook
import com.serkantkn.zeta.data.model.Quickplay
import com.serkantkn.zeta.data.model.QUICKPLAY_ALBUMS
import com.serkantkn.zeta.data.model.QUICKPLAY_APPS
import com.serkantkn.zeta.data.model.QUICKPLAY_NOTES
import com.serkantkn.zeta.data.model.QUICKPLAY_PHOTOS
import com.serkantkn.zeta.data.repository.IconPackInfo
import com.serkantkn.zeta.util.TileIconFace
import androidx.compose.ui.graphics.ImageBitmap

data class FavoriteAppUIModel(
    val appInfo: AppInfo,
    val span: Int
)

/** How many of an album's pictures a pinned album tile cycles through. */
private const val ALBUM_TILE_PICTURES = 8

sealed interface StartTileUIModel {
    val id: String
    val span: Int

    data class Hub(val hubType: HubType, override val span: Int) : StartTileUIModel {
        override val id: String = "hub:${hubType.name}"
    }

    data class App(val appInfo: AppInfo, override val span: Int) : StartTileUIModel {
        override val id: String = "app:${appInfo.packageName}"
    }

    /** A single note pinned to Start. */
    data class NoteTile(val note: Note, override val span: Int) : StartTileUIModel {
        override val id: String = "note:${note.id}"
    }

    /**
     * A website pinned to Start. [title] is what the user saw when they pinned it; a tile that
     * lost its title — one taken out of a folder, where only ids are kept — falls back to the
     * address itself.
     */
    data class Web(val url: String, val title: String, override val span: Int) : StartTileUIModel {
        override val id: String = "${StartTileItem.WEB_PREFIX}$url"

        val label: String
            get() = title.ifBlank {
                runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
                    .ifBlank { url }
            }
    }

    /**
     * A picture album pinned to Start. [covers] is what the tile cycles through; it arrives after
     * the media store has been read, so the tile is drawn empty for a moment and then fills.
     */
    data class Album(
        val bucketId: Long,
        val name: String,
        val covers: List<Uri>,
        override val span: Int
    ) : StartTileUIModel {
        override val id: String = "${StartTileItem.ALBUM_PREFIX}$bucketId"
    }

    /** A record pinned to Start: its cover, and it plays when tapped. */
    data class MusicAlbum(
        val albumId: Long,
        val name: String,
        val artist: String,
        val artUri: Uri?,
        override val span: Int
    ) : StartTileUIModel {
        override val id: String = "${StartTileItem.MUSIC_ALBUM_PREFIX}$albumId"
    }

    /**
     * A person pinned to Start. [contact] is filled in when they are still in the phone book;
     * [name] is what the tile was pinned with and is what it falls back to.
     */
    data class Person(
        val contactId: String,
        val name: String,
        val contact: ContactModel?,
        override val span: Int
    ) : StartTileUIModel {
        override val id: String = "${StartTileItem.PERSON_PREFIX}$contactId"

        val label: String get() = contact?.name?.ifBlank { name } ?: name
    }

    /**
     * A conversation pinned to Start. [name] is who it was pinned with; the thread number is what
     * actually opens it, so the tile survives the person being renamed in the address book.
     */
    data class Thread(
        val threadId: Long,
        val name: String,
        override val span: Int
    ) : StartTileUIModel {
        override val id: String = "${StartTileItem.SMS_PREFIX}$threadId"

        val label: String get() = name
    }

    /** The "hızlı not" tile that opens a blank editor. */
    data class QuickNote(override val span: Int) : StartTileUIModel {
        override val id: String = StartTileItem.QUICK_NOTE_ID
    }

    /** A folder of tiles. [children] are already resolved, in the order they are shown. */
    data class Folder(
        override val id: String,
        val name: String,
        val children: List<StartTileUIModel>,
        override val span: Int
    ) : StartTileUIModel

    /** The same tile at another size. */
    fun withSpan(span: Int): StartTileUIModel = when (this) {
        is Hub -> copy(span = span)
        is App -> copy(span = span)
        is NoteTile -> copy(span = span)
        is Web -> copy(span = span)
        is Album -> copy(span = span)
        is MusicAlbum -> copy(span = span)
        is Person -> copy(span = span)
        is Thread -> copy(span = span)
        is QuickNote -> copy(span = span)
        is Folder -> copy(span = span)
    }
}

private const val PEOPLE_TILE_FACES = 24

@OptIn(FlowPreview::class)
class HomeHubViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = application.appContainer.appRepository
    private val settingsDataStore = application.appContainer.settingsDataStore
    private val mediaRepository = application.appContainer.mediaRepository
    private val musicRepository = application.appContainer.musicRepository
    private val favoritePhotosDataStore = application.appContainer.favoritePhotosDataStore
    private val notesDataStore = application.appContainer.notesDataStore
    private val alarmDataStore = application.appContainer.alarmDataStore
    private val calendarDataStore = application.appContainer.calendarDataStore
    private val browsingDataStore = application.appContainer.browsingDataStore
    private val calculatorDataStore = application.appContainer.calculatorDataStore
    private val calendarRepository = application.appContainer.calendarRepository
    private val contactRepository = application.appContainer.contactRepository
    private val weatherRepository = application.appContainer.weatherRepository
    private val callLogRepository = application.appContainer.callLogRepository
    private val iconPackRepository = application.appContainer.iconPackRepository
    private val tileIconFactory = application.appContainer.tileIconFactory

    /**
     * The Start screen's own store, for the sizes of tiles inside folders. The board keeps a
     * folder's tiles by id alone, so their sizes have to live somewhere the Start screen owns;
     * the preference file is shared with the favourites, so there is one such file, not two.
     */
    private val favoriteAppsDataStore = FavoriteAppsDataStore(application)

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _allImages = MutableStateFlow<List<MediaImage>>(emptyList())

    /** The records on the phone, read only when one of them is pinned to Start. */
    private val _musicAlbums = MutableStateFlow<List<com.serkantkn.zeta.data.model.AlbumModel>>(emptyList())
    /** Records added to the phone lately, for quickplay's "new". */
    private val _newAlbums = MutableStateFlow<List<com.serkantkn.zeta.data.model.RecentAlbum>>(emptyList())


    val hubOrder: StateFlow<List<com.serkantkn.zeta.data.model.HubId>> = settingsDataStore.hubOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The Zune list's order, as the user has just rearranged it. */
    fun setHubOrder(order: List<com.serkantkn.zeta.data.model.HubId>) {
        viewModelScope.launch { settingsDataStore.setHubOrder(order) }
    }

    /**
     * Live tile text for the Notes hub: rotates through the pinned notes (falling back to the
     * latest edited ones) every 6 seconds, like a Windows Phone live tile cycling its content.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val notesTileSubtitle: StateFlow<String?> = notesDataStore.notesFlow
        .flatMapLatest { notes ->
            val active = notes.filter { !it.isArchived && !it.isInTrash }
            val pinned = active.filter { it.isPinned }.sortedByDescending { it.updatedAt }
            val pool = (if (pinned.isNotEmpty()) pinned else active.sortedByDescending { it.updatedAt }).take(5)
            flow {
                if (pool.isEmpty()) {
                    emit(null)
                } else {
                    var i = 0
                    while (true) {
                        val note = pool[i % pool.size]
                        emit(if (note.isLocked) getApplication<Application>().localizedString(R.string.notes_locked_note) else note.displayTitle)
                        i++
                        delay(6000)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * The notes the wide and large notes tiles list: the pinned ones, or the latest edited when
     * none are pinned, as a title and the start of the text. A locked note shows only that it is.
     */
    val notesTileNotes: StateFlow<List<Pair<String, String?>>> = notesDataStore.notesFlow
        .map { notes ->
            val active = notes.filter { !it.isArchived && !it.isInTrash }
            val pinned = active.filter { it.isPinned }.sortedByDescending { it.updatedAt }
            (if (pinned.isNotEmpty()) pinned else active.sortedByDescending { it.updatedAt }).take(5).map { note ->
                if (note.isLocked) {
                    getApplication<Application>().localizedString(R.string.notes_locked_note) to null
                } else {
                    val body = if (note.isChecklist) note.items.joinToString(", ") { it.text } else note.content
                    note.displayTitle to body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString(" ").takeIf { it.isNotBlank() && it != note.displayTitle }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Badge for the Notes hub tile: number of pinned, non-archived notes. */
    /** Enabled alarms (clock alarms and note reminders) for the saat live tile. */
    val enabledAlarms: StateFlow<List<Alarm>> = alarmDataStore.alarmsFlow
        .map { alarms -> alarms.filter { it.isEnabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * What the phone's calendars hold over the next fortnight, re-read when the Start screen comes
     * back into view. The tile used to be fed only by the launcher's own store, which is why a
     * phone with a full diary still said there was nothing planned.
     */
    private val _systemEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())

    fun refreshCalendarEvents() {
        viewModelScope.launch {
            _systemEvents.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!calendarRepository.canRead()) {
                    emptyList()
                } else {
                    val now = System.currentTimeMillis()
                    val visible = calendarDataStore.visibleCalendars.first()
                    calendarRepository.events(
                        com.serkantkn.zeta.util.startOfDay(now),
                        now + TILE_HORIZON_MS,
                        visible
                    )
                }
            }
        }
    }

    /** Events from the start of today onwards, soonest first, for the takvim live tile. */
    val upcomingEvents: StateFlow<List<CalendarEvent>> =
        combine(calendarDataStore.eventsFlow, _systemEvents) { local, system -> local + system }
            .map { events ->
                val startOfToday = com.serkantkn.zeta.util.startOfDay(System.currentTimeMillis())
                events.filter { it.endMillis >= startOfToday }
                    .sortedBy { it.startMillis }
                    .take(6)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "12 × 3 = 36": the last calculation, flipped onto the hesap makinesi tile. */
    val calculatorTileSubtitle: StateFlow<String?> = application.appContainer.calculatorDataStore.historyFlow
        .map { history -> history.firstOrNull()?.let { entry -> entry.expression + " = " + (entry.result.toBigDecimalOrNull()?.let { com.serkantkn.zeta.util.CalcEngine.format(it) } ?: entry.result) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The last few sums, for the wide and large calculator tiles. */
    val calculatorTileHistory: StateFlow<List<String>> = application.appContainer.calculatorDataStore.historyFlow
        .map { history ->
            history.take(5).map { entry ->
                entry.expression + " = " + (entry.result.toBigDecimalOrNull()?.let { com.serkantkn.zeta.util.CalcEngine.format(it) } ?: entry.result)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Unread mail across every inbox: badge of the e-posta tile. */
    val emailUnreadCount: StateFlow<Int> = application.appContainer.emailCache.inboxUnread

    val pinnedNotesCount: StateFlow<Int> = notesDataStore.notesFlow
        .map { notes -> notes.count { it.isPinned && !it.isArchived && !it.isInTrash } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * What the pictures tile shows: the favourites, or the newest pictures when there are none.
     *
     * Videos are left out. The tile is a still picture that changes, and a clip's first frame is
     * often a dark half-second of someone lifting the phone — not what anybody pinned.
     *
     * A favourite marked by an older build was remembered by its store id, one marked now by a key
     * that survives a rescan; both are matched, so nothing already kept falls off the tile.
     */
    val favoritePhotoUris: StateFlow<List<Uri>> = combine(
        _allImages,
        favoritePhotosDataStore.favoritePhotoIds
    ) { images, favourites ->
        val stills = images.filter { !it.isVideo }
        val favs = stills
            .filter { it.stableKey in favourites || it.id.toString() in favourites }
            .map { it.uri }
        if (favs.isNotEmpty()) favs else stills.take(10).map { it.uri }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The apps pinned beside the hub list.
     *
     * These used to be looked up in the full list of everything installed, which meant that on a
     * cold start — coming back from an app the phone had made room for by killing the launcher —
     * they could not be drawn until the package manager had named every app on the phone. The
     * hub's own words are just words, so they arrived at once and turned in, and the tiles they
     * belong beside appeared afterwards, which reads as the launcher stumbling.
     *
     * A pinned favourite is now asked for by name, which does not wait for anything. The full
     * list still arrives a moment later and takes over, so nothing else changes.
     */
    val favoriteApps: StateFlow<List<FavoriteAppUIModel>> = combine(
        _allApps,
        appRepository.getFavoritePackages()
    ) { apps, favoritePackages ->
        val appMap = apps.associateBy { it.packageName }
        // Keep the exact order defined in favoritePackages
        favoritePackages.mapNotNull { item ->
            val appInfo = appMap[item.packageName]
                ?: appRepository.appInfoFor(item.packageName)
                ?: return@mapNotNull null
            FavoriteAppUIModel(appInfo, item.span)
        }
    }.flowOn(Dispatchers.IO)
        .onEach { warmTileFaces(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Draws the favourites' faces before the screen asks for them.
     *
     * A tile's face is built the first time it is wanted and kept, and building one reads the
     * app's icon and redraws it. On the first frame after a cold start that work would otherwise
     * happen on the main thread, with the entrance animation already running.
     */
    private fun warmTileFaces(favourites: List<FavoriteAppUIModel>) {
        if (favourites.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val style = settingsDataStore.tileIconStyle.first()
            val pack = settingsDataStore.iconPackPackage.first()
            val overrides = settingsDataStore.tileIconOverrides.first()
            favourites.forEach { favourite ->
                runCatching { tileIconFace(favourite.appInfo, style, pack, overrides) }
            }
        }
    }

    val startTiles: StateFlow<List<StartTileItem>> = settingsDataStore.startTiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Everyone in the phone book, so a pinned person's tile knows their face and name. */
    private val _allContacts = MutableStateFlow<List<ContactModel>>(emptyList())

    /** The sizes of the tiles inside folders, by tile id. See [favoriteAppsDataStore]. */
    private val folderTileSpans: StateFlow<Map<String, Int>> = favoriteAppsDataStore.folderTileSpans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setFolderChildSpan(childId: String, span: Int) {
        viewModelScope.launch { favoriteAppsDataStore.setFolderTileSpan(childId, span) }
    }

    private val tilesWithoutCovers: Flow<List<StartTileUIModel>> = combine(
        combine(startTiles, folderTileSpans) { tiles, spans -> tiles to spans },
        _allApps,
        appRepository.getFavoritePackages(),
        notesDataStore.notesFlow,
        _allContacts
    ) { (currentStartTiles, childSpans), apps, favoritePackages, notes, contacts ->
        val appMap = apps.associateBy { it.packageName }
        val contactMap = contacts.associateBy { it.id }
        val favPkgMap = favoritePackages.associateBy { it.packageName }
        val result = mutableListOf<StartTileUIModel>()
        val includedAppPkgs = mutableSetOf<String>()

        /** One tile of a folder; null when whatever it pointed at is gone. */
        fun resolve(id: String, span: Int): StartTileUIModel? = when {
            id == StartTileItem.QUICK_NOTE_ID -> StartTileUIModel.QuickNote(span)
            id.startsWith("hub:") -> runCatching { HubType.valueOf(id.removePrefix("hub:")) }
                .getOrNull()?.let { StartTileUIModel.Hub(it, span) }

            id.startsWith("app:") -> appMap[id.removePrefix("app:")]
                ?.let { StartTileUIModel.App(it, span) }

            id.startsWith("note:") -> notes.firstOrNull { it.id == id.removePrefix("note:") && !it.isInTrash }
                ?.let { StartTileUIModel.NoteTile(it, span) }

            id.startsWith(StartTileItem.WEB_PREFIX) ->
                StartTileUIModel.Web(id.removePrefix(StartTileItem.WEB_PREFIX), "", span)

            id.startsWith(StartTileItem.PERSON_PREFIX) -> {
                val contactId = id.removePrefix(StartTileItem.PERSON_PREFIX)
                StartTileUIModel.Person(contactId, "", contactMap[contactId], span)
            }

            id.startsWith(StartTileItem.SMS_PREFIX) ->
                id.removePrefix(StartTileItem.SMS_PREFIX).toLongOrNull()
                    ?.let { StartTileUIModel.Thread(it, "", span) }

            id.startsWith(StartTileItem.MUSIC_ALBUM_PREFIX) ->
                id.removePrefix(StartTileItem.MUSIC_ALBUM_PREFIX).toLongOrNull()
                    ?.let { StartTileUIModel.MusicAlbum(it, "", "", null, span) }

            id.startsWith(StartTileItem.ALBUM_PREFIX) ->
                id.removePrefix(StartTileItem.ALBUM_PREFIX).toLongOrNull()
                    ?.let { StartTileUIModel.Album(it, "", emptyList(), span) }

            else -> null
        }

        currentStartTiles.forEach { item ->
            if (item.isFolder) {
                val children = item.children.mapNotNull { childId ->
                    resolve(childId, childSpans[childId] ?: StartTileItem.DEFAULT_SPAN)?.also {
                        if (it is StartTileUIModel.App) includedAppPkgs.add(it.appInfo.packageName)
                    }
                }
                // A folder whose tiles have all been uninstalled is not worth a slot.
                if (children.isNotEmpty()) {
                    result.add(StartTileUIModel.Folder(item.id, item.name, children, item.span))
                }
                return@forEach
            }
            if (item.isHub) {
                item.hubType?.let { hubType ->
                    result.add(StartTileUIModel.Hub(hubType, item.span))
                }
            } else if (item.isQuickNote) {
                result.add(StartTileUIModel.QuickNote(item.span))
            } else if (item.isNote) {
                notes.firstOrNull { it.id == item.noteId && !it.isInTrash }?.let { note ->
                    result.add(StartTileUIModel.NoteTile(note, item.span))
                }
            } else if (item.isPerson) {
                item.contactId?.let { contactId ->
                    result.add(
                        StartTileUIModel.Person(contactId, item.name, contactMap[contactId], item.span)
                    )
                }
            } else if (item.isWeb) {
                item.webUrl?.let { url ->
                    result.add(StartTileUIModel.Web(url, item.name, item.span))
                }
            } else if (item.isMusicAlbum) {
                item.musicAlbumId?.let { albumId ->
                    result.add(StartTileUIModel.MusicAlbum(albumId, item.name, "", null, item.span))
                }
            } else if (item.isAlbum) {
                item.albumBucketId?.let { bucketId ->
                    result.add(StartTileUIModel.Album(bucketId, item.name, emptyList(), item.span))
                }
            } else if (item.isThread) {
                item.smsThreadId?.let { threadId ->
                    result.add(StartTileUIModel.Thread(threadId, item.name, item.span))
                }
            } else if (item.isApp) {
                val pkg = item.packageName
                if (pkg != null && favPkgMap.containsKey(pkg)) {
                    appMap[pkg]?.let { appInfo ->
                        result.add(StartTileUIModel.App(appInfo, item.span))
                        includedAppPkgs.add(pkg)
                    }
                }
            }
        }

        // If there are favorite apps not yet in startTiles (e.g. pinned from App list), append them!
        favoritePackages.forEach { favItem ->
            if (favItem.packageName !in includedAppPkgs) {
                appMap[favItem.packageName]?.let { appInfo ->
                    result.add(StartTileUIModel.App(appInfo, favItem.span))
                }
            }
        }

        result
    }

    /**
     * The Start board's tiles, with each pinned album's pictures filled in.
     *
     * The covers are added in a second pass rather than inside the combine above: the media store
     * is read separately and only matters to one kind of tile, and a five-source combine is at the
     * limit of what reads as one thought.
     */
    val unifiedStartTiles: StateFlow<List<StartTileUIModel>> = combine(
        tilesWithoutCovers,
        _allImages,
        _musicAlbums
    ) { tiles, images, records ->
        if (tiles.none { it.hasAlbum() }) return@combine tiles
        val byBucket = images.asSequence().filter { !it.isVideo }.groupBy { it.bucketId }

        fun fill(model: StartTileUIModel): StartTileUIModel = when (model) {
            is StartTileUIModel.Album -> {
                val inAlbum = byBucket[model.bucketId].orEmpty()
                model.copy(
                    // The album may have been renamed since it was pinned; what it is called now
                    // wins, and what it was called when pinned is the fallback.
                    name = inAlbum.firstOrNull()?.bucketName?.takeIf { it.isNotBlank() } ?: model.name,
                    covers = inAlbum.take(ALBUM_TILE_PICTURES).map { it.uri }
                )
            }
            is StartTileUIModel.MusicAlbum -> {
                val record = records.firstOrNull { it.id == model.albumId }
                if (record == null) model else model.copy(
                    name = record.title,
                    artist = record.artist,
                    artUri = record.artUri
                )
            }
            is StartTileUIModel.Folder -> model.copy(children = model.children.map(::fill))
            else -> model
        }

        tiles.map(::fill)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Whether this tile, or anything in it, is a pinned album waiting for its pictures. */
    private fun StartTileUIModel.hasAlbum(): Boolean = when (this) {
        is StartTileUIModel.Album, is StartTileUIModel.MusicAlbum -> true
        is StartTileUIModel.Folder -> children.any {
            it is StartTileUIModel.Album || it is StartTileUIModel.MusicAlbum
        }
        else -> false
    }

    // ── Live tile feeds ───────────────────────────────────────────────────────────────────────
    // Contacts, the call log and the message store are read straight from the provider, so each
    // one is refreshed by hand: on start-up and every time the launcher comes back to the front.

    private val _peopleFaces = MutableStateFlow<List<ContactModel>>(emptyList())

    /** Faces for the people tile: starred contacts first, then whoever was contacted last. */
    val peopleFaces: StateFlow<List<ContactModel>> = _peopleFaces.asStateFlow()

    private val _missedCalls = MutableStateFlow(0)

    /** Missed calls since the last answered one — the count on the phone tile. */
    val missedCalls: StateFlow<Int> = _missedCalls.asStateFlow()

    private val _lastMissedCaller = MutableStateFlow<String?>(null)
    val lastMissedCaller: StateFlow<String?> = _lastMissedCaller.asStateFlow()

    private val _missedCallList = MutableStateFlow<List<Pair<String, Long>>>(emptyList())

    /** Who the missed calls were from, and when, newest first: the wide and large phone tiles list them. */
    val missedCallList: StateFlow<List<Pair<String, Long>>> = _missedCallList.asStateFlow()

    private val _unreadMessages = MutableStateFlow<List<SmsConversationModel>>(emptyList())

    /** Unread text conversations, newest first, behind the messaging tile's count and preview. */
    val unreadMessages: StateFlow<List<SmsConversationModel>> = _unreadMessages.asStateFlow()

    private val mediaController = ThirdPartyMediaController(application)

    /** What is playing right now, for the music tile's cover art. */
    val nowPlaying: StateFlow<ActiveMediaState> = mediaController.mediaState

    /** Newest unread mail, shown on the back of the e-mail tile. */
    val emailTilePreview: StateFlow<Pair<String, String>?> = application.appContainer.emailCache.messages
        .map { byFolder ->
            byFolder.values.asSequence()
                .flatten()
                .filter { !it.isRead }
                .maxByOrNull { it.date }
                ?.let { it.from.display to it.subject }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The newest unread mail, who from, about what and when, for the wide and large e-mail tiles. */
    val emailTileMessages: StateFlow<List<Triple<String, String, Long>>> = application.appContainer.emailCache.messages
        .map { byFolder ->
            byFolder.values.asSequence()
                .flatten()
                .filter { !it.isRead }
                .sortedByDescending { it.date }
                .take(5)
                .map { Triple(it.from.display, it.subject, it.date) }
                .toList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cached forecast for the place the weather hub is set to, for the weather tile. */
    val weatherSnapshot: StateFlow<WeatherSnapshot?> = combine(
        weatherRepository.places,
        weatherRepository.selectedPlaceId,
        weatherRepository.cache
    ) { places, selectedId, cache ->
        val place = places.firstOrNull { it.id == selectedId } ?: places.firstOrNull()
        place?.let { cache[it.id] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val newsRepository = application.appContainer.newsRepository

    /**
     * The news hub's front page as last fetched, then the stories from the user's own sources,
     * for the news tile. Google's front page has no pictures; the sources' stories lend the tile
     * theirs, and the front page's lead is still the first headline.
     */
    val newsStories: StateFlow<List<com.serkantkn.zeta.data.model.NewsItem>> = newsRepository.sections
        .map { sections ->
            sections[com.serkantkn.zeta.data.model.NewsTopic.TOP.sectionKey]?.items.orEmpty() +
                sections[com.serkantkn.zeta.data.model.NewsSection.SOURCES_KEY]?.items.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weatherUnit: StateFlow<TemperatureUnit> = application.appContainer.weatherDataStore.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemperatureUnit.CELSIUS)

    val notificationCounts: StateFlow<Map<String, Int>> = SocialRepository.notificationCounts

    // Which apps' notifications the messaging and phone tiles count: the holders of the two
    // roles, asked again whenever the launcher comes back to the front.
    private val _defaultSmsPackage = MutableStateFlow<String?>(null)
    val defaultSmsPackage: StateFlow<String?> = _defaultSmsPackage.asStateFlow()
    private val _defaultDialerPackage = MutableStateFlow<String?>(null)
    val defaultDialerPackage: StateFlow<String?> = _defaultDialerPackage.asStateFlow()

    val latestMessages: StateFlow<Map<String, SocialMessageModel>> = SocialRepository.messages
        .map { list ->
            list.associateBy { it.packageName }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tileCornerStyle: StateFlow<TileCornerStyle> = settingsDataStore.tileCornerStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileCornerStyle.ROUNDED)

    val tileOpacity: StateFlow<Int> = settingsDataStore.tileOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.DEFAULT_TILE_OPACITY)

    val tileAnimation: StateFlow<TileAnimation> = settingsDataStore.tileAnimation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileAnimation.SLIDE)

    val tileInk: StateFlow<TileInk> = settingsDataStore.tileInk
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileInk.AUTO)

    val tileSpacing: StateFlow<Int> = settingsDataStore.tileSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.DEFAULT_TILE_SPACING)

    val homeScreenLayout: StateFlow<HomeScreenLayout> = settingsDataStore.homeScreenLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeScreenLayout.ZUNE)

    // The providers behind the live tiles are watched, not polled: a message that arrives while
    // Start is in front used to wait for the launcher to go away and come back. Each observer is
    // registered once the permission is there — asked again on every refresh, since it may be
    // granted while the launcher is running.
    private val resolver = application.contentResolver
    private val observers = mutableMapOf<Uri, android.database.ContentObserver>()

    init {
        loadImages()
        runCatching { mediaController.startListening() }
            .onFailure { ZuneLog.w("HomeHubViewModel", "media session listening unavailable", it) }

        viewModelScope.launch {
            appRepository.getInstalledApps().collect { apps ->
                _allApps.value = apps
            }
        }

        // The pinned records are only worth looking up while one is pinned — and they were
        // looked up once, at start-up, when nothing was pinned yet, so their covers never came.
        viewModelScope.launch {
            startTiles
                .map { tiles -> StartFolders.allTileIds(tiles).filter { it.startsWith(StartTileItem.MUSIC_ALBUM_PREFIX) }.toSet() }
                .distinctUntilChanged()
                .collect { pinned -> if (pinned.isNotEmpty()) loadMusicAlbums() }
        }

        // The gallery changes behind the launcher's back — a photo taken, a screenshot, a
        // download. The store says so; its many small changes are taken as one.
        watch(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ) { loadImages() }
    }

    /**
     * Watches [uris] for changes, debounced into one call of [onChange], for as long as the view
     * model lives. Several addresses share one observer so a change the provider announces on
     * all of them is answered once.
     */
    private fun watch(vararg uris: Uri, onChange: suspend () -> Unit) {
        val wanted = uris.filterNot { observers.containsKey(it) }
        if (wanted.isEmpty()) return
        val changes = MutableStateFlow(0L)
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                changes.value = System.nanoTime()
            }
        }
        wanted.forEach { uri ->
            try {
                resolver.registerContentObserver(uri, true, observer)
                observers[uri] = observer
            } catch (e: Exception) {
                ZuneLog.w("HomeHubViewModel", "could not watch $uri", e)
            }
        }
        if (wanted.none { observers.containsKey(it) }) return
        viewModelScope.launch {
            changes.drop(1).debounce(PROVIDER_SETTLE_MS).collect { onChange() }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Re-reads the providers behind the live tiles. Each one is guarded on its own and runs on
     * its own: a launcher without the contacts or call-log permission simply shows the plain
     * face of that tile, and the weather's trip to the network holds nothing else up.
     */
    fun refreshLiveTiles() {
        if (hasPermission(android.Manifest.permission.READ_SMS)) {
            watch(
                android.provider.Telephony.Sms.CONTENT_URI,
                android.provider.Telephony.MmsSms.CONTENT_URI
            ) { refreshMessages() }
        }
        if (hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
            watch(android.provider.CallLog.Calls.CONTENT_URI) { refreshCalls() }
        }
        refreshRoleHolders()
        viewModelScope.launch { refreshContacts() }
        viewModelScope.launch { refreshCalls() }
        viewModelScope.launch { refreshWeather() }
        viewModelScope.launch { refreshNews() }
        viewModelScope.launch { refreshMessages() }
        viewModelScope.launch { refreshStorage() }
    }

    private val _storage = MutableStateFlow<Pair<Long, Long>?>(null)

    /** Free and total bytes of the phone's own storage, for the wide and large files tiles. */
    val storage: StateFlow<Pair<Long, Long>?> = _storage.asStateFlow()

    private suspend fun refreshStorage() {
        _storage.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                stat.availableBytes to stat.totalBytes
            }.getOrNull()
        }
    }

    private fun refreshRoleHolders() {
        val context = getApplication<Application>()
        _defaultSmsPackage.value = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }
            .getOrNull()
        _defaultDialerPackage.value = runCatching {
            context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()
    }

    private suspend fun refreshContacts() {
        val contacts = try {
            contactRepository.getContacts()
        } catch (e: Exception) {
            ZuneLog.w("HomeHubViewModel", "contacts unavailable for the people tile", e)
            emptyList()
        }
        _allContacts.value = contacts
        _peopleFaces.value = contacts
            .sortedWith(compareByDescending<ContactModel> { it.isFavorite }.thenByDescending { it.lastTimeContacted })
            .take(PEOPLE_TILE_FACES)
    }

    private suspend fun refreshCalls() {
        try {
            val calls = callLogRepository.getRecentCalls()
            val missed = calls.takeWhile { it.type == android.provider.CallLog.Calls.MISSED_TYPE }
            _missedCalls.value = missed.size
            _lastMissedCaller.value = missed.firstOrNull()?.let { it.name?.takeIf { name -> name.isNotBlank() } ?: it.number }
            _missedCallList.value = missed.take(5).map { (it.name?.takeIf { name -> name.isNotBlank() } ?: it.number) to it.dateMillis }
        } catch (e: Exception) {
            ZuneLog.w("HomeHubViewModel", "call log unavailable for the phone tile", e)
            _missedCalls.value = 0
            _lastMissedCaller.value = null
            _missedCallList.value = emptyList()
        }
    }

    /**
     * The news tile reads what the hub last fetched; when that is over an hour old and the tile is
     * on Start, the front page is fetched again so the tile is not a day behind.
     */
    private suspend fun refreshNews() {
        try {
            newsRepository.load()
            val onStart = settingsDataStore.startTiles.first()
                .any { it.isHub && it.hubType == HubType.NEWS }
            if (!onStart) return
            listOf(
                com.serkantkn.zeta.data.model.NewsTopic.TOP.sectionKey,
                com.serkantkn.zeta.data.model.NewsSection.SOURCES_KEY
            ).forEach { key ->
                val age = newsRepository.sections.value[key]?.let { System.currentTimeMillis() - it.fetchedAt }
                if (age == null || age > com.serkantkn.zeta.data.repository.NewsRepository.TILE_STALE_MS) {
                    newsRepository.refresh(key, force = false)
                }
            }
        } catch (e: Exception) {
            ZuneLog.w("HomeHubViewModel", "news tile refresh skipped", e)
        }
    }

    private suspend fun refreshWeather() {
        // The weather tile is only worth having if it is current: the same staleness and
        // allowance rules as the hub decide whether this actually goes to the network.
        try {
            val place = weatherRepository.places.first().let { list ->
                list.firstOrNull { it.id == weatherRepository.selectedPlaceId.first() } ?: list.firstOrNull()
            }
            if (place != null) weatherRepository.refresh(place, force = false)
        } catch (e: Exception) {
            ZuneLog.w("HomeHubViewModel", "weather tile refresh skipped", e)
        }
    }

    private suspend fun refreshMessages() {
        _unreadMessages.value = try {
            SmsRepository.getConversations(getApplication())
                .filter { !it.isRead }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            ZuneLog.w("HomeHubViewModel", "messages unavailable for the messaging tile", e)
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { mediaController.stopListening() }
        observers.values.forEach { runCatching { resolver.unregisterContentObserver(it) } }
        observers.clear()
    }

    fun loadImages() {
        viewModelScope.launch {
            try {
                _allImages.value = mediaRepository.getAllImages()
            } catch (e: Exception) { ZuneLog.w("HomeHubViewModel", "loadImages ignored Exception", e) }
        }
    }

    /**
     * Reads the music again for quickplay's "new", when the music may be read at all: quickplay
     * asks as it comes into view. Without the permission there is simply nothing new to show.
     */
    fun refreshNewMusic() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), permission) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        viewModelScope.launch { loadMusicAlbums() }
    }

    private suspend fun loadMusicAlbums() {
        try {
            val songs = musicRepository.getLocalSongs()
            _musicAlbums.value = musicRepository.albumsOf(songs)
            _newAlbums.value = com.serkantkn.zeta.data.model.newestAlbums(
                songs,
                nowSeconds = System.currentTimeMillis() / 1000L,
                withinSeconds = com.serkantkn.zeta.data.model.QUICKPLAY_NEW_SECONDS,
                limit = com.serkantkn.zeta.data.model.QUICKPLAY_NEW_ALBUMS
            )
        } catch (e: Exception) {
            ZuneLog.w("HomeHubViewModel", "the pinned record could not be looked up", e)
        }
    }

    fun launchApp(packageName: String) {
        appRepository.launchApp(packageName)
        viewModelScope.launch { settingsDataStore.noteAppLaunched(packageName) }
    }

    fun getAppIcon(packageName: String): Drawable? = appRepository.getAppIcon(packageName)

    // ── Tile icons ────────────────────────────────────────────────────────────────────────────

    val tileIconStyle: StateFlow<TileIconStyle> = settingsDataStore.tileIconStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileIconStyle.WINDOWS_PHONE)

    val iconPackPackage: StateFlow<String?> = settingsDataStore.iconPackPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tileIconOverrides: StateFlow<Map<String, TileIcon>> = settingsDataStore.tileIconOverrides
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * What an app's tile draws.
     *
     * The three settings are passed in rather than read from here so the screen collects them and
     * redraws when they change; the factory's cache keeps the bitmap work off the frame.
     */
    fun tileIconFace(
        app: AppInfo,
        style: TileIconStyle,
        packPackage: String?,
        overrides: Map<String, TileIcon>
    ): TileIconFace = tileIconFactory.face(
        app = app,
        style = style,
        packPackage = packPackage,
        override = overrides[app.packageName] ?: TileIcon.Default
    )

    // ── What the Zune list says under the names that had nothing to say ──────────────────────

    /** The last page the browser opened (a private tab leaves none), for the internet line. */
    val lastSite: StateFlow<com.serkantkn.zeta.data.model.BrowserHistory?> = browsingDataStore.history
        .map { json ->
            com.serkantkn.zeta.data.model.parseJsonObjectList(
                json, "HomeHubViewModel", com.serkantkn.zeta.data.model.BrowserHistory::fromJson
            ).maxByOrNull { it.timestamp }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The calculator's last sum, for its line. */
    val lastCalculation: StateFlow<com.serkantkn.zeta.data.model.CalcHistoryEntry?> =
        calculatorDataStore.historyFlow.map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** How many people the phone knows, for the people line. */
    val contactCount: StateFlow<Int> = _allContacts.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Quickplay: the Zune list's "lately" ────────────────────────────────────────────────────

    /** Everything in the gallery, newest first, so the list can say how many came in today. */
    val allImages: StateFlow<List<MediaImage>> = _allImages.asStateFlow()

    val quickplay: StateFlow<Quickplay> = combine(
        combine(
            settingsDataStore.recentAlbums,
            _allImages,
            notesDataStore.notesFlow,
            settingsDataStore.recentApps,
            _allApps
        ) { albums, images, notes, recentPackages, apps ->
            val byPackage = apps.associateBy { it.packageName }
            val nowSeconds = System.currentTimeMillis() / 1000L
            Quickplay(
                recentAlbums = albums.take(QUICKPLAY_ALBUMS),
                recentPhotos = images.filter { !it.isVideo }.take(QUICKPLAY_PHOTOS),
                // A locked note keeps its title to itself, and a deleted or archived one is not "lately".
                recentNotes = notes.filter { it.deletedAt == null && !it.isArchived && !it.isLocked }
                    .sortedByDescending { it.updatedAt }.take(QUICKPLAY_NOTES),
                recentApps = recentPackages.mapNotNull { byPackage[it] }.take(QUICKPLAY_APPS),
                newVideos = images.filter {
                    it.isVideo && nowSeconds - it.dateAdded <= com.serkantkn.zeta.data.model.QUICKPLAY_NEW_SECONDS
                }.sortedByDescending { it.dateAdded }.take(com.serkantkn.zeta.data.model.QUICKPLAY_NEW_VIDEOS)
            )
        },
        settingsDataStore.quickplayPins,
        _newAlbums,
        _allApps
    ) { base, pins, newAlbums, apps ->
        // An app pinned and since removed has nothing to open.
        val installed = apps.mapTo(HashSet()) { it.packageName }
        base.copy(
            pins = pins.filter { it.kind != com.serkantkn.zeta.data.model.QuickplayPin.Kind.APP || apps.isEmpty() || it.key in installed },
            newAlbums = newAlbums
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Quickplay())

    /** Pins something to quickplay, or takes it off. */
    fun toggleQuickplayPin(pin: com.serkantkn.zeta.data.model.QuickplayPin) {
        viewModelScope.launch { settingsDataStore.toggleQuickplayPin(pin) }
    }

    /** Every app by package, for drawing a pinned app's icon. */
    fun appFor(packageName: String): AppInfo? = _allApps.value.firstOrNull { it.packageName == packageName }

    // ── What a hub's tile or title opens ─────────────────────────────────────────────────────

    /** Every app on the phone, for choosing what a hub opens instead of itself. */
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    val hubTargetApps: StateFlow<Map<HubType, String>> = settingsDataStore.hubTargetApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setHubTargetApp(hub: HubType, packageName: String?) {
        viewModelScope.launch { settingsDataStore.setHubTargetApp(hub, packageName) }
    }

    /**
     * Opens the app a hub has been pointed at, if any. False when the hub should open itself —
     * nothing was chosen, or what was chosen is no longer installed, in which case the choice is
     * forgotten rather than left pointing at nothing.
     */
    fun openHubTarget(hub: com.serkantkn.zeta.data.model.HubId): Boolean {
        // Only Zeta's own hubs can be pointed at another app; another app's hub is one already.
        if (hub !is HubType) return false
        val packageName = hubTargetApps.value[hub] ?: return false
        if (appRepository.appInfoFor(packageName) == null) {
            setHubTargetApp(hub, null)
            return false
        }
        appRepository.launchApp(packageName)
        viewModelScope.launch { settingsDataStore.noteAppLaunched(packageName) }
        return true
    }

    // ── One tile's own look ───────────────────────────────────────────────────────────────────

    val tileLooks: StateFlow<Map<String, TileLook>> = settingsDataStore.tileLooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setTileLook(tileId: String, look: TileLook) {
        viewModelScope.launch { settingsDataStore.setTileLook(tileId, look) }
    }

    /** Back to the board's own look; the pictures copied for this tile go with it. */
    fun resetTileLook(tileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = settingsDataStore.tileLooks.first()[tileId]
            settingsDataStore.setTileLook(tileId, TileLook.DEFAULT)
            current?.picture?.let { deleteOwnFile(it) }
            (current?.icon as? TileIcon.Picture)?.path?.let { deleteOwnFile(it) }
            tileIconFactory.invalidate()
        }
    }

    /** Forgets a removed tile's look, and the pictures copied for it, in either build. */
    private fun dropTileLook(tileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dropped = settingsDataStore.dropTileLook(tileId) ?: return@launch
            dropped.picture?.let { deleteOwnFile(it) }
            (dropped.icon as? TileIcon.Picture)?.path?.let { deleteOwnFile(it) }
            tileIconFactory.invalidate()
        }
    }

    /** An icon a tile was given, ready to draw; the factory keeps it once drawn. */
    fun customIconFace(icon: TileIcon, style: TileIconStyle): TileIconFace? =
        tileIconFactory.faceOf(icon, style)

    /** Copies a picture the user chose into the launcher's own files and makes it the tile's icon. */
    fun setTileLookIconPicture(tileId: String, uri: android.net.Uri) {
        // A tile's own pictures are Pro's; the free build copies none it could not show.
        if (!com.serkantkn.zeta.util.Pro.unlocked) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = ownFileFor("tile_icon", tileId, "png")
            try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                val look = settingsDataStore.tileLooks.first()[tileId] ?: TileLook.DEFAULT
                (look.icon as? TileIcon.Picture)?.path?.let { deleteOwnFile(it) }
                settingsDataStore.setTileLook(tileId, look.copy(icon = TileIcon.Picture(file.absolutePath)))
                tileIconFactory.invalidate()
            } catch (e: Exception) {
                ZuneLog.w("HomeHubViewModel", "tile icon picture could not be saved", e)
            }
        }
    }

    /**
     * Copies a picture the user chose into the launcher's own files, no larger than a tile could
     * ever need, and lays it across the tile.
     */
    fun setTileLookPicture(tileId: String, uri: android.net.Uri) {
        // A tile's own pictures are Pro's; the free build copies none it could not show.
        if (!com.serkantkn.zeta.util.Pro.unlocked) return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val file = ownFileFor("tile_picture", tileId, "jpg")
            try {
                val bitmap = decodeScaled(context, uri, TILE_PICTURE_MAX_PX) ?: return@launch
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                val look = settingsDataStore.tileLooks.first()[tileId] ?: TileLook.DEFAULT
                look.picture?.let { deleteOwnFile(it) }
                settingsDataStore.setTileLook(tileId, look.copy(picture = file.absolutePath))
            } catch (e: Exception) {
                ZuneLog.w("HomeHubViewModel", "tile picture could not be saved", e)
            }
        }
    }

    fun clearTileLookPicture(tileId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val look = settingsDataStore.tileLooks.first()[tileId] ?: return@launch
            settingsDataStore.setTileLook(tileId, look.copy(picture = null))
            look.picture?.let { deleteOwnFile(it) }
        }
    }

    /**
     * A fresh file every time: the same name would be served from the image cache with the old
     * picture in it.
     */
    private fun ownFileFor(prefix: String, tileId: String, extension: String): java.io.File {
        val safeId = tileId.replace(Regex("[^A-Za-z0-9]"), "_").take(60)
        return java.io.File(getApplication<Application>().filesDir, "${prefix}_${safeId}_${System.currentTimeMillis()}.$extension")
    }

    private fun deleteOwnFile(path: String) {
        val file = java.io.File(path)
        if (file.parentFile == getApplication<Application>().filesDir) file.delete()
    }

    private fun decodeScaled(context: android.content.Context, uri: android.net.Uri, maxPx: Int): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        // The picture may carry an orientation the decoder does not apply.
        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (android.media.ExifInterface(stream).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return decoded
        val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
        return android.graphics.Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    }

    fun installedIconPacks(): List<IconPackInfo> = iconPackRepository.installedPacks()

    fun iconPackDrawables(packPackage: String): List<String> =
        iconPackRepository.drawableNames(packPackage)

    fun iconPackPreview(packPackage: String, name: String): ImageBitmap? =
        tileIconFactory.previewOfPack(packPackage, name)

    fun updateFavoritesOrder(uiModels: List<FavoriteAppUIModel>) {
        viewModelScope.launch {
            val items = uiModels.map { FavoriteAppItem(it.appInfo.packageName, it.span) }
            appRepository.updateFavoritesOrder(items)
        }
    }

    /**
     * Writes the board's order back. Folders keep what is inside them: the board only ever knows
     * a folder by its id and size, so the children come from what was stored.
     */
    fun updateStartTilesOrder(uiModels: List<StartTileUIModel>) {
        viewModelScope.launch {
            val storedById = storedTiles().associateBy { it.id }
            val items = uiModels.map { model ->
                val stored = storedById[model.id]
                if (stored != null && stored.isFolder) stored.copy(span = model.span)
                // A pinned site keeps the title it was pinned with; rebuilding the tile from its
                // id alone used to drop it on every reorder.
                else StartTileItem(model.id, model.span, name = stored?.name.orEmpty())
            }
            settingsDataStore.setStartTiles(items)
        }
    }


    fun removeTile(id: String) {
        viewModelScope.launch {
            val currentList = if (startTiles.value.isNotEmpty()) startTiles.value
            else unifiedStartTiles.value.map { StartTileItem(it.id, it.span) }
            // A tile can be sitting inside a folder, so it is taken out of both places.
            settingsDataStore.setStartTiles(StartFolders.removeTile(currentList, id))
            if (id.startsWith("app:")) {
                // Unpinned only if it is pinned: toggling a tile that had already come off the
                // favourites — one taken out of a folder — put it straight back on.
                val pkg = id.removePrefix("app:")
                if (appRepository.getFavoritePackages().first().any { it.packageName == pkg }) {
                    appRepository.toggleFavorite(pkg)
                }
            }
            favoriteAppsDataStore.setFolderTileSpan(id, null)
            // Both lists fill themselves in with whatever hub they are missing, so a hub has to
            // be remembered as taken off or it comes straight back on the next read.
            StartTileItem(id).hubId?.let { settingsDataStore.setHubRemoved(it, true) }
            // A tile that has gone takes its own look with it: pinned again, it starts plain.
            dropTileLook(id)
        }
    }

    /** Puts a hub back on the home screen; it returns to the end of the board and of the list. */
    fun addHub(hub: com.serkantkn.zeta.data.model.HubId) {
        viewModelScope.launch { settingsDataStore.setHubRemoved(hub, false) }
    }

    /** The hubs not on the home screen at the moment, in the order they are offered in. */
    val hubsOffHome: StateFlow<List<com.serkantkn.zeta.data.model.HubId>> = settingsDataStore.removedHubs
        .map { removed -> HubType.entries.filter { it != HubType.HOME && it in removed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun removeFavorite(packageName: String) {
        removeTile("app:$packageName")
    }

    // ── Folders ───────────────────────────────────────────────────────────────────────────────

    /** The board as it is stored, with folders intact. Board edits are applied to this. */
    private suspend fun storedTiles(): List<StartTileItem> {
        val stored = startTiles.value
        if (stored.isNotEmpty()) return stored
        return settingsDataStore.startTiles.first()
    }

    private fun editTiles(edit: (List<StartTileItem>) -> List<StartTileItem>) {
        viewModelScope.launch {
            val updated = StartFolders.collapseThinFolders(edit(storedTiles()))
            settingsDataStore.setStartTiles(updated)
        }
    }

    /** Dropping one tile onto another: they become a folder, or the tile joins an existing one. */
    fun mergeIntoFolder(sourceId: String, targetId: String) {
        editTiles { StartFolders.merge(it, sourceId, targetId) }
    }

    fun renameFolder(folderId: String, name: String) {
        editTiles { StartFolders.rename(it, folderId, name) }
    }

    /** Takes a tile out of its folder and puts it back on the board next to it, at its size. */
    fun removeFromFolder(folderId: String, childId: String) {
        val span = folderTileSpans.value[childId] ?: StartTileItem.DEFAULT_SPAN
        editTiles { StartFolders.extract(it, folderId, childId, span) }
    }

    fun dissolveFolder(folderId: String) {
        val spans = folderTileSpans.value
        editTiles { tiles -> StartFolders.dissolve(tiles, folderId) { spans[it] ?: StartTileItem.DEFAULT_SPAN } }
    }

    fun moveWithinFolder(folderId: String, from: Int, to: Int) {
        editTiles { StartFolders.reorderChildren(it, folderId, from, to) }
    }

}

/** A tile is never wider than a phone, so a picture this big is already more than it can show. */
private const val TILE_PICTURE_MAX_PX = 1024

/** How long a provider is given to finish changing before its tile is re-read. */
private const val PROVIDER_SETTLE_MS = 600L

/** How far ahead the Start tile looks: a fortnight is more than it can ever show. */
private const val TILE_HORIZON_MS = 14 * 24 * 60 * 60 * 1000L
