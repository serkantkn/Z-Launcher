package com.serkantkn.zeta.data.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import com.serkantkn.zeta.util.ZuneLog

/**
 * An invisible strip laid over the system status bar, so that a pull from the top of the screen
 * opens the Windows Phone action center instead of Android's notification shade.
 *
 * Only an accessibility service may do this. An ordinary overlay window
 * (TYPE_APPLICATION_OVERLAY) is kept *below* the status bar by the window manager, so a finger
 * there reaches SystemUI first; an accessibility overlay sits above the status bar and gets the
 * touch before it does. The strip takes every touch that lands on it, which is what stops the
 * system shade from ever starting its drag; a pull past the slop hands the gesture to
 * [ActionCenterOverlay], which follows the finger.
 *
 * While an app is full screen — a video, a game — the status bar is hidden and the strip would
 * only swallow the top of that app, so it stops being touchable until the bar is shown again.
 */
object StatusBarShield {

    private const val TAG = "StatusBarShield"

    private var windowManager: WindowManager? = null
    private var strip: StripView? = null
    private var params: WindowManager.LayoutParams? = null
    private var touchable = true

    val isAttached: Boolean get() = strip != null

    /** Lays the strip over the status bar. Must be given the accessibility service itself. */
    fun attach(service: AccessibilityService) {
        if (strip != null) {
            remeasure(service)
            return
        }
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight(service, wm),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                // The strip must not be pushed down below the bar it is there to cover.
                fitInsetsTypes = 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "ZuneStatusBarShield"
        }
        val view = StripView(service)
        try {
            wm.addView(view, lp)
            windowManager = wm
            strip = view
            params = lp
            touchable = true
            ZuneLog.d(TAG, "attached, height=${lp.height}")
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the strip could not be laid over the status bar", e)
        }
    }

    fun detach() {
        val wm = windowManager
        val view = strip
        strip = null
        params = null
        windowManager = null
        if (wm != null && view != null) {
            runCatching { wm.removeViewImmediate(view) }
                .onFailure { ZuneLog.e(TAG, "detach failed", it) }
        }
    }

    /** The bar's height changes with rotation, the cutout and the display size. */
    fun remeasure(context: Context) {
        val wm = windowManager ?: return
        val view = strip ?: return
        val lp = params ?: return
        val height = statusBarHeight(context, wm)
        if (lp.height == height) return
        lp.height = height
        runCatching { wm.updateViewLayout(view, lp) }
    }

    /**
     * Whether the strip answers touches. Off while the status bar is hidden, so a full-screen
     * app keeps the top of its own screen.
     *
     * The strip cannot learn this from its own insets: it sits above the status bar, so the bar
     * never insets it and always reads as absent. The accessibility window list does know —
     * see [isStatusBarShown].
     */
    fun setTouchable(on: Boolean) {
        if (touchable == on) return
        val wm = windowManager ?: return
        val view = strip ?: return
        val lp = params ?: return
        touchable = on
        lp.flags = if (on) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { wm.updateViewLayout(view, lp) }
        ZuneLog.d(TAG, "touchable=$on")
    }

    /**
     * Whether the status bar is on screen, from the accessibility window list: a system window
     * across the top of the screen. A full-screen app pushes it up off the screen and then out
     * of the list. A navigation bar on the side in landscape also starts at the top, so the
     * window must be wider than half the screen to count.
     */
    fun isStatusBarShown(windows: List<android.view.accessibility.AccessibilityWindowInfo>, screenWidth: Int): Boolean {
        val stripHeight = params?.height ?: return true
        return windows.any { w ->
            if (w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM) return@any false
            val r = android.graphics.Rect().also { w.getBoundsInScreen(it) }
            r.top <= 0 && r.bottom >= stripHeight / 2 && r.width() > screenWidth / 2
        }
    }

    private fun statusBarHeight(context: Context, wm: WindowManager): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = wm.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
            if (insets.top > 0) return insets.top
        }
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val fromResource = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        return if (fromResource > 0) fromResource else (24 * context.resources.displayMetrics.density).toInt()
    }

    /**
     * The strip itself: a plain view, nothing drawn. A gesture that starts on it stays with it
     * until the finger lifts, however far down the finger goes, so the whole pull is ours.
     */
    @SuppressLint("ViewConstructor")
    private class StripView(private val service: AccessibilityService) : View(service) {

        private val slop = ViewConfiguration.get(service).scaledTouchSlop
        private var downY = 0f
        private var pulling = false
        private var tracker: VelocityTracker? = null

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    pulling = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(event)
                    if (!pulling && event.rawY - downY > slop) {
                        pulling = true
                        ActionCenterOverlay.beginDrag(service)
                    }
                    if (pulling) ActionCenterOverlay.dragTo(event.rawY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000)
                    val velocity = tracker?.yVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        // A quick pull from the very top edge is the system's "swipe from top"
                        // gesture: with the status bar showing, Android hands the rest of the
                        // touch to the status bar (DisplayPolicy.requestTransientBars) and nothing
                        // of it comes back to us; no window can refuse that. On a quick pull it
                        // happens before the first move has even reached us, so any cancel here
                        // is taken as a pull that was stolen: the panel finishes the way by
                        // itself and the shade now under the finger is pushed back up.
                        ActionCenterOverlay.open(service)
                        ActionCenterOverlay.coverShade()
                        (service as? ZuneKeyAccessibilityService)?.pushShadeAway()
                    } else if (pulling) {
                        ActionCenterOverlay.endDrag(velocity)
                    }
                    pulling = false
                }
            }
            // Every touch is kept, taps included: anything let through would reach the status
            // bar underneath and could start the system shade after all.
            return true
        }
    }
}
