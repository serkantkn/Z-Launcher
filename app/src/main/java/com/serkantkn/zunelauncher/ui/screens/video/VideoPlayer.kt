package com.serkantkn.zunelauncher.ui.screens.video

import android.content.Context
import com.serkantkn.zunelauncher.util.Pro
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudItem
import java.io.File

/**
 * The way into the launcher's own video player, from wherever a video turns up: the music +
 * videos hub, the Files hub (on the device and on a drive), the pictures, a mail or a message.
 *
 * The player is an activity of its own rather than a screen inside the launcher, because the
 * launcher is the home screen and the home screen cannot shrink into a corner; the player can,
 * and goes on playing in a small window when it is left.
 */
object VideoPlayer {

    internal const val EXTRA_TITLE = "video_title"
    internal const val EXTRA_CLOUD_ACCOUNT = "video_cloud_account"
    internal const val EXTRA_CLOUD_ID = "video_cloud_id"
    internal const val EXTRA_PATH = "video_path"
    internal const val EXTRA_QUEUE_FOLDER = "video_queue_folder"

    /** Whether a file of this type is one the player should take rather than another app. */
    fun handles(mimeType: String?): Boolean =
        mimeType?.substringBefore(';')?.trim()?.lowercase()?.startsWith("video/") == true

    /** Plays a video the launcher can read: a media-store item, a file of ours, a shared uri. */
    fun open(context: Context, uri: Uri, title: String? = null, mimeType: String? = null) {
        val intent = Intent(context, VideoPlayerActivity::class.java)
            .setDataAndType(uri, mimeType?.takeIf { it.isNotBlank() } ?: "video/*")
            .putExtra(EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        VideoPlayerActivity.closeSmallWindow()
        context.startActivity(intent)
    }

    /**
     * Plays a file on the device, handed over through the launcher's own file provider. With
     * [withFolder] the other videos in its folder come after it, in the order of their names -
     * for a folder somebody keeps films in, not for the copy of an attachment.
     */
    fun open(context: Context, file: File, mimeType: String? = null, withFolder: Boolean = false) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(context, VideoPlayerActivity::class.java)
            .setDataAndType(uri, mimeType?.takeIf { it.isNotBlank() } ?: "video/*")
            .putExtra(EXTRA_TITLE, file.name)
            .putExtra(EXTRA_PATH, file.absolutePath)
            .putExtra(EXTRA_QUEUE_FOLDER, withFolder)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        VideoPlayerActivity.closeSmallWindow()
        context.startActivity(intent)
    }

    /** Plays a video straight off a drive, as it arrives, without downloading it first. */
    fun openCloud(context: Context, account: CloudAccount, item: CloudItem) {
        val intent = Intent(context, VideoPlayerActivity::class.java)
            .putExtra(EXTRA_TITLE, item.name)
            .putExtra(EXTRA_CLOUD_ACCOUNT, account.toJson().toString())
            .putExtra(EXTRA_CLOUD_ID, item.contentId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        VideoPlayerActivity.closeSmallWindow()
        context.startActivity(intent)
    }
}

/** How the videos page lays the videos out. */
internal enum class VideoOrder { DATE, NAME, FOLDER }

/** What happens when a video comes to its end. */
internal enum class VideoEndAction { STOP, NEXT, REPEAT }

/**
 * How the player was last set up, kept for the next film.
 *
 * Filling the screen, what happens at the end and subtitles are Pro's: the free build reads them
 * as they stand by default and leaves whatever was stored for Pro.
 */
internal class VideoPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE)

    /** The picture filling the screen, cut at its edges, rather than fitted inside it. */
    var fill: Boolean
        get() = Pro.unlocked && prefs.getBoolean("fill", false)
        set(value) { if (Pro.unlocked) prefs.edit().putBoolean("fill", value).apply() }

    var endAction: VideoEndAction
        get() = if (!Pro.unlocked) VideoEndAction.STOP
            else runCatching { VideoEndAction.valueOf(prefs.getString("end", null).orEmpty()) }.getOrDefault(VideoEndAction.NEXT)
        set(value) { if (Pro.unlocked) prefs.edit().putString("end", value.name).apply() }

    var videosOrder: VideoOrder
        get() = runCatching { VideoOrder.valueOf(prefs.getString("order", null).orEmpty()) }.getOrDefault(VideoOrder.DATE)
        set(value) = prefs.edit().putString("order", value.name).apply()

    /** Subtitles shown when a film has some; turned off, they stay off for the next one too. */
    var subtitles: Boolean
        get() = Pro.unlocked && prefs.getBoolean("subtitles", true)
        set(value) { if (Pro.unlocked) prefs.edit().putBoolean("subtitles", value).apply() }
}

/**
 * Where each video was left, so it starts again from there. Only the last [KEEP] are kept, and
 * a video watched to within a few seconds of its end is forgotten - it was finished.
 */
internal class VideoResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_resume", Context.MODE_PRIVATE)

    fun positionOf(key: String): Long = prefs.getString(key, null)?.substringBefore(':')?.toLongOrNull() ?: 0L

    /** Every video left part way, with where it was left and when: key to (position, saved at). */
    fun all(): Map<String, Pair<Long, Long>> = prefs.all.mapNotNull { (key, value) ->
        val text = value as? String ?: return@mapNotNull null
        val position = text.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        key to (position to (text.substringAfter(':').toLongOrNull() ?: 0L))
    }.toMap()

    fun remember(key: String, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && durationMs - positionMs < END_MARGIN_MS
        if (positionMs < START_MARGIN_MS || finished) {
            prefs.edit().remove(key).apply()
            return
        }
        val editor = prefs.edit().putString(key, "$positionMs:${System.currentTimeMillis()}")
        val all = prefs.all
        if (all.size > KEEP) {
            all.entries
                .sortedBy { (it.value as? String)?.substringAfter(':')?.toLongOrNull() ?: 0L }
                .take(all.size - KEEP)
                .forEach { editor.remove(it.key) }
        }
        editor.apply()
    }

    companion object {
        /**
         * The name a video is remembered by. A video in the media store is the same video
         * whether the pictures or the videos page opened it, so it goes by its number there.
         */
        fun keyOf(uri: Uri): String {
            if (uri.authority == android.provider.MediaStore.AUTHORITY) {
                val id = runCatching { android.content.ContentUris.parseId(uri) }.getOrDefault(-1L)
                if (id >= 0) return "media:$id"
            }
            return uri.toString()
        }

        private const val KEEP = 100
        private const val START_MARGIN_MS = 5_000L
        private const val END_MARGIN_MS = 5_000L
    }
}
