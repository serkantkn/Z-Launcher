package com.serkantkn.zunelauncher.data.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
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
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.components.WpVolumeControl
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme

/**
 * Hosts the Windows Phone volume bar in a system overlay window so it can appear on top of
 * other apps (driven by ZuneKeyAccessibilityService). Mirrors WpToastOverlay: non-focusable,
 * wrap-content, attached only while the bar is visible.
 */
object WpVolumeOverlay {

    private const val TAG = "WpVolumeOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

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
        if (overlayView != null) return

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = ComposeView(appContext).apply {
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

                ZuneLauncherTheme(
                    themeMode = themeMode,
                    accentColor = accentColor,
                    dynamicThemeColor = dynamicThemeColor,
                    customThemeColor = customThemeColor,
                    solidBackgroundEnabled = solidBackgroundEnabled
                ) {
                    WpVolumeControl(
                        fillHeight = false,
                        onHidden = { detach() }
                    )
                }
            }
        }

        try {
            wm.addView(view, params)
            windowManager = wm
            overlayView = view
            lifecycleOwner = owner
            owner.onResume()
            android.util.Log.d(TAG, "attached volume overlay")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "addView failed", e)
            owner.onDestroy()
        }
    }

    private fun detach() {
        val wm = windowManager
        val view = overlayView
        val owner = lifecycleOwner
        if (view != null) android.util.Log.d(TAG, "detach volume overlay")
        overlayView = null
        lifecycleOwner = null
        windowManager = null
        if (wm != null && view != null) {
            try {
                if (view.isAttachedToWindow || view.windowToken != null) wm.removeViewImmediate(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
