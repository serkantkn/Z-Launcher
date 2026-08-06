package com.serkantkn.zunelauncher.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.serkantkn.zunelauncher.ui.components.WpActionCenterPanel
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneExtendedColors

/**
 * System Overlay Manager for Windows Phone Action Center.
 * 1. Attaches a thin top-edge gesture detector (WpTopEdgeTriggerOverlay) on top of screen across apps.
 * 2. Pulling down on top edge expands full WpActionCenterPanel on top of any active third-party app.
 */
object WpActionCenterOverlayManager {
    private var windowManager: WindowManager? = null
    private var triggerView: ComposeView? = null
    private var panelOverlayView: ComposeView? = null
    private var triggerLifecycleOwner: CustomOverlayLifecycleOwner? = null
    private var panelLifecycleOwner: CustomOverlayLifecycleOwner? = null

    var isPanelExpanded: Boolean = false
        private set

    fun startTopEdgeTrigger(context: Context, isWideScreen: Boolean, zuneColors: ZuneExtendedColors = ZuneExtendedColors()) {
        if (!Settings.canDrawOverlays(context)) return

        stopTopEdgeTrigger()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val lifecycle = CustomOverlayLifecycleOwner()
        lifecycle.onCreate()
        triggerLifecycleOwner = lifecycle

        val triggerHeightPx = (54 * context.resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            triggerHeightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
            x = 0
            y = 0
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                CompositionLocalProvider(
                    LocalZuneColors provides zuneColors,
                    LocalIsWideScreen provides isWideScreen
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(Color.Transparent)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 8f && !isPanelExpanded) {
                                        collapseStatusBar(context)
                                        expandPanelOverlay(context, isWideScreen, zuneColors)
                                    }
                                }
                            }
                    )
                }
            }
        }

        triggerView = composeView
        try {
            wm.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun expandPanelOverlay(context: Context, isWideScreen: Boolean, zuneColors: ZuneExtendedColors = ZuneExtendedColors()) {
        if (!Settings.canDrawOverlays(context)) return

        collapseStatusBar(context)
        closePanelOverlay()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val lifecycle = CustomOverlayLifecycleOwner()
        lifecycle.onCreate()
        panelLifecycleOwner = lifecycle

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
            x = 0
            y = 0
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                CompositionLocalProvider(
                    LocalZuneColors provides zuneColors,
                    LocalIsWideScreen provides isWideScreen
                ) {
                    WpActionCenterPanel(
                        isOpen = true,
                        onClose = { closePanelOverlay() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        panelOverlayView = composeView
        isPanelExpanded = true
        try {
            wm.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun closePanelOverlay() {
        try {
            panelOverlayView?.let { view ->
                windowManager?.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            panelOverlayView = null
            panelLifecycleOwner?.onDestroy()
            panelLifecycleOwner = null
            isPanelExpanded = false
        }
    }

    fun stopTopEdgeTrigger() {
        closePanelOverlay()
        try {
            triggerView?.let { view ->
                windowManager?.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            triggerView = null
            triggerLifecycleOwner?.onDestroy()
            triggerLifecycleOwner = null
        }
    }

    private fun collapseStatusBar(context: Context) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val collapse = statusBarManager.getMethod("collapsePanels")
            collapse.invoke(statusBarService)
        } catch (e: Exception) {
            try {
                val statusBarService = context.getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val collapse = statusBarManager.getMethod("collapse")
                collapse.invoke(statusBarService)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}
