package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.ui.screens.calendar.AgendaPage
import com.serkantkn.zunelauncher.ui.screens.calendar.DayHeading
import com.serkantkn.zunelauncher.ui.screens.calendar.EventRow
import com.serkantkn.zunelauncher.ui.screens.social.SocialMessageRow
import java.util.Calendar

/**
 * What the lessons' hubs are filled with, so a rehearsal looks like the real thing.
 *
 * An empty social page and a calendar with one line of grey on it teach that the hubs are empty.
 * These are made up - nobody's real messages or plans are read for a tour - but they are drawn
 * with the hubs' own rows, so what somebody sees here is exactly what the hub will look like once
 * it has their own things in it.
 */

// -- Social -----------------------------------------------------------------

/** One made-up message: who, from which app, what they said, and how many minutes ago. */
private data class SampleMessage(
    val from: Int,
    val app: String,
    val text: Int,
    val minutesAgo: Int,
    val group: Int? = null
)

private val SAMPLE_MESSAGES = listOf(
    SampleMessage(R.string.onboarding_sample_from_1, "WhatsApp", R.string.onboarding_sample_text_1, 3),
    SampleMessage(R.string.onboarding_sample_from_2, "WhatsApp", R.string.onboarding_sample_text_2, 18, group = R.string.onboarding_sample_group_2),
    SampleMessage(R.string.onboarding_sample_from_3, "Instagram", R.string.onboarding_sample_text_3, 47),
    SampleMessage(R.string.onboarding_sample_from_4, "Telegram", R.string.onboarding_sample_text_4, 95),
    SampleMessage(R.string.onboarding_sample_from_5, "Instagram", R.string.onboarding_sample_text_5, 190)
)

/** The social hub's list, with a handful of made-up messages in it. */
@Composable
fun SampleSocialFeed(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages = remember {
        val now = System.currentTimeMillis()
        SAMPLE_MESSAGES.mapIndexed { index, sample ->
            SocialMessageModel(
                id = "sample_$index",
                packageName = "sample.${sample.app.lowercase()}",
                appName = sample.app,
                title = context.getString(sample.from),
                text = context.getString(sample.text),
                timestamp = now - sample.minutesAgo * MINUTE_MS,
                icon = null,
                replyAction = null,
                openIntent = null,
                conversationTitle = sample.group?.let(context::getString)
            )
        }
    }
    Column(modifier = modifier) {
        messages.forEach { message ->
            SocialMessageRow(message = message, showAppName = true, onClick = { }, onLongClick = { })
        }
    }
}

// -- Calendar ---------------------------------------------------------------

/** One made-up event: a day from today, a start, how long, and a colour of its own. */
private data class SampleEvent(
    val title: Int,
    val dayOffset: Int,
    val hour: Int,
    val minute: Int,
    val minutes: Int,
    val colour: String,
    val place: Int? = null,
    val allDay: Boolean = false,
    val weekly: Boolean = false,
    val reminder: Boolean = false
)

private val SAMPLE_EVENTS = listOf(
    SampleEvent(R.string.onboarding_sample_event_1, 0, 9, 30, 30, "#FFA200", place = R.string.onboarding_sample_place_1),
    SampleEvent(R.string.onboarding_sample_event_2, 0, 14, 0, 60, "#1BA1E2", place = R.string.onboarding_sample_place_2, reminder = true),
    SampleEvent(R.string.onboarding_sample_event_3, 0, 18, 30, 90, "#60A917", weekly = true),
    SampleEvent(R.string.onboarding_sample_event_4, 1, 10, 15, 45, "#E51400", place = R.string.onboarding_sample_place_4, reminder = true),
    SampleEvent(R.string.onboarding_sample_event_5, 3, 0, 0, 0, "#D80073", allDay = true),
    SampleEvent(R.string.onboarding_sample_event_6, 5, 20, 0, 120, "#AA00FF", place = R.string.onboarding_sample_place_6)
)

/** The made-up events, placed on the days around today. */
@Composable
private fun rememberSampleEvents(): List<CalendarEvent> {
    val context = LocalContext.current
    return remember {
        SAMPLE_EVENTS.mapIndexed { index, sample ->
            val start = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, sample.dayOffset)
                set(Calendar.HOUR_OF_DAY, sample.hour)
                set(Calendar.MINUTE, sample.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            CalendarEvent(
                id = "sample_$index",
                title = context.getString(sample.title),
                location = sample.place?.let(context::getString).orEmpty(),
                startMillis = start,
                endMillis = if (sample.allDay) start + DAY_MS else start + sample.minutes * MINUTE_MS,
                isAllDay = sample.allDay,
                colorHex = sample.colour,
                reminderMinutes = if (sample.reminder) 15 else null,
                recurrence = if (sample.weekly) "FREQ=WEEKLY" else null
            )
        }
    }
}

/**
 * One page of the rehearsal calendar: [page] 0 is today, 1 what comes after it, 2 the agenda
 * with the next thing up in large type, as the calendar hub has them.
 */
@Composable
fun SampleCalendarPage(page: Int, modifier: Modifier = Modifier, bottomPadding: Dp = 0.dp) {
    val events = rememberSampleEvents()
    val now = remember { System.currentTimeMillis() }
    val today = remember { startOfDay(now) }
    if (page >= 2) {
        AgendaPage(
            events = events,
            now = now,
            showPast = false,
            onOpen = { },
            onLongPress = { },
            onAdd = { },
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = modifier
        )
        return
    }
    val shown = if (page == 0) {
        events.filter { startOfDay(it.startMillis) == today }
    } else {
        events.filter { startOfDay(it.startMillis) > today }
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        shown.groupBy { startOfDay(it.startMillis) }.forEach { (day, dayEvents) ->
            item(key = "day_$day") { DayHeading(dayMillis = day, nowMillis = now, count = dayEvents.size) }
            dayEvents.forEach { event ->
                item(key = event.id) {
                    EventRow(event = event, onClick = { }, onLongClick = { }, modifier = Modifier.padding(end = 4.dp))
                }
            }
        }
    }
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 60 * MINUTE_MS
