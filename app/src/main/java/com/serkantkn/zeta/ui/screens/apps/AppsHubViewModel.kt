package com.serkantkn.zeta.ui.screens.apps

import android.app.Application
import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.AppInfo
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.util.AppUsage
import com.serkantkn.zeta.util.rankApps
import com.serkantkn.zeta.util.sectionLetterOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class AppsHubViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = application.appContainer.appRepository
    private val iconPacks = application.appContainer.iconPackRepository
    private val settingsDataStore = application.appContainer.settingsDataStore

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Apps taken out of the list by the user. Still installed, just not offered here. */
    val hiddenApps: StateFlow<Set<String>> = settingsDataStore.hiddenApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** While this is on, hidden apps are shown so they can be put back. */
    private val _showingHidden = MutableStateFlow(false)
    val showingHidden: StateFlow<Boolean> = _showingHidden.asStateFlow()

    /** The icon pack the launcher is dressed in, so the list matches the Start board. */
    private val iconPackPackage: StateFlow<String?> = settingsDataStore.iconPackPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Everything the list may show, hidden apps taken out unless they are being looked at. */
    private val visibleApps: StateFlow<List<AppInfo>> = combine(
        _allApps,
        hiddenApps,
        _showingHidden
    ) { apps, hidden, showingHidden ->
        when {
            hidden.isEmpty() -> apps
            showingHidden -> apps.filter { it.packageName in hidden }
            else -> apps.filterNot { it.packageName in hidden }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Apps by first letter. Turkish keeps Ç and Ş as their own headings, as its alphabet has them;
     * anything that does not start with a letter files under "#".
     */
    val groupedApps: StateFlow<Map<Char, List<AppInfo>>> = visibleApps.map { apps ->
        val locale = Locale.getDefault()
        apps.groupBy { sectionLetterOf(it.label, locale) }
            .toSortedMap(compareBy(java.text.Collator.getInstance(locale)) { it.toString() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Letters that have at least one app, for the jump list. */
    val availableLetters: StateFlow<Set<Char>> = groupedApps
        .map { it.keys.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** What the search box has turned up, best match first. Empty when nobody is searching. */
    val searchResults: StateFlow<List<AppInfo>> = combine(
        visibleApps,
        _searchQuery
    ) { apps, query -> rankApps(apps, query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Time in front, by package, or empty until the phone is allowed to say. */
    private val _usage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val usage: StateFlow<Map<String, Long>> = _usage.asStateFlow()

    private val _usageGranted = MutableStateFlow(false)
    val usageGranted: StateFlow<Boolean> = _usageGranted.asStateFlow()

    val frequentApps: StateFlow<List<AppInfo>> = combine(visibleApps, _usage) { apps, usage ->
        if (usage.isEmpty()) {
            emptyList()
        } else {
            apps.filter { usage.containsKey(it.packageName) }
                .sortedByDescending { usage[it.packageName] ?: 0L }
                .take(FREQUENT_LIMIT)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Packages pinned to the Start screen. */
    val favoritePackages: StateFlow<Set<String>> = appRepository.getFavoritePackages()
        .map { list -> list.map { it.packageName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** The one collector of the installed list; restarting it cancels the old one first. */
    private var appsJob: Job? = null

    init {
        loadApps()
    }

    private fun loadApps() {
        // One receiver, one collector: a refresh used to start a second of each and leave the
        // first running, so every return to the hub registered another package receiver.
        appsJob?.cancel()
        appsJob = viewModelScope.launch {
            // Every launcher activity, so a package's second entry is a second row.
            appRepository.getInstalledApps(allActivities = true).collect { apps -> _allApps.value = apps }
        }
    }

    /** Re-reads the installed apps — after one is removed, or the hub is returned to. */
    fun refresh() {
        loadApps()
        refreshUsage()
    }

    fun refreshUsage() {
        val context = getApplication<Application>()
        _usageGranted.value = AppUsage.isGranted(context)
        viewModelScope.launch {
            _usage.value = if (_usageGranted.value) {
                AppUsage.foregroundTimeByPackage(context)
            } else {
                emptyMap()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    /** Opens an app's own entry — the activity the row stands for — and says so when it cannot. */
    fun launchApp(app: AppInfo) {
        val opened = appRepository.launchApp(app.packageName, app.activityName)
        if (!opened) {
            Toast.makeText(getApplication(), R.string.apps_launch_failed, Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch { settingsDataStore.noteAppLaunched(app.packageName) }
    }

    /** The apps pinned to quickplay, which the Zune layout's app list offers to pin or take off. */
    val quickplayApps: kotlinx.coroutines.flow.StateFlow<Set<String>> = settingsDataStore.quickplayPins
        .map { pins ->
            pins.filter { it.kind == com.serkantkn.zeta.data.model.QuickplayPin.Kind.APP }.mapTo(HashSet()) { it.key }
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet<String>())

    fun toggleQuickplayApp(packageName: String, label: String) {
        viewModelScope.launch {
            settingsDataStore.toggleQuickplayPin(
                com.serkantkn.zeta.data.model.QuickplayPin(
                    com.serkantkn.zeta.data.model.QuickplayPin.Kind.APP, packageName, label
                )
            )
        }
    }

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch { appRepository.toggleFavorite(packageName) }
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAppHidden(packageName, hidden)
            // The last app put back closes the hidden view, which would otherwise be empty.
            if (!hidden && hiddenApps.value.size <= 1) _showingHidden.value = false
        }
    }

    fun toggleShowingHidden() {
        _showingHidden.value = !_showingHidden.value
        if (_showingHidden.value) _searchQuery.value = ""
    }

    fun uninstall(packageName: String): Boolean = appRepository.requestUninstall(packageName)

    fun isSystemApp(packageName: String): Boolean = appRepository.isSystemApp(packageName)

    fun openAppSettings(packageName: String): Boolean = appRepository.openAppSettings(packageName)

    fun shareApp(app: AppInfo, chooserTitle: String): Boolean =
        appRepository.shareApp(app.packageName, app.label, chooserTitle)

    fun openUsageAccessSettings(context: Context): Boolean = try {
        context.startActivity(AppUsage.settingsIntent())
        true
    } catch (e: Exception) {
        false
    }

    /**
     * The picture for one app: the icon pack's, when the launcher is wearing one, and otherwise
     * the app's own. Unlike a tile, the list shows it in its own colours — Windows Phone's app
     * list did the same while its tiles carried white glyphs.
     */
    fun iconFor(app: AppInfo): Drawable? {
        val pack = iconPackPackage.value
        if (!pack.isNullOrBlank()) {
            // Never parses here, on the main thread: null until the pack has been read on IO,
            // and the list asks again when iconPackKey ticks.
            iconPacks.iconForIfReady(pack, app.packageName, app.activityName)?.let { return it }
        }
        return appRepository.getAppIcon(app.packageName)
    }

    /**
     * Redraws with a different pack, and again once that pack has been read; the list keys its
     * icons on this so a change lands without a restart and no icon is stuck on the app's own.
     */
    val iconPackKey: StateFlow<String?> = combine(iconPackPackage, iconPacks.ready) { pack, ready ->
        pack?.let { "$it#$ready" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private companion object {
        /** How many of the most-used apps ride at the top of the list. */
        const val FREQUENT_LIMIT = 5
    }
}
