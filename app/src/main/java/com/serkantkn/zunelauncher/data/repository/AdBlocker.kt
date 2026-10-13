package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.net.ConnectivityManager
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * The browser's ad and tracker blocking: what a page asks for from a known advertising or
 * tracking host is refused, so it never loads and never reports back.
 *
 * The hosts come from Steven Black's unified hosts list (github.com/StevenBlack/hosts, MIT), the
 * list most blockers on phones are built on. It is not shipped with the launcher: it is fetched
 * and kept, and fetched again once a week on Wi-Fi. Until it first arrives a short list of the
 * best-known hosts stands in. The first list is fetched over whatever connection there is.
 *
 * Asked from WebView's network threads, so what can change is held in volatile fields and
 * replaced whole, never edited in place.
 */
object AdBlocker {

    private const val TAG = "AdBlocker"
    private const val LIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val FILE_NAME = "adblock_hosts.txt"
    private const val REFRESH_MILLIS = 7L * 24 * 60 * 60 * 1000
    private const val TIMEOUT_MS = 30_000

    /** The best-known advertising and tracking hosts, for before the full list has arrived. */
    private val SEED = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "googletagservices.com",
        "adnxs.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "scorecardresearch.com",
        "quantserve.com", "moatads.com", "amazon-adsystem.com", "adsrvr.org", "rubiconproject.com",
        "pubmatic.com", "openx.net", "casalemedia.com", "smartadserver.com", "teads.tv",
        "hotjar.com", "mixpanel.com", "mc.yandex.ru", "adform.net", "bidswitch.net",
        "admatic.com.tr", "advertising.com", "media.net", "zedo.com", "33across.com", "sharethrough.com"
    )

    @Volatile
    private var hosts: Set<String> = SEED

    /** Whether blocking is on at all. */
    @Volatile
    var enabled: Boolean = true

    /** Sites the user switched blocking off for, by host without "www.". */
    @Volatile
    var allowedSites: Set<String> = emptySet()

    /** How many hosts are known; the seed's size until the list has arrived. */
    val size: Int get() = hosts.size

    @Volatile
    private var loaded = false

    /**
     * Whether a request for [url], made by a page on [pageHost], is to be refused. A request to
     * the page's own site never is, nor anything on a site blocking was switched off for.
     */
    fun shouldBlock(url: String, pageHost: String?): Boolean {
        if (!enabled) return false
        val page = pageHost?.removePrefix("www.")
        if (page != null && page in allowedSites) return false
        val host = hostOf(url) ?: return false
        if (page != null && (host == page || host.endsWith(".$page"))) return false
        return matches(host, hosts)
    }

    /** [host] or any domain above it is on the list: "a.ads.example.com" is caught by "ads.example.com". */
    internal fun matches(host: String, list: Set<String>): Boolean {
        var candidate = host
        while (true) {
            if (candidate in list) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    internal fun hostOf(url: String): String? {
        val start = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return null
        var end = url.length
        for (i in start until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#' || c == ':') { end = i; break }
        }
        val host = url.substring(start, end).substringAfterLast('@').lowercase(Locale.ROOT)
        return host.ifBlank { null }
    }

    /** Reads the kept list, once; then fetches a fresh one when it is a week old and on Wi-Fi. */
    suspend fun prepare(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, FILE_NAME)
        if (!loaded) {
            loaded = true
            if (file.exists()) {
                runCatching { file.readLines().filter { it.isNotBlank() }.toHashSet() }
                    .getOrNull()?.takeIf { it.isNotEmpty() }?.let { hosts = it + SEED }
            }
        }
        // The first list is fetched over any connection - without it blocking is barely there,
        // and it is under a megabyte on the wire. The weekly refresh waits for Wi-Fi.
        val missing = !file.exists()
        val stale = missing || System.currentTimeMillis() - file.lastModified() > REFRESH_MILLIS
        if (stale && (missing || onUnmeteredNetwork(context)) && hasNetwork(context)) refresh(file)
    }

    private fun refresh(file: File) {
        try {
            val connection = URL(LIST_URL).openConnection() as HttpURLConnection
            val fetched = try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().useLines { lines -> parseHosts(lines) }
            } finally {
                connection.disconnect()
            }
            if (fetched.size < MIN_SENSIBLE_LIST) throw IOException("list too short: ${fetched.size}")
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(fetched.joinToString("\n"))
            if (!temp.renameTo(file)) {
                file.writeText(fetched.joinToString("\n"))
                temp.delete()
            }
            hosts = fetched + SEED
            ZuneLog.d(TAG, "blocklist refreshed: ${fetched.size} hosts")
        } catch (e: Exception) {
            // Kept as it was: the old list, or the seed, goes on working.
            ZuneLog.w(TAG, "blocklist not refreshed", e)
        }
    }

    /** A hosts file's lines as host names: "0.0.0.0 ads.example.com # comment" gives the host. */
    internal fun parseHosts(lines: Sequence<String>): HashSet<String> {
        val result = HashSet<String>(120_000)
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
            if (parts.size < 2) continue
            if (parts[0] != "0.0.0.0" && parts[0] != "127.0.0.1") continue
            val host = parts[1].lowercase(Locale.ROOT)
            if (host == "localhost" || host == "0.0.0.0" || host.endsWith(".localdomain") || '.' !in host) continue
            result += host
        }
        return result
    }

    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.activeNetwork != null
    }

    private fun onUnmeteredNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return manager.activeNetwork != null && !manager.isActiveNetworkMetered
    }

    private const val MIN_SENSIBLE_LIST = 1_000
}
