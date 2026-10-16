package com.serkantkn.zunelauncher.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import android.util.DisplayMetrics
import android.view.WindowManager
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What the phone itself is, for the about page: as Windows Phone's "about" listed the phone's
 * name, software, firmware and hardware under the version of the phone's own software.
 */
object DeviceInfo {

    data class Info(
        val deviceName: String,
        val model: String,
        val androidVersion: String,
        val apiLevel: Int,
        val securityPatch: String?,
        val build: String,
        val kernel: String?,
        val baseband: String?,
        val bootloader: String?,
        val processor: String,
        val cores: Int,
        val memory: String?,
        val storageTotal: String?,
        val storageFree: String?,
        val screenWidth: Int,
        val screenHeight: Int,
        val screenDpi: Int
    )

    fun read(context: Context): Info {
        val app = context.applicationContext
        val model = modelName(Build.MANUFACTURER, Build.MODEL)
        val storage = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        // The screen is asked of the screen's own context: the app's has no window to measure.
        val (width, height) = screenSize(context)
        return Info(
            deviceName = runCatching { Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: model,
            model = model,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = patchDate(Build.VERSION.SECURITY_PATCH, Locale.getDefault()),
            build = Build.DISPLAY,
            kernel = System.getProperty("os.version")?.takeIf { it.isNotBlank() },
            baseband = Build.getRadioVersion()?.let(::radioVersion),
            bootloader = Build.BOOTLOADER.takeIf { it.isNotBlank() && it != Build.UNKNOWN },
            processor = processorName(),
            cores = Runtime.getRuntime().availableProcessors(),
            memory = runCatching {
                val memory = ActivityManager.MemoryInfo()
                app.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
                memory.totalMem.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(app, it) }
            }.getOrNull(),
            storageTotal = storage?.let { Formatter.formatShortFileSize(app, it.totalBytes) },
            storageFree = storage?.let { Formatter.formatShortFileSize(app, it.availableBytes) },
            screenWidth = width,
            screenHeight = height,
            screenDpi = app.resources.displayMetrics.densityDpi
        )
    }

    /** "Samsung SM-S721B"; a model that already starts with its maker is left as it is. */
    fun modelName(manufacturer: String, model: String): String {
        val maker = manufacturer.trim().replaceFirstChar { it.titlecase(Locale.ROOT) }
        return if (maker.isEmpty() || model.startsWith(manufacturer, ignoreCase = true)) model.trim()
        else "$maker ${model.trim()}"
    }

    /** One value per SIM slot, often the same twice: "A,A" is A, "A,B" is "A, B". */
    fun radioVersion(raw: String): String? = raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != Build.UNKNOWN }
        .distinct()
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }

    /** "2026-09-01" as the reader writes dates; null when the phone does not say. */
    fun patchDate(patch: String?, locale: Locale): String? {
        if (patch.isNullOrBlank()) return null
        val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(patch) }
            .getOrNull() ?: return patch
        return DateFormat.getDateInstance(DateFormat.LONG, locale).format(date)
    }

    /** The chip by its maker's name where Android says it (12 and on), its board otherwise. */
    private fun processorName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maker = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            val model = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            if (model != null) return listOfNotNull(maker, model).joinToString(" ")
        }
        return Build.HARDWARE
    }

    /** The whole screen in pixels, the narrow side first, as a phone's screen is given. */
    @Suppress("DEPRECATION")
    private fun screenSize(context: Context): Pair<Int, Int> = runCatching {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }
        minOf(w, h) to maxOf(w, h)
    }.getOrDefault(0 to 0)
}
