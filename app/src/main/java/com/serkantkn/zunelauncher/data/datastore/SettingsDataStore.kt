package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsDataStore(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val SOCIAL_HUB_LAYOUT = stringPreferencesKey("social_hub_layout")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val DIRECT_CALL_ENABLED = booleanPreferencesKey("direct_call_enabled")
        val HUB_ORDER = stringPreferencesKey("hub_order")
        val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")
        val DYNAMIC_THEME_COLOR = intPreferencesKey("dynamic_theme_color")
        val CUSTOM_THEME_COLOR = intPreferencesKey("custom_theme_color")
        val SOLID_BACKGROUND_ENABLED = booleanPreferencesKey("solid_background_enabled")
        val BROWSER_FAVORITES = stringPreferencesKey("browser_favorites")
        val BROWSER_HISTORY = stringPreferencesKey("browser_history")
        val NOTIFICATION_STYLE = stringPreferencesKey("notification_style")
        val VOLUME_BAR_STYLE = stringPreferencesKey("volume_bar_style")
        val DISABLED_NOTIFICATION_APPS = stringPreferencesKey("disabled_notification_apps")
    }

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        try {
            ThemeMode.valueOf(prefs[THEME_MODE] ?: ThemeMode.DARK.name)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    val fontScale: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[FONT_SCALE] ?: 1.0f
    }

    val animationsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ANIMATIONS_ENABLED] ?: true
    }

    val socialHubLayout: Flow<SocialHubLayout> = context.settingsDataStore.data.map { prefs ->
        try {
            SocialHubLayout.valueOf(prefs[SOCIAL_HUB_LAYOUT] ?: SocialHubLayout.TIMELINE.name)
        } catch (e: Exception) {
            SocialHubLayout.TIMELINE
        }
    }

    val accentColor: Flow<AccentColor> = context.settingsDataStore.data.map { prefs ->
        try {
            AccentColor.valueOf(prefs[ACCENT_COLOR] ?: AccentColor.MAGENTA.name)
        } catch (e: Exception) {
            AccentColor.MAGENTA
        }
    }

    val directCallEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[DIRECT_CALL_ENABLED] ?: false
    }

    val hubOrder: Flow<List<com.serkantkn.zunelauncher.data.model.HubType>> = context.settingsDataStore.data.map { prefs ->
        val defaultOrder = listOf(
            com.serkantkn.zunelauncher.data.model.HubType.MUSIC,
            com.serkantkn.zunelauncher.data.model.HubType.PEOPLE,
            com.serkantkn.zunelauncher.data.model.HubType.MESSAGING,
            com.serkantkn.zunelauncher.data.model.HubType.PICTURES,
            com.serkantkn.zunelauncher.data.model.HubType.FILES,
            com.serkantkn.zunelauncher.data.model.HubType.PHONE,
            com.serkantkn.zunelauncher.data.model.HubType.INTERNET,
            com.serkantkn.zunelauncher.data.model.HubType.SETTINGS
        )
        val saved = prefs[HUB_ORDER]
        if (saved.isNullOrEmpty()) {
            defaultOrder
        } else {
            val savedList = saved.split(",").mapNotNull { 
                try {
                    com.serkantkn.zunelauncher.data.model.HubType.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            }
            val missingHubs = defaultOrder.filter { it !in savedList && it != com.serkantkn.zunelauncher.data.model.HubType.HOME }
            (savedList + missingHubs).ifEmpty { defaultOrder }
        }
    }

    val customWallpaperPath: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[CUSTOM_WALLPAPER_PATH]
    }

    val solidBackgroundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SOLID_BACKGROUND_ENABLED] ?: false
    }

    val dynamicThemeColor: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        prefs[DYNAMIC_THEME_COLOR]
    }

    val customThemeColor: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        prefs[CUSTOM_THEME_COLOR]
    }

    val browserFavorites: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[BROWSER_FAVORITES]
    }

    val browserHistory: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[BROWSER_HISTORY]
    }

    val notificationStyle: Flow<NotificationStyle> = context.settingsDataStore.data.map { prefs ->
        try {
            NotificationStyle.valueOf(prefs[NOTIFICATION_STYLE] ?: NotificationStyle.WINDOWS_PHONE.name)
        } catch (e: Exception) {
            NotificationStyle.WINDOWS_PHONE
        }
    }

    val volumeBarStyle: Flow<com.serkantkn.zunelauncher.data.model.VolumeBarStyle> = context.settingsDataStore.data.map { prefs ->
        try {
            com.serkantkn.zunelauncher.data.model.VolumeBarStyle.valueOf(prefs[VOLUME_BAR_STYLE] ?: com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE.name)
        } catch (e: Exception) {
            com.serkantkn.zunelauncher.data.model.VolumeBarStyle.WINDOWS_PHONE
        }
    }

    val disabledNotificationApps: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[DISABLED_NOTIFICATION_APPS] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }


    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }

    suspend fun setFontScale(scale: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[FONT_SCALE] = scale
        }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[ANIMATIONS_ENABLED] = enabled
        }
    }

    suspend fun setSocialHubLayout(layout: SocialHubLayout) {
        context.settingsDataStore.edit { prefs ->
            prefs[SOCIAL_HUB_LAYOUT] = layout.name
        }
    }

    suspend fun setAccentColor(color: AccentColor) {
        context.settingsDataStore.edit { prefs ->
            prefs[ACCENT_COLOR] = color.name
        }
    }

    suspend fun setDirectCallEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[DIRECT_CALL_ENABLED] = enabled
        }
    }

    suspend fun setHubOrder(order: List<com.serkantkn.zunelauncher.data.model.HubType>) {
        context.settingsDataStore.edit { prefs ->
            prefs[HUB_ORDER] = order.joinToString(",") { it.name }
        }
    }

    suspend fun setCustomWallpaperPath(path: String?) {
        context.settingsDataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(CUSTOM_WALLPAPER_PATH)
            } else {
                prefs[CUSTOM_WALLPAPER_PATH] = path
            }
        }
    }

    suspend fun setSolidBackgroundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[SOLID_BACKGROUND_ENABLED] = enabled
        }
    }

    suspend fun setDynamicThemeColor(color: Int?) {
        context.settingsDataStore.edit { prefs ->
            if (color == null) {
                prefs.remove(DYNAMIC_THEME_COLOR)
            } else {
                prefs[DYNAMIC_THEME_COLOR] = color
            }
        }
    }

    suspend fun setCustomThemeColor(color: Int?) {
        context.settingsDataStore.edit { prefs ->
            if (color == null) {
                prefs.remove(CUSTOM_THEME_COLOR)
            } else {
                prefs[CUSTOM_THEME_COLOR] = color
            }
        }
    }

    suspend fun setBrowserFavorites(json: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[BROWSER_FAVORITES] = json
        }
    }

    suspend fun setBrowserHistory(json: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[BROWSER_HISTORY] = json
        }
    }

    suspend fun setNotificationStyle(style: NotificationStyle) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFICATION_STYLE] = style.name
        }
    }

    suspend fun setVolumeBarStyle(style: com.serkantkn.zunelauncher.data.model.VolumeBarStyle) {
        context.settingsDataStore.edit { prefs ->
            prefs[VOLUME_BAR_STYLE] = style.name
        }
    }

    suspend fun setDisabledNotificationApps(apps: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[DISABLED_NOTIFICATION_APPS] = apps.joinToString(",")
        }
    }
}
