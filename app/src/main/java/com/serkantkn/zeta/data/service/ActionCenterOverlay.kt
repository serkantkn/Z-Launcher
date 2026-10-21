package com.serkantkn.zeta.data.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.serkantkn.zeta.ui.components.ActionCenterPanel
import com.serkantkn.zeta.ui.theme.ZuneLauncherTheme
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.localized

/**
 * The Windows Phone action center's window: a full-screen sheet that comes down from the top.
 *
 * It is drawn as an accessibility overlay when the launcher's accessibility service is running,
 * which puts it over the status bar and over every app; failing that, as a panel of the
 * launcher's own window, which is where it is pulled from then - an ordinary "over other apps"
 * overlay is the last resort, since Android announces one with a notification each time.
 *
 * How far down it is lives in [progress] — 0 put away, 1 fully open. A pull on the status-bar
 * strip drives it directly ([beginDrag], [dragTo], [endDrag]); the launcher's own pull, a tap
 * that should open it, and the safety net that catches the system shade use [open].
 *
 * Building the window and composing the panel takes a few hundred milliseconds on a slower
 * tablet — long enough for the pull to look ignored. So while the accessibility service runs
 * the window is built once, ahead of time ([prepare]), and kept hidden between uses: a pull
 * only has to show it.
 */
object ActionCenterOverlay {

    private const val TAG = "ActionCenter"
    private const val SETTLE_MILLIS = 260L
    private const val COVER_HOLD_MILLIS = 700L
    private const val COVER_FADE_MILLIS = 350L

    /** 0 = away, 1 = open. Read by the panel to place itself. */
    val progress = mutableFloatStateOf(0f)

    /** How dark the dimmed part of the screen is held while Android's shade is pushed away. */
    private val cover = mutableFloatStateOf(0f)
    private var coverAnimator: ValueAnimator? = null

    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayLifecycleOwner? = null
    private var animator: ValueAnimator? = null
    private var heightPx = 1f
    private var showing = false
    private var keptByService = false
    private var panelLocale: java.util.Locale? = null

    /** Whether the panel is in use; while it is not, its clock and music card stand still. */
    private val active = androidx.compose.runtime.mutableStateOf(false)

    val isShowing: Boolean get() = showing
    val isOpen: Boolean get() = showing && progress.floatValue > 0f

    /** Whether anything at all can draw the panel from [context] right now. */
    fun isAvailable(context: Context): Boolean =
        ZuneKeyAccessibilityService.current != null || Settings.canDrawOverlays(context)

    // ── Driven by a finger on the status-bar strip ──────────────────────────────

    fun beginDrag(context: Context) {
        animator?.cancel()
        if (!attach(context)) return
    }

    fun dragTo(rawY: Float) {
        if (!showing) return
        progress.floatValue = (rawY / heightPx).coerceIn(0f, 1f)
    }

    fun endDrag(velocityY: Float) {
        if (!showing) return
        val open = when {
            velocityY > FLING -> true
            velocityY < -FLING -> false
            else -> progress.floatValue > OPEN_PAST
        }
        ZuneLog.d(TAG, "release at ${progress.floatValue} v=$velocityY -> ${if (open) "open" else "away"}")
        settle(if (open) 1f else 0f)
    }

    // ── Asked for outright ──────────────────────────────────────────────────────

    /** Brings the panel down. Returns false if nothing on this phone may draw it. */
    fun open(context: Context): Boolean {
        // A television has no status bar to pull and no shade to replace.
        if (com.serkantkn.zeta.ui.theme.isTelevision(context)) return false
        if (!attach(context)) return false
        settle(1f)
        return true
    }

    /**
     * Android's shade has the finger and is coming down under the panel; on a tablet it would
     * show through the dimmed part of the screen. That part goes nearly black until the shade
     * has been pushed back up, then lightens again.
     */
    fun coverShade() {
        coverAnimator?.cancel()
        cover.floatValue = 1f
        coverAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = COVER_HOLD_MILLIS
            duration = COVER_FADE_MILLIS
            addUpdateListener { cover.floatValue = it.animatedValue as Float }
            start()
        }
    }

    fun close() {
        if (!showing) return
        settle(0f)
    }

    // ── Window ──────────────────────────────────────────────────────────────────

    /** Builds the hidden window ahead of the first pull. Called by the accessibility service. */
    fun prepare(context: Context) {
        ensureWindow(context)
    }

    /** Takes the window down for good: the service is going, or the action center was switched off. */
    fun release() {
        animator?.cancel()
        animator = null
        val wm = windowManager
        val view = root
        val lifecycle = owner
        root = null
        params = null
        owner = null
        windowManager = null
        showing = false
        keptByService = false
        active.value = false
        progress.floatValue = 0f
        if (wm != null && view != null) {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { ZuneLog.e(TAG, "release failed", it) }
        }
        lifecycle?.onDestroy()
    }

    /** Shows the (perhaps already built) window, still put away, ready to be pulled down. */
    private fun attach(context: Context): Boolean {
        if (showing) return true
        if (!ensureWindow(context)) return false
        val wm = windowManager ?: return false
        heightPx = screenHeight(wm, context).toFloat().coerceAtLeast(1f)
        progress.floatValue = 0f
        showing = true
        active.value = true
        root?.visibility = android.view.View.VISIBLE
        return true
    }

    private fun ensureWindow(context: Context): Boolean {
        // A window built in another language (the user has changed it since) is built again.
        val locale = appContextLocalized(context).resources.configuration.locales[0]
        if (root != null && !showing && panelLocale != locale) release()
        if (root != null) return true
        val service = ZuneKeyAccessibilityService.current
        // Without the accessibility service the panel can only be pulled from inside the
        // launcher, so it is laid over the launcher's own window as a panel of it. Drawing it
        // "over other apps" instead had Android post its "Zeta is displaying over other
        // apps" notice every time the action center came down.
        val activity = if (service == null) attachedActivityOf(context) else null
        val host: Context = service ?: activity ?: context.applicationContext
        val type = when {
            service != null -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            activity != null -> WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            Settings.canDrawOverlays(host) -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> {
                ZuneLog.w(TAG, "nothing may draw the action center")
                return false
            }
        }
        val wm = (activity?.windowManager ?: host.getSystemService(Context.WINDOW_SERVICE)) as WindowManager
        heightPx = screenHeight(wm, host).toFloat().coerceAtLeast(1f)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Neither focusable nor touchable while it is still coming down: a window that
            // could take touches, appearing under a finger, makes the system cancel the pull
            // the strip is following. It takes both once it has settled open.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "ZuneActionCenter"
            // A panel belongs to the window it is laid over.
            if (activity != null) token = activity.window.decorView.windowToken
        }

        val lifecycle = OverlayLifecycleOwner().also { it.onCreate() }
        val appContext = context.applicationContext
        // The launcher's own language, not the phone's: the service context only lends the window.
        val compose = ComposeView(appContextLocalized(context)).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                val settings = appContext.appContainer.settingsDataStore
                val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.DARK)
                val accentColor by settings.accentColor.collectAsState(initial = AccentColor.MAGENTA)
                val dynamicThemeColor by settings.dynamicThemeColor.collectAsState(initial = null)
                val customThemeColor by settings.customThemeColor.collectAsState(initial = null)
                val fontScale by settings.fontScale.collectAsState(initial = 1.0f)
                ZuneLauncherTheme(
                    themeMode = themeMode,
                    accentColor = accentColor,
                    dynamicThemeColor = dynamicThemeColor,
                    customThemeColor = customThemeColor,
                    solidBackgroundEnabled = true,
                    fontScale = fontScale
                ) {
                    ActionCenterPanel(
                        active = active.value,
                        progress = { progress.floatValue },
                        cover = { cover.floatValue },
                        onDragBy = { fraction -> progress.floatValue = (progress.floatValue + fraction).coerceIn(0f, 1f) },
                        onDragEnd = { velocity -> endDrag(velocity) },
                        onClose = { close() }
                    )
                }
            }
        }
        // Compose looks for its lifecycle from the window's root, not from itself.
        val frame = BackAwareFrame(host).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            addView(compose)
            // Hidden, the window has no surface on screen, but the panel is still composed and
            // laid out inside it, which is the slow part.
            visibility = android.view.View.INVISIBLE
        }

        return try {
            progress.floatValue = 0f
            wm.addView(frame, lp)
            windowManager = wm
            root = frame
            params = lp
            owner = lifecycle
            keptByService = service != null
            panelLocale = locale
            lifecycle.onResume()
            ZuneLog.d(TAG, "built (${when { service != null -> "accessibility overlay"; activity != null -> "launcher panel"; else -> "app overlay" }})")
            true
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the action center window could not be added", e)
            lifecycle.onDestroy()
            false
        }
    }

    /** Put away: hidden and kept for next time when the service holds it, taken down otherwise. */
    private fun detach() {
        animator?.cancel()
        animator = null
        progress.floatValue = 0f
        showing = false
        active.value = false
        setFocusable(false)
        if (keptByService && ZuneKeyAccessibilityService.current != null) {
            root?.visibility = android.view.View.INVISIBLE
        } else {
            release()
        }
    }

    /** Animates to [target]; put away, the window goes with it. */
    private fun settle(target: Float) {
        animator?.cancel()
        if (target <= 0f) setFocusable(false)
        val from = progress.floatValue
        if (from == target) {
            finishedAt(target)
            return
        }
        animator = ValueAnimator.ofFloat(from, target).apply {
            duration = (SETTLE_MILLIS * kotlin.math.abs(target - from)).toLong().coerceAtLeast(80L)
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { progress.floatValue = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) finishedAt(target)
                }
            })
            start()
        }
    }

    private fun finishedAt(target: Float) {
        if (target <= 0f) {
            detach()
        } else {
            // Open: the window takes focus so the Back key reaches it.
            setFocusable(true)
        }
    }

    private fun setFocusable(on: Boolean) {
        val wm = windowManager ?: return
        val view = root ?: return
        val lp = params ?: return
        val both = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val flags = if (on) lp.flags and both.inv() else lp.flags or both
        if (flags == lp.flags) return
        lp.flags = flags
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun appContextLocalized(context: Context): Context = context.applicationContext.localized()

    /** The launcher's activity behind [context], when it is on screen with a window to lay a panel over. */
    private fun attachedActivityOf(context: Context): android.app.Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) {
                val activity = current
                val attached = !activity.isFinishing && !activity.isDestroyed &&
                    activity.window?.decorView?.windowToken != null
                return activity.takeIf { attached }
            }
            current = current.baseContext
        }
        return null
    }

    private fun screenHeight(wm: WindowManager, context: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.height()
        } else {
            context.resources.displayMetrics.heightPixels
        }

    /** The panel's root, which hands the Back key to [close] while the panel has focus. */
    @SuppressLint("ViewConstructor")
    private class BackAwareFrame(context: Context) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) close()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
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

    /** A throw faster than this, in px/s, decides by its direction rather than its distance. */
    private const val FLING = 1200f

    /** Let go past this much of the way down and the panel stays open. */
    private const val OPEN_PAST = 0.3f
}
