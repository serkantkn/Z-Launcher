package com.serkantkn.zunelauncher.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: BrowserViewModel = viewModel(factory = BrowserViewModelFactory(application))
    val state by viewModel.state.collectAsState()
    val zuneColors = LocalZuneColors.current

    // Observe error messages (e.g. max tabs limit)
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    // Keep a reference to the WebViews to control them
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
        if (activeTab.showStartScreen) {
            onClose()
        } else if (webViewCache[activeTab.id]?.canGoBack() == true) {
            webViewCache[activeTab.id]?.goBack()
        } else {
            viewModel.goHome(activeTab.id)
        }
    }

    val isWideScreen = LocalIsWideScreen.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Status Bar Background
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(if (zuneColors.isDark) Color.Black else Color.White)
        )

        val coroutineScope = rememberCoroutineScope()
        val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
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
                        Toast.makeText(context, "Sık kullanılanlara site ekleme özelliği sadece Z Launcher Pro'da geçerlidir.", Toast.LENGTH_SHORT).show()
                    }
                },
                onNewTab = { viewModel.openNewTab() },
                onCloseTab = {
                    val webView = webViewCache.remove(it)
                    webView?.destroy()
                    viewModel.closeTab(it)
                },
                onSwitchTab = { viewModel.switchTab(it) }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            
            state.tabs.forEachIndexed { index, tab ->
                val xOffset = (index - state.activeTabIndex) * screenWidthPx + dragOffset.value
                val isVisible = abs(index - state.activeTabIndex) <= 1

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isVisible) 1f else 0f)
                        .zIndex(if (index == state.activeTabIndex) 1f else 0f)
                        .graphicsLayer { translationX = xOffset }
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
                val density = LocalDensity.current
                val overflowYPx = with(density) { (-24).dp.toPx() }

                Text(
                    text = "internet",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        lineHeight = 96.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = overflowYPx
                        }
                        .padding(
                            start = ZuneDimens.ScreenPaddingHorizontal,
                            top = 28.dp
                        )
                )
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
                        Toast.makeText(context, "Sık kullanılanlara site ekleme özelliği sadece Z Launcher Pro'da geçerlidir.", Toast.LENGTH_SHORT).show()
                    }
                },
                onNewTab = { viewModel.openNewTab() },
                onCloseTab = { 
                    val webView = webViewCache.remove(it)
                    webView?.destroy()
                    viewModel.closeTab(it)
                },
                onSwitchTab = { viewModel.switchTab(it) },
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
                modifier = Modifier.fillMaxWidth().background(wp8MenuBarColor).navigationBarsPadding()
            )
        }
    }
}
