package com.serkantkn.zeta.ui.screens.browser

import com.serkantkn.zeta.util.localizedString
import com.serkantkn.zeta.R
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.data.model.toJsonStringArray
import com.serkantkn.zeta.data.model.parseJsonStringList
import com.serkantkn.zeta.di.appContainer
import android.app.Application
import android.webkit.URLUtil
import android.webkit.WebSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.serkantkn.zeta.data.model.BrowserDownload
import com.serkantkn.zeta.data.model.BrowserFavorite
import com.serkantkn.zeta.data.model.BrowserHistory
import com.serkantkn.zeta.data.model.StartFolders
import com.serkantkn.zeta.data.model.StartTileItem
import com.serkantkn.zeta.data.model.SearchEngine
import com.serkantkn.zeta.data.model.parseJsonObjectList
import com.serkantkn.zeta.data.model.toJsonArrayString
import android.app.DownloadManager
import androidx.core.content.FileProvider
import com.serkantkn.zeta.util.PackageInstall
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
    val isReadingView: Boolean = false,
    /** The page reads as an article, so reading view is offered beside the address. */
    val readingAvailable: Boolean = false,
    /** The page's renderer died; the tab shows an offer to load it again instead of a view. */
    val isCrashed: Boolean = false,
    /** Bumped on every reload after a crash, so the hub builds a fresh view for the tab. */
    val generation: Int = 0
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
    /** Text size in percent by host; [TextSizes.EVERY_SITE] for the rest. */
    val textSizes: Map<String, Int> = emptyMap(),
    val adBlockEnabled: Boolean = true,
    /** Sites ad blocking was switched off for. */
    val adBlockAllowed: Set<String> = emptySet(),
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

        /** A page finishing again this soon after the last time is the same visit. */
        const val SAME_VISIT_MS = 30_000L
    }

    private val repository = application.appContainer.settingsDataStore

    /** History and downloads live apart from the settings, and out of the backup. */
    private val browsing = application.appContainer.browsingDataStore

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
            browsing.history.collect { json ->
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
            com.serkantkn.zeta.data.repository.AdBlocker.prepare(getApplication())
        }
        viewModelScope.launch {
            browsing.adBlockEnabled.collect { enabled ->
                com.serkantkn.zeta.data.repository.AdBlocker.enabled = enabled
                _state.update { it.copy(adBlockEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            browsing.adBlockAllowed.collect { json ->
                val allowed = parseJsonStringList(json, TAG).toSet()
                com.serkantkn.zeta.data.repository.AdBlocker.allowedSites = allowed
                _state.update { it.copy(adBlockAllowed = allowed) }
            }
        }
        viewModelScope.launch {
            browsing.textSizes.collect { json ->
                _state.update { it.copy(textSizes = TextSizes.parse(json)) }
            }
        }
        viewModelScope.launch {
            browsing.downloads.collect { json ->
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
            browsing.setHistory(newHistory.toJsonArrayString { it.toJson() })
        }
    }

    private fun saveDownloads(newDownloads: List<BrowserDownload>) {
        viewModelScope.launch {
            browsing.setDownloads(newDownloads.toJsonArrayString { it.toJson() })
        }
    }

    fun toggleDownloadsScreen(show: Boolean) {
        _state.update { it.copy(showDownloadsScreen = show) }
        if (show) {
            refreshDownloadsStatus()
            startDownloadPolling()
        }
    }

    /**
     * Starts a download the page asked for.
     *
     * The download manager fetches on its own, outside the page's session: without the page's
     * cookies and user agent a file behind a login came back as the login page. A `data:` link
     * is not fetched at all — the file is right there in the address and is written straight
     * to Downloads. A `blob:` link only exists inside the page's renderer and cannot be reached
     * from here. A download from a [isPrivate] tab still runs, but is not written to the list.
     */
    fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        isPrivate: Boolean = false
    ) {
        val app = getApplication<Application>()
        when {
            url.startsWith("blob:") -> {
                _state.update { it.copy(errorMessage = app.localizedString(R.string.browser_download_blob)) }
                return
            }
            url.startsWith("data:") -> {
                saveDataUrl(url, contentDisposition, mimeType, isPrivate)
                return
            }
        }
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                if (!mimeType.isNullOrEmpty()) {
                    setMimeType(mimeType)
                }
                addRequestHeader("User-Agent", userAgent?.takeIf { it.isNotEmpty() } ?: WebSettings.getDefaultUserAgent(app))
                android.webkit.CookieManager.getInstance().getCookie(url)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { addRequestHeader("Cookie", it) }
                setTitle(fileName)
                setDescription(app.localizedString(R.string.browser_downloading))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            if (isPrivate) {
                _state.update { it.copy(errorMessage = app.localizedString(R.string.browser_download_started, fileName)) }
                return
            }

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
                    errorMessage = app.localizedString(R.string.browser_download_started, fileName)
                )
            }
            saveDownloads(updatedList)
            startDownloadPolling()
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = app.localizedString(R.string.browser_download_failed, e.localizedMessage ?: "")) }
        }
    }

    /** Writes the payload of a `data:` link to Downloads and lists it as a finished download. */
    private fun saveDataUrl(url: String, contentDisposition: String?, mimeType: String?, isPrivate: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val payload = decodeDataUrl(url) ?: error("not a data url")
                val mime = mimeType?.takeIf { it.isNotBlank() } ?: payload.first
                val fileName = URLUtil.guessFileName("https://data/download", contentDisposition, mime)
                val saved = writeToDownloads(app, fileName, mime, payload.second)
                BrowserDownload(
                    id = -System.currentTimeMillis(),
                    fileName = fileName,
                    url = url.take(DATA_URL_KEEP),
                    mimeType = mime,
                    totalBytes = payload.second.size.toLong(),
                    downloadedBytes = payload.second.size.toLong(),
                    status = DownloadManager.STATUS_SUCCESSFUL,
                    localUri = saved.toString()
                )
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { download ->
                    val updated = if (isPrivate) _state.value.downloads else listOf(download) + _state.value.downloads
                    _state.update {
                        it.copy(
                            downloads = updated,
                            errorMessage = app.localizedString(R.string.browser_download_started, download.fileName)
                        )
                    }
                    if (!isPrivate) saveDownloads(updated)
                }.onFailure { e ->
                    _state.update { it.copy(errorMessage = app.localizedString(R.string.browser_download_failed, e.localizedMessage ?: "")) }
                }
            }
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
                val fresh = dm.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else download.status
                    val downloadedBytes = if (bytesDownloadedIdx >= 0) cursor.getLong(bytesDownloadedIdx) else download.downloadedBytes
                    val totalBytes = if (bytesTotalIdx >= 0) cursor.getLong(bytesTotalIdx) else download.totalBytes
                    val localUri = if (localUriIdx >= 0) cursor.getString(localUriIdx) ?: download.localUri else download.localUri
                    download.copy(
                        status = status,
                        downloadedBytes = downloadedBytes,
                        totalBytes = if (totalBytes > 0) totalBytes else download.totalBytes,
                        localUri = localUri
                    )
                }
                    // No row at all: the download manager no longer knows the id — cancelled from
                    // its notification, or cleared by the system. Left as "running" it would be
                    // polled forever; as "failed" it can be retried or removed.
                    ?: download.copy(status = DownloadManager.STATUS_FAILED)
                if (fresh != download) changed = true
                fresh
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

    /**
     * Opens a finished download in whatever handles it.
     *
     * The download manager names the file by a file:// address, which no other app may be handed
     * since Android 7 — the tap used to end in an exception and nothing on screen. So the file is
     * offered through the launcher's own provider; an .apk goes to the installer, which has its
     * own asking to do; and a server that called the file "octet-stream" is overruled by the name
     * the file actually has.
     */
    fun openDownloadedFile(context: Context, download: BrowserDownload) {
        try {
            val local = download.localUri?.let { Uri.parse(it) }
            val uri: Uri = when {
                local?.scheme == "file" && local.path != null ->
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(local.path!!))
                local != null -> local
                else -> (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                    .getUriForDownloadedFile(download.id) ?: Uri.parse(download.url)
            }
            if (PackageInstall.isApk(download.fileName)) {
                PackageInstall.open(context, uri)
                return
            }
            val extension = download.fileName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val mime = download.mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
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
    fun openTabWith(url: String, isPrivate: Boolean = false): String {
        val tab = BrowserTab(url = url, showStartScreen = false, isPrivate = isPrivate)
        var openedId = tab.id
        _state.update { currentState ->
            // An empty tab sitting there is the one to use, rather than piling another on top.
            val blank = currentState.tabs.singleOrNull()?.takeIf { it.url.isBlank() && it.isPrivate == isPrivate }
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
    /**
     * Shares a picture from a page as the picture itself, not its address: it is fetched - with
     * the page's cookies, as the page itself fetched it - into the cache and handed over from there.
     */
    fun sharePicture(context: Context, address: String, userAgent: String?) {
        viewModelScope.launch {
            val shared = withContext(Dispatchers.IO) {
                try {
                    val dir = java.io.File(context.cacheDir, "shared_pictures").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val (bytes, mime) = if (address.startsWith("data:")) {
                        val header = address.substringBefore(',')
                        val body = address.substringAfter(',')
                        val type = header.removePrefix("data:").substringBefore(';').ifBlank { "image/png" }
                        val data = if (header.endsWith(";base64")) {
                            android.util.Base64.decode(body, android.util.Base64.DEFAULT)
                        } else {
                            java.net.URLDecoder.decode(body, "UTF-8").toByteArray()
                        }
                        data to type
                    } else {
                        val connection = java.net.URL(address).openConnection() as java.net.HttpURLConnection
                        try {
                            connection.connectTimeout = 15_000
                            connection.readTimeout = 15_000
                            userAgent?.let { connection.setRequestProperty("User-Agent", it) }
                            android.webkit.CookieManager.getInstance().getCookie(address)
                                ?.let { connection.setRequestProperty("Cookie", it) }
                            val type = connection.contentType?.substringBefore(';')?.trim()
                                ?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                            connection.inputStream.use { it.readBytes() } to type
                        } finally {
                            connection.disconnect()
                        }
                    }
                    val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
                    val file = java.io.File(dir, "picture.$extension")
                    file.writeBytes(bytes)
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) to mime
                } catch (e: Exception) {
                    ZuneLog.w(TAG, "picture could not be fetched to share", e)
                    null
                }
            }
            if (shared == null) {
                _state.update { it.copy(errorMessage = getApplication<Application>().localizedString(R.string.browser_share_picture_failed)) }
                return@launch
            }
            val (uri, mime) = shared
            val send = Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = android.content.ClipData.newRawUri(null, uri)
            try {
                context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                ZuneLog.w(TAG, "nothing here shares a picture", e)
            }
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        com.serkantkn.zeta.data.repository.AdBlocker.enabled = enabled
        _state.update { it.copy(adBlockEnabled = enabled) }
        viewModelScope.launch { browsing.setAdBlockEnabled(enabled) }
    }

    /** Switches blocking off for [host], or back on. */
    fun setAdBlockAllowed(host: String, allowed: Boolean) {
        val sites = if (allowed) _state.value.adBlockAllowed + host else _state.value.adBlockAllowed - host
        com.serkantkn.zeta.data.repository.AdBlocker.allowedSites = sites
        _state.update { it.copy(adBlockAllowed = sites) }
        viewModelScope.launch { browsing.setAdBlockAllowed(sites.toList().toJsonStringArray()) }
    }

    /** Sets the text size for [host], or for every site without a size of its own when [host] is null. */
    fun setTextSize(host: String?, percent: Int) {
        val key = host ?: TextSizes.EVERY_SITE
        val sizes = _state.value.textSizes.toMutableMap()
        if (host != null && percent == (sizes[TextSizes.EVERY_SITE] ?: TextSizes.DEFAULT)) sizes.remove(key) else sizes[key] = percent
        if (host == null) {
            // A size chosen for every site replaces the ones sites had of their own.
            sizes.keys.retainAll(setOf(TextSizes.EVERY_SITE))
        }
        _state.update { it.copy(textSizes = sizes) }
        viewModelScope.launch { browsing.setTextSizes(TextSizes.toJson(sizes)) }
    }

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

    fun setReadingAvailable(tabId: String, available: Boolean) {
        updateTab(tabId) { if (it.readingAvailable == available) it else it.copy(readingAvailable = available) }
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
        // A new page is a new page: the reading view belonged to the one before it.
        updateTab(tabId) {
            it.copy(
                isLoading = true,
                isReadingView = false,
                readingAvailable = false,
                url = url ?: it.url,
                title = title ?: getApplication<Application>().localizedString(R.string.browser_loading)
            )
        }
    }

    /** The tab's renderer died; the hub drops the view and shows the offer to load it again. */
    fun markTabCrashed(tabId: String) {
        updateTab(tabId) { it.copy(isCrashed = true, isLoading = false, isReadingView = false, readingAvailable = false) }
    }

    /** Builds the crashed tab a fresh page, which then loads the address it had. */
    fun reloadCrashedTab(tabId: String) {
        updateTab(tabId) { it.copy(isCrashed = false, generation = it.generation + 1) }
    }

    fun onPageFinished(tabId: String, url: String?, title: String?) {
        val finalUrl = url ?: return
        val finalTitle = title ?: finalUrl
        
        updateTab(tabId) { it.copy(isLoading = false, progress = 100f, url = finalUrl, title = finalTitle) }

        // A private tab is private precisely here: the page is shown and then forgotten.
        if (_state.value.tabs.firstOrNull { it.id == tabId }?.isPrivate == true) return

        // "Finished" can come several times for one page as it settles; within a short while of
        // the last it is the same visit, not another.
        val now = System.currentTimeMillis()
        val before = _state.value.history.firstOrNull { it.url == finalUrl }
        val sameVisit = before != null && now - before.timestamp < SAME_VISIT_MS
        val newHistoryItem = BrowserHistory(
            finalTitle,
            finalUrl,
            timestamp = now,
            visits = (before?.visits ?: 0) + if (sameVisit) 0 else 1
        ).let { if (it.visits < 1) it.copy(visits = 1) else it }
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
        updateTab(tabId) {
            it.copy(showStartScreen = true, url = "", title = "", isReadingView = false, readingAvailable = false)
        }
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

/** How much of a `data:` address is kept in the download record; the rest is the file itself. */
private const val DATA_URL_KEEP = 64

/**
 * Pulls the file out of a `data:` address: its media type and its bytes. Returns null for
 * anything that is not one. A missing type means `text/plain`, as the RFC has it.
 */
internal fun decodeDataUrl(url: String): Pair<String, ByteArray>? {
    if (!url.startsWith("data:", ignoreCase = true)) return null
    val comma = url.indexOf(',')
    if (comma < 0) return null
    val header = url.substring(5, comma)
    val body = url.substring(comma + 1)
    val isBase64 = header.endsWith(";base64", ignoreCase = true)
    val mime = header.removeSuffix(";base64").substringBefore(';').ifBlank { "text/plain" }
    val bytes = if (isBase64) {
        runCatching { java.util.Base64.getMimeDecoder().decode(body.trim()) }.getOrNull() ?: return null
    } else {
        java.net.URLDecoder.decode(body.replace("+", "%2B"), "UTF-8").toByteArray()
    }
    return mime to bytes
}

/** Puts [bytes] into the public Downloads folder under [fileName] and returns where it landed. */
private fun writeToDownloads(context: Context, fileName: String, mime: String, bytes: ByteArray): Uri {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("downloads not writable")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("downloads not writable")
            values.clear()
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            // A half-written pending row is invisible and never cleaned up; take it with us.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }
    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }
    var file = java.io.File(dir, fileName)
    var n = 2
    while (file.exists()) {
        file = java.io.File(dir, "${fileName.substringBeforeLast('.')} ($n).${fileName.substringAfterLast('.', "")}".removeSuffix("."))
        n++
    }
    file.writeBytes(bytes)
    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
    return Uri.fromFile(file)
}

class BrowserViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return BrowserViewModel(application) as T
    }
}

/** Text sizes by site: how they are kept, and which one a page gets. */
object TextSizes {
    const val EVERY_SITE = "*"
    const val DEFAULT = 100
    val CHOICES = listOf(80, 90, 100, 115, 130, 150, 175, 200)

    fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The size for [url]: its site's own, else the one for every site, else 100%. */
    fun forUrl(sizes: Map<String, Int>, url: String): Int =
        hostOf(url)?.let { sizes[it] } ?: sizes[EVERY_SITE] ?: DEFAULT

    fun parse(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getInt(it) }.filterValues { it in 50..300 }
        } catch (e: org.json.JSONException) {
            emptyMap()
        }
    }

    fun toJson(sizes: Map<String, Int>): String =
        org.json.JSONObject().apply { sizes.forEach { (host, size) -> put(host, size) } }.toString()
}
