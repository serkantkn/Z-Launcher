package com.serkantkn.zeta.util

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process

/**
 * The shortcuts an app publishes — "new message", "take a selfie" — the way a held icon shows
 * them on any Android home screen.
 *
 * The phone hands them only to its default launcher, so every call is guarded: a launcher that
 * is not the home yet, or an app that publishes none, simply gets an empty list and the tile
 * behaves as it always did. Widgets, pin requests and work-profile apps are not taken on here.
 */
object AppShortcuts {

    private const val TAG = "AppShortcuts"

    /** How many of an app's shortcuts are offered; the phone's own menus stop at about this. */
    private const val MAX_SHOWN = 6

    fun of(context: Context, packageName: String): List<ShortcutInfo> = try {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        launcherApps.getShortcuts(query, Process.myUserHandle())
            .orEmpty()
            .filter { it.isEnabled }
            // Manifest shortcuts first, in the order the app ranked them.
            .sortedWith(compareByDescending<ShortcutInfo> { it.isDeclaredInManifest }.thenBy { it.rank })
            .take(MAX_SHOWN)
    } catch (e: Exception) {
        ZuneLog.w(TAG, "shortcuts of $packageName unavailable", e)
        emptyList()
    }

    fun icon(context: Context, shortcut: ShortcutInfo): Drawable? = try {
        context.getSystemService(LauncherApps::class.java)
            ?.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
    } catch (e: Exception) {
        ZuneLog.w(TAG, "shortcut icon unavailable", e)
        null
    }

    /** True when the shortcut was started; false when the phone would not have it. */
    fun start(context: Context, shortcut: ShortcutInfo): Boolean = try {
        context.getSystemService(LauncherApps::class.java)
            ?.startShortcut(shortcut.`package`, shortcut.id, null, null, Process.myUserHandle())
        true
    } catch (e: Exception) {
        ZuneLog.w(TAG, "shortcut ${shortcut.id} would not start", e)
        false
    }
}
