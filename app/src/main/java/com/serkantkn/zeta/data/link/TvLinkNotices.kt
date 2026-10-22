package com.serkantkn.zeta.data.link

import com.serkantkn.zeta.ui.animation.installZetaMotion
import android.content.Context
import android.graphics.PixelFormat
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
import com.serkantkn.zeta.data.service.WpToastOverlay
import com.serkantkn.zeta.ui.theme.ZuneLauncherTheme
import com.serkantkn.zeta.ui.tv.TvLinkNoticeCard
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.localized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A notice on the TV: what the phone said, and which phone said it. */
internal data class ShownNotice(
    val notice: LinkNotice,
    val phone: String,
    val shownAt: Long = System.currentTimeMillis(),
    /** A sign of its own, for the TV's own news; a phone's notices are signed by their kind. */
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null
)

/**
 * The corner of the TV where a paired phone's calls and messages show, over whatever is on: a
 * film, another app, Start. Drawn as an accessibility overlay while the launcher's service runs
 * (it does on a TV that gives Zeta the Home key), or as a "display over other apps" window
 * when that is allowed; failing both, Start shows it while it is on the screen.
 *
 * It never takes the remote: a notice is read, not answered.
 */
internal object TvLinkNotices {
    private const val TAG = "TvLinkNotices"
    private val main = Handler(Looper.getMainLooper())

    private val _shown = MutableStateFlow<ShownNotice?>(null)
    val shown: StateFlow<ShownNotice?> = _shown.asStateFlow()

    /** True while the notice is in its own window; Start then leaves it to that. */
    private val _inWindow = MutableStateFlow(false)
    val inWindow: StateFlow<Boolean> = _inWindow.asStateFlow()

    private var view: ComposeView? = null
    private var owner: Owner? = null
    private var windowManager: WindowManager? = null
    private val hideLater = Runnable { hideNow() }

    fun show(context: Context, notice: LinkNotice, phone: String) {
        main.post { showNow(context, notice, phone) }
    }

    /**
     * The TV's own news in the same corner, as the Xbox told of an achievement: [title] large,
     * [text] under it, [header] above (Media Center) or after it (Xbox).
     */
    fun showSystem(context: Context, title: String, text: String, header: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        main.post { showNow(context, LinkNotice(LinkNotice.Kind.SYSTEM, title, text = text, app = header), "", icon) }
    }

    private fun showNow(context: Context, notice: LinkNotice, phone: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
        val app = context.applicationContext
        when (notice.kind) {
            // The call was answered: the notice goes.
            LinkNotice.Kind.CALL_ENDED -> {
                if (_shown.value?.notice?.kind == LinkNotice.Kind.CALL) hideNow()
                return
            }
            else -> _shown.value = ShownNotice(notice, phone, icon = icon)
        }
        main.removeCallbacks(hideLater)
        main.postDelayed(hideLater, if (notice.kind == LinkNotice.Kind.CALL) CALL_MILLIS else MESSAGE_MILLIS)
        if (view == null) attach(app)
        runCatching {
            com.serkantkn.zeta.ui.tv.VistaSounds.play(app, com.serkantkn.zeta.ui.tv.VistaSounds.Kind.SELECT)
        }
    }

    fun hide() {
        main.post { hideNow() }
    }

    private fun hideNow() {
        main.removeCallbacks(hideLater)
        _shown.value = null
        detach()
    }

    private fun attach(app: Context) {
        if (!WpToastOverlay.isAvailable(app)) return
        val (host, type) = WpToastOverlay.overlayHost(app)
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lifecycle = Owner().also { it.create() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Media Center's in the bottom right corner; the Xbox's along the bottom, in the middle.
            gravity = if (com.serkantkn.zeta.ui.tv.TvDesign.vista.value) Gravity.BOTTOM or Gravity.END
            else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val compose = ComposeView(app.localized()).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            installZetaMotion(lifecycle.lifecycle)
            setContent {
                val shown by this@TvLinkNotices.shown.collectAsState()
                ZuneLauncherTheme { TvLinkNoticeCard(shown) }
            }
        }
        try {
            wm.addView(compose, params)
            view = compose
            owner = lifecycle
            windowManager = wm
            lifecycle.resume()
            _inWindow.value = true
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the notice window could not be put up", e)
            lifecycle.destroy()
        }
    }

    private fun detach() {
        val wm = windowManager
        val v = view
        view = null
        windowManager = null
        _inWindow.value = false
        if (wm != null && v != null) runCatching { wm.removeViewImmediate(v) }
        owner?.destroy()
        owner = null
    }

    private class Owner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        fun create() {
            saved.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun resume() = registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    /** A call shows while it rings (half a minute at most); a message for a few seconds. */
    private const val CALL_MILLIS = 30_000L
    private const val MESSAGE_MILLIS = 9_000L
}
