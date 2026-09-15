package com.serkantkn.zunelauncher.data.model

import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Something in the diary.
 *
 * It can come from two places and the hub shows both as one list: the phone's own calendar, which
 * is where a meeting somebody else invited you to lives, and the launcher's own store, which is
 * where a reminder attached to a note lives and where everything goes when the calendar permission
 * has not been given.
 *
 * An event is a span, not a moment. The old shape kept a `timestamp` for the day and a separate
 * `hour`/`minute` for the time, which meant the same event carried two different times — the one
 * inside the timestamp and the one beside it — and nothing said which was real. Here there is one
 * start and one end, both absolute, and the hour and minute are read back off the start.
 */
data class CalendarEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val location: String = "",
    val startMillis: Long,
    val endMillis: Long = startMillis + DEFAULT_DURATION_MS,
    val isAllDay: Boolean = false,
    val colorHex: String = DEFAULT_COLOR,
    /** How many minutes before the start to give warning; null means no reminder. */
    val reminderMinutes: Int? = null,
    /** How it repeats, as the calendar's own RRULE. Null means it happens once. */
    val recurrence: String? = null,
    /** Set for Notes Hub reminders: the note this belongs to. */
    val linkedNoteId: String? = null,
    /** The phone calendar it lives in, when it is not one of ours. */
    val calendarId: Long? = null,
    /** Its row in the phone's calendar; null for an event the launcher keeps itself. */
    val systemEventId: Long? = null,
    /** Which calendar it came from, for saying so and for knowing where to write it back. */
    val isLocal: Boolean = true,
    /** False when the calendar it lives in is read-only (a birthdays or holidays feed). */
    val isEditable: Boolean = true
) {
    /** The start, for the callers that only care when it begins. */
    val timestamp: Long get() = startMillis

    val hour: Int get() = fieldOf(Calendar.HOUR_OF_DAY)

    val minute: Int get() = fieldOf(Calendar.MINUTE)

    val formattedTime: String get() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** How long it runs for, never negative. */
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)

    /** Whether it repeats rather than happening once. */
    val isRepeating: Boolean get() = !recurrence.isNullOrBlank()

    private fun fieldOf(field: Int): Int =
        Calendar.getInstance().apply { timeInMillis = startMillis }.get(field)

    companion object {
        const val DEFAULT_DURATION_MS = 60 * 60 * 1000L
        const val DEFAULT_COLOR = "#E0007A"

        /** How long before an event the launcher warns by default. */
        const val DEFAULT_REMINDER_MINUTES = 10

        /** The reminders offered, in minutes; zero means "as it starts". */
        val REMINDER_CHOICES = listOf(0, 5, 10, 15, 30, 60, 24 * 60)

        /** The repeats offered, as the calendar's own rules. Null is "once". */
        val RECURRENCE_CHOICES: List<String?> = listOf(
            null,
            "FREQ=DAILY",
            "FREQ=WEEKLY",
            "FREQ=MONTHLY",
            "FREQ=YEARLY"
        )
    }
}
