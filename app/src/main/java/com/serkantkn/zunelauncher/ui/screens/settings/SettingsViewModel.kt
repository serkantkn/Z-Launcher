package com.serkantkn.zunelauncher.ui.screens.settings

import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubBackgroundMode
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.util.SystemSettingsManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = application.appContainer.settingsRepository

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

    private val _targetTab = MutableStateFlow<String?>(null)
    val targetTab: StateFlow<String?> = _targetTab.asStateFlow()

    fun setTargetTab(tab: String?) {
        _targetTab.value = tab
    }

    fun clearTargetTab() {
        _targetTab.value = null
    }

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

    val timeFormat: StateFlow<String> = settingsRepository.timeFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "HH:mm")

    val dateFormat: StateFlow<String> = settingsRepository.dateFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "EEEE, MMMM d")

    fun setTimeFormat(format: String) {
        viewModelScope.launch { settingsRepository.setTimeFormat(format) }
    }

    fun setDateFormat(format: String) {
        viewModelScope.launch { settingsRepository.setDateFormat(format) }
    }

    val installedApps: kotlinx.coroutines.flow.Flow<List<com.serkantkn.zunelauncher.data.model.AppInfo>> =
        application.appContainer.appRepository.getInstalledApps()

    val hubOrder: StateFlow<List<HubType>> = settingsRepository.hubOrder
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            listOf(
                HubType.PHONE,
                HubType.MESSAGING,
                HubType.PEOPLE,
                HubType.INTERNET,
                HubType.PICTURES,
                HubType.MUSIC,
                HubType.FILES,
                HubType.CLOCK,
                HubType.CALENDAR,
                HubType.NOTES,
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

    fun openDefaultAppsSettings() {
        val context = getApplication<Application>()
        val intentsToTry = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun requestDefaultPhoneApp(context: android.content.Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    launcher.launch(intent)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        val intentsToTry = listOf(
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName),
            Intent("android.settings.ACTION_CHANGE_DEFAULT_DIALER"),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun requestDefaultSmsApp(context: android.content.Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    launcher.launch(intent)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        val intentsToTry = listOf(
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName),
            Intent("android.provider.Telephony.ACTION_CHANGE_DEFAULT"),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun requestDefaultBrowserApp(context: android.content.Context, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    launcher.launch(intent)
                    return
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        val intentsToTry = listOf(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intentsToTry) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    private fun openSystemSettings(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    val customWallpaperPath: StateFlow<String?> = settingsRepository.customWallpaperPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customHubWallpaperPath: StateFlow<String?> = settingsRepository.customHubWallpaperPath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hubBackgroundMode: StateFlow<HubBackgroundMode> = settingsRepository.hubBackgroundMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HubBackgroundMode.MATCH_LAUNCHER)

    val hubBackgroundOpacity: StateFlow<Float> = settingsRepository.hubBackgroundOpacity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.85f)

    val tileCornerStyle: StateFlow<TileCornerStyle> = settingsRepository.tileCornerStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileCornerStyle.ROUNDED)

    val tileSpacing: StateFlow<Int> = settingsRepository.tileSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val tileColumns: StateFlow<Int> = settingsRepository.tileColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val homeScreenLayout: StateFlow<HomeScreenLayout> = settingsRepository.homeScreenLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeScreenLayout.ZUNE)

    val dynamicThemeColor: StateFlow<Int?> = settingsRepository.dynamicThemeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customThemeColor: StateFlow<Int?> = settingsRepository.customThemeColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val solidBackgroundEnabled: StateFlow<Boolean> = settingsRepository.solidBackgroundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setHubBackgroundMode(mode: HubBackgroundMode) {
        viewModelScope.launch {
            settingsRepository.setHubBackgroundMode(mode)
        }
    }

    fun setHubBackgroundOpacity(opacity: Float) {
        viewModelScope.launch {
            settingsRepository.setHubBackgroundOpacity(opacity)
        }
    }

    fun setTileCornerStyle(style: TileCornerStyle) {
        viewModelScope.launch {
            settingsRepository.setTileCornerStyle(style)
        }
    }

    fun setTileSpacing(spacing: Int) {
        viewModelScope.launch {
            settingsRepository.setTileSpacing(spacing)
        }
    }

    fun setTileColumns(columns: Int) {
        viewModelScope.launch {
            settingsRepository.setTileColumns(columns)
        }
    }

    fun setHomeScreenLayout(layout: HomeScreenLayout) {
        viewModelScope.launch {
            settingsRepository.setHomeScreenLayout(layout)
        }
    }

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

    fun saveCroppedHubWallpaper(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val file = java.io.File(context.filesDir, "custom_hub_wallpaper.jpg")
                val outputStream = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()

                settingsRepository.setCustomHubWallpaperPath(file.absolutePath)
                settingsRepository.setHubBackgroundMode(HubBackgroundMode.CUSTOM)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearCustomHubWallpaper() {
        viewModelScope.launch {
            settingsRepository.setCustomHubWallpaperPath(null)
        }
    }

    fun clearBrowserHistory() {
        viewModelScope.launch {
            settingsRepository.setBrowserHistory("[]")
        }
    }
}
