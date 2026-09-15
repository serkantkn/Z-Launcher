package com.serkantkn.zunelauncher.data.model

import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val isEnabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet(), // 1 = Sunday, 2 = Monday, ..., 7 = Saturday (Calendar.SUNDAY)
    /** Set for Notes Hub reminders: the owning note. Such alarms are one-shot. */
    val noteId: String? = null,
    /** Exact trigger time for one-shot reminders; overrides hour/minute when set. */
    val exactTimeMillis: Long? = null,
    /** What it rings with. Null means whatever the phone's default alarm sound is. */
    val ringtoneUri: String? = null,
    /** Whether it shakes the phone as well as ringing it. */
    val vibrate: Boolean = true,
    /** How long "ertele" puts it off for, in minutes. */
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    /** Whether the ring starts quiet and climbs, instead of arriving at full volume. */
    val gradualVolume: Boolean = true,
    /** Gives up after this many minutes of nobody answering. Zero means it never gives up. */
    val autoSilenceMinutes: Int = DEFAULT_AUTO_SILENCE_MINUTES,
    /** Set while the alarm is snoozed: the moment it will come back. */
    val snoozedUntilMillis: Long? = null
) {
    val timeString: String
        get() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** Whether this one comes back every week rather than firing once and going quiet. */
    val isRepeating: Boolean get() = daysOfWeek.isNotEmpty()

    /**
     * Next moment this alarm fires, relative to [now]: a pending snooze first, then the exact time
     * for one-shot reminders, otherwise today's/tomorrow's [hour]:[minute] advanced to the next
     * selected weekday. Same rule AlarmScheduler uses, so the Start tile and the real alarm agree.
     */
    fun nextTriggerMillis(now: Long = System.currentTimeMillis()): Long {
        snoozedUntilMillis?.let { if (it > now) return it }
        exactTimeMillis?.let { return it }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        if (daysOfWeek.isNotEmpty()) {
            var guard = 0
            while (!daysOfWeek.contains(calendar.get(Calendar.DAY_OF_WEEK)) && guard++ < 8) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_AUTO_SILENCE_MINUTES = 10

        /** Monday to Friday, in Calendar's own numbering. */
        val WEEKDAYS: Set<Int> = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
        )

        /** Saturday and Sunday. */
        val WEEKEND: Set<Int> = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

        /** Every day. */
        val EVERY_DAY: Set<Int> = (Calendar.SUNDAY..Calendar.SATURDAY).toSet()

        /**
         * The seven days in the order the reader's own week runs, so a Turkish week starts on
         * Monday and an American one on Sunday without either being written down twice.
         */
        fun weekOrder(locale: Locale = Locale.getDefault()): List<Int> {
            val first = Calendar.getInstance(locale).firstDayOfWeek
            return (0..6).map { ((first - 1 + it) % 7) + 1 }
        }
    }
}
