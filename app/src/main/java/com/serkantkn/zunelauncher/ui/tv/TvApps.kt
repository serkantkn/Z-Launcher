package com.serkantkn.zunelauncher.ui.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * An app as a television lists it: the wide banner it made for the TV's own launcher, or failing
 * that its icon, and the intent that opens it on the TV.
 */
data class TvApp(
    val label: String,
    val packageName: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val launch: Intent
)

/**
 * The apps made for a television - those that answer LEANBACK_LAUNCHER - in the order of their
 * names. Phone apps that happen to be installed are left out: on a TV they open sideways, if at
 * all, and the TV's own launcher does not list them either.
 */
fun loadTvApps(context: Context): List<TvApp> {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val found = runCatching { pm.queryIntentActivities(query, PackageManager.MATCH_ALL) }
        .onFailure { ZuneLog.w(TAG, "the TV apps could not be listed", it) }
        .getOrDefault(emptyList())
    return found
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            val activity = info.activityInfo
            TvApp(
                label = info.loadLabel(pm).toString(),
                packageName = activity.packageName,
                banner = runCatching {
                    (activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm))
                        ?.toBitmap(BANNER_WIDTH, BANNER_HEIGHT)?.asImageBitmap()
                }.getOrNull(),
                icon = runCatching { info.loadIcon(pm)?.toBitmap(ICON_SIZE, ICON_SIZE)?.asImageBitmap() }.getOrNull(),
                launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(activity.packageName, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** A banner is 16:9, 320 × 180 at the TV's density. */
private const val BANNER_WIDTH = 320
private const val BANNER_HEIGHT = 180
private const val ICON_SIZE = 192
private const val TAG = "TvApps"
