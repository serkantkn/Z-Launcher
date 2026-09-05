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
}
