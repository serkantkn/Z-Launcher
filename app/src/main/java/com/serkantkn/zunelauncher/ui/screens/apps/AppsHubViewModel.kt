package com.serkantkn.zunelauncher.ui.screens.apps

import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.domain.usecase.SearchAppsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppsHubViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = application.appContainer.appRepository
    private val searchAppsUseCase = SearchAppsUseCase()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Apps grouped by first letter, filtered by search query.
     * Non-letter characters are grouped under '#'.
     */
    val groupedApps: StateFlow<Map<Char, List<AppInfo>>> = combine(
        _allApps,
        _searchQuery
    ) { apps, query ->
        val filtered = searchAppsUseCase(apps, query)
        filtered.groupBy {
            val firstChar = it.label.firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }.toSortedMap()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    /** Set of letters that have at least one app, for the alphabet index. */
    val availableLetters: StateFlow<Set<Char>> = groupedApps.map { grouped ->
        grouped.keys.toSet()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    /** Set of package names that are currently favorited. */
    val favoritePackages: StateFlow<Set<String>> = appRepository.getFavoritePackages()
        .map { list -> list.map { it.packageName }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            appRepository.getInstalledApps().collect { apps ->
                _allApps.value = apps
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun launchApp(packageName: String) {
        appRepository.launchApp(packageName)
    }

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch {
            appRepository.toggleFavorite(packageName)
        }
    }

    fun getAppIcon(packageName: String): Drawable? = appRepository.getAppIcon(packageName)
}
