package com.serkantkn.zeta.data.repository

import android.app.Notification
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.service.AlarmNotifier
import com.serkantkn.zeta.data.service.CallNotifier
import com.serkantkn.zeta.data.service.EmailNotifier
import com.serkantkn.zeta.data.service.EventReminderReceiver
import com.serkantkn.zeta.data.service.MessageNotifier
import com.serkantkn.zeta.data.service.MissedCallReceiver
import com.serkantkn.zeta.data.service.TimerNotifier

/**
 * Which of the launcher's hubs one of its own notifications speaks for.
 *
 * To Android every one of them comes from "Zeta" — a new mail, a text, a missed call, the
 * alarm — and a list headed with that name leaves the reader to work out what each one is. The
 * channel says it plainly; failing a channel (an old notification, one posted before the
 * channel existed), the category does.
 */
object NotificationSource {

    private val CHANNELS: Map<String, HubType> = mapOf(
        CallNotifier.CHANNEL_ID to HubType.PHONE,
        MissedCallReceiver.CHANNEL_ID to HubType.PHONE,
        MessageNotifier.CHANNEL_ID to HubType.MESSAGING,
        EmailNotifier.CHANNEL_ID to HubType.EMAIL,
        TimerNotifier.CHANNEL_ID to HubType.CLOCK,
        AlarmNotifier.CHANNEL_RINGING to HubType.CLOCK,
        AlarmNotifier.CHANNEL_MISSED to HubType.CLOCK,
        EventReminderReceiver.CHANNEL_ID to HubType.CALENDAR
    )

    fun hubOf(channelId: String?, category: String?): HubType? =
        channelId?.let { CHANNELS[it] } ?: when (category) {
            Notification.CATEGORY_EMAIL -> HubType.EMAIL
            Notification.CATEGORY_MESSAGE -> HubType.MESSAGING
            Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL -> HubType.PHONE
            Notification.CATEGORY_ALARM, Notification.CATEGORY_STOPWATCH -> HubType.CLOCK
            Notification.CATEGORY_EVENT -> HubType.CALENDAR
            else -> null
        }
}
