package com.serkantkn.zeta.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.serkantkn.zeta.data.datastore.FavoriteAppsDataStore
import com.serkantkn.zeta.data.model.AppInfo
import com.serkantkn.zeta.util.AppIconCache
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.serkantkn.zeta.data.model.FavoriteAppItem

class AppRepository(private val context: Context) {

    private val packageManager = context.packageManager
    private val iconCache = AppIconCache()
    val favoriteAppsDataStore = FavoriteAppsDataStore(context)

    /**
     * Queries all installed apps with a launcher intent and emits them sorted alphabetically.
     *
     * [allActivities] keeps every launcher activity a package declares — the app list wants
     * the game and its map editor both. Off, which is what Start and the pickers want, a package
     * is one entry.
     */
    fun getInstalledApps(allActivities: Boolean = false): Flow<List<AppInfo>> = callbackFlow {
        // The list is read once at the start and again whenever the phone says a package came,
        // went or changed — so an app removed from the list leaves it at once, and one just
        // installed appears without the launcher being restarted.
        launch(Dispatchers.IO) { send(queryInstalledApps(allActivities)) }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                launch(Dispatchers.IO) { send(queryInstalledApps(allActivities)) }
            }
        }
        // Package broadcasts come from the system alone, which is what the plain registration
        // is still for.
        context.registerReceiver(receiver, packageFilter())
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /**
     * The packages the phone says came, went or changed, as they happen — for anything that
     * keeps a cache keyed on a package (an icon pack's filter, say) and must drop it then.
     */
    fun packageChanges(): Flow<String> = callbackFlow {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val packageName = intent?.data?.schemeSpecificPart ?: return
                trySend(packageName)
            }
        }
        context.registerReceiver(receiver, packageFilter())
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun packageFilter() = android.content.IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addDataScheme("package")
    }

    private fun queryInstalledApps(allActivities: Boolean): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        val installedAt = installTimes()

        return resolveInfos
            .filter { it.activityInfo.packageName != context.packageName }
            .map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                AppInfo(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    activityName = resolveInfo.activityInfo.name,
                    firstInstallTime = installedAt[packageName] ?: 0L
                )
            }
            .sortedBy { it.label.lowercase() }
            // Keyed on package and activity, so a second launcher activity is a second entry
            // rather than silently the first one again — unless one per package was asked for.
            .distinctBy { if (allActivities) it.key else it.packageName }
    }

    /**
     * When each installed package arrived, asked once for the whole phone rather than once per
     * app: a hundred separate questions to the package manager is a visible pause on an older
     * phone, and this is only needed to sort a list.
     */
    private fun installTimes(): Map<String, Long> = try {
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }
        packages.associate { it.packageName to it.firstInstallTime }
    } catch (e: Exception) {
        emptyMap()
    }

    /**
     * Returns the app icon drawable with LRU caching.
     */
    fun getAppIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }

        return try {
            val icon = packageManager.getApplicationIcon(packageName)
            iconCache.put(packageName, icon)
            icon
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Lets go of the cached icons.
     *
     * Called when Android says memory is short: a launcher that will not give anything back is
     * a launcher that gets killed, and re-reading an icon costs one call to the package manager.
     */
    fun trimIconCache() {
        iconCache.clear()
    }

    /**
     * Launches an app: the named activity when one is given (a package's second entry in the
     * list is not what its launch intent opens), else whatever the package's launch intent is.
     *
     * Returns whether a screen was asked for. It is false, not a crash, when the app was removed
     * a moment ago, is not exported, or the phone refuses the start — so the caller can say
     * "uygulama açılamadı" instead of the launcher dying.
     */
    fun launchApp(packageName: String, activityName: String? = null): Boolean {
        val intent = if (!activityName.isNullOrBlank()) {
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(packageName, activityName)
        } else {
            packageManager.getLaunchIntentForPackage(packageName)
        } ?: return false
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            ZuneLog.w("AppRepository", "no screen to open for $packageName/$activityName", e)
            // The activity named may have gone with an update; the package's own may still do.
            if (!activityName.isNullOrBlank()) launchApp(packageName, null) else false
        } catch (e: SecurityException) {
            ZuneLog.w("AppRepository", "the phone refused to open $packageName", e)
            false
        } catch (e: Exception) {
            ZuneLog.w("AppRepository", "$packageName would not open", e)
            false
        }
    }

    /**
     * Returns a flow of favorite apps data.
     */
    fun getFavoritePackages(): Flow<List<FavoriteAppItem>> = favoriteAppsDataStore.favoritePackages

    /**
     * One app, asked for by name.
     *
     * Reading the whole phone takes a question per installed app for the labels alone, and on a
     * phone with a hundred and fifty of them that is most of a second. A handful of pinned
     * favourites do not need to wait behind all of that: each of them is two quick questions.
     */
    fun appInfoFor(packageName: String): AppInfo? = try {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }.firstOrNull() ?: return null

        AppInfo(
            packageName = packageName,
            label = resolved.loadLabel(packageManager).toString(),
            activityName = resolved.activityInfo.name,
            firstInstallTime = 0L
        )
    } catch (e: Exception) {
        ZuneLog.w("AppRepository", "no such app: $packageName", e)
        null
    }

    /**
     * Toggle favorite status for a package.
     */
    suspend fun toggleFavorite(packageName: String) {
        favoriteAppsDataStore.toggleFavorite(packageName)
    }

    suspend fun updateSpan(packageName: String, span: Int) {
        favoriteAppsDataStore.updateSpan(packageName, span)
    }

    suspend fun updateFavoritesOrder(newList: List<FavoriteAppItem>) {
        favoriteAppsDataStore.updateOrder(newList)
    }

    // ════════════════════════════════════════════════════════════
    // WHAT ELSE CAN BE DONE WITH AN APP
    // ════════════════════════════════════════════════════════════

    /**
     * Asks Android to remove an app, and returns whether the request could even be made.
     *
     * The launcher never removes anything itself: it opens the system's own dialog, which is the
     * only place the answer can be given.
     */
    fun requestUninstall(packageName: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    /** Whether an app came with the phone, and so cannot simply be removed. */
    fun isSystemApp(packageName: String): Boolean = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    } catch (e: Exception) {
        false
    }

    /** Opens the system's page for one app: permissions, storage, notifications. */
    fun openAppSettings(packageName: String): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    /** Shares an app as its shop address, which is the only link anybody else can open. */
    fun shareApp(packageName: String, label: String, chooserTitle: String): Boolean = try {
        val share = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, label)
            .putExtra(Intent.EXTRA_TEXT, "$label\n$STORE_URL$packageName")
        context.startActivity(
            Intent.createChooser(share, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val STORE_URL = "https://play.google.com/store/apps/details?id="
    }
}
