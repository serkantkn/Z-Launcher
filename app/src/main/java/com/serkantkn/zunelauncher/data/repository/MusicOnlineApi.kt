package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.util.Lyrics
import com.serkantkn.zunelauncher.util.LyricsSource
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.parseLrc
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The two things worth asking the internet for: the words to a song, and the picture on the front
 * of a record.
 *
 * Both services are free and want no account, which is why they were chosen over the ones that do.
 * Both are asked politely: a user agent that says what this is, one request at a time, and a pause
 * between them where the service asks for one. Everything here blocks and belongs on an IO thread.
 *
 * - Lyrics come from LRCLIB, which exists for music players and hands back the timed `.lrc` form
 *   as well as plain words.
 * - Covers come from the Cover Art Archive, by way of MusicBrainz to turn a name into an id.
 *   MusicBrainz asks for no more than one request a second and blocks callers who ignore it, so
 *   [waitForMusicBrainz] holds the line.
 * - When the archive has no cover, Apple's search endpoint usually does; it needs no key either.
 */
object MusicOnlineApi {

    private const val TAG = "MusicOnlineApi"
    private const val TIMEOUT_MS = 12_000

    /**
     * Who is asking.
     *
     * MusicBrainz refuses anything that does not say, and rightly: a shared service has no other
     * way to tell a well-behaved client from a runaway one.
     */
    private const val USER_AGENT =
        "ZuneLauncher/1.0 ( https://github.com/serkantkn/ZuneLauncher )"

    private const val MUSICBRAINZ_GAP_MS = 1_100L
    private var lastMusicBrainzAt = 0L
    private val musicBrainzLock = Any()

    // -- Lyrics ---------------------------------------------------------------

    /** The words to one song, or null when nobody has them. */
    fun fetchLyrics(artist: String, title: String, album: String, durationSeconds: Int): Lyrics? {
        val exact = runCatching {
            val url = buildString {
                append("https://lrclib.net/api/get")
                append("?artist_name=").append(encode(artist))
                append("&track_name=").append(encode(title))
                if (album.isNotBlank()) append("&album_name=").append(encode(album))
                if (durationSeconds > 0) append("&duration=").append(durationSeconds)
            }
            getJson(url)
        }.getOrNull()

        val fromExact = exact?.let(::lyricsOf)
        if (fromExact != null) return fromExact

        // The exact lookup wants the duration to match within a couple of seconds, which a
        // re-encoded file often will not; the search is looser.
        return runCatching {
            val url = "https://lrclib.net/api/search?q=" + encode("$artist $title")
            val array = JSONArray(getString(url))
            (0 until array.length())
                .asSequence()
                .mapNotNull { array.optJSONObject(it) }
                .mapNotNull(::lyricsOf)
                .firstOrNull()
        }.getOrElse {
            ZuneLog.w(TAG, "no lyrics could be found for $artist - $title", it)
            null
        }
    }

    private fun lyricsOf(json: JSONObject): Lyrics? {
        if (json.optBoolean("instrumental", false)) return null
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        val text = synced ?: plain ?: return null
        return parseLrc(text, LyricsSource.ONLINE).takeIf { !it.isEmpty }
    }

    // -- Cover art ------------------------------------------------------------

    /** The front of a record, as bytes, or null when nobody has a picture of it. */
    fun fetchCoverArt(artist: String, album: String): ByteArray? {
        if (album.isBlank()) return null
        return coverFromArchive(artist, album) ?: coverFromAppleSearch(artist, album)
    }

    private fun coverFromArchive(artist: String, album: String): ByteArray? = runCatching {
        waitForMusicBrainz()
        val query = buildString {
            if (artist.isNotBlank()) append("artist:\"").append(artist).append("\" AND ")
            append("releasegroup:\"").append(album).append("\"")
        }
        val url = "https://musicbrainz.org/ws/2/release-group/?query=" +
            encode(query) + "&fmt=json&limit=1"
        val groups = getJson(url).optJSONArray("release-groups") ?: return@runCatching null
        val mbid = groups.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: return@runCatching null

        // The archive redirects to wherever the image really lives, which is followed below.
        getBytes("https://coverartarchive.org/release-group/$mbid/front-500")
    }.getOrElse {
        ZuneLog.w(TAG, "the cover archive had nothing for $artist - $album", it)
        null
    }

    private fun coverFromAppleSearch(artist: String, album: String): ByteArray? = runCatching {
        val url = "https://itunes.apple.com/search?media=music&entity=album&limit=1&term=" +
            encode(listOf(artist, album).filter { it.isNotBlank() }.joinToString(" "))
        val results = getJson(url).optJSONArray("results") ?: return@runCatching null
        val small = results.optJSONObject(0)?.optString("artworkUrl100")?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        // The thumbnail's size is written into its address, so a bigger one can simply be asked for.
        getBytes(small.replace("100x100", "600x600"))
    }.getOrElse {
        ZuneLog.w(TAG, "apple had no cover for $artist - $album", it)
        null
    }

    /** Keeps MusicBrainz to the one request a second it asks for. */
    private fun waitForMusicBrainz() {
        val sleepFor = synchronized(musicBrainzLock) {
            val now = System.currentTimeMillis()
            val wait = (lastMusicBrainzAt + MUSICBRAINZ_GAP_MS - now).coerceAtLeast(0L)
            lastMusicBrainzAt = now + wait
            wait
        }
        if (sleepFor > 0L) Thread.sleep(sleepFor)
    }

    // -- The little bit of HTTP this needs ------------------------------------

    private fun getJson(url: String): JSONObject = JSONObject(getString(url))

    private fun getString(url: String): String = open(url).use { connection ->
        connection.inputStream.bufferedReader().readText()
    }

    private fun getBytes(url: String): ByteArray? = open(url).use { connection ->
        val bytes = connection.inputStream.readBytes()
        bytes.takeIf { it.isNotEmpty() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            if (responseCode !in 200..299) {
                disconnect()
                throw java.io.IOException("$url answered $responseCode")
            }
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
