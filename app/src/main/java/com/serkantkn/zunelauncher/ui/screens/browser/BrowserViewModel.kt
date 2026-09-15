package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.util.localizedString
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.data.model.BrowserDownload
import com.serkantkn.zunelauncher.data.model.BrowserFavorite
import com.serkantkn.zunelauncher.data.model.BrowserHistory
import com.serkantkn.zunelauncher.data.model.StartFolders
import com.serkantkn.zunelauncher.data.model.StartTileItem
import com.serkantkn.zunelauncher.data.model.SearchEngine
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BrowserTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val showStartScreen: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** Kept out of the history, and marked as such in the tab list. */
    val isPrivate: Boolean = false,
    /** Asking the site for the version it would send a computer. */
    val isDesktopSite: Boolean = false,
    /** The page stripped back to what it says, Windows Phone's reading view. */
    val isReadingView: Boolean = false
)

data class BrowserSuggestion(
    val displayText: String,
    val subtitle: String? = null,
    val query: String,
    val isLucky: Boolean = false
)

data class BrowserState(
    val tabs: List<BrowserTab> = listOf(BrowserTab()),
    val activeTabIndex: Int = 0,
    val favorites: List<BrowserFavorite> = emptyList(),
    val history: List<BrowserHistory> = emptyList(),
    val downloads: List<BrowserDownload> = emptyList(),
    val showDownloadsScreen: Boolean = false,
    val suggestions: List<BrowserSuggestion> = emptyList(),
    /** Addresses that already have a tile of their own on the start screen. */
    val pinnedSites: Set<String> = emptySet(),
    val searchEngine: SearchEngine = SearchEngine.DEFAULT,
    val suggestionsEnabled: Boolean = true,
    val errorMessage: String? = null
) {
    val activeTab: BrowserTab
        get() = tabs.getOrNull(activeTabIndex) ?: BrowserTab()
}

data class SitePrediction(val queryMatch: String, val name: String, val url: String)

val popularSites = listOf(
    SitePrediction("trendyol", "Trendyol", "https://www.trendyol.com"),
    SitePrediction("facebook", "Facebook", "https://www.facebook.com"),
    SitePrediction("instagram", "Instagram", "https://www.instagram.com"),
    SitePrediction("twitter", "Twitter (X)", "https://twitter.com"),
    SitePrediction("youtube", "YouTube", "https://www.youtube.com"),
    SitePrediction("google", "Google", "https://www.google.com"),
    SitePrediction("hepsiburada", "Hepsiburada", "https://www.hepsiburada.com"),
    SitePrediction("sahibinden", "Sahibinden", "https://www.sahibinden.com"),
    SitePrediction("eksisozluk", "Ekşi Sözlük", "https://eksisozluk.com"),
    SitePrediction("wikipedia", "Wikipedia", "https://www.wikipedia.org"),
    SitePrediction("netflix", "Netflix", "https://www.netflix.com"),
    SitePrediction("spotify", "Spotify", "https://open.spotify.com"),
    SitePrediction("whatsapp", "WhatsApp Web", "https://web.whatsapp.com"),
    SitePrediction("amazon", "Amazon", "https://www.amazon.com.tr"),
    SitePrediction("yemeksepeti", "Yemeksepeti", "https://www.yemeksepeti.com"),
    SitePrediction("getir", "Getir", "https://getir.com"),
    SitePrediction("linkedin", "LinkedIn", "https://www.linkedin.com"),
    SitePrediction("twitch", "Twitch", "https://www.twitch.tv"),
    SitePrediction("github", "GitHub", "https://github.com"),
    SitePrediction("chatgpt", "ChatGPT", "https://chat.openai.com"),
    SitePrediction("reddit", "Reddit", "https://www.reddit.com"),
    SitePrediction("tiktok", "TikTok", "https://www.tiktok.com")
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "BrowserViewModel"

        /** More pages than this alive at once is a memory problem, not a browsing style. */
        const val MAX_TABS = 10

        /** How far back the history goes. */
        const val HISTORY_LIMIT = 300
    }

    private val repository = application.appContainer.settingsDataStore

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.browserFavorites.collect { json ->
                val list = if (json.isNullOrEmpty()) {
                    listOf(
                        BrowserFavorite("Google", "https://www.google.com"),
                        BrowserFavorite("YouTube", "https://www.youtube.com"),
                        BrowserFavorite("Wikipedia", "https://www.wikipedia.org")
                    )
                } else {
                    parseJsonObjectList(json, TAG, BrowserFavorite::fromJson)
                }
                _state.update { it.copy(favorites = list) }
            }
        }
        viewModelScope.launch {
            repository.browserHistory.collect { json ->
                val list = parseJsonObjectList(json, TAG, BrowserHistory::fromJson)
                _state.update { it.copy(history = list) }
            }
        }
        viewModelScope.launch {
            repository.searchEngine.collect { engine ->
                _state.update { it.copy(searchEngine = engine) }
            }
        }
        viewModelScope.launch {
            repository.searchSuggestionsEnabled.collect { enabled ->
                _state.update { it.copy(suggestionsEnabled = enabled) }
                if (!enabled) clearSuggestions()
            }
        }
        viewModelScope.launch {
            repository.startTiles.collect { tiles ->
                _state.update { state ->
                    state.copy(pinnedSites = tiles.mapNotNull { it.webUrl }.toSet())
                }
            }
        }
        viewModelScope.launch {
            repository.browserDownloads.collect { json ->
                val list = parseJsonObjectList(json, TAG, BrowserDownload::fromJson)
                _state.update { it.copy(downloads = list) }
                if (list.any { it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING }) {
                    startDownloadPolling()
                }
            }
        }
    }

    private var downloadPollJob: Job? = null

    private fun startDownloadPolling() {
        if (downloadPollJob?.isActive == true) return
        downloadPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                refreshDownloadsStatus()
                val hasActive = _state.value.downloads.any {
                    it.status == DownloadManager.STATUS_RUNNING ||
                    it.status == DownloadManager.STATUS_PENDING ||
                    it.status == DownloadManager.STATUS_PAUSED
                }
                if (!hasActive && !_state.value.showDownloadsScreen) {
                    break
                }
                delay(500L)
            }
        }
    }

    private fun saveFavorites(newFavorites: List<BrowserFavorite>) {
        viewModelScope.launch {
            repository.setBrowserFavorites(newFavorites.toJsonArrayString { it.toJson() })
        }
    }

    private fun saveHistory(newHistory: List<BrowserHistory>) {
        viewModelScope.launch {
            repository.setBrowserHistory(newHistory.toJsonArrayString { it.toJson() })
        }
    }

    private fun saveDownloads(newDownloads: List<BrowserDownload>) {
        viewModelScope.launch {
            repository.setBrowserDownloads(newDownloads.toJsonArrayString { it.toJson() })
        }
    }

    fun toggleDownloadsScreen(show: Boolean) {
        _state.update { it.copy(showDownloadsScreen = show) }
        if (show) {
            refreshDownloadsStatus()
            startDownloadPolling()
        }
    }

    fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (!mimeType.isNullOrEmpty()) {
                    setMimeType(mimeType)
                }
                if (!userAgent.isNullOrEmpty()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setTitle(fileName)
                setDescription(getApplication<Application>().localizedString(R.string.browser_downloading))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            val newDownload = BrowserDownload(
                id = downloadId,
                fileName = fileName,
                url = url,
                mimeType = mimeType,
                totalBytes = if (contentLength > 0) contentLength else 0L,
                status = DownloadManager.STATUS_RUNNING
            )

            val updatedList = listOf(newDownload) + _state.value.downloads
            _state.update {
                it.copy(
                    downloads = updatedList,
                    errorMessage = getApplication<Application>().localizedString(R.string.browser_download_started, fileName)
                )
            }
            saveDownloads(updatedList)
            startDownloadPolling()
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = getApplication<Application>().localizedString(R.string.browser_download_failed, e.localizedMessage ?: "")) }
        }
    }

    fun refreshDownloadsStatus() {
        val currentDownloads = _state.value.downloads
        if (currentDownloads.isEmpty()) return

        val dm = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
        var changed = false
        val updatedList = currentDownloads.map { download ->
            if (download.status == DownloadManager.STATUS_SUCCESSFUL && download.localUri != null) {
                download
            } else {
                val query = DownloadManager.Query().setFilterById(download.id)
                val cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else download.status
                    val downloadedBytes = if (bytesDownloadedIdx >= 0) cursor.getLong(bytesDownloadedIdx) else download.downloadedBytes
                    val totalBytes = if (bytesTotalIdx >= 0) cursor.getLong(bytesTotalIdx) else download.totalBytes
                    val localUri = if (localUriIdx >= 0) cursor.getString(localUriIdx) ?: download.localUri else download.localUri

                    cursor.close()
                    if (status != download.status || downloadedBytes != download.downloadedBytes || totalBytes != download.totalBytes || localUri != download.localUri) {
                        changed = true
                        download.copy(
                            status = status,
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (totalBytes > 0) totalBytes else download.totalBytes,
                            localUri = localUri
                        )
                    } else {
                        download
                    }
                } else {
                    download
                }
            }
        }
        if (changed) {
            _state.update { it.copy(downloads = updatedList) }
            saveDownloads(updatedList)
        }
    }

    /**
     * Takes a download off the list, and off the phone.
     *
     * Taking it only off the list left it running with no way back to it — the notification was
     * the only thing that still knew about it. A download still going is cancelled; one already
     * finished keeps its file and only loses its row.
     */
    fun removeDownload(downloadId: Long) {
        val download = _state.value.downloads.firstOrNull { it.id == downloadId }
        if (download != null && download.status != DownloadManager.STATUS_SUCCESSFUL) {
            cancelInDownloadManager(downloadId)
        }
        val updatedList = _state.value.downloads.filterNot { it.id == downloadId }
        _state.update { it.copy(downloads = updatedList) }
        saveDownloads(updatedList)
    }

    fun clearAllDownloads() {
        _state.value.downloads
            .filter { it.status != DownloadManager.STATUS_SUCCESSFUL }
            .forEach { cancelInDownloadManager(it.id) }
        _state.update { it.copy(downloads = emptyList()) }
        saveDownloads(emptyList())
    }

    private fun cancelInDownloadManager(downloadId: Long) {
        try {
            val dm = getApplication<Application>()
                .getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            dm?.remove(downloadId)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not stop download $downloadId", e)
        }
    }

    /**
     * Starts a failed or cancelled download over again.
     *
     * Android's DownloadManager has no public way to pause and resume, so the honest offer for a
     * download that did not finish is to run it again from the beginning.
     */
    fun retryDownload(download: BrowserDownload) {
        removeDownload(download.id)
        startDownload(download.url, null, null, download.mimeType, download.totalBytes)
    }

    fun openDownloadedFile(context: Context, download: BrowserDownload) {
        try {
            val uri = download.localUri?.let { Uri.parse(it) } ?: Uri.parse(download.url)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, download.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = getApplication<Application>().localizedString(R.string.browser_open_failed, e.localizedMessage ?: "")) }
        }
    }

    // ── The start screen ──────────────────────────────────────────────────────────────────────

    fun isPinnedToStart(url: String): Boolean = url in _state.value.pinnedSites

    /**
     * Puts the page on the start screen as a tile of its own, or takes it off again.
     *
     * Windows Phone let any page become a tile, which on a launcher is the most natural thing the
     * browser can do: the site sits on the board beside the apps and opens straight into the hub.
     */
    fun togglePinToStart(url: String, title: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val tiles = repository.startTiles.first()
            val updated = if (tiles.any { it.webUrl == url }) {
                StartFolders.removeTile(tiles, "${StartTileItem.WEB_PREFIX}$url")
            } else {
                tiles + StartTileItem.fromWeb(url, title)
            }
            repository.setStartTiles(updated)
            _state.update {
                it.copy(
                    errorMessage = getApplication<Application>().localizedString(
                        if (updated.size > tiles.size) R.string.browser_pinned else R.string.browser_unpinned
                    )
                )
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun openNewTab() {
        _state.update { currentState ->
            if (currentState.tabs.size >= MAX_TABS) {
                return@update currentState.copy(errorMessage = getApplication<Application>().localizedString(R.string.browser_max_tabs))
            }
            val newTabs = currentState.tabs + BrowserTab()
            currentState.copy(
                tabs = newTabs,
                activeTabIndex = newTabs.size - 1
            )
        }
    }

    /**
     * Opens [url] in a tab of its own — for a link that asked for a new window, and for a site
     * pinned to the start screen. Returns the tab's id so the caller can load the page into it.
     */
    fun openTabWith(url: String): String {
        val tab = BrowserTab(url = url, showStartScreen = false)
        var openedId = tab.id
        _state.update { currentState ->
            // An empty tab sitting there is the one to use, rather than piling another on top.
            val blank = currentState.tabs.singleOrNull()?.takeIf { it.url.isBlank() }
            if (blank != null) {
                openedId = blank.id
                return@update currentState.copy(
                    tabs = listOf(blank.copy(url = url, showStartScreen = false)),
                    activeTabIndex = 0
                )
            }
            if (currentState.tabs.size >= MAX_TABS) {
                openedId = currentState.activeTab.id
                return@update currentState.copy(
                    errorMessage = getApplication<Application>().localizedString(R.string.browser_max_tabs)
                )
            }
            val newTabs = currentState.tabs + tab
            currentState.copy(tabs = newTabs, activeTabIndex = newTabs.size - 1)
        }
        return openedId
    }

    /** A page that would not load says why, rather than leaving a blank screen unexplained. */
    fun reportPageError(detail: String) {
        _state.update {
            it.copy(
                errorMessage = getApplication<Application>()
                    .localizedString(R.string.browser_page_failed, detail)
            )
        }
    }

    fun closeTab(tabId: String) {
        _state.update { currentState ->
            val index = currentState.tabs.indexOfFirst { it.id == tabId }
            if (index == -1) return@update currentState

            val newTabs = currentState.tabs.filter { it.id != tabId }
            if (newTabs.isEmpty()) {
                // If all tabs closed, create a new empty one
                currentState.copy(tabs = listOf(BrowserTab()), activeTabIndex = 0)
            } else {
                val newIndex = if (currentState.activeTabIndex >= newTabs.size) {
                    newTabs.size - 1
                } else if (index < currentState.activeTabIndex) {
                    currentState.activeTabIndex - 1
                } else {
                    currentState.activeTabIndex
                }
                currentState.copy(tabs = newTabs, activeTabIndex = newIndex)
            }
        }
    }

    /** A tab that leaves no trace in the history. */
    fun openPrivateTab() {
        _state.update { currentState ->
            if (currentState.tabs.size >= MAX_TABS) {
                return@update currentState.copy(
                    errorMessage = getApplication<Application>().localizedString(R.string.browser_max_tabs)
                )
            }
            val newTabs = currentState.tabs + BrowserTab(isPrivate = true)
            currentState.copy(tabs = newTabs, activeTabIndex = newTabs.size - 1)
        }
    }

    fun setDesktopSite(tabId: String, on: Boolean) {
        updateTab(tabId) { it.copy(isDesktopSite = on) }
    }

    fun setReadingView(tabId: String, on: Boolean) {
        updateTab(tabId) { it.copy(isReadingView = on) }
    }

    fun switchTab(index: Int) {
        _state.update { it.copy(activeTabIndex = index) }
    }

    fun clearAllTabs() {
        _state.update { it.copy(tabs = listOf(BrowserTab()), activeTabIndex = 0) }
    }

    private fun updateTab(tabId: String, modifier: (BrowserTab) -> BrowserTab) {
        _state.update { currentState ->
            val tabs = currentState.tabs.toMutableList()
            val index = tabs.indexOfFirst { it.id == tabId }
            if (index != -1) {
                tabs[index] = modifier(tabs[index])
            }
            currentState.copy(tabs = tabs)
        }
    }

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(200) // Debounce reduced slightly for faster prediction
            val results = fetchSuggestions(query)
            _state.update { it.copy(suggestions = results) }
        }
    }

    private suspend fun fetchSuggestions(query: String): List<BrowserSuggestion> = withContext(Dispatchers.IO) {
        val engine = _state.value.searchEngine
        val list = mutableListOf<BrowserSuggestion>()

        // 1. Lucky Search (Index 0 - visually bottom with reverseLayout)
        if (engine.hasLuckySearch) {
            list.add(
                BrowserSuggestion(
                    displayText = query,
                    subtitle = engine.luckyUrl(query),
                    query = query,
                    isLucky = true
                )
            )
        }

        // 2. Smart Prediction (Index 1 - just above lucky search)
        val qLower = query.lowercase().replace(" ", "")
        if (qLower.length >= 2) {
            val prediction = popularSites.firstOrNull { it.queryMatch.startsWith(qLower) }
            if (prediction != null) {
                list.add(BrowserSuggestion(
                    displayText = prediction.name,
                    subtitle = prediction.url,
                    query = prediction.url,
                    isLucky = false
                ))
            }
        }

        // 3. What the engine itself suggests — only when the user has left that switched on.
        if (!_state.value.suggestionsEnabled) return@withContext list
        try {
            val suggestUrl = engine.suggestUrl(query) ?: return@withContext list
            val url = URL(suggestUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                // Response format: ["query", ["suggestion1", "suggestion2", ...]]
                val jsonArray = JSONArray(response)
                if (jsonArray.length() >= 2) {
                    val suggestionsArray = jsonArray.getJSONArray(1)
                    for (i in 0 until suggestionsArray.length()) {
                        val str = suggestionsArray.getString(i)
                        list.add(BrowserSuggestion(str, null, str, isLucky = false))
                    }
                }
            }
        } catch (e: Exception) {
            ZuneLog.e(TAG, "fetchSuggestions failed", e)
        }

        return@withContext list
    }

    /**
     * Puts the suggestion list away.
     *
     * Emptying the list is not enough on its own: the last keystroke's request is still in flight
     * and lands a moment later, which is how a loaded page ended up with a list of suggestions for
     * something typed in another tab sitting on top of it.
     */
    fun clearSuggestions() {
        searchJob?.cancel()
        searchJob = null
        if (_state.value.suggestions.isNotEmpty()) {
            _state.update { it.copy(suggestions = emptyList()) }
        }
    }

    fun loadUrl(tabId: String, input: String): String {
        val finalUrl = when {
            input.isBlank() -> return ""
            URLUtil.isValidUrl(input) -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> _state.value.searchEngine.searchUrl(input)
        }
        
        updateTab(tabId) { it.copy(url = finalUrl, showStartScreen = false) }
        clearSuggestions()
        return finalUrl
    }

    fun loadLuckyUrl(tabId: String, input: String): String {
        val finalUrl = _state.value.searchEngine.luckyUrl(input)
        updateTab(tabId) { it.copy(url = finalUrl, showStartScreen = false) }
        clearSuggestions()
        return finalUrl
    }

    fun onPageStarted(tabId: String, url: String?, title: String?) {
        updateTab(tabId) { it.copy(isLoading = true, url = url ?: it.url, title = title ?: getApplication<Application>().localizedString(R.string.browser_loading)) }
    }

    fun onPageFinished(tabId: String, url: String?, title: String?) {
        val finalUrl = url ?: return
        val finalTitle = title ?: finalUrl
        
        updateTab(tabId) { it.copy(isLoading = false, progress = 100f, url = finalUrl, title = finalTitle) }

        // A private tab is private precisely here: the page is shown and then forgotten.
        if (_state.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true) return

        val newHistoryItem = BrowserHistory(finalTitle, finalUrl)
        val updatedHistory = listOf(newHistoryItem) + _state.value.history.filter { it.url != finalUrl }
        saveHistory(updatedHistory.take(HISTORY_LIMIT))
    }

    fun onProgressChanged(tabId: String, progress: Int) {
        updateTab(tabId) { it.copy(progress = progress / 100f) }
    }

    fun setNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }
    /** Drops one page from the history, for the rows that should not have been kept. */
    fun removeHistoryEntry(url: String) {
        saveHistory(_state.value.history.filterNot { it.url == url })
    }

    fun clearHistory() {
        saveHistory(emptyList())
        _state.update { it.copy(history = emptyList()) }
    }
    fun goHome(tabId: String) {
        updateTab(tabId) { it.copy(showStartScreen = true, url = "", title = "") }
    }
    
    fun isFavorite(url: String): Boolean {
        val normUrl = normalizeUrl(url)
        return _state.value.favorites.any { normalizeUrl(it.url) == normUrl }
    }

    fun addFavorite(title: String, url: String, folder: String = "") {
        val currentFavorites = _state.value.favorites
        if (!isFavorite(url)) {
            val updatedFavorites = currentFavorites + BrowserFavorite(title, url, folder)
            saveFavorites(updatedFavorites)
        }
    }

    /** Rewrites one favourite in place — its name, its address or the folder it sits in. */
    fun updateFavorite(originalUrl: String, title: String, url: String, folder: String) {
        val normOriginal = normalizeUrl(originalUrl)
        saveFavorites(
            _state.value.favorites.map { favorite ->
                if (normalizeUrl(favorite.url) == normOriginal) {
                    BrowserFavorite(title, url, folder.trim())
                } else {
                    favorite
                }
            }
        )
    }

    /** Moves a favourite one place earlier or later, within the folder it belongs to. */
    fun moveFavorite(url: String, forward: Boolean) {
        val favorites = _state.value.favorites.toMutableList()
        val index = favorites.indexOfFirst { normalizeUrl(it.url) == normalizeUrl(url) }
        if (index < 0) return
        val folder = favorites[index].folder
        // Neighbours in other folders are not neighbours on screen, so they are stepped over.
        val target = if (forward) {
            (index + 1 until favorites.size).firstOrNull { favorites[it].folder == folder }
        } else {
            (index - 1 downTo 0).firstOrNull { favorites[it].folder == folder }
        } ?: return
        favorites[index] = favorites[target].also { favorites[target] = favorites[index] }
        saveFavorites(favorites)
    }

    /** Every folder that has something in it, in the order they first appear. */
    val favoriteFolders: List<String>
        get() = _state.value.favorites.map { it.folder }.filter { it.isNotBlank() }.distinct()

    fun removeFavorite(url: String) {
        val normUrl = normalizeUrl(url)
        val currentFavorites = _state.value.favorites
        val updatedFavorites = currentFavorites.filter { normalizeUrl(it.url) != normUrl }
        saveFavorites(updatedFavorites)
    }
    
    fun toggleFavorite(title: String, url: String) {
        if (isFavorite(url)) {
            removeFavorite(url)
        } else {
            addFavorite(title, url)
        }
    }

    private fun normalizeUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            var host = uri.host ?: ""
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            host + (uri.path?.removeSuffix("/") ?: "")
        } catch (e: Exception) {
            url.trimEnd('/')
        }
    }
}

class BrowserViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return BrowserViewModel(application) as T
    }
}
