package com.serkantkn.zunelauncher.ui.screens.home

import com.serkantkn.zunelauncher.util.localizedString
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.FavoriteAppItem
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.data.model.TileAnimation
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.data.service.ActiveMediaState
import com.serkantkn.zunelauncher.data.service.ThirdPartyMediaController
import kotlinx.coroutines.flow.asStateFlow
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import android.net.Uri
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.data.model.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoriteAppUIModel(
    val appInfo: AppInfo,
    val span: Int
)

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

    /** The "hızlı not" tile that opens a blank editor. */
    data class QuickNote(override val span: Int) : StartTileUIModel {
        override val id: String = StartTileItem.QUICK_NOTE_ID
    }
}

private const val PEOPLE_TILE_FACES = 24

class HomeHubViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = application.appContainer.appRepository
    private val settingsDataStore = application.appContainer.settingsDataStore
    private val mediaRepository = application.appContainer.mediaRepository
    private val favoritePhotosDataStore = application.appContainer.favoritePhotosDataStore
    private val notesDataStore = application.appContainer.notesDataStore
    private val alarmDataStore = application.appContainer.alarmDataStore
    private val calendarDataStore = application.appContainer.calendarDataStore
    private val contactRepository = application.appContainer.contactRepository
    private val weatherRepository = application.appContainer.weatherRepository
    private val callLogRepository = application.appContainer.callLogRepository

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _allImages = MutableStateFlow<List<MediaImage>>(emptyList())

    val hubOrder: StateFlow<List<HubType>> = settingsDataStore.hubOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Badge for the Notes hub tile: number of pinned, non-archived notes. */
    /** Enabled alarms (clock alarms and note reminders) for the saat live tile. */
    val enabledAlarms: StateFlow<List<Alarm>> = alarmDataStore.alarmsFlow
        .map { alarms -> alarms.filter { it.isEnabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Events from the start of today onwards, soonest first, for the takvim live tile. */
    val upcomingEvents: StateFlow<List<CalendarEvent>> = calendarDataStore.eventsFlow
        .map { events ->
            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            events.filter { it.timestamp >= startOfToday }
                .sortedWith(compareBy({ it.timestamp }, { it.hour }, { it.minute }))
                .take(6)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** "12 × 3 = 36": the last calculation, flipped onto the hesap makinesi tile. */
    val calculatorTileSubtitle: StateFlow<String?> = application.appContainer.calculatorDataStore.historyFlow
        .map { history -> history.firstOrNull()?.let { entry -> entry.expression + " = " + (entry.result.toBigDecimalOrNull()?.let { com.serkantkn.zunelauncher.util.CalcEngine.format(it) } ?: entry.result) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Unread mail across every inbox: badge of the e-posta tile. */
    val emailUnreadCount: StateFlow<Int> = application.appContainer.emailCache.inboxUnread

    val pinnedNotesCount: StateFlow<Int> = notesDataStore.notesFlow
        .map { notes -> notes.count { it.isPinned && !it.isArchived && !it.isInTrash } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoritePhotoUris: StateFlow<List<Uri>> = combine(
        _allImages,
        favoritePhotosDataStore.favoritePhotoIds
    ) { images, favIds ->
        if (favIds.isNotEmpty()) {
            val favs = images.filter { favIds.contains(it.id.toString()) }.map { it.uri }
            if (favs.isNotEmpty()) favs else images.take(10).map { it.uri }
        } else {
            images.take(10).map { it.uri }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteApps: StateFlow<List<FavoriteAppUIModel>> = combine(
        _allApps,
        appRepository.getFavoritePackages()
    ) { apps, favoritePackages ->
        val appMap = apps.associateBy { it.packageName }
        // Keep the exact order defined in favoritePackages
        favoritePackages.mapNotNull { item ->
            appMap[item.packageName]?.let { appInfo ->
                FavoriteAppUIModel(appInfo, item.span)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val startTiles: StateFlow<List<StartTileItem>> = settingsDataStore.startTiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unifiedStartTiles: StateFlow<List<StartTileUIModel>> = combine(
        startTiles,
        _allApps,
        appRepository.getFavoritePackages(),
        notesDataStore.notesFlow
    ) { currentStartTiles, apps, favoritePackages, notes ->
        val appMap = apps.associateBy { it.packageName }
        val favPkgMap = favoritePackages.associateBy { it.packageName }
        val result = mutableListOf<StartTileUIModel>()
        val includedAppPkgs = mutableSetOf<String>()

        currentStartTiles.forEach { item ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Cached forecast for the place the weather hub is set to, for the weather tile. */
    val weatherSnapshot: StateFlow<WeatherSnapshot?> = combine(
        weatherRepository.places,
        weatherRepository.selectedPlaceId,
        weatherRepository.cache
    ) { places, selectedId, cache ->
        val place = places.firstOrNull { it.id == selectedId } ?: places.firstOrNull()
        place?.let { cache[it.id] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weatherUnit: StateFlow<TemperatureUnit> = application.appContainer.weatherDataStore.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemperatureUnit.CELSIUS)

    val latestNotification = MutableStateFlow<SocialMessageModel?>(null)
    val notificationCounts: StateFlow<Map<String, Int>> = SocialRepository.notificationCounts

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

    val tileSpacing: StateFlow<Int> = settingsDataStore.tileSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val tileColumns: StateFlow<Int> = settingsDataStore.tileColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val homeScreenLayout: StateFlow<HomeScreenLayout> = settingsDataStore.homeScreenLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeScreenLayout.ZUNE)

    private val _hubCustomSpans = MutableStateFlow<Map<HubType, Int>>(
        mapOf(
            HubType.PICTURES to 4,
            HubType.MUSIC to 4,
            HubType.PHONE to 2,
            HubType.MESSAGING to 2,
            HubType.PEOPLE to 2,
            HubType.INTERNET to 2,
            HubType.FILES to 2,
            HubType.SETTINGS to 2,
            HubType.CLOCK to 2,
            HubType.CALENDAR to 2,
            HubType.NOTES to 2,
            HubType.EMAIL to 2,
            HubType.CALCULATOR to 2,
            HubType.WEATHER to 4
        )
    )
    val hubCustomSpans: StateFlow<Map<HubType, Int>> = _hubCustomSpans

    init {
        loadImages()
        refreshLiveTiles()
        runCatching { mediaController.startListening() }
            .onFailure { ZuneLog.w("HomeHubViewModel", "media session listening unavailable", it) }

        viewModelScope.launch {
            appRepository.getInstalledApps().collect { apps ->
                _allApps.value = apps
            }
        }

        viewModelScope.launch {
            SocialRepository.messages.collect { messages ->
                if (messages.isNotEmpty()) {
                    val newMsg = messages.first()
                    if (latestNotification.value?.id != newMsg.id) {
                        latestNotification.value = newMsg
                        delay(4000)
                        if (latestNotification.value?.id == newMsg.id) {
                            latestNotification.value = null
                        }
                    }
                }
            }
        }
    }

    /**
     * Re-reads the providers behind the live tiles. Each one is guarded on its own: a launcher
     * without the contacts or call-log permission simply shows the plain face of that tile.
     */
    fun refreshLiveTiles() {
        viewModelScope.launch {
            _peopleFaces.value = try {
                contactRepository.getContacts()
                    .sortedWith(compareByDescending<ContactModel> { it.isFavorite }.thenByDescending { it.lastTimeContacted })
                    .take(PEOPLE_TILE_FACES)
            } catch (e: Exception) {
                ZuneLog.w("HomeHubViewModel", "contacts unavailable for the people tile", e)
                emptyList()
            }

            try {
                val calls = callLogRepository.getRecentCalls()
                val missed = calls.takeWhile { it.type == android.provider.CallLog.Calls.MISSED_TYPE }
                _missedCalls.value = missed.size
                _lastMissedCaller.value = missed.firstOrNull()?.let { it.name?.takeIf { name -> name.isNotBlank() } ?: it.number }
            } catch (e: Exception) {
                ZuneLog.w("HomeHubViewModel", "call log unavailable for the phone tile", e)
                _missedCalls.value = 0
                _lastMissedCaller.value = null
            }

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

            _unreadMessages.value = try {
                SmsRepository.getConversations(getApplication())
                    .filter { !it.isRead }
                    .sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                ZuneLog.w("HomeHubViewModel", "messages unavailable for the messaging tile", e)
                emptyList()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { mediaController.stopListening() }
    }

    fun loadImages() {
        viewModelScope.launch {
            try {
                _allImages.value = mediaRepository.getAllImages()
            } catch (e: Exception) { ZuneLog.w("HomeHubViewModel", "loadImages ignored Exception", e) }
        }
    }

    fun launchApp(packageName: String) {
        appRepository.launchApp(packageName)
    }

    fun getAppIcon(packageName: String): Drawable? = appRepository.getAppIcon(packageName)

    fun updateFavoritesOrder(uiModels: List<FavoriteAppUIModel>) {
        viewModelScope.launch {
            val items = uiModels.map { FavoriteAppItem(it.appInfo.packageName, it.span) }
            appRepository.updateFavoritesOrder(items)
        }
    }

    fun updateStartTilesOrder(uiModels: List<StartTileUIModel>) {
        viewModelScope.launch {
            val items = uiModels.map { StartTileItem(it.id, it.span) }
            settingsDataStore.setStartTiles(items)
        }
    }


    fun removeTile(id: String) {
        viewModelScope.launch {
            val currentList = (if (startTiles.value.isNotEmpty()) startTiles.value else unifiedStartTiles.value.map { StartTileItem(it.id, it.span) }).toMutableList()
            currentList.removeAll { it.id == id }
            settingsDataStore.setStartTiles(currentList)
            if (id.startsWith("app:")) {
                val pkg = id.removePrefix("app:")
                appRepository.toggleFavorite(pkg)
            }
        }
    }


    fun removeFavorite(packageName: String) {
        removeTile("app:$packageName")
    }

}
