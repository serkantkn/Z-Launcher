package com.serkantkn.zunelauncher.util

import android.content.Context
import android.util.Log
import com.serkantkn.zunelauncher.R
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single logging entry point for the launcher. Every log line carries a "Zune." prefix so
 * `adb logcat -s Zune.*` shows only our output, and failures are never swallowed silently.
 * Coroutine cancellation is normal control flow, so a CancellationException is never logged.
 */
object ZuneLog {
    private const val PREFIX = "Zune."

    fun d(tag: String, message: String) {
        Log.d(PREFIX + tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable is CancellationException) return
        Log.w(PREFIX + tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable is CancellationException) return
        Log.e(PREFIX + tag, message, throwable)
    }
}

/** Short user-facing description of a failure, suitable for empty states and status banners. */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is SecurityException -> context.localizedString(R.string.error_permission_denied)
    is FileNotFoundException -> context.localizedString(R.string.error_file_not_found)
    is IOException -> context.localizedString(R.string.error_storage)
    is IllegalArgumentException, is IllegalStateException -> context.localizedString(R.string.error_unexpected)
    else -> localizedMessage?.takeIf { it.isNotBlank() } ?: context.localizedString(R.string.error_unknown)
}
