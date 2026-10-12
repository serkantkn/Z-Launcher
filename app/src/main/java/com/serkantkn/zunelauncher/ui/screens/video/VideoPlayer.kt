package com.serkantkn.zunelauncher.ui.screens.video

import android.content.Context
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

    /** Plays a file on the device, handed over through the launcher's own file provider. */
    fun open(context: Context, file: File, mimeType: String? = null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        open(context, uri, file.name, mimeType)
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

/**
 * Where each video was left, so it starts again from there. Only the last [KEEP] are kept, and
 * a video watched to within a few seconds of its end is forgotten - it was finished.
 */
internal class VideoResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_resume", Context.MODE_PRIVATE)

    fun positionOf(key: String): Long = prefs.getString(key, null)?.substringBefore(':')?.toLongOrNull() ?: 0L

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

    private companion object {
        const val KEEP = 100
        const val START_MARGIN_MS = 5_000L
        const val END_MARGIN_MS = 5_000L
    }
}
