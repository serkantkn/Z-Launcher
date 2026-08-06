package com.serkantkn.zunelauncher.ui.screens.home

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.FavoriteAppItem
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.repository.AppRepository
import com.serkantkn.zunelauncher.data.repository.SettingsRepository
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

class HomeHubViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val settingsRepository = SettingsRepository(SettingsDataStore(application))

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val hubOrder: StateFlow<List<HubType>> = settingsRepository.hubOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val latestNotification = MutableStateFlow<SocialMessageModel?>(null)
    val notificationCounts: StateFlow<Map<String, Int>> = SocialRepository.notificationCounts

    val latestMessages: StateFlow<Map<String, SocialMessageModel>> = SocialRepository.messages
        .map { list ->
            list.associateBy { it.packageName }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
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

        // Inject mock notifications to demonstrate Live Tile flip animations
        viewModelScope.launch {
            val favs = favoriteApps.first { it.isNotEmpty() }
            val mockCounts = mutableMapOf<String, Int>()
            favs.take(4).forEachIndexed { i, fav ->
                val pkg = fav.appInfo.packageName
                mockCounts[pkg] = (i + 1) * 2
                
                SocialRepository.addOrUpdateMessage(
                    SocialMessageModel(
                        id = "mock_$i",
                        packageName = pkg,
                        appName = fav.appInfo.label,
                        title = "Bildirim: ${fav.appInfo.label}",
                        text = "Bu, Canlı Karo 3D flip animasyonunu test etmek için oluşturulmuş örnek bir bildirimdir.",
                        timestamp = System.currentTimeMillis(),
                        icon = null,
                        replyAction = null,
                        openIntent = null
                    )
                )
            }
            SocialRepository.updateNotificationCounts(mockCounts)
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

    fun updateHubOrder(order: List<HubType>) {
        viewModelScope.launch {
            settingsRepository.setHubOrder(order)
        }
    }

    fun toggleAppSize(packageName: String) {
        viewModelScope.launch {
            val current = favoriteApps.value.find { it.appInfo.packageName == packageName }
            if (current != null) {
                val newSpan = when (current.span) {
                    1 -> 2
                    2 -> 4
                    else -> 1
                }
                appRepository.updateSpan(packageName, newSpan)
            }
        }
    }

    fun removeFavorite(packageName: String) {
        viewModelScope.launch {
            appRepository.toggleFavorite(packageName) // Will remove since it's already a favorite
        }
    }
}
