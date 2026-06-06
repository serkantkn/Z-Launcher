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
import com.serkantkn.zunelauncher.data.repository.SettingsRepository
import com.serkantkn.zunelauncher.util.SystemSettingsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(
        SettingsDataStore(application)
    )

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

    fun setAccentColor(color: AccentColor) {
        viewModelScope.launch { settingsRepository.setAccentColor(color) }
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
}
