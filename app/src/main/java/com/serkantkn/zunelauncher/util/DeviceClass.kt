package com.serkantkn.zunelauncher.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * What kind of phone this is, as far as the launcher needs to care.
 *
 * There is exactly one decision here — whether this is a phone that has to be treated gently —
 * and it is asked of Android rather than guessed from a model name. Nothing about how the
 * launcher *looks* hangs on it: the answer only changes how much is kept in memory and whether
 * the panel is asked to run faster than it has to.
 */
object DeviceClass {

    /** Below this, a phone is not going to enjoy holding a lot of pictures in memory. */
    private const val LOW_RAM_BYTES = 3L * 1024 * 1024 * 1024

    /** Below this heap, the picture cache has to be modest whatever the phone says about itself. */
    private const val SMALL_HEAP_MEGABYTES = 128

    @Volatile
    private var answer: Boolean? = null

    /**
     * True when the phone has little memory to spare: Android's own low-RAM flag, less than 3 GB
     * of memory in total, or a heap so small that a quarter of it is not worth spending on a
     * picture cache. Worked out once and remembered — none of it changes while the phone is on.
     */
    fun isLowEnd(context: Context): Boolean {
        answer?.let { return it }
        val result = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
            manager.isLowRamDevice ||
                info.totalMem in 1 until LOW_RAM_BYTES ||
                manager.memoryClass <= SMALL_HEAP_MEGABYTES
        }.getOrDefault(false)
        answer = result
        return result
    }

    /**
     * Whether to ask the screen for its fastest mode.
     *
     * A launcher that pins the panel to 120 Hz is asking a phone to spend battery on the screen
     * it spends the least time looking at, and a weak GPU that cannot hold 120 misses more
     * deadlines than it would at 60 — the same animation reads as worse. Fast panels on capable
     * phones still get asked; everything else is left to Android, which already picks well.
     */
    fun wantsHighRefreshRate(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLowEnd(context)
}
