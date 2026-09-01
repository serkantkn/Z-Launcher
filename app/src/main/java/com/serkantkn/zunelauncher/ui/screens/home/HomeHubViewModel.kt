package com.serkantkn.zunelauncher.ui.screens.home

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.FavoriteAppItem
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.repository.AppRepository
import com.serkantkn.zunelauncher.data.repository.SettingsRepository
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import android.net.Uri
import com.serkantkn.zunelauncher.data.datastore.FavoritePhotosDataStore
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.data.repository.MediaRepository
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
}

class HomeHubViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val settingsRepository = SettingsRepository(SettingsDataStore(application))
    private val mediaRepository = MediaRepository(application)
    private val favoritePhotosDataStore = FavoritePhotosDataStore(application)

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _allImages = MutableStateFlow<List<MediaImage>>(emptyList())

    val hubOrder: StateFlow<List<HubType>> = settingsRepository.hubOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val startTiles: StateFlow<List<StartTileItem>> = settingsRepository.startTiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unifiedStartTiles: StateFlow<List<StartTileUIModel>> = combine(
        startTiles,
        _allApps,
        appRepository.getFavoritePackages()
    ) { currentStartTiles, apps, favoritePackages ->
        val appMap = apps.associateBy { it.packageName }
        val favPkgMap = favoritePackages.associateBy { it.packageName }
        val result = mutableListOf<StartTileUIModel>()
        val includedAppPkgs = mutableSetOf<String>()

        currentStartTiles.forEach { item ->
            if (item.isHub) {
                item.hubType?.let { hubType ->
                    result.add(StartTileUIModel.Hub(hubType, item.span))
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

    val latestNotification = MutableStateFlow<SocialMessageModel?>(null)
    val notificationCounts: StateFlow<Map<String, Int>> = SocialRepository.notificationCounts

    val latestMessages: StateFlow<Map<String, SocialMessageModel>> = SocialRepository.messages
        .map { list ->
            list.associateBy { it.packageName }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tileCornerStyle: StateFlow<TileCornerStyle> = settingsRepository.tileCornerStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileCornerStyle.ROUNDED)

    val tileSpacing: StateFlow<Int> = settingsRepository.tileSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val tileColumns: StateFlow<Int> = settingsRepository.tileColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val homeScreenLayout: StateFlow<HomeScreenLayout> = settingsRepository.homeScreenLayout
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
            HubType.CALENDAR to 2
        )
    )
    val hubCustomSpans: StateFlow<Map<HubType, Int>> = _hubCustomSpans

    init {
        loadImages()

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

    fun loadImages() {
        viewModelScope.launch {
            try {
                _allImages.value = mediaRepository.getAllImages()
            } catch (_: Exception) {
            }
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
            settingsRepository.setStartTiles(items)
        }
    }

    fun toggleTileSize(id: String) {
        viewModelScope.launch {
            val currentList = if (startTiles.value.isNotEmpty()) startTiles.value else unifiedStartTiles.value.map { StartTileItem(it.id, it.span) }
            val index = currentList.indexOfFirst { it.id == id }
            if (index != -1) {
                val currentSpan = currentList[index].span
                val newSpan = when (currentSpan) {
                    1 -> 2
                    2 -> 4
                    else -> 1
                }
                val updatedList = currentList.toMutableList()
                updatedList[index] = updatedList[index].copy(span = newSpan)
                settingsRepository.setStartTiles(updatedList)
                if (id.startsWith("app:")) {
                    val pkg = id.removePrefix("app:")
                    appRepository.updateSpan(pkg, newSpan)
                }
            } else {
                val updatedList = currentList.toMutableList()
                updatedList.add(StartTileItem(id, 4))
                settingsRepository.setStartTiles(updatedList)
            }
        }
    }

    fun removeTile(id: String) {
        viewModelScope.launch {
            val currentList = (if (startTiles.value.isNotEmpty()) startTiles.value else unifiedStartTiles.value.map { StartTileItem(it.id, it.span) }).toMutableList()
            currentList.removeAll { it.id == id }
            settingsRepository.setStartTiles(currentList)
            if (id.startsWith("app:")) {
                val pkg = id.removePrefix("app:")
                appRepository.toggleFavorite(pkg)
            }
        }
    }

    fun toggleAppSize(packageName: String) {
        toggleTileSize("app:$packageName")
    }

    fun removeFavorite(packageName: String) {
        removeTile("app:$packageName")
    }

    fun toggleHubSize(hubType: HubType) {
        toggleTileSize("hub:${hubType.name}")
    }
}
