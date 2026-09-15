package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.AlbumModel
import com.serkantkn.zunelauncher.data.model.ArtistModel
import com.serkantkn.zunelauncher.data.model.SongModel
import java.text.Collator
import java.util.Locale

/**
 * The music hub's arithmetic: how long a track runs, what order things go in, and what a typed
 * query matches. None of it touches Android, so all of it can be tested.
 *
 * Sorting is the part worth being careful about. `ORDER BY title` in the media store's own SQL
 * sorts by byte value, which in Turkish puts "Çınar" after "Zeytin" and "Işık" somewhere nobody
 * expects. A [Collator] for the phone's language puts them where a reader would look.
 */

// ── Time ────────────────────────────────────────────────────────────────────

/** A track's length: `3:42`, or `1:02:03` once there is an hour of it. */
fun formatTrackDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * How long a whole record runs, said the way a person would: "42 dk", or "1 sa 12 dk".
 *
 * The unit words are passed in rather than hard-coded, so this stays free of Android's resources
 * and the hub keeps saying it in the phone's language.
 */
fun formatTotalDuration(millis: Long, hourLabel: String, minuteLabel: String): String {
    val totalMinutes = (millis.coerceAtLeast(0L) + 59_999L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "$hours $hourLabel $minutes $minuteLabel" else "$totalMinutes $minuteLabel"
}

// ── Order ───────────────────────────────────────────────────────────────────

/** The orders the song list can be put in. */
enum class MusicSort { TITLE, ARTIST, ALBUM, RECENT, DURATION }

/** A collator for the phone's language, told to ignore case and marks. */
private fun collator(): Collator = Collator.getInstance(Locale.getDefault()).apply {
    strength = Collator.SECONDARY
}

fun sortSongs(songs: List<SongModel>, sort: MusicSort): List<SongModel> {
    val collator = collator()
    return when (sort) {
        MusicSort.TITLE -> songs.sortedWith { a, b -> collator.compare(a.title, b.title) }
        MusicSort.ARTIST -> songs.sortedWith { a, b ->
            val byArtist = collator.compare(a.artist, b.artist)
            if (byArtist != 0) byArtist else collator.compare(a.title, b.title)
        }
        // Inside an album, the running order is the one the record was made in — track number,
        // not the alphabet.
        MusicSort.ALBUM -> songs.sortedWith { a, b ->
            val byAlbum = collator.compare(a.album, b.album)
            when {
                byAlbum != 0 -> byAlbum
                a.trackNumber != b.trackNumber -> a.trackNumber - b.trackNumber
                else -> collator.compare(a.title, b.title)
            }
        }
        MusicSort.RECENT -> songs.sortedByDescending { it.dateAdded }
        MusicSort.DURATION -> songs.sortedByDescending { it.duration }
    }
}

/** An album's own running order. */
fun sortAlbumTracks(songs: List<SongModel>): List<SongModel> {
    val collator = collator()
    return songs.sortedWith { a, b ->
        if (a.trackNumber != b.trackNumber) a.trackNumber - b.trackNumber
        else collator.compare(a.title, b.title)
    }
}

fun sortAlbums(albums: List<AlbumModel>): List<AlbumModel> {
    val collator = collator()
    return albums.sortedWith { a, b -> collator.compare(a.title, b.title) }
}

fun sortArtists(artists: List<ArtistModel>): List<ArtistModel> {
    val collator = collator()
    return artists.sortedWith { a, b -> collator.compare(a.name, b.name) }
}

// ── Searching ───────────────────────────────────────────────────────────────

/** Whether a track answers to what somebody typed — title, artist or album. */
fun SongModel.matchesMusicQuery(query: String): Boolean {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) return true
    return foldForSearch("$title $artist $album").contains(needle)
}

fun AlbumModel.matchesMusicQuery(query: String): Boolean {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) return true
    return foldForSearch("$title $artist").contains(needle)
}

fun ArtistModel.matchesMusicQuery(query: String): Boolean {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) return true
    return foldForSearch(name).contains(needle)
}

// ── The alphabet down the side ──────────────────────────────────────────────

/**
 * The letter a name files under.
 *
 * Windows Phone put a letter over every run of the list and let you jump between them. Anything
 * that does not start with a letter — a number, a bracket, a symbol — files under "#", as it did
 * there. Turkish letters keep their own heading rather than being folded into the nearest English
 * one: "Çınar" belongs under Ç, not under C.
 */
fun jumpLetterOf(name: String): String {
    val first = name.trimStart().firstOrNull() ?: return "#"
    if (!first.isLetter()) return "#"
    return first.toString().uppercase(Locale.getDefault())
}

/**
 * A list broken into lettered runs, in the order the list is already in.
 *
 * It does not sort: whatever order it is handed is the order it keeps, so the headings match a
 * list sorted by artist just as well as one sorted by title.
 */
fun <T> groupByLetter(items: List<T>, nameOf: (T) -> String): List<Pair<String, List<T>>> {
    if (items.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<String, MutableList<T>>>()
    items.forEach { item ->
        val letter = jumpLetterOf(nameOf(item))
        val last = result.lastOrNull()
        if (last != null && last.first == letter) last.second.add(item) else result.add(letter to mutableListOf(item))
    }
    return result.map { it.first to it.second.toList() }
}

// ── The queue ───────────────────────────────────────────────────────────────

/**
 * What order a queue plays in when it is shuffled.
 *
 * The track that was tapped stays first — shuffle means "surprise me after this", not "play
 * something else instead" — and the rest are dealt out behind it. [random] is passed in so the
 * order can be pinned down in a test.
 */
fun shuffledOrder(size: Int, startIndex: Int, random: kotlin.random.Random): List<Int> {
    if (size <= 0) return emptyList()
    val start = startIndex.coerceIn(0, size - 1)
    val rest = (0 until size).filter { it != start }.shuffled(random)
    return listOf(start) + rest
}
