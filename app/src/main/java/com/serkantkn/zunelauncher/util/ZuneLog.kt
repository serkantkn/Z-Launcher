package com.serkantkn.zunelauncher.util

import android.content.Context
import android.util.Log
import com.serkantkn.zunelauncher.BuildConfig
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

    /**
     * Notes for whoever is debugging, and only for them.
     *
     * Everything this launcher touches is somebody's private business — who rang, what the
     * message said, which folder is open — and logcat is not a private place: a phone plugged
     * into a computer, a bug report, or a manufacturer's log collector can all read it. Debug
     * lines are compiled out of a release build.
     */
    fun d(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
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
