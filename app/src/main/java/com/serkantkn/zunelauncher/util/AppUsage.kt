package com.serkantkn.zunelauncher.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Which apps actually get opened.
 *
 * Android will not tell an app this for free: the answer sits behind a special access the user has
 * to grant by hand in system settings, and until they do, every question here comes back empty.
 * That is deliberate on Android's part and it is respected here — the hub asks once, says why, and
 * otherwise carries on without the answer rather than nagging.
 */
object AppUsage {

    private const val TAG = "AppUsage"

    /** Whether the phone will answer at all. */
    fun isGranted(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        // Renamed in Android 10. Asking for the new name on Android 9 is not an exception that
        // can be caught — it is a NoSuchMethodError, and it ends the process.
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps?.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        ZuneLog.w(TAG, "the phone would not say whether usage access is granted", e)
        false
    }

    /** The settings page where the access is given. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * How long each app has been in front over the last [days] days, in milliseconds.
     *
     * Time rather than launch count: a launcher opened forty times for a second each is not what
     * anybody means by "most used", and time in front is the only measure Android offers that
     * matches what people have in mind.
     */
    fun foregroundTimeByPackage(context: Context, days: Int = DEFAULT_DAYS): Map<String, Long> {
        if (!isGranted(context)) return emptyMap()
        return try {
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return emptyMap()
            val now = System.currentTimeMillis()
            val since = now - days * MILLIS_PER_DAY
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, since, now)
                ?: return emptyMap()

            // The same package comes back once per interval bucket; the buckets add up.
            buildMap {
                stats.forEach { entry ->
                    val time = entry.totalTimeInForeground
                    if (time > 0L) put(entry.packageName, (get(entry.packageName) ?: 0L) + time)
                }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the phone would not hand over its usage figures", e)
            emptyMap()
        }
    }

    private const val DEFAULT_DAYS = 30
    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
}
