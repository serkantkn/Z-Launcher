package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import com.serkantkn.zunelauncher.data.model.SongModel
import com.serkantkn.zunelauncher.util.Lyrics
import com.serkantkn.zunelauncher.util.LyricsSource
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.parseLrc
import com.serkantkn.zunelauncher.util.readId3Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where a song's words come from.
 *
 * In order: a `.lrc` file sitting beside the track, then the lyrics frame inside the file itself,
 * then - only when neither is there and the setting allows it - the network. Local first is not
 * only politeness about somebody's data allowance: a file somebody has curated lyrics into is
 * right, and a stranger's upload might not be.
 *
 * What is found is remembered, including the fact that nothing was found, so the same song is not
 * looked up again every time it comes round.
 */
class LyricsRepository(
    private val context: Context,
    private val online: MusicOnlineApi = MusicOnlineApi
) {

    private val cache = mutableMapOf<String, Lyrics>()

    /** A key that survives a rescan: what it is, by whom, and how long. */
    private fun keyOf(song: SongModel): String =
        "${song.artist}|${song.title}|${song.duration / 1000L}"

    /**
     * The words to a song, or empty lyrics when there are none to be had.
     *
     * [allowNetwork] is the setting: with it off, this never leaves the phone.
     */
    suspend fun lyricsFor(song: SongModel, allowNetwork: Boolean): Lyrics =
        withContext(Dispatchers.IO) {
            val key = keyOf(song)
            cache[key]?.let { remembered ->
                // A remembered miss is only worth re-trying once the network is allowed.
                if (!remembered.isEmpty || !allowNetwork) return@withContext remembered
                if (remembered.source == LyricsSource.ONLINE) return@withContext remembered
            }

            val found = sidecarLyrics(song)
                ?: embeddedLyrics(song)
                ?: if (allowNetwork) onlineLyrics(song) else null

            val result = found ?: Lyrics(emptyList(), LyricsSource.NONE)
            cache[key] = result
            result
        }

    /** A `.lrc` written beside the track, which is how people who collect lyrics keep them. */
    private fun sidecarLyrics(song: SongModel): Lyrics? {
        val path = song.filePath.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val track = File(path)
            val sidecar = File(track.parentFile, track.nameWithoutExtension + ".lrc")
            // On modern Android a plain file beside a track is not ours to read unless the launcher
            // has been given the run of storage; that is a refusal, not a failure.
            if (!sidecar.isFile || !sidecar.canRead()) return@runCatching null
            parseLrc(sidecar.readText(), LyricsSource.SIDECAR_FILE).takeIf { !it.isEmpty }
        }.getOrNull()
    }

    /** The lyrics frame inside the file, which Android's own metadata reader does not report. */
    private fun embeddedLyrics(song: SongModel): Lyrics? = runCatching {
        context.contentResolver.openInputStream(song.uri)?.use { stream ->
            // Tags live at the front of the file; a megabyte is far more than any of them need.
            val header = ByteArray(TAG_READ_BYTES)
            var read = 0
            while (read < header.size) {
                val got = stream.read(header, read, header.size - read)
                if (got <= 0) break
                read += got
            }
            readId3Lyrics(if (read == header.size) header else header.copyOf(read))
        }?.takeIf { !it.isEmpty }
    }.getOrElse {
        ZuneLog.w(TAG, "the file's own lyrics could not be read", it)
        null
    }

    private fun onlineLyrics(song: SongModel): Lyrics? =
        online.fetchLyrics(
            artist = song.artist,
            title = song.title,
            album = song.album,
            durationSeconds = (song.duration / 1000L).toInt()
        )

    private companion object {
        const val TAG = "LyricsRepository"
        const val TAG_READ_BYTES = 1024 * 1024
    }
}
