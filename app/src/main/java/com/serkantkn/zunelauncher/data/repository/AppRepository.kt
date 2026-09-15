package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.serkantkn.zunelauncher.data.datastore.FavoriteAppsDataStore
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.util.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.serkantkn.zunelauncher.data.model.FavoriteAppItem

class AppRepository(private val context: Context) {

    private val packageManager = context.packageManager
    private val iconCache = AppIconCache()
    val favoriteAppsDataStore = FavoriteAppsDataStore(context)

    /**
     * Queries all installed apps with a launcher intent and emits them sorted alphabetically.
     */
    fun getInstalledApps(): Flow<List<AppInfo>> = flow {
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

        val apps = resolveInfos
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
            .distinctBy { it.packageName }

        emit(apps)
    }.flowOn(Dispatchers.IO)

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
     * Launches an app by package name.
     */
    fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    /**
     * Returns a flow of favorite apps data.
     */
    fun getFavoritePackages(): Flow<List<FavoriteAppItem>> = favoriteAppsDataStore.favoritePackages

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
