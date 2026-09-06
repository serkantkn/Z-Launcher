package com.serkantkn.zunelauncher.data.model

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
    val exactTimeMillis: Long? = null
) {
    val timeString: String
        get() = String.format("%02d:%02d", hour, minute)

    /**
     * Next moment this alarm fires, relative to [now]: the exact time for one-shot reminders,
     * otherwise today's/tomorrow's [hour]:[minute] advanced to the next selected weekday.
     * Same rule AlarmScheduler uses, so the Start tile and the real alarm agree.
     */
    fun nextTriggerMillis(now: Long = System.currentTimeMillis()): Long {
        exactTimeMillis?.let { return it }
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        if (daysOfWeek.isNotEmpty()) {
            var guard = 0
            while (!daysOfWeek.contains(calendar.get(java.util.Calendar.DAY_OF_WEEK)) && guard++ < 8) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }
}
