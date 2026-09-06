package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.HomeScreenLayout
import com.serkantkn.zunelauncher.data.model.HubBackgroundMode
import com.serkantkn.zunelauncher.data.model.NotificationStyle
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.model.looksLikeJsonArray
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.parseJsonStringList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import com.serkantkn.zunelauncher.data.model.toJsonStringArray
import com.serkantkn.zunelauncher.settingsDataStore
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

/**
 * Launcher-wide settings. List-valued preferences ([HUB_ORDER], [START_TILES],
 * [DISABLED_NOTIFICATION_APPS]) are stored as JSON arrays; their pre-JSON comma-delimited
 * values are still read as a fallback and rewritten as JSON the first time they are collected.
 * The browser keys hold JSON arrays produced by the Browser* model serializers.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private const val TAG = "SettingsDataStore"
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val SOCIAL_HUB_LAYOUT = stringPreferencesKey("social_hub_layout")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val DIRECT_CALL_ENABLED = booleanPreferencesKey("direct_call_enabled")
        val HUB_ORDER = stringPreferencesKey("hub_order")
        val START_TILES = stringPreferencesKey("start_tiles_v1")
        val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")
        val DYNAMIC_THEME_COLOR = intPreferencesKey("dynamic_theme_color")
        val CUSTOM_THEME_COLOR = intPreferencesKey("custom_theme_color")
        val SOLID_BACKGROUND_ENABLED = booleanPreferencesKey("solid_background_enabled")
        val BROWSER_FAVORITES = stringPreferencesKey("browser_favorites")
        val BROWSER_HISTORY = stringPreferencesKey("browser_history")
        val BROWSER_DOWNLOADS = stringPreferencesKey("browser_downloads")
        val NOTIFICATION_STYLE = stringPreferencesKey("notification_style")
        val VOLUME_BAR_STYLE = stringPreferencesKey("volume_bar_style")
        val DISABLED_NOTIFICATION_APPS = stringPreferencesKey("disabled_notification_apps")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val HUB_BACKGROUND_MODE = stringPreferencesKey("hub_background_mode")
        val CUSTOM_HUB_WALLPAPER_PATH = stringPreferencesKey("custom_hub_wallpaper_path")
        val HUB_BACKGROUND_OPACITY = floatPreferencesKey("hub_background_opacity")
        val TILE_CORNER_STYLE = stringPreferencesKey("tile_corner_style")
        val TILE_SPACING = intPreferencesKey("tile_spacing")
        val HOME_SCREEN_LAYOUT = stringPreferencesKey("home_screen_layout")
        val TILE_COLUMNS = intPreferencesKey("tile_columns")
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

    val homeScreenLayout: Flow<HomeScreenLayout> = context.settingsDataStore.data.map { prefs ->
        try {
            HomeScreenLayout.valueOf(prefs[HOME_SCREEN_LAYOUT] ?: HomeScreenLayout.ZUNE.name)
        } catch (e: Exception) {
            HomeScreenLayout.ZUNE
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

    val hubOrder: Flow<List<com.serkantkn.zunelauncher.data.model.HubType>> = flow {
        migrateLegacyListsIfNeeded()
        emitAll(context.settingsDataStore.data.map { prefs -> readHubOrder(prefs[HUB_ORDER]) })
    }

    /** JSON array of HubType names, or the legacy "A,B,C" string; unknown names are skipped. */
    private fun readHubOrder(saved: String?): List<com.serkantkn.zunelauncher.data.model.HubType> {
        val defaultOrder = listOf(
            com.serkantkn.zunelauncher.data.model.HubType.PHONE,
            com.serkantkn.zunelauncher.data.model.HubType.MESSAGING,
            com.serkantkn.zunelauncher.data.model.HubType.PEOPLE,
            com.serkantkn.zunelauncher.data.model.HubType.INTERNET,
            com.serkantkn.zunelauncher.data.model.HubType.PICTURES,
            com.serkantkn.zunelauncher.data.model.HubType.MUSIC,
            com.serkantkn.zunelauncher.data.model.HubType.FILES,
            com.serkantkn.zunelauncher.data.model.HubType.CLOCK,
            com.serkantkn.zunelauncher.data.model.HubType.CALENDAR,
            com.serkantkn.zunelauncher.data.model.HubType.NOTES,
            com.serkantkn.zunelauncher.data.model.HubType.EMAIL,
            com.serkantkn.zunelauncher.data.model.HubType.SETTINGS
        )
        if (saved.isNullOrEmpty()) return defaultOrder
        val names = if (saved.looksLikeJsonArray()) parseJsonStringList(saved, TAG) else saved.split(",")
        val savedList = names.mapNotNull { name ->
            try {
                com.serkantkn.zunelauncher.data.model.HubType.valueOf(name)
            } catch (e: IllegalArgumentException) {
                ZuneLog.w(TAG, "skipping unknown hub in hub order: $name")
                null
            }
        }
        val missingHubs = defaultOrder.filter { it !in savedList && it != com.serkantkn.zunelauncher.data.model.HubType.HOME }
        return (savedList + missingHubs).ifEmpty { defaultOrder }
    }

    val defaultStartTiles = listOf(
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.PHONE, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.MESSAGING, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.PEOPLE, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.INTERNET, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.PICTURES, 4),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.MUSIC, 4),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.FILES, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.CLOCK, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.CALENDAR, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.NOTES, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.EMAIL, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.SETTINGS, 2)
    )

    val startTiles: Flow<List<StartTileItem>> = flow {
        migrateLegacyListsIfNeeded()
        emitAll(context.settingsDataStore.data.map { prefs -> readStartTiles(prefs[START_TILES]) })
    }

    /** JSON array of [StartTileItem.toJson] objects, or the legacy "id#span,..." string. */
    private fun readStartTiles(saved: String?): List<StartTileItem> {
        if (saved.isNullOrEmpty()) return defaultStartTiles
        val list = if (saved.looksLikeJsonArray()) {
            parseJsonObjectList(saved, TAG, StartTileItem::fromJson)
        } else {
            saved.split(",").mapNotNull { StartTileItem.fromLegacyString(it) }
        }
        val existingHubs = list.filter { it.isHub }.mapNotNull { it.hubType }.toSet()
        val missingHubs = defaultStartTiles.filter { it.isHub && it.hubType !in existingHubs }
        return (list + missingHubs).ifEmpty { defaultStartTiles }
    }

    val customWallpaperPath: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[CUSTOM_WALLPAPER_PATH]
    }

    val customHubWallpaperPath: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[CUSTOM_HUB_WALLPAPER_PATH]
    }

    val hubBackgroundMode: Flow<HubBackgroundMode> = context.settingsDataStore.data.map { prefs ->
        try {
            HubBackgroundMode.valueOf(prefs[HUB_BACKGROUND_MODE] ?: HubBackgroundMode.MATCH_LAUNCHER.name)
        } catch (e: Exception) {
            HubBackgroundMode.MATCH_LAUNCHER
        }
    }

    val hubBackgroundOpacity: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[HUB_BACKGROUND_OPACITY] ?: 0.85f
    }

    val tileCornerStyle: Flow<TileCornerStyle> = context.settingsDataStore.data.map { prefs ->
        try {
            TileCornerStyle.valueOf(prefs[TILE_CORNER_STYLE] ?: TileCornerStyle.ROUNDED.name)
        } catch (e: Exception) {
            TileCornerStyle.ROUNDED
        }
    }

    val tileSpacing: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[TILE_SPACING] ?: 2
    }

    val tileColumns: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[TILE_COLUMNS] ?: 4
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

    val browserDownloads: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[BROWSER_DOWNLOADS]
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

    val disabledNotificationApps: Flow<Set<String>> = flow {
        migrateLegacyListsIfNeeded()
        emitAll(context.settingsDataStore.data.map { prefs -> readDisabledNotificationApps(prefs[DISABLED_NOTIFICATION_APPS]) })
    }

    /** JSON array of package names, or the legacy "pkg1,pkg2" string. */
    private fun readDisabledNotificationApps(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        val names = if (raw.looksLikeJsonArray()) parseJsonStringList(raw, TAG) else raw.split(",")
        return LinkedHashSet(names.filter { it.isNotBlank() })
    }

    @Volatile
    private var legacyListsMigrated = false

    /**
     * Rewrites the comma-delimited [HUB_ORDER], [START_TILES] and [DISABLED_NOTIFICATION_APPS]
     * values as JSON once per process, so the legacy parsers above are only a fallback.
     */
    private suspend fun migrateLegacyListsIfNeeded() {
        if (legacyListsMigrated) return
        try {
            val prefs = context.settingsDataStore.data.first()
            val needsMigration = listOf(HUB_ORDER, START_TILES, DISABLED_NOTIFICATION_APPS).any { key ->
                val value = prefs[key]
                !value.isNullOrEmpty() && !value.looksLikeJsonArray()
            }
            if (needsMigration) {
                context.settingsDataStore.edit { mutable ->
                    mutable[HUB_ORDER]?.let { raw ->
                        if (raw.isNotEmpty() && !raw.looksLikeJsonArray()) {
                            mutable[HUB_ORDER] = raw.split(",").filter { it.isNotBlank() }.toJsonStringArray()
                        }
                    }
                    mutable[START_TILES]?.let { raw ->
                        if (raw.isNotEmpty() && !raw.looksLikeJsonArray()) {
                            mutable[START_TILES] = raw.split(",")
                                .mapNotNull { StartTileItem.fromLegacyString(it) }
                                .toJsonArrayString { it.toJson() }
                        }
                    }
                    mutable[DISABLED_NOTIFICATION_APPS]?.let { raw ->
                        if (raw.isNotEmpty() && !raw.looksLikeJsonArray()) {
                            mutable[DISABLED_NOTIFICATION_APPS] = readDisabledNotificationApps(raw).toJsonStringArray()
                        }
                    }
                }
                ZuneLog.d(TAG, "migrated legacy delimited settings lists to json")
            }
            legacyListsMigrated = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ZuneLog.w(TAG, "legacy settings list migration failed; legacy values kept as fallback", e)
        }
    }

    val timeFormat: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[TIME_FORMAT] ?: "HH:mm"
    }

    val dateFormat: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[DATE_FORMAT] ?: "EEEE, MMMM d"
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
            prefs[HUB_ORDER] = order.map { it.name }.toJsonStringArray()
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

    suspend fun setCustomHubWallpaperPath(path: String?) {
        context.settingsDataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(CUSTOM_HUB_WALLPAPER_PATH)
            } else {
                prefs[CUSTOM_HUB_WALLPAPER_PATH] = path
            }
        }
    }

    suspend fun setHubBackgroundMode(mode: HubBackgroundMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[HUB_BACKGROUND_MODE] = mode.name
        }
    }

    suspend fun setHubBackgroundOpacity(opacity: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[HUB_BACKGROUND_OPACITY] = opacity
        }
    }

    suspend fun setTileCornerStyle(style: TileCornerStyle) {
        context.settingsDataStore.edit { prefs ->
            prefs[TILE_CORNER_STYLE] = style.name
        }
    }

    suspend fun setTileSpacing(spacing: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[TILE_SPACING] = spacing
        }
    }

    suspend fun setHomeScreenLayout(layout: HomeScreenLayout) {
        context.settingsDataStore.edit { prefs ->
            prefs[HOME_SCREEN_LAYOUT] = layout.name
        }
    }

    suspend fun setTileColumns(columns: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[TILE_COLUMNS] = columns
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

    suspend fun setBrowserDownloads(json: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[BROWSER_DOWNLOADS] = json
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
            prefs[DISABLED_NOTIFICATION_APPS] = apps.toJsonStringArray()
        }
    }

    suspend fun setTimeFormat(format: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[TIME_FORMAT] = format
        }
    }

    suspend fun setDateFormat(format: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[DATE_FORMAT] = format
        }
    }

    suspend fun setStartTiles(tiles: List<StartTileItem>) {
        context.settingsDataStore.edit { prefs ->
            prefs[START_TILES] = tiles.toJsonArrayString { it.toJson() }
        }
    }
}
