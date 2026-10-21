package com.serkantkn.zeta.ui.screens.video

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.serkantkn.zeta.util.FileViewer
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.viewerFor
import java.io.File
import java.util.Locale

/** One video the player can go on to: where it is, what it is called, and its type. */
internal data class QueuedVideo(val uri: Uri, val title: String, val mimeType: String?, val path: String? = null)

/**
 * The videos beside the one playing, in the order of their names, so the player can go on to
 * the next episode: the rest of a folder on the device, or the rest of its album in the media
 * store. A video from anywhere else - a mail, a message, a drive - has nothing beside it.
 */
internal object VideoQueue {
    private const val TAG = "VideoQueue"

    /** The folder a file is in, its videos in the order of their names. */
    fun ofFolder(context: Context, file: File): List<QueuedVideo> {
        val siblings = file.parentFile?.listFiles()?.filter { it.isFile && isVideoName(it.name) }.orEmpty()
        return siblings
            .sortedWith(compareBy(NaturalOrder) { it.name })
            .mapNotNull { sibling ->
                runCatching {
                    QueuedVideo(
                        uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sibling),
                        title = sibling.name,
                        mimeType = null,
                        path = sibling.absolutePath
                    )
                }.getOrNull()
            }
    }

    /** The album a media-store video is in, its videos in the order of their names. */
    fun ofMediaStore(context: Context, uri: Uri): List<QueuedVideo> {
        if (uri.authority != MediaStore.AUTHORITY) return emptyList()
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull()?.takeIf { it >= 0 } ?: return emptyList()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        return try {
            val bucket = context.contentResolver.query(
                collection, arrayOf(MediaStore.Video.Media.BUCKET_ID), "${MediaStore.Video.Media._ID} = ?", arrayOf(id.toString()), null
            )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return emptyList()
            val videos = mutableListOf<QueuedVideo>()
            context.contentResolver.query(
                collection,
                @Suppress("DEPRECATION")
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.MIME_TYPE, MediaStore.MediaColumns.DATA),
                "${MediaStore.Video.Media.BUCKET_ID} = ?",
                arrayOf(bucket),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    videos += QueuedVideo(
                        uri = ContentUris.withAppendedId(collection, cursor.getLong(0)),
                        title = cursor.getString(1).orEmpty(),
                        mimeType = cursor.getString(2),
                        path = cursor.getString(3)
                    )
                }
            }
            videos.sortedWith(compareBy(NaturalOrder) { it.title })
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the album could not be read", e)
            emptyList()
        }
    }

    fun isVideoName(name: String): Boolean = viewerFor(name.substringAfterLast('.', "")) == FileViewer.VIDEO
}

/**
 * Names in the order people read them: "bölüm 2" before "bölüm 10", digits compared as numbers
 * and letters without regard to case.
 */
internal object NaturalOrder : Comparator<String> {
    private val collator = java.text.Collator.getInstance(Locale.getDefault()).apply { strength = java.text.Collator.SECONDARY }

    override fun compare(a: String, b: String): Int {
        val left = chunks(a)
        val right = chunks(b)
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val bothNumbers = x[0].isDigit() && y[0].isDigit()
            val order = if (bothNumbers) {
                val xs = x.trimStart('0')
                val ys = y.trimStart('0')
                if (xs.length != ys.length) xs.length - ys.length else xs.compareTo(ys)
            } else {
                collator.compare(x, y)
            }
            if (order != 0) return order
        }
        return if (left.size != right.size) left.size - right.size else a.compareTo(b)
    }

    private fun chunks(text: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val digit = text[i].isDigit()
            var j = i
            while (j < text.length && text[j].isDigit() == digit) j++
            out += text.substring(i, j)
            i = j
        }
        return out
    }
}

/**
 * The subtitle files kept beside a film under the same name - "film.srt" or "film.tr.srt" for
 * "film.mp4" - out of the names in its folder, in the order a person would pick: one in their
 * own language first. The folder can only be read with access to all files; without it there
 * are none, and a subtitle can still be picked by hand.
 */
internal fun sidecarSubtitles(videoName: String, folderNames: List<String>, language: String = Locale.getDefault().language): List<String> {
    val stem = videoName.substringBeforeLast('.')
    val found = folderNames.filter { candidate ->
        val extension = candidate.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val name = candidate.substringBeforeLast('.')
        extension in SUBTITLE_EXTENSIONS && (name == stem || name.startsWith("$stem."))
    }
    return found.sortedWith(
        compareByDescending<String> { subtitleLanguage(videoName, it) == language }.thenBy { it }
    )
}

/** "tr" for "film.tr.srt" beside "film.mp4"; null when the name says no language. */
internal fun subtitleLanguage(videoName: String, subtitleName: String): String? {
    val stem = videoName.substringBeforeLast('.')
    val middle = subtitleName.substringBeforeLast('.').removePrefix(stem).removePrefix(".").lowercase(Locale.ROOT)
    return middle.takeIf { it.length in 2..3 && it.all { c -> c in 'a'..'z' } }
}

/** The subtitle types the player reads. */
internal val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml")
