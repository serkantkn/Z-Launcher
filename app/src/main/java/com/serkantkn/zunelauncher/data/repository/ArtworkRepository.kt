package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.serkantkn.zunelauncher.data.model.AlbumModel
import com.serkantkn.zunelauncher.data.model.SongModel
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The picture on the front of a record.
 *
 * Three places are tried, in the order that respects what is already on the phone: the media
 * store's own album art, then the picture inside the file itself, then - only if the setting
 * allows - the internet. The middle one matters more than it sounds: the store's album art table
 * is built by the system scanner and is missing surprisingly often, while the very same file
 * carries the cover in its tags.
 *
 * Whatever is found from the last two is written into the launcher's cache directory, so a record
 * is only ever worked out once. An album nobody has a picture of is remembered as such, so the
 * network is not asked again every time the hub opens.
 */
class ArtworkRepository(
    private val context: Context,
    private val online: MusicOnlineApi = MusicOnlineApi
) {

    private val cacheDir: File by lazy {
        File(context.cacheDir, "albumart").apply { mkdirs() }
    }

    /** Albums already looked at, so the work is not repeated within one run. */
    private val checked = mutableSetOf<Long>()

    /** Albums nobody has a picture of; remembered so the network is asked once, not always. */
    private val missing = mutableSetOf<Long>()

    /**
     * A cover for [album], or null when there is none to be had.
     *
     * Returns the cached file it wrote. The caller decides what to do with it; nothing here
     * touches the media store, which is not ours to write into.
     */
    suspend fun coverFor(
        album: AlbumModel,
        sampleTrack: SongModel?,
        allowNetwork: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        val cached = cacheFileOf(album.id)
        if (cached.isFile && cached.length() > 0L) return@withContext Uri.fromFile(cached)
        if (album.id in missing && !allowNetwork) return@withContext null
        if (album.id in checked && album.id in missing) return@withContext null
        checked += album.id

        // What the store says, when it really answers. An album row that will not open is the
        // usual case this whole class exists for.
        if (album.artUri != null && opens(album.artUri)) return@withContext null

        val embedded = sampleTrack?.let(::embeddedPicture)
        if (embedded != null) {
            write(cached, embedded)
            return@withContext Uri.fromFile(cached)
        }

        if (!allowNetwork) {
            missing += album.id
            return@withContext null
        }

        val fetched = online.fetchCoverArt(album.artist, album.title)
        if (fetched == null) {
            missing += album.id
            return@withContext null
        }
        write(cached, fetched)
        Uri.fromFile(cached)
    }

    /** Whether the store's own album art actually opens, rather than merely having an address. */
    private fun opens(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.read() != -1 } ?: false
    }.getOrDefault(false)

    /** The cover inside the file, which the store's album art table often does not know about. */
    private fun embeddedPicture(song: SongModel): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, song.uri)
            retriever.embeddedPicture
        }.getOrElse {
            ZuneLog.w(TAG, "the file carried no readable cover", it)
            null
        }.also {
            runCatching { retriever.release() }
        }
    }

    private fun write(file: File, bytes: ByteArray) {
        runCatching { file.writeBytes(bytes) }
            .onFailure { ZuneLog.w(TAG, "the cover could not be cached", it) }
    }

    private fun cacheFileOf(albumId: Long): File = File(cacheDir, "$albumId.img")

    /** Throws away every cached cover, for the setting that turns this off. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
        checked.clear()
        missing.clear()
        Unit
    }

    private companion object {
        const val TAG = "ArtworkRepository"
    }
}
