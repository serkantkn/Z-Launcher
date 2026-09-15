package com.serkantkn.zunelauncher.data.model

import android.app.PendingIntent
import android.graphics.Bitmap

data class SocialMessageModel(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    /** Whoever the notification put in front: usually the person's own picture. */
    val icon: Bitmap?,
    val replyAction: ReplyAction?,
    val openIntent: PendingIntent?,
    /**
     * The app's own icon, kept beside the person's so a message can say who wrote it *and* where.
     * Two people called Ali on WhatsApp and on Instagram are otherwise the same row.
     */
    val appIcon: Bitmap? = null,
    /** The notification's own category, as the app declared it: "msg", "social", "email"… */
    val category: String? = null,
    /** The group chat a message belongs to, when the app says so. */
    val conversationTitle: String? = null
) {
    /** Messaging or a feed, when the app is one the hub knows. */
    val kind: SocialKind? get() = SocialApps.kindOf(packageName)

    /** What to show as the sender: the conversation when there is one, otherwise the title. */
    val heading: String get() = conversationTitle?.takeIf { it.isNotBlank() } ?: title
}

data class ReplyAction(
    val pendingIntent: PendingIntent,
    val remoteInputResultKey: String
)
