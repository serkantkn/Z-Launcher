package com.serkantkn.zunelauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.serkantkn.zunelauncher.data.datastore.SettingsDataStore
import com.serkantkn.zunelauncher.data.repository.SettingsRepository
import com.serkantkn.zunelauncher.ui.theme.ZuneExtendedColors
import com.serkantkn.zunelauncher.ui.theme.toColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * System Accessibility Service to intercept system status bar expansion across ALL Android applications
 * (including Samsung One UI, Xiaomi MIUI, Google Pixel) and open the Windows Phone Action Center Panel instead.
 */
class ZuneNotificationAccessibilityService : AccessibilityService() {

    private var lastTriggerTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString()?.lowercase() ?: ""

        // 1. CRITICAL: Never trigger when inside Zune Launcher itself!
        if (pkgName == packageName.lowercase() || pkgName.contains("zunelauncher")) {
            return
        }

        // 2. Detect System UI or Samsung Quick Panel status bar expansion
        if (pkgName == "com.android.systemui" ||
            pkgName.contains("systemui") ||
            pkgName.contains("quickpanel")
        ) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = event.className?.toString() ?: ""
                
                val isNotificationPanel = className.contains("Notification") ||
                        className.contains("StatusBar") ||
                        className.contains("QuickPanel") ||
                        className.contains("Shade") ||
                        className.contains("Panel") ||
                        className.contains("Expanded")

                if (isNotificationPanel) {
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerTime < 800) return
                    lastTriggerTime = now

                    Log.d("ZuneAccessibility", "Intercepted SystemUI notification shade on $pkgName ($className)")

                    // Immediately dismiss Android system notification shade
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                    }
                    collapseStatusBar(this)

                    // Fetch current accent color & theme from DataStore and open WP Action Center Overlay
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val repository = SettingsRepository(SettingsDataStore(applicationContext))
                            val accentEnum = repository.accentColor.first()
                            val themeMode = repository.themeMode.first()
                            val resolvedColor = accentEnum.toColor()
                            val zuneColors = ZuneExtendedColors(
                                accentColor = resolvedColor,
                                isDark = themeMode != com.serkantkn.zunelauncher.data.model.ThemeMode.LIGHT
                            )
                            WpActionCenterOverlayManager.expandPanelOverlay(
                                context = applicationContext,
                                isWideScreen = isWideScreen(),
                                zuneColors = zuneColors
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {}

    private fun isWideScreen(): Boolean {
        return resources.configuration.smallestScreenWidthDp >= 600
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
