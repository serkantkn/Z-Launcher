package com.serkantkn.zunelauncher.util

import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * LRU cache for app icon drawables to avoid repeated PackageManager lookups.
 */
class AppIconCache(maxSize: Int = 150) {
    private val cache = LruCache<String, Drawable>(maxSize)

    fun get(packageName: String): Drawable? = cache.get(packageName)

    fun put(packageName: String, icon: Drawable) {
        cache.put(packageName, icon)
    }

    fun clear() {
        cache.evictAll()
    }
}
