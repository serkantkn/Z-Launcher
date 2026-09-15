package com.serkantkn.zunelauncher.util

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * Clearing what browsing leaves behind.
 *
 * "Clear history" used to empty the launcher's own list of visited pages and nothing else — the
 * cookies that keep you signed in, the cached files and the sites' stored data all stayed, which
 * is not what anybody means by the phrase. Each of these is separate so the user can say which of
 * them they meant.
 */
object BrowsingData {

    private const val TAG = "BrowsingData"

    /** The cookies that keep sites signed in. */
    fun clearCookies() = guard {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    /** The files pages were saved as, so they load faster next time. */
    fun clearCache(context: Context) = guard {
        // A WebView has to exist for its cache to be cleared; a throwaway one does the job.
        val web = WebView(context)
        web.clearCache(true)
        web.clearFormData()
        web.clearHistory()
        web.destroy()
    }

    /** What sites themselves have stored: local storage, databases, offline pages. */
    fun clearSiteData() = guard {
        WebStorage.getInstance().deleteAllData()
    }

    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not clear browsing data", e)
        }
    }
}
