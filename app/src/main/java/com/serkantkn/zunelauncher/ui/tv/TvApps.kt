package com.serkantkn.zunelauncher.ui.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
    return tvAppActivities(context)
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

/**
 * What the TV's apps are, cheaply: each one's package and when it was last installed or updated.
 * Start lists the apps again only when this has changed - an app put on, taken off or updated
 * while the launcher was away - since drawing every banner again on a slow TV is not free.
 */
fun tvAppsSignature(context: Context): List<Pair<String, Long>> {
    val pm = context.packageManager
    return tvAppActivities(context)
        .map { it.activityInfo.packageName }
        .distinct()
        .map { name -> name to (runCatching { pm.getPackageInfo(name, 0).lastUpdateTime }.getOrDefault(0L)) }
        .sortedBy { it.first }
}

private fun tvAppActivities(context: Context): List<ResolveInfo> {
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    return runCatching { context.packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL) }
        .onFailure { ZuneLog.w(TAG, "the TV apps could not be listed", it) }
        .getOrDefault(emptyList())
}

/** A banner is 16:9; drawn at the size of Start's tile on a 1080p TV, so it is not blown up. */
private const val BANNER_WIDTH = 480
private const val BANNER_HEIGHT = 270
private const val ICON_SIZE = 192
private const val TAG = "TvApps"
