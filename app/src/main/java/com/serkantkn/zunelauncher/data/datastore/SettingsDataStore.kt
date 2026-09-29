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
import com.serkantkn.zunelauncher.data.model.StartFolders
import com.serkantkn.zunelauncher.data.model.SearchEngine
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.data.model.TileIconStyle
import com.serkantkn.zunelauncher.data.model.TileAnimation
import com.serkantkn.zunelauncher.data.model.TileInk
import com.serkantkn.zunelauncher.data.model.TileLook
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

        /** Windows Phone 8.1's own translucency, and what a fresh install starts with. */
        const val DEFAULT_TILE_OPACITY = 45
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val TASK_SWITCHER_VISIBLE = booleanPreferencesKey("task_switcher_visible")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val DIRECT_CALL_ENABLED = booleanPreferencesKey("direct_call_enabled")
        val HUB_ORDER = stringPreferencesKey("hub_order")
        val REMOVED_HUBS = stringPreferencesKey("removed_hubs")
        val START_TILES = stringPreferencesKey("start_tiles_v1")
        val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")
        val DYNAMIC_THEME_COLOR = intPreferencesKey("dynamic_theme_color")
        val CUSTOM_THEME_COLOR = intPreferencesKey("custom_theme_color")
        val SOLID_BACKGROUND_ENABLED = booleanPreferencesKey("solid_background_enabled")
        val BROWSER_FAVORITES = stringPreferencesKey("browser_favorites")
        val BROWSER_HISTORY = stringPreferencesKey("browser_history")
        val SEARCH_ENGINE = stringPreferencesKey("browser_search_engine")
        val SEARCH_SUGGESTIONS = booleanPreferencesKey("browser_search_suggestions")
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
        val TILE_OPACITY = intPreferencesKey("tile_opacity")
        val TILE_ANIMATION = stringPreferencesKey("tile_animation")
        val TILE_INK = stringPreferencesKey("tile_ink")
        val TILE_SPACING = intPreferencesKey("tile_spacing")
        val HOME_SCREEN_LAYOUT = stringPreferencesKey("home_screen_layout")
        val TILE_ICON_STYLE = stringPreferencesKey("tile_icon_style")
        val ICON_PACK_PACKAGE = stringPreferencesKey("icon_pack_package")
        val TILE_ICON_OVERRIDES = stringPreferencesKey("tile_icon_overrides")
        val TILE_LOOKS = stringPreferencesKey("tile_looks")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val SOCIAL_SOURCES = stringPreferencesKey("social_sources")
        val SOCIAL_HUB_LAYOUT = stringPreferencesKey("social_hub_layout")
        val ALARM_SNOOZE_MINUTES = intPreferencesKey("alarm_snooze_minutes")
        val ALARM_AUTO_SILENCE_MINUTES = intPreferencesKey("alarm_auto_silence_minutes")
        val CLOCK_SHOW_SECONDS = booleanPreferencesKey("clock_show_seconds")
        val FILES_SORT = stringPreferencesKey("files_sort")
        val FILES_SORT_ASCENDING = booleanPreferencesKey("files_sort_ascending")
        val FILES_SHOW_HIDDEN = booleanPreferencesKey("files_show_hidden")
        val GALLERY_COLUMNS = intPreferencesKey("gallery_columns")
        val MUSIC_ONLINE_EXTRAS = booleanPreferencesKey("music_online_extras")
        val MUSIC_ONLINE_WIFI_ONLY = booleanPreferencesKey("music_online_wifi_only")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_SEEN_VERSION = intPreferencesKey("last_seen_version")
        val ONBOARDING_STARTED = booleanPreferencesKey("onboarding_started")
        val ONBOARDING_STEP = intPreferencesKey("onboarding_step")
    }

    /** Windows Phone tiles carried a white glyph and nothing else, so that is the default. */
    val tileIconStyle: Flow<TileIconStyle> = context.settingsDataStore.data.map { prefs ->
        try {
            TileIconStyle.valueOf(prefs[TILE_ICON_STYLE] ?: TileIconStyle.WINDOWS_PHONE.name)
        } catch (e: Exception) {
            TileIconStyle.WINDOWS_PHONE
        }
    }

    /** The installed icon pack tiles draw from, or null for none. */
    val iconPackPackage: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[ICON_PACK_PACKAGE]?.takeIf { it.isNotBlank() }
    }

    /** Icons the user picked by hand, by package name. */
    val tileIconOverrides: Flow<Map<String, TileIcon>> = context.settingsDataStore.data.map { prefs ->
        TileIcon.mapFromJson(prefs[TILE_ICON_OVERRIDES])
    }

    /** What single tiles have been told to look like, by tile id. Tiles left alone are absent. */
    val tileLooks: Flow<Map<String, TileLook>> = context.settingsDataStore.data.map { prefs ->
        TileLook.mapFromJson(prefs[TILE_LOOKS])
    }

    /** Stores one tile's look; a default look drops the entry, which is what a reset is. */
    suspend fun setTileLook(tileId: String, look: TileLook) {
        context.settingsDataStore.edit { prefs ->
            val current = TileLook.mapFromJson(prefs[TILE_LOOKS]).toMutableMap()
            if (look.isDefault) current.remove(tileId) else current[tileId] = look
            prefs[TILE_LOOKS] = TileLook.mapToJson(current)
        }
    }

    // ── The app list ──

    /**
     * Apps the user has taken out of the list. They are still installed and still launchable from
     * anywhere else; the list simply stops offering them.
     */
    val hiddenApps: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[HIDDEN_APPS]
        if (raw.isNullOrBlank()) emptySet()
        else LinkedHashSet(parseJsonStringList(raw, TAG).filter { it.isNotBlank() })
    }

    suspend fun setAppHidden(packageName: String, hidden: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[HIDDEN_APPS]
                ?.takeIf { it.isNotBlank() }
                ?.let { LinkedHashSet(parseJsonStringList(it, TAG)) }
                ?: LinkedHashSet()
            if (hidden) current.add(packageName) else current.remove(packageName)
            prefs[HIDDEN_APPS] = current.toList().toJsonStringArray()
        }
    }

    suspend fun clearHiddenApps() {
        context.settingsDataStore.edit { prefs -> prefs[HIDDEN_APPS] = emptyList<String>().toJsonStringArray() }
    }

    suspend fun setTileIconStyle(style: TileIconStyle) {
        context.settingsDataStore.edit { prefs -> prefs[TILE_ICON_STYLE] = style.name }
    }

    suspend fun setIconPackPackage(packageName: String?) {
        context.settingsDataStore.edit { prefs ->
            prefs[ICON_PACK_PACKAGE] = packageName?.takeIf { it.isNotBlank() } ?: ""
        }
    }

    /** Sets one app's icon, or clears it back to the launcher's own choice with [TileIcon.Default]. */
    suspend fun setTileIconOverride(packageName: String, icon: TileIcon) {
        context.settingsDataStore.edit { prefs ->
            val current = TileIcon.mapFromJson(prefs[TILE_ICON_OVERRIDES]).toMutableMap()
            if (icon == TileIcon.Default) current.remove(packageName) else current[packageName] = icon
            prefs[TILE_ICON_OVERRIDES] = TileIcon.mapToJson(current)
        }
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

    /**
     * Whether the running-hubs strip is shown at the foot of Start. Turning it off hides the list,
     * not the hubs: they go on running and their own tiles still open them where they stood.
     */
    val taskSwitcherVisible: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[TASK_SWITCHER_VISIBLE] ?: true
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

    /**
     * The hubs the user has taken off the home screen.
     *
     * Both lists fill themselves in with any hub they are missing, so that a hub added in a new
     * version turns up on a board that was saved before it existed. That back-fill is also what
     * used to undo a removal on the very next read, which is why a removed hub has to be
     * remembered by name rather than by its absence.
     */
    val removedHubs: Flow<Set<com.serkantkn.zunelauncher.data.model.HubType>> =
        context.settingsDataStore.data.map { prefs -> readRemovedHubs(prefs[REMOVED_HUBS]) }

    private fun readRemovedHubs(saved: String?): Set<com.serkantkn.zunelauncher.data.model.HubType> {
        if (saved.isNullOrEmpty()) return emptySet()
        val names = if (saved.looksLikeJsonArray()) parseJsonStringList(saved, TAG) else saved.split(",")
        return names.mapNotNull { name ->
            try {
                com.serkantkn.zunelauncher.data.model.HubType.valueOf(name)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toSet()
    }

    val hubOrder: Flow<List<com.serkantkn.zunelauncher.data.model.HubType>> = flow {
        migrateLegacyListsIfNeeded()
        emitAll(context.settingsDataStore.data.map { prefs ->
            val removed = readRemovedHubs(prefs[REMOVED_HUBS])
            readHubOrder(prefs[HUB_ORDER]).filterNot { it in removed }
        })
    }

    /** JSON array of HubType names, or the legacy "A,B,C" string; unknown names are skipped. */
    private fun readHubOrder(saved: String?): List<com.serkantkn.zunelauncher.data.model.HubType> {
        val defaultOrder = listOf(
            com.serkantkn.zunelauncher.data.model.HubType.PHONE,
            com.serkantkn.zunelauncher.data.model.HubType.MESSAGING,
            com.serkantkn.zunelauncher.data.model.HubType.PEOPLE,
            com.serkantkn.zunelauncher.data.model.HubType.INTERNET,
            com.serkantkn.zunelauncher.data.model.HubType.PICTURES,
            com.serkantkn.zunelauncher.data.model.HubType.CAMERA,
            com.serkantkn.zunelauncher.data.model.HubType.MUSIC,
            com.serkantkn.zunelauncher.data.model.HubType.FILES,
            com.serkantkn.zunelauncher.data.model.HubType.CLOCK,
            com.serkantkn.zunelauncher.data.model.HubType.CALENDAR,
            com.serkantkn.zunelauncher.data.model.HubType.NOTES,
            com.serkantkn.zunelauncher.data.model.HubType.EMAIL,
            com.serkantkn.zunelauncher.data.model.HubType.CALCULATOR,
            com.serkantkn.zunelauncher.data.model.HubType.WEATHER,
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
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.CAMERA, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.MUSIC, 4),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.FILES, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.CLOCK, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.CALENDAR, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.NOTES, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.EMAIL, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.CALCULATOR, 2),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.WEATHER, 4),
        StartTileItem.fromHub(com.serkantkn.zunelauncher.data.model.HubType.SETTINGS, 2)
    )

    val startTiles: Flow<List<StartTileItem>> = flow {
        migrateLegacyListsIfNeeded()
        emitAll(context.settingsDataStore.data.map { prefs ->
            readStartTiles(prefs[START_TILES], readRemovedHubs(prefs[REMOVED_HUBS]))
        })
    }

    /** JSON array of [StartTileItem.toJson] objects, or the legacy "id#span,..." string. */
    private fun readStartTiles(
        saved: String?,
        removed: Set<com.serkantkn.zunelauncher.data.model.HubType> = emptySet()
    ): List<StartTileItem> {
        fun List<StartTileItem>.withoutRemovedHubs() =
            filterNot { it.isHub && it.hubType in removed }
        if (saved.isNullOrEmpty()) return defaultStartTiles.withoutRemovedHubs()
        val list = if (saved.looksLikeJsonArray()) {
            parseJsonObjectList(saved, TAG, StartTileItem::fromJson)
        } else {
            saved.split(",").mapNotNull { StartTileItem.fromLegacyString(it) }
        }
        // A hub the user has filed away in a folder is not missing, so folders are counted too.
        val existingHubs = StartFolders.allTileIds(list)
            .mapNotNull { StartTileItem(it).hubType }
            .toSet()
        val missingHubs = defaultStartTiles.filter {
            it.isHub && it.hubType !in existingHubs && it.hubType !in removed
        }
        return (list.withoutRemovedHubs() + missingHubs)
            .ifEmpty { defaultStartTiles.withoutRemovedHubs() }
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

    /**
     * How solid the Start tiles are, 0-100. The wallpaper shows through everything below 100;
     * the default matches the translucent look Windows Phone 8.1 tiles had.
     */
    val tileOpacity: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[TILE_OPACITY] ?: DEFAULT_TILE_OPACITY).coerceIn(0, 100)
    }

    /** Whether the tiles write in black, in white, or in whatever suits the theme. */
    val tileInk: Flow<TileInk> = context.settingsDataStore.data.map { prefs ->
        try {
            TileInk.valueOf(prefs[TILE_INK] ?: TileInk.AUTO.name)
        } catch (e: IllegalArgumentException) {
            ZuneLog.w(TAG, "unknown tile ink, letting the theme decide", e)
            TileInk.AUTO
        }
    }

    /** Motion the live tiles use when they turn to their back face. */
    val tileAnimation: Flow<TileAnimation> = context.settingsDataStore.data.map { prefs ->
        try {
            TileAnimation.valueOf(prefs[TILE_ANIMATION] ?: TileAnimation.SLIDE.name)
        } catch (e: IllegalArgumentException) {
            ZuneLog.w(TAG, "unknown tile animation, falling back to slide", e)
            TileAnimation.SLIDE
        }
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

    /**
     * Where browsing history used to be kept. It lives in BrowsingDataStore now, out of the
     * file that gets backed up; this is only here so what was written before can be moved.
     */
    val browserHistory: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[BROWSER_HISTORY]
    }

    /** Where a typed search goes, and whether the engine is asked for suggestions as you type. */
    val searchEngine: Flow<SearchEngine> = context.settingsDataStore.data.map { prefs ->
        SearchEngine.fromName(prefs[SEARCH_ENGINE])
    }

    val searchSuggestionsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SEARCH_SUGGESTIONS] ?: true
    }

    /** As [browserHistory]: kept only so what was written before the split can be moved. */
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

    // ── Pictures ────────────────────────────────────────────────────────────

    /** How many pictures fit across the grid; zero means "work it out from how many there are". */
    val galleryColumns: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[GALLERY_COLUMNS] ?: 0
    }

    suspend fun setGalleryColumns(columns: Int) {
        context.settingsDataStore.edit { prefs -> prefs[GALLERY_COLUMNS] = columns }
    }

    /**
     * Whether the music hub may look up covers and lyrics it cannot find on the phone.
     *
     * Off by default. It sends what is playing to a service outside the phone, which is somebody's
     * decision to make rather than something to switch on for them.
     */
    val musicOnlineExtras: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[MUSIC_ONLINE_EXTRAS] ?: false
    }

    suspend fun setMusicOnlineExtras(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[MUSIC_ONLINE_EXTRAS] = enabled }
    }

    /** Whether those lookups wait for Wi-Fi. On by default, since covers are not small. */
    val musicOnlineWifiOnly: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[MUSIC_ONLINE_WIFI_ONLY] ?: true
    }

    suspend fun setMusicOnlineWifiOnly(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[MUSIC_ONLINE_WIFI_ONLY] = enabled }
    }

    // ── First run ───────────────────────────────────────────────────────────

    /** Whether the tour has been taken. */
    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = completed }
    }

    /**
     * Whether the tour has been begun but not finished.
     *
     * Making the launcher the default home app restarts it, and the tour has by then written a
     * setting or two; without this it would look like a used installation and be abandoned midway.
     */
    val onboardingStarted: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ONBOARDING_STARTED] ?: false
    }

    suspend fun setOnboardingStarted(started: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[ONBOARDING_STARTED] = started }
    }

    /** Which page the tour was on, so a restart picks it up rather than starting over. */
    val onboardingStep: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[ONBOARDING_STEP] ?: 0
    }

    suspend fun setOnboardingStep(index: Int) {
        context.settingsDataStore.edit { prefs -> prefs[ONBOARDING_STEP] = index }
    }

    /** The newest version whose changes have been read. */
    val lastSeenVersion: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[LAST_SEEN_VERSION] ?: 0
    }

    suspend fun setLastSeenVersion(version: Int) {
        context.settingsDataStore.edit { prefs -> prefs[LAST_SEEN_VERSION] = version }
    }

    /**
     * Whether this installation has ever been used.
     *
     * An empty settings store means a launcher opened for the very first time. Anything at all in
     * it - a moved tile, a chosen colour - means somebody has been here, which is how an
     * installation that predates the tour is told apart from a new one. It is read once, before
     * anything else has had a chance to write a default.
     */
    suspend fun hasExistingData(): Boolean =
        context.settingsDataStore.data.first().asMap().isNotEmpty()

    // ── Clock ───────────────────────────────────────────────────────────────

    /**
     * What a new alarm starts out with. Each alarm keeps its own copy once it is made, so changing
     * this never quietly rewrites an alarm somebody set deliberately.
     */
    val alarmSnoozeMinutes: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[ALARM_SNOOZE_MINUTES] ?: com.serkantkn.zunelauncher.data.model.Alarm.DEFAULT_SNOOZE_MINUTES
    }

    suspend fun setAlarmSnoozeMinutes(minutes: Int) {
        context.settingsDataStore.edit { prefs -> prefs[ALARM_SNOOZE_MINUTES] = minutes }
    }

    /** How long a new alarm rings for before giving up. Zero means it never gives up. */
    val alarmAutoSilenceMinutes: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[ALARM_AUTO_SILENCE_MINUTES] ?: com.serkantkn.zunelauncher.data.model.Alarm.DEFAULT_AUTO_SILENCE_MINUTES
    }

    suspend fun setAlarmAutoSilenceMinutes(minutes: Int) {
        context.settingsDataStore.edit { prefs -> prefs[ALARM_AUTO_SILENCE_MINUTES] = minutes }
    }

    /** Whether the Clock hub's own big clock counts seconds as well as minutes. */
    val clockShowSeconds: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[CLOCK_SHOW_SECONDS] ?: true
    }

    suspend fun setClockShowSeconds(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[CLOCK_SHOW_SECONDS] = enabled }
    }

    // ── Files ───────────────────────────────────────────────────────────────

    /** How a folder's contents are ordered, remembered between visits. */
    val filesSort: Flow<com.serkantkn.zunelauncher.util.FileSort> = context.settingsDataStore.data.map { prefs ->
        runCatching {
            com.serkantkn.zunelauncher.util.FileSort.valueOf(
                prefs[FILES_SORT] ?: com.serkantkn.zunelauncher.util.FileSort.NAME.name
            )
        }.getOrDefault(com.serkantkn.zunelauncher.util.FileSort.NAME)
    }

    suspend fun setFilesSort(sort: com.serkantkn.zunelauncher.util.FileSort) {
        context.settingsDataStore.edit { prefs -> prefs[FILES_SORT] = sort.name }
    }

    val filesSortAscending: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[FILES_SORT_ASCENDING] ?: true
    }

    suspend fun setFilesSortAscending(ascending: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[FILES_SORT_ASCENDING] = ascending }
    }

    /** Whether dot-files and Android's own bookkeeping folders are listed. */
    val filesShowHidden: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[FILES_SHOW_HIDDEN] ?: false
    }

    suspend fun setFilesShowHidden(show: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[FILES_SHOW_HIDDEN] = show }
    }

    val socialHubLayout: Flow<SocialHubLayout> = context.settingsDataStore.data.map { prefs ->
        runCatching { SocialHubLayout.valueOf(prefs[SOCIAL_HUB_LAYOUT] ?: SocialHubLayout.TIMELINE.name) }
            .getOrDefault(SocialHubLayout.TIMELINE)
    }

    suspend fun setSocialHubLayout(layout: SocialHubLayout) {
        context.settingsDataStore.edit { prefs -> prefs[SOCIAL_HUB_LAYOUT] = layout.name }
    }

    /**
     * The apps whose notifications the Social hub collects.
     *
     * Null means nobody has chosen yet, and the hub should switch itself on for whichever social
     * apps are installed. An empty set is a choice like any other: the user turned everything off.
     */
    val socialSources: Flow<Set<String>?> = flow {
        migrateLegacyListsIfNeeded()
        emitAll(
            context.settingsDataStore.data.map { prefs ->
                prefs[SOCIAL_SOURCES]?.let { raw -> LinkedHashSet(parseJsonStringList(raw, TAG)) }
            }
        )
    }

    suspend fun setSocialSources(packages: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[SOCIAL_SOURCES] = packages.toList().toJsonStringArray()
        }
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

    suspend fun setTaskSwitcherVisible(visible: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[TASK_SWITCHER_VISIBLE] = visible
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

    suspend fun setHubRemoved(hub: com.serkantkn.zunelauncher.data.model.HubType, removed: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val current = readRemovedHubs(prefs[REMOVED_HUBS]).toMutableSet()
            if (removed) current.add(hub) else current.remove(hub)
            prefs[REMOVED_HUBS] = current.map { it.name }.toJsonStringArray()
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

    suspend fun setTileOpacity(opacity: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[TILE_OPACITY] = opacity.coerceIn(0, 100)
        }
    }

    suspend fun setTileInk(ink: TileInk) {
        context.settingsDataStore.edit { prefs ->
            prefs[TILE_INK] = ink.name
        }
    }

    suspend fun setTileAnimation(animation: TileAnimation) {
        context.settingsDataStore.edit { prefs ->
            prefs[TILE_ANIMATION] = animation.name
        }
    }

    suspend fun setHomeScreenLayout(layout: HomeScreenLayout) {
        context.settingsDataStore.edit { prefs ->
            prefs[HOME_SCREEN_LAYOUT] = layout.name
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

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.settingsDataStore.edit { prefs -> prefs[SEARCH_ENGINE] = engine.name }
    }

    suspend fun setSearchSuggestionsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[SEARCH_SUGGESTIONS] = enabled }
    }

    /** Removes what BrowsingDataStore has taken over, so no copy is left in the backed-up file. */
    suspend fun clearLegacyBrowsingKeys() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(BROWSER_HISTORY)
            prefs.remove(BROWSER_DOWNLOADS)
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
