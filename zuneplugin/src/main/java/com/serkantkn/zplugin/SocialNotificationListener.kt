package com.serkantkn.zplugin

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SocialNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Notification listener for Zune Extras Plugin
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification removal listener
    }
}
