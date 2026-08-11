package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val dataStore: SettingsDataStore) {

    val themeMode: Flow<ThemeMode> = dataStore.themeMode
    val fontScale: Flow<Float> = dataStore.fontScale
    val animationsEnabled: Flow<Boolean> = dataStore.animationsEnabled
    val socialHubLayout: Flow<SocialHubLayout> = dataStore.socialHubLayout
    val accentColor: Flow<AccentColor> = dataStore.accentColor
    val directCallEnabled: Flow<Boolean> = dataStore.directCallEnabled
    val hubOrder: Flow<List<com.serkantkn.zunelauncher.data.model.HubType>> = dataStore.hubOrder
    val customWallpaperPath: Flow<String?> = dataStore.customWallpaperPath
    val dynamicThemeColor: Flow<Int?> = dataStore.dynamicThemeColor
    val customThemeColor: Flow<Int?> = dataStore.customThemeColor
    val solidBackgroundEnabled: Flow<Boolean> = dataStore.solidBackgroundEnabled
    val browserFavorites: Flow<String?> = dataStore.browserFavorites
    val browserHistory: Flow<String?> = dataStore.browserHistory
    val notificationStyle: Flow<NotificationStyle> = dataStore.notificationStyle
    val volumeBarStyle: Flow<com.serkantkn.zunelauncher.data.model.VolumeBarStyle> = dataStore.volumeBarStyle
    val disabledNotificationApps: Flow<Set<String>> = dataStore.disabledNotificationApps

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
    suspend fun setFontScale(scale: Float) = dataStore.setFontScale(scale)
    suspend fun setAnimationsEnabled(enabled: Boolean) = dataStore.setAnimationsEnabled(enabled)
    suspend fun setSocialHubLayout(layout: SocialHubLayout) = dataStore.setSocialHubLayout(layout)
    suspend fun setAccentColor(color: AccentColor) = dataStore.setAccentColor(color)
    suspend fun setDirectCallEnabled(enabled: Boolean) = dataStore.setDirectCallEnabled(enabled)
    suspend fun setHubOrder(order: List<com.serkantkn.zunelauncher.data.model.HubType>) = dataStore.setHubOrder(order)
    suspend fun setCustomWallpaperPath(path: String?) = dataStore.setCustomWallpaperPath(path)
    suspend fun setDynamicThemeColor(color: Int?) = dataStore.setDynamicThemeColor(color)
    suspend fun setCustomThemeColor(color: Int?) = dataStore.setCustomThemeColor(color)
    suspend fun setSolidBackgroundEnabled(enabled: Boolean) = dataStore.setSolidBackgroundEnabled(enabled)
    suspend fun setBrowserFavorites(json: String) = dataStore.setBrowserFavorites(json)
    suspend fun setBrowserHistory(json: String) = dataStore.setBrowserHistory(json)
    suspend fun setNotificationStyle(style: NotificationStyle) = dataStore.setNotificationStyle(style)
    suspend fun setVolumeBarStyle(style: com.serkantkn.zunelauncher.data.model.VolumeBarStyle) = dataStore.setVolumeBarStyle(style)
    suspend fun setDisabledNotificationApps(apps: Set<String>) = dataStore.setDisabledNotificationApps(apps)
}
