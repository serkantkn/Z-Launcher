package com.serkantkn.zunelauncher.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import com.serkantkn.zunelauncher.data.repository.SettingsRepository
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.util.SystemSettingsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(
        SettingsDataStore(application)
    )

    init {
        viewModelScope.launch {
            val path = settingsRepository.customWallpaperPath.firstOrNull()
            val color = settingsRepository.dynamicThemeColor.firstOrNull()
            if (path != null && color == null) {
                try {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                                val swatch = palette?.dominantSwatch ?: palette?.vibrantSwatch ?: palette?.swatches?.maxByOrNull { it.population }
                                if (swatch != null) {
                                    viewModelScope.launch {
                                        settingsRepository.setDynamicThemeColor(swatch.rgb)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private val systemSettings = SystemSettingsManager(application)

    val brightness: StateFlow<Float> = systemSettings.brightness
    val mediaVolume: StateFlow<Float> = systemSettings.mediaVolume
    val ringVolume: StateFlow<Float> = systemSettings.ringVolume

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val fontScale: StateFlow<Float> = settingsRepository.fontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val animationsEnabled: StateFlow<Boolean> = settingsRepository.animationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val socialHubLayout: StateFlow<SocialHubLayout> = settingsRepository.socialHubLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SocialHubLayout.TIMELINE)

    val accentColor: StateFlow<AccentColor> = settingsRepository.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccentColor.MAGENTA)

    val directCallEnabled: StateFlow<Boolean> = settingsRepository.directCallEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notificationStyle: StateFlow<NotificationStyle> = settingsRepository.notificationStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationStyle.WINDOWS_PHONE)

    val volumeBarStyle: StateFlow<com.serkantkn.zunelauncher.data.model.VolumeBarStyle> = settingsRepository.volumeBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE)

    val disabledNotificationApps: StateFlow<Set<String>> = settingsRepository.disabledNotificationApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val installedApps: kotlinx.coroutines.flow.Flow<List<com.serkantkn.zunelauncher.data.model.AppInfo>> =
        com.serkantkn.zunelauncher.data.repository.AppRepository(getApplication()).getInstalledApps()

    val hubOrder: StateFlow<List<HubType>> = settingsRepository.hubOrder
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            listOf(
                HubType.MUSIC,
                HubType.PEOPLE,
                HubType.PICTURES,
                HubType.PHONE,
                HubType.INTERNET,
                HubType.SETTINGS
            )
        )

    fun moveHub(index: Int, direction: Int) {
        val current = hubOrder.value.toMutableList()
        val targetIndex = index + direction
        if (index in current.indices && targetIndex in current.indices) {
            val item = current.removeAt(index)
            current.add(targetIndex, item)
            viewModelScope.launch { settingsRepository.setHubOrder(current) }
        }
    }

    fun resetHubOrder() {
        val defaultOrder = listOf(
            HubType.MUSIC,
            HubType.PEOPLE,
            HubType.PICTURES,
            HubType.PHONE,
            HubType.INTERNET,
            HubType.SETTINGS
        )
        viewModelScope.launch { settingsRepository.setHubOrder(defaultOrder) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setFontScale(scale) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAnimationsEnabled(enabled) }
    }

    fun setSocialHubLayout(layout: SocialHubLayout) {
        viewModelScope.launch { settingsRepository.setSocialHubLayout(layout) }
    }

    fun setDirectCallEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDirectCallEnabled(enabled) }
    }

    fun setNotificationStyle(style: NotificationStyle) {
        viewModelScope.launch {
            settingsRepository.setNotificationStyle(style)
        }
    }

    fun setVolumeBarStyle(style: com.serkantkn.zunelauncher.data.model.VolumeBarStyle) {
        viewModelScope.launch {
            settingsRepository.setVolumeBarStyle(style)
        }
    }

    fun setDisabledNotificationApps(apps: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setDisabledNotificationApps(apps)
        }
    }

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch { settingsRepository.setAccentColor(color) }
    }

    fun setCustomThemeColor(color: Int?) {
        viewModelScope.launch { settingsRepository.setCustomThemeColor(color) }
    }

    fun setSolidBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSolidBackgroundEnabled(enabled) }
    }

    fun setBrightness(value: Float) {
        systemSettings.setBrightness(value)
    }

    fun setMediaVolume(value: Float) {
        systemSettings.setMediaVolume(value)
    }

    fun setRingVolume(value: Float) {
        systemSettings.setRingVolume(value)
    }

    fun refreshSystemSettings() {
        systemSettings.updateBrightness()
        systemSettings.updateVolumes()
    }

    fun openDisplaySettings() = openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)

    fun openWallpaperSettings() = openSystemSettings(Intent.ACTION_SET_WALLPAPER)

    fun openWifiSettings() = openSystemSettings(
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            Settings.Panel.ACTION_WIFI
        else Settings.ACTION_WIFI_SETTINGS
    )

    fun openBluetoothSettings() = openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun openSoundSettings() = openSystemSettings(Settings.ACTION_SOUND_SETTINGS)

    fun openNotificationSettings() {
        // App notification settings require EXTRA_APP_PACKAGE, but for general settings we can use:
        openSystemSettings("android.settings.ALL_APPS_NOTIFICATION_SETTINGS")
    }

    fun openNotificationAccessSettings() = openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun openDefaultAppsSettings() = openSystemSettings(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    private fun openSystemSettings(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    val customWallpaperPath: StateFlow<String?> = settingsRepository.customWallpaperPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dynamicThemeColor: StateFlow<Int?> = settingsRepository.dynamicThemeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customThemeColor: StateFlow<Int?> = settingsRepository.customThemeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val solidBackgroundEnabled: StateFlow<Boolean> = settingsRepository.solidBackgroundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun saveCroppedWallpaper(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                // Save to internal storage
                val file = java.io.File(context.filesDir, "custom_wallpaper.jpg")
                val outputStream = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                
                settingsRepository.setCustomWallpaperPath(file.absolutePath)

                // Extract dominant color
                androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                    val swatch = palette?.dominantSwatch ?: palette?.vibrantSwatch ?: palette?.swatches?.maxByOrNull { it.population }
                    val dominantColor = swatch?.rgb
                    viewModelScope.launch {
                        settingsRepository.setDynamicThemeColor(dominantColor)
                    }
                }


                // Also attempt to set the system wallpaper
                try {
                    com.serkantkn.zunelauncher.ui.components.ZuneWallpaperManager.lastInternalWallpaperChangeTime = System.currentTimeMillis()
                    val wm = android.app.WallpaperManager.getInstance(context)
                    val fileInputStream = java.io.FileInputStream(file)
                    wm.setStream(fileInputStream)
                    fileInputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearCustomWallpaper() {
        viewModelScope.launch {
            settingsRepository.setCustomWallpaperPath(null)
            settingsRepository.setDynamicThemeColor(null)

        }
    }

    fun clearBrowserHistory() {
        viewModelScope.launch {
            settingsRepository.setBrowserHistory("[]")
        }
    }
}
