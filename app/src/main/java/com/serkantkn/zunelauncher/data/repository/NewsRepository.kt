package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.util.Pro
import android.content.Context
import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.datastore.NewsDataStore
import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.data.model.NewsSection
import com.serkantkn.zunelauncher.data.model.NewsSource
import com.serkantkn.zunelauncher.data.model.NewsTopic
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.hasNetwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** What Google's page for a story signs its request for the publisher's address with. */
data class GoogleSignature(val timestamp: Long, val value: String)

/** Why a feed could not be added; the words are the hub's to show. */
class NewsException(@StringRes val messageRes: Int, cause: Throwable? = null) : Exception(cause)

/** How a refresh ended, so the hub can say so when it was asked for by hand. */
enum class NewsRefresh { UPDATED, FRESH, NO_NETWORK, FAILED }

/**
 * The feeds the hub offers ready-made. Every one is a site's own public RSS feed; the hub shows
 * the headline and the site's own summary, and the story itself is read on the site.
 */
object NewsPresets {

    val all: List<NewsSource> = listOf(
        preset("bbc-turkce", "BBC News Türkçe", "https://feeds.bbci.co.uk/turkce/rss.xml", "tr"),
        preset("dw-turkce", "DW Türkçe", "https://rss.dw.com/rdf/rss-tur-all", "tr"),
        preset("euronews-tr", "Euronews Türkçe", "https://tr.euronews.com/rss", "tr"),
        preset("aa", "Anadolu Ajansı", "https://www.aa.com.tr/tr/rss/default?cat=guncel", "tr"),
        preset("trt-haber", "TRT Haber", "https://www.trthaber.com/sondakika.rss", "tr"),
        preset("ntv", "NTV", "https://www.ntv.com.tr/gundem.rss", "tr"),
        preset("hurriyet", "Hürriyet", "https://www.hurriyet.com.tr/rss/anasayfa", "tr"),
        preset("haberturk", "Habertürk", "https://www.haberturk.com/rss", "tr"),
        preset("cumhuriyet", "Cumhuriyet", "https://www.cumhuriyet.com.tr/rss/son_dakika.xml", "tr"),
        preset("sozcu", "Sözcü", "https://www.sozcu.com.tr/feeds-rss-category-sozcu", "tr"),
        preset("webtekno", "Webtekno", "https://www.webtekno.com/rss.xml", "tr"),
        preset("shiftdelete", "ShiftDelete.Net", "https://shiftdelete.net/feed", "tr"),
        preset("bbc-news", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "en"),
        preset("guardian-world", "The Guardian", "https://www.theguardian.com/world/rss", "en"),
        preset("nyt", "The New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", "en"),
        preset("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "en"),
        preset("verge", "The Verge", "https://www.theverge.com/rss/index.xml", "en"),
        preset("ars", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "en")
    )

    private fun preset(id: String, name: String, url: String, language: String) =
        NewsSource(id = id, name = name, url = url, language = language, preset = true)

    /** What a new hub follows: a few in the phone's own language, or the English ones. */
    fun defaultIdsFor(language: String): Set<String> = when (language) {
        "tr" -> setOf("bbc-turkce", "dw-turkce", "aa", "ntv")
        else -> setOf("bbc-news", "guardian-world", "verge")
    }

    /** The presets in the phone's language first, then the rest. */
    fun forLanguage(language: String): List<NewsSource> =
        all.sortedBy { if (it.language == language) 0 else 1 }
}

/**
 * The news hub's stories: Google News for the topics, the user's feeds for "my sources", both
 * kept in a file so the hub opens on what it last had and the tile has something to show.
 */
class NewsRepository(
    private val context: Context,
    private val dataStore: NewsDataStore
) {

    val topics: Flow<List<NewsTopic>> = dataStore.topics

    /** The feeds followed: the ready-made ones that are on, then the user's own. */
    /**
     * The feeds followed. They are Pro's: the free build follows none and fetches none, and keeps
     * the ones chosen for Pro.
     */
    val sources: Flow<List<NewsSource>> = combine(dataStore.enabledPresetIds, dataStore.customSources, Pro.flow) { ids, custom, pro ->
        if (pro) NewsPresets.all.filter { it.id in ids } + custom else emptyList()
    }

    val enabledPresetIds: Flow<Set<String>> = dataStore.enabledPresetIds
    val customSources: Flow<List<NewsSource>> = dataStore.customSources

    private val _sections = MutableStateFlow<Map<String, NewsSection>>(emptyMap())

    /** Every heading's stories as last fetched, by [NewsSection.key]. */
    val sections: StateFlow<Map<String, NewsSection>> = _sections.asStateFlow()

    private val cacheFile = File(context.filesDir, "news_cache.json")
    private val loadLock = Mutex()
    @Volatile private var loaded = false
    private val sectionLocks = ConcurrentHashMap<String, Mutex>()

    /** Reads what was kept from last time; cheap after the first call. */
    suspend fun load() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            val kept = withContext(Dispatchers.IO) {
                try {
                    if (!cacheFile.exists()) return@withContext emptyMap()
                    val array = JSONArray(cacheFile.readText())
                    (0 until array.length()).mapNotNull { i ->
                        runCatching { NewsSection.fromJson(array.getJSONObject(i)) }.getOrNull()
                    }.associateBy { it.key }
                } catch (e: IOException) {
                    ZuneLog.w(TAG, "news cache unreadable", e)
                    emptyMap()
                } catch (e: JSONException) {
                    ZuneLog.w(TAG, "news cache malformed", e)
                    emptyMap()
                }
            }
            _sections.update { current -> kept + current }
            loaded = true
        }
    }

    /**
     * Brings a heading up to date when what is kept has gone stale, or always when [force]d.
     * [key] is a topic's [NewsTopic.sectionKey] or [NewsSection.SOURCES_KEY].
     */
    suspend fun refresh(key: String, force: Boolean): NewsRefresh = withContext(Dispatchers.IO) {
        load()
        sectionLocks.getOrPut(key) { Mutex() }.withLock {
            val kept = _sections.value[key]
            val age = kept?.let { System.currentTimeMillis() - it.fetchedAt }
            if (age != null && (age < JUST_FETCHED_MS || (!force && age < STALE_MS))) return@withLock NewsRefresh.FRESH
            if (!context.hasNetwork()) return@withLock NewsRefresh.NO_NETWORK

            val items = try {
                if (key == NewsSection.SOURCES_KEY) fetchSources() else fetchTopic(topicOf(key) ?: return@withLock NewsRefresh.FAILED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A broken feed is as much a failed refresh as a dropped connection.
                ZuneLog.w(TAG, "news refresh of $key failed", e)
                null
            }
            if (items == null) return@withLock NewsRefresh.FAILED
            // Pictures found for a story by opening it are kept when the story comes round again.
            val known = kept?.items.orEmpty().associateBy { it.link }
            val merged = items.map { item ->
                val before = known[item.link]
                if (item.imageUrl == null && before?.imageUrl != null) item.copy(imageUrl = before.imageUrl) else item
            }
            put(NewsSection(key, merged, System.currentTimeMillis()))
            NewsRefresh.UPDATED
        }
    }

    /** Forgets when a heading was fetched, so the next look fetches it again. */
    fun invalidate(key: String) {
        _sections.update { current ->
            val section = current[key] ?: return@update current
            current + (key to section.copy(fetchedAt = 0L))
        }
    }

    private fun topicOf(key: String): NewsTopic? =
        NewsTopic.entries.firstOrNull { it.sectionKey == key }

    private fun fetchTopic(topic: NewsTopic): List<NewsItem> {
        val bytes = download(googleNewsUrl(topic, Locale.getDefault()))
        return NewsFeedParser.parse(ByteArrayInputStream(bytes), "Google News", googleNews = true)
            .items.take(SECTION_LIMIT)
    }

    /** Every followed feed at once; one that fails leaves the others standing. */
    private suspend fun fetchSources(): List<NewsItem>? = coroutineScope {
        val followed = sources.first()
        if (followed.isEmpty()) return@coroutineScope emptyList()
        val results = followed.map { source ->
            async(Dispatchers.IO) {
                try {
                    val feed = NewsFeedParser.parse(ByteArrayInputStream(download(source.url)), source.name)
                    // The name the user chose the feed by is what the stories are filed under.
                    feed.items.take(PER_SOURCE_LIMIT).map { it.copy(source = source.name) }
                } catch (e: Exception) {
                    ZuneLog.w(TAG, "feed ${source.url} failed", e)
                    null
                }
            }
        }.awaitAll()
        if (results.all { it == null }) return@coroutineScope null
        results.filterNotNull().flatten()
            .distinctBy { it.link }
            .sortedByDescending { it.publishedAt }
            .take(SOURCES_LIMIT)
    }

    /**
     * Fills in a story's picture and summary from its page's own share card (`og:image`,
     * `og:description`) when the feed gave none. Google's links lead through Google, whose page
     * has no card of the story's: for those the publisher's address is asked of Google first,
     * and kept, so the story is also read straight from the publisher.
     */
    suspend fun withPageCard(item: NewsItem): NewsItem = withContext(Dispatchers.IO) {
        if (item.imageUrl != null && item.summary.isNotBlank()) return@withContext item
        if (!context.hasNetwork()) return@withContext item
        val address = item.pageUrl
            ?: if (isGoogleLink(item.link)) publisherLink(item.link) ?: return@withContext item else item.link
        val page = try {
            String(download(address, PAGE_HEAD_BYTES), Charsets.UTF_8)
        } catch (e: IOException) {
            ZuneLog.w(TAG, "page card of $address unavailable", e)
            return@withContext item.copy(pageUrl = address.takeIf { it != item.link }).also { if (it != item) replaceEverywhere(it) }
        }
        val image = item.imageUrl ?: metaContent(page, "og:image") ?: metaContent(page, "twitter:image")
        val summary = item.summary.ifBlank { metaContent(page, "og:description") ?: metaContent(page, "description").orEmpty() }
        val filled = item.copy(
            imageUrl = image?.takeIf { it.startsWith("http") },
            summary = NewsFeedParser.summaryOf(summary),
            pageUrl = address.takeIf { it != item.link }
        )
        if (filled != item) replaceEverywhere(filled)
        filled
    }

    /**
     * The publisher's address behind a Google News link, as Google's own page finds it: the page
     * carries a signature, and Google's page service trades it for the address. Null when Google
     * will not say, which leaves the story as it was.
     */
    private fun publisherLink(link: String): String? = try {
        val id = URL(link).path.substringAfterLast('/').takeIf { it.isNotBlank() }
        val page = id?.let { String(download("https://news.google.com/rss/articles/$it", GOOGLE_PAGE_BYTES), Charsets.UTF_8) }
        val signature = page?.let { googleSignature(it) }
        if (id == null || signature == null) {
            null
        } else {
            googleDecodedLink(post(GOOGLE_DECODE_URL, "f.req=" + URLEncoder.encode(googleDecodeRequest(id, signature), "UTF-8")))
        }
    } catch (e: IOException) {
        ZuneLog.w(TAG, "publisher of $link unavailable", e)
        null
    }

    // --- Settings --------------------------------------------------------------------------------

    suspend fun setTopics(topics: List<NewsTopic>) = dataStore.setTopics(topics)

    suspend fun setPresetEnabled(id: String, enabled: Boolean) {
        val current = dataStore.enabledPresetIds.first()
        dataStore.setEnabledPresets(if (enabled) current + id else current - id)
        invalidate(NewsSection.SOURCES_KEY)
    }

    /**
     * Adds a feed by its address, after reading it once to be sure it is one; it is named after
     * the feed's own title. A site's page address is not a feed - the error says so.
     */
    suspend fun addSource(address: String): NewsSource = withContext(Dispatchers.IO) {
        val url = address.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val existing = sources.first()
        if (existing.any { it.url.equals(url, ignoreCase = true) }) throw NewsException(R.string.news_error_already_added)
        if (!context.hasNetwork()) throw NewsException(R.string.news_error_offline)
        val feed = try {
            NewsFeedParser.parse(ByteArrayInputStream(download(url)), "")
        } catch (e: IOException) {
            throw NewsException(R.string.news_error_unreachable, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NewsException(R.string.news_error_not_a_feed, e)
        }
        if (feed.items.isEmpty()) throw NewsException(R.string.news_error_not_a_feed)
        val name = feed.title.ifBlank { URL(url).host.removePrefix("www.") }
        val source = NewsSource(id = "custom:" + url.hashCode().toUInt().toString(16), name = name, url = url)
        dataStore.setCustomSources(dataStore.customSources.first() + source)
        invalidate(NewsSection.SOURCES_KEY)
        source
    }

    suspend fun removeSource(id: String) {
        dataStore.setCustomSources(dataStore.customSources.first().filterNot { it.id == id })
        invalidate(NewsSection.SOURCES_KEY)
    }

    // --- Keeping ---------------------------------------------------------------------------------

    private fun put(section: NewsSection) {
        _sections.update { it + (section.key to section) }
        persist()
    }

    private fun replaceEverywhere(item: NewsItem) {
        _sections.update { current ->
            current.mapValues { (_, section) ->
                if (section.items.none { it.link == item.link }) section
                else section.copy(items = section.items.map { if (it.link == item.link) item else it })
            }
        }
        persist()
    }

    private fun persist() {
        val snapshot = _sections.value.values.toList()
        try {
            val array = JSONArray()
            snapshot.forEach { array.put(it.toJson()) }
            val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(cacheFile)) {
                cacheFile.writeText(array.toString())
                temp.delete()
            }
        } catch (e: IOException) {
            ZuneLog.w(TAG, "news cache not written", e)
        }
    }

    companion object {
        private const val TAG = "NewsRepository"

        /** Stories older than this are fetched again when the hub is looked at. */
        const val STALE_MS = 20 * 60 * 1000L

        /** What the start tile settles for before it fetches on its own. */
        const val TILE_STALE_MS = 60 * 60 * 1000L

        private const val JUST_FETCHED_MS = 15_000L
        private const val SECTION_LIMIT = 60
        private const val PER_SOURCE_LIMIT = 40
        private const val SOURCES_LIMIT = 120
        private const val FEED_MAX_BYTES = 6 * 1024 * 1024
        private const val PAGE_HEAD_BYTES = 192 * 1024

        /** Google's page for a story is large, and the signature is near its end. */
        private const val GOOGLE_PAGE_BYTES = 2 * 1024 * 1024
        private const val GOOGLE_DECODE_URL = "https://news.google.com/_/DotsSplashUi/data/batchexecute"
        private const val TIMEOUT_MS = 15_000
        private const val MAX_REDIRECTS = 5
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) ZLauncher-News/1.0"

        /** Google News's feed for [topic], in the edition of the phone's language and country. */
        fun googleNewsUrl(topic: NewsTopic, locale: Locale): String {
            val language = locale.language.ifBlank { "en" }
            val country = locale.country.ifBlank { if (language == "en") "US" else language.uppercase(Locale.ROOT) }
            val edition = "hl=$language&gl=$country&ceid=$country:$language"
            return when (val section = topic.googleSection) {
                null -> "https://news.google.com/rss?$edition"
                else -> "https://news.google.com/rss/headlines/section/topic/$section?$edition"
            }
        }

        fun isGoogleLink(link: String): Boolean =
            runCatching { URL(link).host.endsWith("news.google.com") }.getOrDefault(false)

        private val GOOGLE_TIMESTAMP = Regex("""data-n-a-ts="(\d+)"""")
        private val GOOGLE_SIGNATURE = Regex("""data-n-a-sg="([^"]+)"""")

        /** The signature and its time on Google's page for a story; null when the page has none. */
        fun googleSignature(page: String): GoogleSignature? {
            val timestamp = GOOGLE_TIMESTAMP.find(page)?.groupValues?.get(1) ?: return null
            val signature = GOOGLE_SIGNATURE.find(page)?.groupValues?.get(1) ?: return null
            return GoogleSignature(timestamp.toLong(), signature)
        }

        /** The request Google's own page makes to turn a story's id and signature into the address. */
        fun googleDecodeRequest(id: String, signature: GoogleSignature): String {
            val inner = JSONArray()
                .put("garturlreq")
                .put(JSONArray("[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0]"))
                .put(id)
                .put(signature.timestamp)
                .put(signature.value)
            return JSONArray().put(JSONArray().put(JSONArray().put("Fbv4je").put(inner.toString()).put(JSONObject.NULL).put("generic"))).toString()
        }

        /** The publisher's address in Google's answer, or null when the answer has none. */
        fun googleDecodedLink(response: String): String? = try {
            val entries = JSONArray(response.substring(response.indexOf('[').coerceAtLeast(0)))
            (0 until entries.length()).firstNotNullOfOrNull { i ->
                val entry = entries.optJSONArray(i)
                if (entry?.optString(0) == "wrb.fr" && entry.optString(1) == "Fbv4je") {
                    JSONArray(entry.getString(2)).optString(1).takeIf { it.startsWith("http") }
                } else {
                    null
                }
            }
        } catch (e: JSONException) {
            null
        }

        private val META = Regex("""<meta\s[^>]*>""", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE = Regex("""([a-zA-Z:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

        /** The content of a page's `<meta property|name="[name]">`, whichever order its attributes are in. */
        fun metaContent(html: String, name: String): String? {
            for (tag in META.findAll(html)) {
                val attributes = ATTRIBUTE.findAll(tag.value).associate { match ->
                    match.groupValues[1].lowercase(Locale.ROOT) to match.groupValues[2].ifEmpty { match.groupValues[3] }
                }
                val key = attributes["property"] ?: attributes["name"]
                if (key.equals(name, ignoreCase = true)) {
                    return attributes["content"]?.let { NewsFeedParser.decodeEntities(it).trim() }?.takeIf { it.isNotEmpty() }
                }
            }
            return null
        }

        /** Sends [form] to [address] as a form, and reads the answer. */
        private fun post(address: String, form: String): String {
            val connection = URL(address).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code for $address")
                return connection.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
            } finally {
                connection.disconnect()
            }
        }

        /** Reads [address] whole, up to [limit] bytes, following redirects across http and https. */
        private fun download(address: String, limit: Int = FEED_MAX_BYTES): ByteArray {
            var url = URL(address)
            repeat(MAX_REDIRECTS) {
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    connection.setRequestProperty(
                        "Accept",
                        "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5"
                    )
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val next = connection.getHeaderField("Location") ?: throw IOException("redirect without a location")
                        url = URL(url, next)
                        return@repeat
                    }
                    if (code !in 200..299) throw IOException("HTTP $code for $address")
                    connection.inputStream.use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(16 * 1024)
                        while (out.size() < limit) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                        return out.toByteArray()
                    }
                } finally {
                    connection.disconnect()
                }
            }
            throw IOException("too many redirects for $address")
        }
    }
}
