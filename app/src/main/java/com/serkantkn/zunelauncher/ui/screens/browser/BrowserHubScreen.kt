package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

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

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearAllTabs()
            webViewCache.values.forEach { it.destroy() }
            webViewCache.clear()
        }
    }

    BackHandler {
        if (state.showDownloadsScreen) {
            viewModel.toggleDownloadsScreen(false)
        } else if (activeTab.showStartScreen) {
            onClose()
        } else if (webViewCache[activeTab.id]?.canGoBack() == true) {
            webViewCache[activeTab.id]?.goBack()
        } else {
            viewModel.goHome(activeTab.id)
        }
    }

    val isWideScreen = LocalIsWideScreen.current
    val downloadsHingeAnim = remember { Animatable(0f) }

    LaunchedEffect(state.showDownloadsScreen) {
        if (state.showDownloadsScreen) {
            downloadsHingeAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
            )
        } else {
            downloadsHingeAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
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
                                        WebView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)

                                            webViewClient = object : WebViewClient() {
                                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                    super.onPageStarted(view, url, favicon)
                                                    viewModel.onPageStarted(tab.id, url, view?.title)
                                                    viewModel.setNavigationState(tab.id, view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    viewModel.onPageFinished(tab.id, url, view?.title)
                                                    viewModel.setNavigationState(tab.id, view?.canGoBack() ?: false, view?.canGoForward() ?: false)
                                                }
                                            }

                                            webChromeClient = object : WebChromeClient() {
                                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                    super.onProgressChanged(view, newProgress)
                                                    viewModel.onProgressChanged(tab.id, newProgress)
                                                }
                                            }

                                            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                                                viewModel.startDownload(url, userAgent, contentDisposition, mimetype, contentLength)
                                            }
                                        }.also { webViewCache[tab.id] = it }
                                    },
                                    update = { view ->
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
                                            onAddFavorite = { title, url -> viewModel.addFavorite(title, url) },
                                            onRemoveFavorite = { url -> viewModel.removeFavorite(url) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }

                        // Top Header (Optional, like other hubs)
                        if (activeTab.showStartScreen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                            onOpenSettings = { onOpenSettings?.invoke("HUBS") },
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
                    onClearAll = { viewModel.clearAllDownloads() },
                    onRefreshStatus = { viewModel.refreshDownloadsStatus() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
