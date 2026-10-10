package com.serkantkn.zunelauncher.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The one answer to "12 or 24 hours?", for the whole process.
 *
 * The setting lives in SettingsDataStore as a pattern ("HH:mm" or "hh:mm a"), and until now only
 * Start read it: the alarm screen, the alarm list, the world clock, the calendar and the weather
 * each wrote "HH:mm" by hand. This holder is fed once at app start ([bind]) and read everywhere a
 * clock reading is written out, so the phone says the same time the same way on every page.
 *
 * No Android in here, so the formatting can be unit-tested and the model classes can use it.
 */
object TimeText {

    private val _use24h = MutableStateFlow(true)

    /** True for "14:05", false for "2:05 PM". */
    val use24h: StateFlow<Boolean> = _use24h.asStateFlow()

    /** Sets the holder by hand — tests, or a caller that already knows. */
    fun set(use24h: Boolean) {
        _use24h.value = use24h
    }

    /** Whether a stored time pattern is a 24-hour one. */
    fun is24hPattern(pattern: String): Boolean = pattern.contains('H')

    /** Keeps the holder in step with the stored pattern for as long as [flow] is collected. */
    suspend fun bind(flow: Flow<String>) {
        flow.collect { _use24h.value = is24hPattern(it) }
    }

    /** The SimpleDateFormat pattern for a clock reading in the current setting. */
    fun pattern(withSeconds: Boolean = false, use24h: Boolean = _use24h.value): String = when {
        use24h && withSeconds -> "HH:mm:ss"
        use24h -> "HH:mm"
        withSeconds -> "h:mm:ss a"
        else -> "h:mm a"
    }

    /** A moment as a clock reading, in the phone's own zone unless another is given. */
    fun format(
        millis: Long,
        zone: TimeZone = TimeZone.getDefault(),
        withSeconds: Boolean = false,
        locale: Locale = Locale.getDefault(),
        use24h: Boolean = _use24h.value
    ): String = SimpleDateFormat(pattern(withSeconds, use24h), locale)
        .apply { timeZone = zone }
        .format(Date(millis))

    /**
     * An hour and minute as a clock reading: "07:05" or "7:05 AM".
     *
     * Goes through a Calendar so the AM/PM word comes out in the reader's own language.
     */
    fun clock(
        hour: Int,
        minute: Int,
        locale: Locale = Locale.getDefault(),
        use24h: Boolean = _use24h.value
    ): String {
        if (use24h) return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), locale).apply {
            clear()
            set(2000, Calendar.JANUARY, 1, hour, minute, 0)
        }
        return SimpleDateFormat("h:mm a", locale)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(calendar.time)
    }

    /** The hour as a 12-hour dial shows it: 0 → 12, 13 → 1. */
    fun hour12Of(hour24: Int): Int = ((hour24 + 11) % 12) + 1

    /** Whether a 24-hour hour is in the afternoon. */
    fun isPm(hour24: Int): Boolean = hour24 >= 12

    /** A 12-hour dial reading back to the 24-hour hour: (12, am) → 0, (1, pm) → 13. */
    fun hour24Of(hour12: Int, pm: Boolean): Int = (hour12 % 12) + if (pm) 12 else 0
}
