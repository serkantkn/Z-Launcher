package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.ActionCenterNotifications
import com.serkantkn.zunelauncher.data.repository.NotificationSource
import com.serkantkn.zunelauncher.data.repository.PanelNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationSourceTest {

    @Test
    fun `the launcher's own channels name their hub`() {
        assertEquals(HubType.EMAIL, NotificationSource.hubOf("email_new_mail", null))
        assertEquals(HubType.MESSAGING, NotificationSource.hubOf("zune_messages", "msg"))
        assertEquals(HubType.PHONE, NotificationSource.hubOf("zune_missed_calls", null))
        assertEquals(HubType.CLOCK, NotificationSource.hubOf("zune_alarm_ringing", "alarm"))
        assertEquals(HubType.CALENDAR, NotificationSource.hubOf("zune_events", null))
    }

    @Test
    fun `without a channel the category decides, and nothing guesses past it`() {
        assertEquals(HubType.EMAIL, NotificationSource.hubOf(null, "email"))
        assertEquals(HubType.PHONE, NotificationSource.hubOf("miscellaneous", "missed_call"))
        assertNull(NotificationSource.hubOf("miscellaneous", null))
        assertNull(NotificationSource.hubOf(null, "promo"))
    }

    @Test
    fun `the launcher's notifications are grouped by hub, not under one name`() {
        fun item(key: String, hub: HubType?, time: Long, pkg: String = "com.serkantkn.zunelauncher") = PanelNotification(
            key = key, packageName = pkg, appName = hub?.name ?: pkg, appIcon = null,
            title = key, text = "", subText = "", postTime = time, clearable = true,
            contentIntent = null, autoCancel = true, reply = null, hub = hub
        )
        val groups = ActionCenterNotifications.group(
            listOf(
                item("mail", HubType.EMAIL, 300),
                item("text", HubType.MESSAGING, 200),
                item("mail2", HubType.EMAIL, 100),
                item("chat", null, 250, pkg = "com.whatsapp")
            )
        )
        assertEquals(listOf(HubType.EMAIL, null, HubType.MESSAGING), groups.map { it.hub })
        assertEquals(listOf("mail", "mail2"), groups.first().items.map { it.key })
        assertEquals(3, groups.map { it.key }.distinct().size)
    }
}
