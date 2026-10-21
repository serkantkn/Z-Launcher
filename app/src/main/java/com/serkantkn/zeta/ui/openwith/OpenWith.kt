package com.serkantkn.zeta.ui.openwith

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.serkantkn.zeta.MainActivity
import com.serkantkn.zeta.data.model.HubType
import java.io.File

/**
 * What the launcher does as the phone's gallery, music player and notes app: other apps hand it a
 * picture, a song or "a new note", and it opens them in its own hubs.
 *
 * A file from the phone's own library is opened as part of it - a photo in the pictures hub among
 * its album, a song in the music hub among its record. Anything else (an attachment, a download
 * another app holds) is copied in first: the permission another app gives to read its file lasts
 * only as long as the screen it was given to, and the launcher's hubs outlive that.
 */
internal object OpenWith {

    /** The launcher brought forward on [hub]. */
    fun openHub(context: Context, hub: HubType) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_HUB, hub.name)
        )
    }

    /**
     * The library's own address for [uri] when it is an item of the media library - the form the
     * hubs know their items by - or null for anything else. A library item can arrive as
     * `external_primary/images/media/7`, `external/file/7` or `external/images/media/7`.
     */
    fun libraryUri(context: Context, uri: Uri, kind: Kind): Uri? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val segments = uri.pathSegments
        val collection = when {
            "images" in segments -> Kind.IMAGE
            "audio" in segments -> Kind.AUDIO
            "video" in segments -> Kind.VIDEO
            "file" in segments -> kindOf(context.contentResolver.getType(uri))
            else -> null
        }
        if (collection != kind) return null
        return ContentUris.withAppendedId(kind.collection, id)
    }

    enum class Kind(val collection: Uri) {
        IMAGE(MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
        AUDIO(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
        VIDEO(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    private fun kindOf(mime: String?): Kind? = when {
        mime == null -> null
        mime.startsWith("image/") -> Kind.IMAGE
        mime.startsWith("audio/") || mime == "application/ogg" -> Kind.AUDIO
        mime.startsWith("video/") -> Kind.VIDEO
        else -> null
    }

    /** The name another app gave its file, or the last part of its address. */
    fun nameOf(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

    /**
     * Copies another app's file into the launcher's cache, under [folder], and returns the copy.
     * Only the last few copies are kept; they are someone else's files and not the launcher's to
     * hoard.
     */
    fun copyIn(context: Context, uri: Uri, folder: String, name: String): File? = runCatching {
        val dir = File(context.cacheDir, folder).apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP_COPIES - 1)?.forEach { it.delete() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        val target = File(dir, safe)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrNull()

    private const val KEEP_COPIES = 5
}
