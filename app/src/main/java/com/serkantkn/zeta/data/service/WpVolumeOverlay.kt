package com.serkantkn.zeta.data.service

import com.serkantkn.zeta.ui.animation.installZetaMotion
import com.serkantkn.zeta.util.localized
import com.serkantkn.zeta.util.ZuneLog
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.serkantkn.zeta.data.model.AccentColor
import com.serkantkn.zeta.data.model.ThemeMode
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.ui.components.LocalBannerAlpha
import com.serkantkn.zeta.ui.components.WpVolumeControl
import com.serkantkn.zeta.ui.components.bannerAlpha
import com.serkantkn.zeta.ui.components.bannerBlurPx
import com.serkantkn.zeta.ui.theme.ZuneLauncherTheme

/**
 * Hosts the Windows Phone volume bar in a system overlay window so it can appear on top of
 * other apps (driven by ZuneKeyAccessibilityService). Mirrors WpToastOverlay: non-focusable,
 * wrap-content, attached only while the bar is visible.
 */
object WpVolumeOverlay {

    private const val TAG = "WpVolumeOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var banner: BannerWindow? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /** How wide the bar is on a wide screen, as the launcher draws it. */
    private const val CARD_WIDTH_DP = 420

    /** Attaches the bar window if it is not already on screen. VolumeController drives the content. */
    fun show(context: Context) {
        val appContext = context.applicationContext
        if (!WpToastOverlay.isAvailable(appContext)) return
        mainHandler.post { attach(appContext) }
    }

    fun hide() {
        mainHandler.post { detach() }
    }

    private fun attach(appContext: Context) {
        if (banner != null) return

        // The bar is driven by the accessibility service, so it is that service's own overlay
        // and Android posts no "displaying over other apps" notice for it.
        val (host, type) = WpToastOverlay.overlayHost(appContext)
        val window = runCatching { BannerWindow(host, type) }
            .onFailure { ZuneLog.e(TAG, "the window could not be made", it) }
            .getOrNull() ?: return
        window.placeAsCard(appContext, CARD_WIDTH_DP)
        val owner = OverlayLifecycleOwner().also { it.onCreate() }

        val view = ComposeView(appContext.localized()).apply {
            installZetaMotion(owner.lifecycle)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val settings = appContext.appContainer.settingsDataStore
                val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
                val accentColor by settings.accentColor.collectAsState(initial = AccentColor.MAGENTA)
                val dynamicThemeColor by settings.dynamicThemeColor.collectAsState(initial = null)
                val customThemeColor by settings.customThemeColor.collectAsState(initial = null)
                val solidBackgroundEnabled by settings.solidBackgroundEnabled.collectAsState(initial = false)
                val fontScale by settings.fontScale.collectAsState(initial = 1.0f)
                val tileBlur by settings.tileBlur.collectAsState(initial = 0)
                val tileOpacity by settings.tileOpacity.collectAsState(initial = 100)
                // Frosted as the tiles are - but only where the phone will blur behind the window.
                val alpha = if (window.blursBehind) bannerAlpha(tileBlur, tileOpacity) else 1f
                LaunchedEffect(alpha, tileBlur) { window.setFrost(if (alpha < 1f) bannerBlurPx(tileBlur) else 0) }

                ZuneLauncherTheme(
                    themeMode = themeMode,
                    accentColor = accentColor,
                    dynamicThemeColor = dynamicThemeColor,
                    customThemeColor = customThemeColor,
                    solidBackgroundEnabled = solidBackgroundEnabled,
                    fontScale = fontScale
                ) {
                    CompositionLocalProvider(LocalBannerAlpha provides alpha) {
                        WpVolumeControl(
                            fillHeight = false,
                            onHidden = { detach() },
                            ownWindow = true,
                            onShown = window::setShown
                        )
                    }
                }
            }
        }

        try {
            window.show(view)
            banner = window
            lifecycleOwner = owner
            owner.onResume()
            ZuneLog.d(TAG, "attached volume overlay")
        } catch (e: Exception) {
            ZuneLog.e(TAG, "show failed", e)
            window.dismiss()
            owner.onDestroy()
        }
    }

    private fun detach() {
        val window = banner
        val owner = lifecycleOwner
        if (window != null) ZuneLog.d(TAG, "detach volume overlay")
        banner = null
        lifecycleOwner = null
        window?.dismiss()
        owner?.onDestroy()
    }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun onResume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
