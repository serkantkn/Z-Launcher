package com.serkantkn.zunelauncher.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
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
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.repository.ActionCenterNotifications
import com.serkantkn.zunelauncher.data.repository.PanelNotification
import com.serkantkn.zunelauncher.data.repository.SocialRepository
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.components.QuickReplySheet
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localized

/**
 * The reply box for a notification answered from the action center.
 *
 * It cannot live inside the action center: that is an accessibility overlay, which the window
 * manager draws above the keyboard, so a text field there would be typed into blind. This is an
 * ordinary overlay window across the top of the screen, below the keyboard, which is what the
 * launcher's notification cards already use.
 */
object QuickReplyOverlay {

    private const val TAG = "QuickReply"

    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var owner: Owner? = null

    /** Shows the box for [item]. Returns false when nothing may draw it. */
    fun show(context: Context, item: PanelNotification): Boolean {
        val reply = item.reply ?: return false
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return false
        hide()
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "ZuneQuickReply"
        }
        val lifecycle = Owner().also { it.create() }
        val compose = ComposeView(appContext.localized()).apply {
            setContent {
                val settings = appContext.appContainer.settingsDataStore
                val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
                val accentColor by settings.accentColor.collectAsState(initial = AccentColor.MAGENTA)
                val dynamicThemeColor by settings.dynamicThemeColor.collectAsState(initial = null)
                val customThemeColor by settings.customThemeColor.collectAsState(initial = null)
                ZuneLauncherTheme(
                    themeMode = themeMode,
                    accentColor = accentColor,
                    dynamicThemeColor = dynamicThemeColor,
                    customThemeColor = customThemeColor,
                    solidBackgroundEnabled = true
                ) {
                    QuickReplySheet(
                        appName = item.appName,
                        title = item.title,
                        text = item.text,
                        onSend = { message ->
                            val sent = SocialRepository.sendReply(appContext, reply, message)
                            Toast.makeText(
                                appContext.localized(),
                                if (sent) R.string.action_center_reply_sent else R.string.action_center_reply_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            if (sent) ActionCenterNotifications.refreshNow()
                            hide()
                        },
                        onCancel = { hide() }
                    )
                }
            }
        }
        val frame = OutsideAwareFrame(appContext).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            addView(compose)
        }
        return try {
            wm.addView(frame, lp)
            windowManager = wm
            root = frame
            owner = lifecycle
            lifecycle.resume()
            true
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the reply box could not be shown", e)
            lifecycle.destroy()
            false
        }
    }

    fun hide() {
        val wm = windowManager
        val view = root
        val lifecycle = owner
        windowManager = null
        root = null
        owner = null
        if (wm != null && view != null) runCatching { wm.removeViewImmediate(view) }
        lifecycle?.destroy()
    }

    /** A touch outside the box, or Back, puts it away unsent. */
    @SuppressLint("ViewConstructor")
    private class OutsideAwareFrame(context: Context) : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                hide()
                return true
            }
            return super.dispatchTouchEvent(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) hide()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private class Owner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun create() {
            savedState.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun resume() = registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
