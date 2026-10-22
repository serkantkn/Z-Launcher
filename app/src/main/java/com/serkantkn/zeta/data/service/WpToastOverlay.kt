package com.serkantkn.zeta.data.service

import com.serkantkn.zeta.ui.animation.installZetaMotion
import com.serkantkn.zeta.util.localized
import com.serkantkn.zeta.util.ZuneLog
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import com.serkantkn.zeta.data.model.NotificationStyle
import com.serkantkn.zeta.data.model.SocialMessageModel
import com.serkantkn.zeta.data.model.ThemeMode
import com.serkantkn.zeta.data.repository.SocialRepository
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.ui.components.LocalBannerAlpha
import com.serkantkn.zeta.ui.components.WpToastNotification
import com.serkantkn.zeta.ui.components.bannerAlpha
import com.serkantkn.zeta.ui.components.bannerBlurPx
import com.serkantkn.zeta.ui.theme.ZuneLauncherTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shows the Windows Phone toast banner ON TOP OF OTHER APPS.
 *
 * The launcher itself renders WpToastNotification inside LauncherScreen while it is in the
 * foreground; this object covers every other moment (WhatsApp open, another app in front, the
 * launcher activity destroyed) by hosting the same composable in a window over them: an
 * accessibility overlay while the launcher's accessibility service runs, otherwise a
 * TYPE_APPLICATION_OVERLAY ("üstte gösterim" / SYSTEM_ALERT_WINDOW), which Android announces.
 *
 * The window starts non-focusable so the app underneath keeps its keyboard and back button;
 * it becomes focusable only while the quick-reply field is open.
 *
 * Note: apps that call Window.setHideOverlayWindows(true) (the system Settings app, some
 * banking apps) make Android hide every overlay while they are in front; the banner is still
 * recorded in the Social hub and shows in the launcher the next time it is opened.
 */
object WpToastOverlay {

    private const val TAG = "WpToastOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val currentMessage = MutableStateFlow<SocialMessageModel?>(null)

    private var banner: BannerWindow? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    /** How wide the card is on a wide screen, as the launcher draws it. */
    private const val CARD_WIDTH_DP = 380

    fun isAvailable(context: Context): Boolean =
        ZuneKeyAccessibilityService.current != null ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /**
     * Where a window over other apps is drawn: as an accessibility overlay while the launcher's
     * accessibility service runs - Android says nothing about those - and only failing that as a
     * "draw over other apps" window, which Android announces with a notification of its own for
     * as long as it is up.
     */
    internal fun overlayHost(appContext: Context): Pair<Context, Int> {
        val service = ZuneKeyAccessibilityService.current
        return if (service != null) {
            service to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            appContext to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            }
        }
    }

    /** Entry point used by SocialRepository for every new toast-worthy message. */
    fun show(context: Context, message: SocialMessageModel) {
        val appContext = context.applicationContext
        if (!isAvailable(appContext)) {
            ZuneLog.w(TAG, "show: overlay permission missing")
            return
        }
        // The launcher draws its own banner while it is on screen.
        if (SocialRepository.isLauncherForeground) {
            ZuneLog.d(TAG, "show: launcher in foreground, LauncherScreen handles the toast")
            return
        }

        scope.launch {
            val style = try {
                appContext.appContainer.settingsDataStore.notificationStyle.first()
            } catch (e: Exception) {
                NotificationStyle.WINDOWS_PHONE
            }
            if (style != NotificationStyle.WINDOWS_PHONE) {
                ZuneLog.d(TAG, "show: notification style is $style, skipping")
                return@launch
            }
            attachOrUpdate(appContext, message)
        }
    }

    /** Hides the banner immediately (e.g. the launcher came back to the foreground). */
    fun hide() {
        mainHandler.post { detach() }
    }

    private fun attachOrUpdate(appContext: Context, message: SocialMessageModel) {
        currentMessage.value = message
        if (banner != null) return  // already showing: the composable swaps content by message.id

        val (host, type) = overlayHost(appContext)
        val window = runCatching { BannerWindow(host, type) }
            .onFailure { ZuneLog.e(TAG, "the window could not be made", it) }
            .getOrNull() ?: run {
                currentMessage.value = null
                return
            }
        window.placeAsCard(appContext, CARD_WIDTH_DP)
        val owner = OverlayLifecycleOwner().also { it.onCreate() }

        val view = ComposeView(appContext.localized()).apply {
            installZetaMotion(owner.lifecycle)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val container = appContext.appContainer
                val settings = container.settingsDataStore
                val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
                val accentColor by settings.accentColor.collectAsState(initial = AccentColor.MAGENTA)
                val dynamicThemeColor by settings.dynamicThemeColor.collectAsState(initial = null)
                val customThemeColor by settings.customThemeColor.collectAsState(initial = null)
                val solidBackgroundEnabled by settings.solidBackgroundEnabled.collectAsState(initial = false)
                val fontScale by settings.fontScale.collectAsState(initial = 1.0f)
                val tileBlur by settings.tileBlur.collectAsState(initial = 0)
                val tileOpacity by settings.tileOpacity.collectAsState(initial = 100)
                val shown by currentMessage.collectAsState()
                // Frosted as the tiles are - but only where the phone will blur behind the window:
                // see-through over an app without it would only be hard to read.
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
                        WpToastNotification(
                            message = shown,
                            onDismiss = {
                                if (currentMessage.value?.id == shown?.id) {
                                    SocialRepository.clearToast()
                                    currentMessage.value = null
                                    detach()
                                }
                            },
                            onExpandedChange = { expanded -> window.setFocusable(expanded) },
                            fillHeight = false,
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
            ZuneLog.d(TAG, "attached overlay for ${message.appName}: ${message.title}")
        } catch (e: Exception) {
            ZuneLog.e(TAG, "show failed", e)
            window.dismiss()
            owner.onDestroy()
            currentMessage.value = null
        }
    }

    private fun detach() {
        if (banner != null) ZuneLog.d(TAG, "detach overlay")
        val window = banner
        val owner = lifecycleOwner
        banner = null
        lifecycleOwner = null
        currentMessage.value = null
        window?.dismiss()
        owner?.onDestroy()
    }

    /** Minimal lifecycle / saved-state / ViewModel owner so ComposeView can live in a raw window. */
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


