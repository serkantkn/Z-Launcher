package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.ThemeMode
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
            com.serkantkn.zunelauncher.data.model.HubType.PICTURES,
            com.serkantkn.zunelauncher.data.model.HubType.APPS,
            com.serkantkn.zunelauncher.data.model.HubType.PHONE,
            com.serkantkn.zunelauncher.data.model.HubType.SETTINGS
        )
        val saved = prefs[HUB_ORDER]
        if (saved.isNullOrEmpty()) {
            defaultOrder
        } else {
            try {
                saved.split(",").map { com.serkantkn.zunelauncher.data.model.HubType.valueOf(it) }
            } catch (e: Exception) {
                defaultOrder
            }
        }
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
}
