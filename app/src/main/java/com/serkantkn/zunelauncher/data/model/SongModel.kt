package com.serkantkn.zunelauncher.data.model

import android.net.Uri

/**
 * One track in the phone's own music library.
 *
 * [albumId] and [artistId] are the media store's own grouping keys. They matter more than they
 * look: two albums can share a name, two artists can share a name, and grouping by the text would
 * quietly merge them. The store's ids never do that.
 */
data class SongModel(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val albumId: Long = 0L,
    val artistId: Long = 0L,
    /** Where the track sits on its album; zero when nothing said. */
    val trackNumber: Int = 0,
    val year: Int = 0,
    /** Seconds since the epoch, the way the media store hands it over. */
    val dateAdded: Long = 0L,
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    /**
     * Where the file actually is, when the store will say.
     *
     * The column is deprecated and often empty on modern Android, and nothing here depends on it
     * — it is only used to look for a `.lrc` file sitting beside the track, which is a thing
     * people who collect lyrics do.
     */
    val filePath: String = ""
)

/** An album: the tracks on it, and what to show on its tile. */
data class AlbumModel(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: Uri?,
    val trackCount: Int,
    /** How long the whole record runs. */
    val totalDuration: Long,
    val year: Int = 0
)

/** An artist, and how much of them there is. */
data class ArtistModel(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    /** The newest cover of theirs, so the row is not a blank square. */
    val artUri: Uri?
)
