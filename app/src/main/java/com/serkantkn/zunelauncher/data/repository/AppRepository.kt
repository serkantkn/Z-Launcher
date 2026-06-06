package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
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

        val apps = resolveInfos
            .filter { it.activityInfo.packageName != context.packageName }
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    activityName = resolveInfo.activityInfo.name
                )
            }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.packageName }

        emit(apps)
    }.flowOn(Dispatchers.IO)

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
}
