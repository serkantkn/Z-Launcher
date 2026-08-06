package com.serkantkn.zunelauncher.data.plugin

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Manages detection and interaction with the external Zune Plugin APK (com.serkantkn.zplugin).
 * Lock Screen and System-wide Notification Panel features are enabled when this plugin APK is installed on the device.
 */
object PluginManager {
    const val PLUGIN_PACKAGE_NAME = "com.serkantkn.zplugin"

    /**
     * Checks if the Zune Extras Plugin APK (com.serkantkn.zplugin) is installed on the user's device.
     */
    fun isPluginInstalled(context: Context): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    PLUGIN_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Launches the Zune Plugin configuration activity or opens install guidance.
     */
    fun launchPluginOrInstall(context: Context) {
        if (isPluginInstalled(context)) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(PLUGIN_PACKAGE_NAME)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } else {
            // Open market or setting guidance
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$PLUGIN_PACKAGE_NAME")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to web search / intent if Play Store app is unavailable
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$PLUGIN_PACKAGE_NAME")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(webIntent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }
}
