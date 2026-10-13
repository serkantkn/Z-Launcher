package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * Internet Hub bookmark. JSON keys match the field names of the previously stored
 * records so existing favorites keep loading.
 */
data class BrowserFavorite(
    val title: String,
    val url: String,
    /** The folder it has been filed under, or blank for one sitting loose on the page. */
    val folder: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("url", url)
        .apply { if (folder.isNotBlank()) put("folder", folder) }

    companion object {
        /** Throws JSONException when "url" is missing. */
        fun fromJson(obj: JSONObject): BrowserFavorite = BrowserFavorite(
            title = obj.optString("title", ""),
            url = obj.getString("url"),
            folder = obj.optString("folder", "")
        )
    }
}

/**
 * Internet Hub history entry. JSON keys match the field names of the previously stored
 * records so existing history keeps loading.
 */
data class BrowserHistory(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** How many times the page has been opened, for the start page's most visited sites. */
    val visits: Int = 1
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("url", url)
        .put("timestamp", timestamp)
        .put("visits", visits)

    companion object {
        /** Throws JSONException when "url" is missing. */
        fun fromJson(obj: JSONObject): BrowserHistory = BrowserHistory(
            title = obj.optString("title", ""),
            url = obj.getString("url"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            visits = obj.optInt("visits", 1).coerceAtLeast(1)
        )
    }
}

/**
 * A page kept to be read later: what it is and where from, and whether a copy of its text is
 * kept on the phone to be read without a connection.
 */
data class ReadingItem(
    val url: String,
    val title: String,
    val source: String,
    val imageUrl: String? = null,
    val excerpt: String = "",
    val savedAt: Long = System.currentTimeMillis(),
    val read: Boolean = false,
    val offline: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("source", source)
        .put("imageUrl", imageUrl ?: JSONObject.NULL)
        .put("excerpt", excerpt)
        .put("savedAt", savedAt)
        .put("read", read)
        .put("offline", offline)

    companion object {
        fun fromJson(obj: JSONObject): ReadingItem = ReadingItem(
            url = obj.getString("url"),
            title = obj.optString("title", ""),
            source = obj.optString("source", ""),
            imageUrl = obj.optStringOrNull("imageUrl"),
            excerpt = obj.optString("excerpt", ""),
            savedAt = obj.optLong("savedAt", 0L),
            read = obj.optBoolean("read", false),
            offline = obj.optBoolean("offline", false)
        )
    }
}

/** A site the start page shows among the most visited: where it is and how often it was opened. */
data class FrequentSite(val host: String, val title: String, val url: String, val visits: Int)

/**
 * The most visited sites, worked out from the history: visits to every page of a site added up,
 * the site's own front page as where its tile leads.
 */
fun frequentSites(history: List<BrowserHistory>, limit: Int = 8): List<FrequentSite> =
    history
        .mapNotNull { entry ->
            val uri = runCatching { java.net.URI(entry.url) }.getOrNull() ?: return@mapNotNull null
            val host = uri.host?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (uri.scheme != "http" && uri.scheme != "https") return@mapNotNull null
            // Google's own pages and its translation proxy are ways through to a site, not sites.
            if (host == "google.com" || host.endsWith(".google.com") || host.endsWith(".translate.goog")) return@mapNotNull null
            Triple(host, entry, "${uri.scheme}://${uri.host}/")
        }
        .groupBy { it.first }
        .map { (host, entries) ->
            val visits = entries.sumOf { it.second.visits }
            val latest = entries.maxByOrNull { it.second.timestamp }!!
            // The site's name, taken from the front page's title when it was visited, else the host.
            val front = entries.firstOrNull { java.net.URI(it.second.url).path.let { p -> p.isNullOrEmpty() || p == "/" } }
            val title = front?.second?.title?.takeIf { it.isNotBlank() }?.substringBefore(" - ")?.substringBefore(" | ")?.trim() ?: host
            FrequentSite(host = host, title = title, url = latest.third, visits = visits) to latest.second.timestamp
        }
        .sortedWith(compareByDescending<Pair<FrequentSite, Long>> { it.first.visits }.thenByDescending { it.second })
        .take(limit)
        .map { it.first }
