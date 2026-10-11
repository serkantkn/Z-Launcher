package com.serkantkn.zunelauncher.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.serkantkn.zunelauncher.data.service.ZuneKeyAccessibilityService

/**
 * What the action center leans on, and the system page where each is granted.
 *
 * Only the accessibility service is strictly needed: without it nothing can sit over the status
 * bar. The rest each light up one part of the panel.
 */
object ActionCenterPermissions {

    const val SERVICE = "service"
    const val APP_INFO = "app_info"
    const val NOTIFICATIONS = "notifications"
    const val OVERLAY = "overlay"
    const val WRITE_SETTINGS = "write_settings"
    const val DND = "dnd"
    const val BATTERY = "battery"

    data class Status(
        val service: Boolean,
        val notifications: Boolean,
        val overlay: Boolean,
        val writeSettings: Boolean,
        val dnd: Boolean,
        val battery: Boolean
    )

    fun read(context: Context): Status {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return Status(
            service = ZuneKeyAccessibilityService.isEnabled(context),
            notifications = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
            overlay = Settings.canDrawOverlays(context),
            writeSettings = Settings.System.canWrite(context),
            dnd = nm.isNotificationPolicyAccessGranted,
            battery = power.isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    fun open(context: Context, which: String) {
        val pkg = Uri.parse("package:${context.packageName}")
        val intent = when (which) {
            SERVICE -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            APP_INFO -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            NOTIFICATIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            // The app's own page: on Samsung the battery choice ("unrestricted") is one tap from there.
            BATTERY -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            else -> return
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { ZuneLog.w("ActionCenterPermissions", "could not open $which", it) }
    }
}
