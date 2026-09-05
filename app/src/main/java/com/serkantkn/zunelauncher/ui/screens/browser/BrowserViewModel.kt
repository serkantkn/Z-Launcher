package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.di.appContainer
import android.app.Application
import android.webkit.URLUtil
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.serkantkn.zunelauncher.data.model.BrowserDownload
import com.serkantkn.zunelauncher.data.model.BrowserFavorite
import com.serkantkn.zunelauncher.data.model.BrowserHistory
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
    val title: String = "Yeni Sekme",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val showStartScreen: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
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
    private val repository = application.appContainer.settingsRepository
    private val gson = Gson()
    
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
                    try {
                        val type = object : TypeToken<List<BrowserFavorite>>() {}.type
                        gson.fromJson<List<BrowserFavorite>>(json, type) ?: emptyList()
                    } catch (e: Exception) { emptyList() }
                }
                _state.update { it.copy(favorites = list) }
            }
        }
        viewModelScope.launch {
            repository.browserHistory.collect { json ->
                val list = if (json.isNullOrEmpty()) emptyList() else {
                    try {
                        val type = object : TypeToken<List<BrowserHistory>>() {}.type
                        gson.fromJson<List<BrowserHistory>>(json, type) ?: emptyList()
                    } catch (e: Exception) { emptyList() }
                }
                _state.update { it.copy(history = list) }
            }
        }
        viewModelScope.launch {
            repository.browserDownloads.collect { json ->
                val list = if (json.isNullOrEmpty()) emptyList() else {
                    try {
                        val type = object : TypeToken<List<BrowserDownload>>() {}.type
                        gson.fromJson<List<BrowserDownload>>(json, type) ?: emptyList()
                    } catch (e: Exception) { emptyList() }
                }
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
            repository.setBrowserFavorites(gson.toJson(newFavorites))
        }
    }

    private fun saveHistory(newHistory: List<BrowserHistory>) {
        viewModelScope.launch {
            repository.setBrowserHistory(gson.toJson(newHistory))
        }
    }

    private fun saveDownloads(newDownloads: List<BrowserDownload>) {
        viewModelScope.launch {
            repository.setBrowserDownloads(gson.toJson(newDownloads))
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
                setDescription("İndiriliyor...")
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
                    errorMessage = "İndirme başlatıldı: $fileName"
                )
            }
            saveDownloads(updatedList)
            startDownloadPolling()
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = "İndirme başlatılamadı: ${e.localizedMessage}") }
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

    fun removeDownload(downloadId: Long) {
        val updatedList = _state.value.downloads.filterNot { it.id == downloadId }
        _state.update { it.copy(downloads = updatedList) }
        saveDownloads(updatedList)
    }

    fun clearAllDownloads() {
        _state.update { it.copy(downloads = emptyList()) }
        saveDownloads(emptyList())
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
            _state.update { it.copy(errorMessage = "Dosya açılamadı: ${e.localizedMessage}") }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun openNewTab() {
        _state.update { currentState ->
            if (currentState.tabs.size >= 10) {
                return@update currentState.copy(errorMessage = "Maksimum 10 sekme açabilirsiniz.")
            }
            val newTabs = currentState.tabs + BrowserTab()
            currentState.copy(
                tabs = newTabs,
                activeTabIndex = newTabs.size - 1
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
        val list = mutableListOf<BrowserSuggestion>()
        
        // 1. Lucky Search (Index 0 - visually bottom with reverseLayout)
        val luckyUrl = "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}&btnI=I"
        list.add(BrowserSuggestion(
            displayText = query, 
            subtitle = luckyUrl, 
            query = query, 
            isLucky = true
        ))

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

        // 3. Google Suggestions
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery&oe=utf8")
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
            e.printStackTrace()
        }

        return@withContext list
    }

    fun loadUrl(tabId: String, input: String): String {
        val finalUrl = when {
            input.isBlank() -> return ""
            URLUtil.isValidUrl(input) -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${input.replace(" ", "+")}"
        }
        
        updateTab(tabId) { it.copy(url = finalUrl, showStartScreen = false) }
        _state.update { it.copy(suggestions = emptyList()) }
        return finalUrl
    }

    fun loadLuckyUrl(tabId: String, input: String): String {
        val finalUrl = "https://www.google.com/search?q=${input.replace(" ", "+")}&btnI=I"
        updateTab(tabId) { it.copy(url = finalUrl, showStartScreen = false) }
        _state.update { it.copy(suggestions = emptyList()) }
        return finalUrl
    }

    fun onPageStarted(tabId: String, url: String?, title: String?) {
        updateTab(tabId) { it.copy(isLoading = true, url = url ?: it.url, title = title ?: "Yükleniyor...") }
    }

    fun onPageFinished(tabId: String, url: String?, title: String?) {
        val finalUrl = url ?: return
        val finalTitle = title ?: finalUrl
        
        updateTab(tabId) { it.copy(isLoading = false, progress = 100f, url = finalUrl, title = finalTitle) }
        
        val newHistoryItem = BrowserHistory(finalTitle, finalUrl)
        val updatedHistory = listOf(newHistoryItem) + _state.value.history.filter { it.url != finalUrl }
        saveHistory(updatedHistory.take(100)) // Keep up to 100 history items
    }

    fun onProgressChanged(tabId: String, progress: Int) {
        updateTab(tabId) { it.copy(progress = progress / 100f) }
    }

    fun setNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }
    fun clearHistory() {
        saveHistory(emptyList())
        _state.update { it.copy(history = emptyList()) }
    }
    fun goHome(tabId: String) {
        updateTab(tabId) { it.copy(showStartScreen = true, url = "", title = "Yeni Sekme") }
    }
    
    fun isFavorite(url: String): Boolean {
        val normUrl = normalizeUrl(url)
        return _state.value.favorites.any { normalizeUrl(it.url) == normUrl }
    }

    fun addFavorite(title: String, url: String) {
        val currentFavorites = _state.value.favorites
        if (!isFavorite(url)) {
            val updatedFavorites = currentFavorites + BrowserFavorite(title, url)
            saveFavorites(updatedFavorites)
        }
    }

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
