package com.serkantkn.zunelauncher.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AlbumModel
import com.serkantkn.zunelauncher.data.model.ArtistModel
import com.serkantkn.zunelauncher.data.model.SongModel
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import com.serkantkn.zunelauncher.util.sortAlbums
import com.serkantkn.zunelauncher.util.sortArtists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the phone has to play: the tracks in the media store, and the albums and artists they make.
 *
 * Albums and artists are worked out from the tracks rather than queried separately. One query is
 * cheaper than three, and — more to the point — it cannot disagree with itself: an album built
 * from the same rows as the song list always holds exactly the songs the song list shows.
 */
class MusicRepository(private val context: Context) {

    /** Every track, in the order a Turkish reader expects. */
    suspend fun getLocalSongs(): List<SongModel> = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
        }.toTypedArray()

        val where = buildList {
            add("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            // A track still being written, or sitting in the bin, is not something to play.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
            add("${MediaStore.Audio.Media.DURATION} > 0")
        }.joinToString(" AND ")

        val unknownTitle = context.localizedString(R.string.music_unknown_song)
        val unknownArtist = context.localizedString(R.string.music_unknown_artist)
        val unknownAlbum = context.localizedString(R.string.music_unknown_album)

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                where,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val artistIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val addedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val mimeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                @Suppress("DEPRECATION")
                val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val albumId = cursor.getLong(albumIdColumn)
                        // The media store writes a track number as disc*1000 + track, so a second
                        // disc reads as 1001 rather than 1. Only the last three digits are the
                        // track, which is what anybody means by "track 1".
                        val rawTrack = if (trackColumn != -1) cursor.getInt(trackColumn) else 0
                        add(
                            SongModel(
                                id = id.toString(),
                                title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: unknownTitle,
                                artist = cursor.getString(artistColumn)
                                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: unknownArtist,
                                album = cursor.getString(albumColumn)
                                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: unknownAlbum,
                                duration = cursor.getLong(durationColumn),
                                uri = ContentUris.withAppendedId(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    id
                                ),
                                albumArtUri = albumArtUri(albumId),
                                albumId = albumId,
                                artistId = if (artistIdColumn != -1) cursor.getLong(artistIdColumn) else 0L,
                                trackNumber = if (rawTrack > 1000) rawTrack % 1000 else rawTrack,
                                year = if (yearColumn != -1) cursor.getInt(yearColumn) else 0,
                                dateAdded = if (addedColumn != -1) cursor.getLong(addedColumn) else 0L,
                                mimeType = if (mimeColumn != -1) cursor.getString(mimeColumn).orEmpty() else "",
                                sizeBytes = if (sizeColumn != -1) cursor.getLong(sizeColumn) else 0L,
                                filePath = if (pathColumn != -1) cursor.getString(pathColumn).orEmpty() else ""
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrElse {
            ZuneLog.e(TAG, "the audio store would not be read", it)
            throw it
        }
    }

    /**
     * Where a cover lives.
     *
     * The old `content://media/external/audio/albumart/<id>` path still works and is still what
     * most of the world uses, but it is undocumented and gone on some phones. From Android 10 the
     * album row itself serves the artwork, which is the supported way to ask.
     */
    private fun albumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        } else {
            @Suppress("DEPRECATION")
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
        }
    }

    /** The albums those tracks belong to. */
    fun albumsOf(songs: List<SongModel>): List<AlbumModel> =
        songs.groupBy { it.albumId }
            .map { (albumId, tracks) ->
                val first = tracks.first()
                AlbumModel(
                    id = albumId,
                    title = first.album,
                    // A record with several singers is theirs jointly; the commonest name on it
                    // is the one somebody would look it up under.
                    artist = tracks.groupingBy { it.artist }.eachCount()
                        .maxByOrNull { it.value }?.key ?: first.artist,
                    artUri = tracks.firstNotNullOfOrNull { it.albumArtUri },
                    trackCount = tracks.size,
                    totalDuration = tracks.sumOf { it.duration },
                    year = tracks.maxOf { it.year }
                )
            }
            .let { sortAlbums(it) }

    /** The artists those tracks belong to. */
    fun artistsOf(songs: List<SongModel>): List<ArtistModel> =
        songs.groupBy { it.artistId to it.artist }
            .map { (key, tracks) ->
                ArtistModel(
                    id = key.first,
                    name = key.second,
                    albumCount = tracks.map { it.albumId }.distinct().size,
                    trackCount = tracks.size,
                    artUri = tracks.maxByOrNull { it.year }?.albumArtUri
                        ?: tracks.firstNotNullOfOrNull { it.albumArtUri }
                )
            }
            .let { sortArtists(it) }

    private companion object {
        const val TAG = "MusicRepository"
    }
}
