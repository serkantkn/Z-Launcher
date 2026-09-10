package com.serkantkn.zunelauncher.ui.screens.settings

import com.serkantkn.zunelauncher.util.AppLanguage
import com.serkantkn.zunelauncher.util.AppLocale
import kotlinx.coroutines.flow.Flow
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubBackgroundMode
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.TileAnimation
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.KeyboardLanguage
import com.serkantkn.zunelauncher.data.model.OneHandedMode
import com.serkantkn.zunelauncher.data.model.TextShortcut
import com.serkantkn.zunelauncher.util.SystemSettingsManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    /** Hot, lifecycle-aware view of a settings flow: the one-liner every setting uses. */
    private fun <T> Flow<T>.asState(default: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), default)

    private val settingsDataStore = application.appContainer.settingsDataStore

    init {
        viewModelScope.launch {
            val path = settingsDataStore.customWallpaperPath.firstOrNull()
            val color = settingsDataStore.dynamicThemeColor.firstOrNull()
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
                                        settingsDataStore.setDynamicThemeColor(swatch.rgb)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { ZuneLog.w("SettingsViewModel", "init ignored Exception", e) }
            }
        }
    }

    private val systemSettings = SystemSettingsManager(application)

    private val _appLanguage = MutableStateFlow(AppLocale.current(application))
    /** In-app language (Ayarlar > sistem > dil). */
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    /** Persists [language]; returns true when the caller must recreate its activity (pre-Android 13). */
    fun setAppLanguage(language: AppLanguage): Boolean {
        _appLanguage.value = language
        return AppLocale.apply(getApplication(), language)
    }

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

    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeMode.asState(ThemeMode.DARK)

    val fontScale: StateFlow<Float> = settingsDataStore.fontScale.asState(1.0f)

    val animationsEnabled: StateFlow<Boolean> = settingsDataStore.animationsEnabled.asState(true)

    val socialHubLayout: StateFlow<SocialHubLayout> = settingsDataStore.socialHubLayout.asState(SocialHubLayout.TIMELINE)

    val accentColor: StateFlow<AccentColor> = settingsDataStore.accentColor.asState(AccentColor.MAGENTA)

    val directCallEnabled: StateFlow<Boolean> = settingsDataStore.directCallEnabled.asState(false)

    val notificationStyle: StateFlow<NotificationStyle> = settingsDataStore.notificationStyle.asState(NotificationStyle.WINDOWS_PHONE)

    val volumeBarStyle: StateFlow<com.serkantkn.zunelauncher.data.model.VolumeBarStyle> = settingsDataStore.volumeBarStyle.asState(com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE)

    val disabledNotificationApps: StateFlow<Set<String>> = settingsDataStore.disabledNotificationApps.asState(emptySet())

    val timeFormat: StateFlow<String> = settingsDataStore.timeFormat.asState("HH:mm")

    val dateFormat: StateFlow<String> = settingsDataStore.dateFormat.asState("EEEE, MMMM d")

    fun setTimeFormat(format: String) {
        viewModelScope.launch { settingsDataStore.setTimeFormat(format) }
    }

    fun setDateFormat(format: String) {
        viewModelScope.launch { settingsDataStore.setDateFormat(format) }
    }

    val installedApps: kotlinx.coroutines.flow.Flow<List<com.serkantkn.zunelauncher.data.model.AppInfo>> =
        application.appContainer.appRepository.getInstalledApps()

    val hubOrder: StateFlow<List<HubType>> = settingsDataStore.hubOrder
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
                HubType.EMAIL,
                HubType.CALCULATOR,
                HubType.WEATHER,
                HubType.SETTINGS
            )
        )

    fun moveHub(index: Int, direction: Int) {
        val current = hubOrder.value.toMutableList()
        val targetIndex = index + direction
        if (index in current.indices && targetIndex in current.indices) {
            val item = current.removeAt(index)
            current.add(targetIndex, item)
            viewModelScope.launch { settingsDataStore.setHubOrder(current) }
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
        viewModelScope.launch { settingsDataStore.setHubOrder(defaultOrder) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { settingsDataStore.setFontScale(scale) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAnimationsEnabled(enabled) }
    }

    fun setSocialHubLayout(layout: SocialHubLayout) {
        viewModelScope.launch { settingsDataStore.setSocialHubLayout(layout) }
    }

    fun setDirectCallEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDirectCallEnabled(enabled) }
    }

    fun setNotificationStyle(style: NotificationStyle) {
        viewModelScope.launch {
            settingsDataStore.setNotificationStyle(style)
        }
    }

    fun setVolumeBarStyle(style: com.serkantkn.zunelauncher.data.model.VolumeBarStyle) {
        viewModelScope.launch {
            settingsDataStore.setVolumeBarStyle(style)
        }
    }

    fun setDisabledNotificationApps(apps: Set<String>) {
        viewModelScope.launch {
            settingsDataStore.setDisabledNotificationApps(apps)
        }
    }

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch { settingsDataStore.setAccentColor(color) }
    }

    fun setCustomThemeColor(color: Int?) {
        viewModelScope.launch { settingsDataStore.setCustomThemeColor(color) }
    }

    fun setSolidBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSolidBackgroundEnabled(enabled) }
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
            } catch (e: Exception) { ZuneLog.w("SettingsViewModel", "openDefaultAppsSettings ignored Exception", e) }
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
                    ZuneLog.e("SettingsViewModel", "requestDefaultPhoneApp failed", e)
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
            } catch (e: Exception) { ZuneLog.w("SettingsViewModel", "requestDefaultPhoneApp ignored Exception", e) }
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
                    ZuneLog.e("SettingsViewModel", "requestDefaultSmsApp failed", e)
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
            } catch (e: Exception) { ZuneLog.w("SettingsViewModel", "requestDefaultSmsApp ignored Exception", e) }
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
                    ZuneLog.e("SettingsViewModel", "requestDefaultBrowserApp failed", e)
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
            } catch (e: Exception) { ZuneLog.w("SettingsViewModel", "requestDefaultBrowserApp ignored Exception", e) }
        }
    }

    private fun openSystemSettings(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    // --- Keyboard (system-wide IME, see ZuneKeyboardService) ---

    private val keyboardDataStore = application.appContainer.keyboardDataStore

    val keyboardSoundEnabled: StateFlow<Boolean> = keyboardDataStore.soundEnabled.asState(false)
    val keyboardVibrationEnabled: StateFlow<Boolean> = keyboardDataStore.vibrationEnabled.asState(true)
    val keyboardPreviewEnabled: StateFlow<Boolean> = keyboardDataStore.keyPreviewEnabled.asState(true)
    val keyboardHeightScale: StateFlow<Float> = keyboardDataStore.heightScale.asState(1.0f)

    fun setKeyboardSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { keyboardDataStore.setSoundEnabled(enabled) }
    }

    fun setKeyboardVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { keyboardDataStore.setVibrationEnabled(enabled) }
    }

    fun setKeyboardPreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { keyboardDataStore.setKeyPreviewEnabled(enabled) }
    }

    fun setKeyboardHeightScale(scale: Float) {
        viewModelScope.launch { keyboardDataStore.setHeightScale(scale) }
    }

    /** True once the keyboard is switched on in the system's keyboard list. */
    fun isKeyboardEnabled(): Boolean = try {
        val app = getApplication<Application>()
        app.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.any { it.packageName == app.packageName } == true
    } catch (e: Exception) {
        ZuneLog.w("SettingsViewModel", "isKeyboardEnabled failed", e)
        false
    }

    /** True while the keyboard is the input method Android actually uses. */
    fun isKeyboardDefault(): Boolean = try {
        val app = getApplication<Application>()
        Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("${app.packageName}/") == true
    } catch (e: Exception) {
        ZuneLog.w("SettingsViewModel", "isKeyboardDefault failed", e)
        false
    }

    val keyboardNumberRow: StateFlow<Boolean> = keyboardDataStore.numberRowEnabled.asState(false)
    val keyboardSuggestions: StateFlow<Boolean> = keyboardDataStore.suggestionsEnabled.asState(true)
    val keyboardAutoCorrect: StateFlow<Boolean> = keyboardDataStore.autoCorrectEnabled.asState(true)
    val keyboardSplit: StateFlow<Boolean> = keyboardDataStore.splitEnabled.asState(false)
    val keyboardBottomPadding: StateFlow<Int> = keyboardDataStore.bottomPaddingDp.asState(0)
    val keyboardOneHanded: StateFlow<OneHandedMode> = keyboardDataStore.oneHandedMode.asState(OneHandedMode.OFF)
    val keyboardLanguages: StateFlow<List<KeyboardLanguage>> = keyboardDataStore.languages.asState(emptyList())
    val keyboardShortcuts: StateFlow<List<TextShortcut>> = keyboardDataStore.textShortcuts.asState(emptyList())
    val keyboardLearnedCount: StateFlow<Int> = keyboardDataStore.learnedWords.map { it.size }.asState(0)
    val keyboardClipboardCount: StateFlow<Int> = keyboardDataStore.clipboardHistory.map { it.size }.asState(0)

    fun setKeyboardNumberRow(enabled: Boolean) {
        viewModelScope.launch { keyboardDataStore.setNumberRowEnabled(enabled) }
    }

    fun setKeyboardSuggestions(enabled: Boolean) {
        viewModelScope.launch { keyboardDataStore.setSuggestionsEnabled(enabled) }
    }

    fun setKeyboardAutoCorrect(enabled: Boolean) {
        viewModelScope.launch { keyboardDataStore.setAutoCorrectEnabled(enabled) }
    }

    fun setKeyboardSplit(enabled: Boolean) {
        viewModelScope.launch { keyboardDataStore.setSplitEnabled(enabled) }
    }

    fun setKeyboardBottomPadding(dp: Int) {
        viewModelScope.launch { keyboardDataStore.setBottomPaddingDp(dp) }
    }

    fun setKeyboardOneHanded(mode: OneHandedMode) {
        viewModelScope.launch { keyboardDataStore.setOneHandedMode(mode) }
    }

    /** Switching a second language on is what puts the language key next to the space bar. */
    fun setKeyboardLanguageEnabled(language: KeyboardLanguage, enabled: Boolean) {
        viewModelScope.launch {
            val current = keyboardDataStore.languages.firstOrNull().orEmpty()
            val updated = if (enabled) {
                if (current.contains(language)) current else current + language
            } else {
                current - language
            }
            keyboardDataStore.setLanguages(updated)
        }
    }

    fun clearKeyboardLearnedWords() {
        viewModelScope.launch { keyboardDataStore.clearLearnedWords() }
    }

    fun clearKeyboardClipboard() {
        viewModelScope.launch { keyboardDataStore.clearClipboard() }
    }

    fun addKeyboardShortcut(trigger: String, expansion: String) {
        val cleanTrigger = trigger.trim()
        val cleanExpansion = expansion.trim()
        if (cleanTrigger.isEmpty() || cleanExpansion.isEmpty()) return
        viewModelScope.launch {
            keyboardDataStore.addShortcut(
                TextShortcut(
                    id = cleanTrigger.lowercase(),
                    trigger = cleanTrigger,
                    expansion = cleanExpansion
                )
            )
        }
    }

    fun removeKeyboardShortcut(id: String) {
        viewModelScope.launch { keyboardDataStore.removeShortcut(id) }
    }

    fun openKeyboardSettings() = openSystemSettings(Settings.ACTION_INPUT_METHOD_SETTINGS)

    /** Opens the system's "change keyboard" picker. */
    fun showKeyboardPicker() {
        try {
            getApplication<Application>()
                .getSystemService(InputMethodManager::class.java)
                ?.showInputMethodPicker()
        } catch (e: Exception) {
            ZuneLog.w("SettingsViewModel", "showInputMethodPicker failed", e)
        }
    }

    val customWallpaperPath: StateFlow<String?> = settingsDataStore.customWallpaperPath.asState(null)

    val customHubWallpaperPath: StateFlow<String?> = settingsDataStore.customHubWallpaperPath.asState(null)

    val hubBackgroundMode: StateFlow<HubBackgroundMode> = settingsDataStore.hubBackgroundMode.asState(HubBackgroundMode.MATCH_LAUNCHER)

    val hubBackgroundOpacity: StateFlow<Float> = settingsDataStore.hubBackgroundOpacity.asState(0.85f)

    val tileCornerStyle: StateFlow<TileCornerStyle> = settingsDataStore.tileCornerStyle.asState(TileCornerStyle.ROUNDED)

    val tileSpacing: StateFlow<Int> = settingsDataStore.tileSpacing.asState(2)

    val tileOpacity: StateFlow<Int> = settingsDataStore.tileOpacity.asState(SettingsDataStore.DEFAULT_TILE_OPACITY)

    val tileAnimation: StateFlow<TileAnimation> = settingsDataStore.tileAnimation.asState(TileAnimation.SLIDE)

    /** The weather hub's moving sky. On unless it is switched off here. */
    val weatherAnimatedSky: StateFlow<Boolean> =
        application.appContainer.weatherDataStore.animatedSky.asState(true)

    val tileColumns: StateFlow<Int> = settingsDataStore.tileColumns.asState(4)

    val homeScreenLayout: StateFlow<HomeScreenLayout> = settingsDataStore.homeScreenLayout.asState(HomeScreenLayout.ZUNE)

    val dynamicThemeColor: StateFlow<Int?> = settingsDataStore.dynamicThemeColor.asState(null)

    val customThemeColor: StateFlow<Int?> = settingsDataStore.customThemeColor.asState(null)

    val solidBackgroundEnabled: StateFlow<Boolean> = settingsDataStore.solidBackgroundEnabled.asState(false)

    fun setHubBackgroundMode(mode: HubBackgroundMode) {
        viewModelScope.launch {
            settingsDataStore.setHubBackgroundMode(mode)
        }
    }

    fun setHubBackgroundOpacity(opacity: Float) {
        viewModelScope.launch {
            settingsDataStore.setHubBackgroundOpacity(opacity)
        }
    }

    fun setTileCornerStyle(style: TileCornerStyle) {
        viewModelScope.launch {
            settingsDataStore.setTileCornerStyle(style)
        }
    }

    fun setWeatherAnimatedSky(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().appContainer.weatherDataStore.setAnimatedSky(enabled)
        }
    }

    fun setTileOpacity(opacity: Int) {
        viewModelScope.launch {
            settingsDataStore.setTileOpacity(opacity)
        }
    }

    fun setTileAnimation(animation: TileAnimation) {
        viewModelScope.launch {
            settingsDataStore.setTileAnimation(animation)
        }
    }

    fun setTileSpacing(spacing: Int) {
        viewModelScope.launch {
            settingsDataStore.setTileSpacing(spacing)
        }
    }

    fun setTileColumns(columns: Int) {
        viewModelScope.launch {
            settingsDataStore.setTileColumns(columns)
        }
    }

    fun setHomeScreenLayout(layout: HomeScreenLayout) {
        viewModelScope.launch {
            settingsDataStore.setHomeScreenLayout(layout)
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
                
                settingsDataStore.setCustomWallpaperPath(file.absolutePath)

                // Extract dominant color
                androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                    val swatch = palette?.dominantSwatch ?: palette?.vibrantSwatch ?: palette?.swatches?.maxByOrNull { it.population }
                    val dominantColor = swatch?.rgb
                    viewModelScope.launch {
                        settingsDataStore.setDynamicThemeColor(dominantColor)
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
                    ZuneLog.e("SettingsViewModel", "saveCroppedWallpaper failed", e)
                }
            } catch (e: Exception) {
                ZuneLog.e("SettingsViewModel", "saveCroppedWallpaper failed", e)
            }
        }
    }

    fun clearCustomWallpaper() {
        viewModelScope.launch {
            settingsDataStore.setCustomWallpaperPath(null)
            settingsDataStore.setDynamicThemeColor(null)
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

                settingsDataStore.setCustomHubWallpaperPath(file.absolutePath)
                settingsDataStore.setHubBackgroundMode(HubBackgroundMode.CUSTOM)
            } catch (e: Exception) {
                ZuneLog.e("SettingsViewModel", "saveCroppedHubWallpaper failed", e)
            }
        }
    }

    fun clearCustomHubWallpaper() {
        viewModelScope.launch {
            settingsDataStore.setCustomHubWallpaperPath(null)
        }
    }

    fun clearBrowserHistory() {
        viewModelScope.launch {
            settingsDataStore.setBrowserHistory("[]")
        }
    }
}
