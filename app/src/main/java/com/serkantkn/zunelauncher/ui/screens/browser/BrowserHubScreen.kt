package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Intent
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserHubScreen(
    onClose: () -> Unit,
    onOpenSettings: ((initialTab: String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: BrowserViewModel = viewModel(factory = BrowserViewModelFactory(application))
    val state by viewModel.state.collectAsState()
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current

    // Observe error messages
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val webViewCache = remember { mutableMapOf<String, WebView>() }
    val activeTab = state.activeTab

    /** What is being looked for on the page, while the find bar is open. */
    var findQuery by remember { mutableStateOf<String?>(null) }
    var findMatches by remember { mutableStateOf(0 to 0) }

    /** The last look of each tab, for the tab list. */
    val thumbnails = remember { mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>() }

    /** A video that has asked for the whole screen, and the way to give it back. */
    var fullscreen by remember { mutableStateOf<FullscreenPage?>(null) }

    /** A tab that has just been made for an address that still has to be loaded into it. */
    var pendingLoad by remember { mutableStateOf<Pair<String, String>?>(null) }

    /** The page waiting for the file the user is about to pick, if any. */
    var pendingFileChooser by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooser = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // A page is left waiting forever unless it is told even about a cancelled pick.
        pendingFileChooser?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        )
        pendingFileChooser = null
    }

    DisposableEffect(Unit) {
        onDispose {
            // The tabs themselves are the user's and are kept; only their pages are let go of, and
            // a page comes back the next time its tab is looked at.
            webViewCache.values.forEach { it.destroy() }
            webViewCache.clear()
            fullscreen?.callback?.onCustomViewHidden()
            pendingFileChooser?.onReceiveValue(null)
        }
    }

    BackHandler {
        if (fullscreen != null) {
            fullscreen?.callback?.onCustomViewHidden()
            fullscreen = null
        } else if (state.showDownloadsScreen) {
            viewModel.toggleDownloadsScreen(false)
        } else if (activeTab.showStartScreen) {
            onClose()
        } else if (webViewCache[activeTab.id]?.canGoBack() == true) {
            webViewCache[activeTab.id]?.goBack()
        } else {
            viewModel.goHome(activeTab.id)
        }
    }

    // A site pinned to the start board opens straight into its page.
    LaunchedEffect(Unit) {
        com.serkantkn.zunelauncher.data.repository.BrowserBridge.consume()?.let { url ->
            val tabId = viewModel.openTabWith(url)
            pendingLoad = tabId to url
        }
    }

    // The tab exists before its page does, so the address is loaded once the view is there.
    pendingLoad?.let { (tabId, url) ->
        LaunchedEffect(tabId, webViewCache[tabId]) {
            webViewCache[tabId]?.let { web ->
                web.loadUrl(url)
                pendingLoad = null
            }
        }
    }

    val isWideScreen = LocalIsWideScreen.current
    val hingeSpec = rememberHingeSpec()
    val downloadsHingeAnim = remember { Animatable(0f) }

    LaunchedEffect(state.showDownloadsScreen) {
        if (state.showDownloadsScreen) {
            downloadsHingeAnim.animateTo(
                targetValue = 1f,
                animationSpec = hingeSpec
            )
        } else {
            downloadsHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = hingeSpec
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Main Browser Content (Hinges Out to -90° when Downloads Screen opens)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = downloadsHingeAnim.value
                    rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * p
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                    alpha = (1f - p * 1.5f).coerceIn(0f, 1f)
                }
        ) {
            ZuneHubEntranceLayout(modifier = Modifier.fillMaxSize()) { bottomBarModifier ->
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Status Bar Background
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(if (zuneColors.isDark) Color.Black else Color.White)
                    )

                    val coroutineScope = rememberCoroutineScope()
                    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                    val dragOffset = remember { Animatable(0f) }

                    // Tablet / Desktop Browser Header (Top)
                    if (isWideScreen) {
                        DesktopBrowserHeader(
                            url = activeTab.url,
                            isLoading = activeTab.isLoading,
                            progress = activeTab.progress,
                            canGoBack = activeTab.canGoBack,
                            canGoForward = activeTab.canGoForward,
                            tabs = state.tabs,
                            activeTabIndex = state.activeTabIndex,
                            isFavorited = viewModel.isFavorite(activeTab.url),
                            suggestions = state.suggestions,
                            onInputChanged = { viewModel.onSearchQueryChanged(it) },
                            onUrlSubmitted = {
                                val finalUrl = viewModel.loadUrl(activeTab.id, it)
                                if (finalUrl.isNotEmpty()) webViewCache[activeTab.id]?.loadUrl(finalUrl)
                            },
                            onRefresh = { webViewCache[activeTab.id]?.reload() },
                            onStop = { webViewCache[activeTab.id]?.stopLoading() },
                            onGoBack = { webViewCache[activeTab.id]?.goBack() },
                            onGoForward = { webViewCache[activeTab.id]?.goForward() },
                            onGoHome = { viewModel.goHome(activeTab.id) },
                            onToggleFavorite = {
                                if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                    viewModel.toggleFavorite(activeTab.title, activeTab.url)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.browser_fav_add_pro), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onNewTab = { viewModel.openNewTab() },
                            onCloseTab = {
                                val webView = webViewCache.remove(it)
                                webView?.destroy()
                                viewModel.closeTab(it)
                            },
                            onSwitchTab = { viewModel.switchTab(it) },
                            onOpenDownloads = { viewModel.toggleDownloadsScreen(true) },
                            onOpenSettings = { onOpenSettings?.invoke("HUBS") }
                        )
                    }

                    // Main Content (WebViews & Start Screen)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        state.tabs.forEachIndexed { index, tab ->
                            // Keyed by the tab, not by where it sits: without this Compose reuses
                            // a closed tab's view for whichever tab slides into its place, and the
                            // tab after the one you closed goes blank for good.
                            key(tab.id) {
                                val isVisible = index == state.activeTabIndex

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(if (isVisible) 1f else 0f)
                                        .zIndex(if (isVisible) 1f else 0f)
                                        .graphicsLayer {
                                            if (!isWideScreen && state.tabs.size > 1) {
                                                translationX = if (isVisible) dragOffset.value else 0f
                                            }
                                        }
                                ) {
                                    AndroidView(
                                        factory = { ctx ->
                                            createBrowserWebView(
                                                ctx,
                                                BrowserWebCallbacks(
                                                    onStarted = { url, title -> viewModel.onPageStarted(tab.id, url, title) },
                                                    onFinished = { url, title -> viewModel.onPageFinished(tab.id, url, title) },
                                                    onProgress = { viewModel.onProgressChanged(tab.id, it) },
                                                    onNavState = { back, forward -> viewModel.setNavigationState(tab.id, back, forward) },
                                                    onDownload = { url, ua, disposition, mime, length ->
                                                        viewModel.startDownload(url, ua, disposition, mime, length)
                                                    },
                                                    onOpenInNewTab = { viewModel.openTabWith(it) },
                                                    onEnterFullscreen = { view, callback ->
                                                        fullscreen = FullscreenPage(view, callback)
                                                    },
                                                    onExitFullscreen = { fullscreen = null },
                                                    onFileChooser = { callback, params ->
                                                        pendingFileChooser?.onReceiveValue(null)
                                                        pendingFileChooser = callback
                                                        try {
                                                            fileChooser.launch(params.createIntent())
                                                            true
                                                        } catch (e: Exception) {
                                                            pendingFileChooser = null
                                                            false
                                                        }
                                                    },
                                                    onSitePermission = { request -> answerSitePermission(context, request) },
                                                    onGeolocation = { origin, callback ->
                                                        callback.invoke(origin, hasLocationPermission(context), false)
                                                    },
                                                    onError = { viewModel.reportPageError(it) }
                                                )
                                            ).also { webViewCache[tab.id] = it }
                                        },
                                        onRelease = { webViewCache.remove(tab.id) },
                                        update = { view ->
                                            val wantedAgent = if (tab.isDesktopSite) DESKTOP_USER_AGENT else null
                                            if (view.settings.userAgentString != wantedAgent &&
                                                (wantedAgent != null || tab.isDesktopSite)
                                            ) {
                                                view.settings.userAgentString = wantedAgent
                                            }
                                            // A tab kept from last time has an address but a fresh,
                                            // empty page; this is what puts the two back together.
                                            if (!tab.showStartScreen &&
                                                tab.url.isNotBlank() &&
                                                view.url.isNullOrBlank()
                                            ) {
                                                view.loadUrl(tab.url)
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                view.settings.isAlgorithmicDarkeningAllowed = zuneColors.isDark
                                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                @Suppress("DEPRECATION")
                                                view.settings.forceDark = if (zuneColors.isDark) android.webkit.WebSettings.FORCE_DARK_ON else android.webkit.WebSettings.FORCE_DARK_OFF
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (tab.showStartScreen && isVisible) {
                                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                            BrowserStartScreen(
                                                favorites = state.favorites,
                                                history = state.history,
                                                onUrlSelected = { url ->
                                                    val finalUrl = viewModel.loadUrl(tab.id, url)
                                                    if (finalUrl.isNotEmpty()) webViewCache[tab.id]?.loadUrl(finalUrl)
                                                },
                                                onAddFavorite = { title, url, folder ->
                                                    viewModel.addFavorite(title, url, folder)
                                                },
                                                onUpdateFavorite = { original, title, url, folder ->
                                                    viewModel.updateFavorite(original, title, url, folder)
                                                },
                                                onRemoveFavorite = { url -> viewModel.removeFavorite(url) },
                                                onMoveFavorite = { url, forward ->
                                                    viewModel.moveFavorite(url, forward)
                                                },
                                                onRemoveHistoryEntry = { viewModel.removeHistoryEntry(it) },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // Top Header (Optional, like other hubs)
                        if (activeTab.showStartScreen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // The tab underneath claims a z-index of its own, so without
                                    // one of its own the hub's name is drawn beneath it and never
                                    // seen.
                                    .zIndex(2f)
                                    .padding(
                                        start = ZuneDimens.ScreenPaddingHorizontal,
                                        top = if (isWideScreen) 28.dp else 44.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.hub_internet),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = if (isWideScreen) 48.sp else 36.sp
                                    ),
                                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Find on page, just above the address bar as Windows Phone put it.
                    findQuery?.let { query ->
                        BrowserFindBar(
                            query = query,
                            matches = findMatches,
                            onQueryChanged = { text ->
                                findQuery = text
                                findMatches = 0 to 0
                                webViewCache[activeTab.id]?.findAllAsync(text)
                            },
                            onNext = { webViewCache[activeTab.id]?.findNext(true) },
                            onPrevious = { webViewCache[activeTab.id]?.findNext(false) },
                            onClose = {
                                webViewCache[activeTab.id]?.clearMatches()
                                findQuery = null
                                findMatches = 0 to 0
                            }
                        )
                    }

                    // Bottom Address Bar (Phone only)
                    if (!isWideScreen) {
                        val wp8MenuBarColor = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE6E6E6)
                        WP8AddressBar(
                            url = activeTab.url,
                            isLoading = activeTab.isLoading,
                            progress = activeTab.progress,
                            canGoBack = activeTab.canGoBack,
                            canGoForward = activeTab.canGoForward,
                            suggestions = state.suggestions,
                            tabs = state.tabs,
                            activeTabIndex = state.activeTabIndex,
                            onInputChanged = { viewModel.onSearchQueryChanged(it) },
                            onUrlSubmitted = { 
                                val finalUrl = viewModel.loadUrl(activeTab.id, it)
                                if (finalUrl.isNotEmpty()) webViewCache[activeTab.id]?.loadUrl(finalUrl)
                            },
                            onLuckyUrlSubmitted = { 
                                val finalUrl = viewModel.loadLuckyUrl(activeTab.id, it)
                                if (finalUrl.isNotEmpty()) webViewCache[activeTab.id]?.loadUrl(finalUrl)
                            },
                            onRefresh = { webViewCache[activeTab.id]?.reload() },
                            onStop = { webViewCache[activeTab.id]?.stopLoading() },
                            onGoBack = { webViewCache[activeTab.id]?.goBack() },
                            onGoForward = { webViewCache[activeTab.id]?.goForward() },
                            onGoHome = { viewModel.goHome(activeTab.id) },
                            isFavorited = viewModel.isFavorite(activeTab.url),
                            onToggleFavorite = { 
                                if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                    viewModel.toggleFavorite(activeTab.title, activeTab.url)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.browser_fav_add_pro), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onNewTab = { viewModel.openNewTab() },
                            onCloseTab = { 
                                val webView = webViewCache.remove(it)
                                webView?.destroy()
                                viewModel.closeTab(it)
                            },
                            onSwitchTab = { viewModel.switchTab(it) },
                            onOpenDownloads = { viewModel.toggleDownloadsScreen(true) },
                            downloadsCount = state.downloads.size,
                            isPinnedToStart = viewModel.isPinnedToStart(activeTab.url),
                            onTogglePinToStart = {
                                viewModel.togglePinToStart(activeTab.url, activeTab.title)
                            },
                            onFindInPage = {
                                findQuery = ""
                                findMatches = 0 to 0
                                webViewCache[activeTab.id]?.setFindListener { ordinal, count, done ->
                                    if (done) {
                                        findMatches = (if (count == 0) 0 else ordinal + 1) to count
                                    }
                                }
                            },
                            onShare = { sharePage(context, activeTab.title, activeTab.url) },
                            isDesktopSite = activeTab.isDesktopSite,
                            onToggleDesktopSite = {
                                val on = !activeTab.isDesktopSite
                                viewModel.setDesktopSite(activeTab.id, on)
                                webViewCache[activeTab.id]?.let { web ->
                                    web.settings.userAgentString = if (on) DESKTOP_USER_AGENT else null
                                    web.settings.useWideViewPort = true
                                    web.settings.loadWithOverviewMode = true
                                    web.reload()
                                }
                            },
                            isReadingView = activeTab.isReadingView,
                            onToggleReadingView = {
                                val web = webViewCache[activeTab.id]
                                if (activeTab.isReadingView) {
                                    viewModel.setReadingView(activeTab.id, false)
                                    web?.reload()
                                } else {
                                    web?.evaluateJavascript(readingViewScript(zuneColors.isDark)) { result ->
                                        // "nothing" means the page had no article worth keeping.
                                        if (result.contains("ok")) {
                                            viewModel.setReadingView(activeTab.id, true)
                                        } else {
                                            viewModel.reportPageError("")
                                        }
                                    }
                                }
                            },
                            onNewPrivateTab = { viewModel.openPrivateTab() },
                            thumbnails = thumbnails,
                            onTabsOpened = {
                                state.tabs.forEach { tab ->
                                    webViewCache[tab.id]?.let { web ->
                                        captureThumbnail(web)?.let { thumbnails[tab.id] = it }
                                    }
                                }
                            },
                            onOpenSettings = { onOpenSettings?.invoke("HUBS") },
                            onSuggestionsDismissed = { viewModel.clearSuggestions() },
                            onDrag = { dragAmount ->
                                coroutineScope.launch {
                                    val canSwipeLeft = state.activeTabIndex < state.tabs.size - 1
                                    val canSwipeRight = state.activeTabIndex > 0
                                    var newOffset = dragOffset.value + dragAmount
                                    if (!canSwipeRight && newOffset > 0) newOffset = 0f
                                    if (!canSwipeLeft && newOffset < 0) newOffset = 0f
                                    dragOffset.snapTo(newOffset)
                                }
                            },
                            onDragEnd = {
                                coroutineScope.launch {
                                    val threshold = screenWidthPx * 0.25f
                                    if (dragOffset.value > threshold && state.activeTabIndex > 0) {
                                        dragOffset.animateTo(screenWidthPx)
                                        viewModel.switchTab(state.activeTabIndex - 1)
                                        dragOffset.snapTo(0f)
                                    } else if (dragOffset.value < -threshold && state.activeTabIndex < state.tabs.size - 1) {
                                        dragOffset.animateTo(-screenWidthPx)
                                        viewModel.switchTab(state.activeTabIndex + 1)
                                        dragOffset.snapTo(0f)
                                    } else {
                                        dragOffset.animateTo(0f)
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch { dragOffset.animateTo(0f) }
                            },
                            modifier = Modifier.fillMaxWidth().background(wp8MenuBarColor).imePadding().navigationBarsPadding().then(bottomBarModifier)
                        )
                    }
                }
            }
        }

        // 2. Standalone Downloads Screen (Hinges In from 90° to 0°)
        if (state.showDownloadsScreen || downloadsHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = downloadsHingeAnim.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                BrowserDownloadsScreen(
                    downloads = state.downloads,
                    onClose = { viewModel.toggleDownloadsScreen(false) },
                    onOpenDownload = { viewModel.openDownloadedFile(context, it) },
                    onRemoveDownload = { viewModel.removeDownload(it) },
                    onRetryDownload = { viewModel.retryDownload(it) },
                    onClearAll = { viewModel.clearAllDownloads() },
                    onRefreshStatus = { viewModel.refreshDownloadsStatus() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. A video that asked for the whole screen gets the whole screen.
        fullscreen?.let { page ->
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        addView(
                            page.view,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                onRelease = { it.removeAllViews() },
                modifier = Modifier.fillMaxSize().zIndex(10f)
            )
        }
    }
}

/** A page's video, blown up, and the callback that puts it back in its box. */
private data class FullscreenPage(
    val view: View,
    val callback: WebChromeClient.CustomViewCallback
)

/**
 * A page asking for the camera or the microphone can only be given what the launcher itself has
 * been given. Asking the user again from in here would be a second dialog for the same question,
 * so a permission the launcher lacks is a refusal rather than a prompt.
 */
private fun answerSitePermission(context: Context, request: PermissionRequest) {
    val granted = request.resources.filter { resource ->
        when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> holds(context, Manifest.permission.CAMERA)
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> holds(context, Manifest.permission.RECORD_AUDIO)
            else -> false
        }
    }
    if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
}

private fun hasLocationPermission(context: Context): Boolean =
    holds(context, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun holds(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Hands the page's address to whatever the phone shares with. */
private fun sharePage(context: Context, title: String, url: String) {
    if (url.isBlank()) return
    try {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, url)
                },
                title.ifBlank { url }
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        ZuneLog.w("BrowserHubScreen", "nothing here shares a link", e)
    }
}

/**
 * A small picture of a page, for its card in the tab list — the thumbnails Windows Phone showed
 * instead of a line of text.
 */
private fun captureThumbnail(web: WebView): androidx.compose.ui.graphics.ImageBitmap? {
    if (web.width <= 0 || web.height <= 0) return null
    return try {
        val scale = THUMBNAIL_WIDTH_PX.toFloat() / web.width
        val bitmap = android.graphics.Bitmap.createBitmap(
            THUMBNAIL_WIDTH_PX,
            (web.height * scale).toInt().coerceAtLeast(1),
            android.graphics.Bitmap.Config.RGB_565
        )
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(scale, scale)
        web.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

private const val THUMBNAIL_WIDTH_PX = 240
