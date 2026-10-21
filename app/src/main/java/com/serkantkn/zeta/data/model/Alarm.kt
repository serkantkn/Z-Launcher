package com.serkantkn.zeta.data.model

import com.serkantkn.zeta.util.TimeText
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
    val snoozedUntilMillis: Long? = null,
    /**
     * The moment a one-shot alarm was last booked for, so a phone that was off when it should
     * have rung can tell a missed alarm from one that is simply due tomorrow.
     */
    val bookedForMillis: Long? = null
) {
    /** The time as the clock setting says it: "07:05" or "7:05 AM". */
    val timeString: String
        get() = TimeText.clock(hour, minute)

    /** Whether this one comes back every week rather than firing once and going quiet. */
    val isRepeating: Boolean get() = daysOfWeek.isNotEmpty()

    /** A note's reminder, or a reminder with a fixed moment: fires once, at that moment. */
    val isExact: Boolean get() = exactTimeMillis != null

    /** Whether a snooze is pending at [now]. */
    fun isSnoozedAt(now: Long): Boolean = snoozedUntilMillis != null && snoozedUntilMillis > now

    /**
     * Next moment this alarm fires, relative to [now]: a pending snooze first, then the exact time
     * for one-shot reminders, otherwise today's/tomorrow's [hour]:[minute] advanced to the next
     * selected weekday. Same rule AlarmScheduler uses, so the Start tile and the real alarm agree.
     *
     * An exact time that has already gone by is still returned: see [dueAt] for the version a
     * scheduler wants, which refuses to book something in the past.
     */
    fun nextTriggerMillis(now: Long = System.currentTimeMillis()): Long {
        snoozedUntilMillis?.let { if (it > now) return it }
        exactTimeMillis?.let { return it }
        return nextClockTime(now)
    }

    /**
     * When the system should be asked to ring this, or null when there is nothing left to ring:
     * an exact-time reminder whose moment has passed with no snooze pending.
     */
    fun dueAt(now: Long = System.currentTimeMillis()): Long? {
        snoozedUntilMillis?.let { if (it > now) return it }
        exactTimeMillis?.let { return if (it > now) it else null }
        return nextClockTime(now)
    }

    /**
     * Whether this alarm should have rung already and never did — the phone was off, or the
     * launcher was stopped. A repeating alarm is never missed in this sense: it simply moves on
     * to its next day. A pending snooze means it is still waiting, not missed.
     */
    fun isMissedAt(now: Long = System.currentTimeMillis()): Boolean {
        if (!isEnabled || isRepeating) return false
        if (isSnoozedAt(now)) return false
        exactTimeMillis?.let { return it <= now }
        // A snooze that came and went while the phone was off counts as missed too.
        snoozedUntilMillis?.let { if (it <= now) return true }
        return bookedForMillis != null && bookedForMillis <= now
    }

    private fun nextClockTime(now: Long): Long {
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

        /**
         * The PendingIntent request code for an alarm's own booking, and the different one for its
         * snooze: a snooze booked under the base code would replace the next day's booking of a
         * repeating alarm, and the day after would never ring.
         */
        fun requestCode(id: String): Int = id.hashCode()

        fun snoozeRequestCode(id: String): Int = id.hashCode() xor SNOOZE_CODE_BIT

        private const val SNOOZE_CODE_BIT = 0x40000000

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
