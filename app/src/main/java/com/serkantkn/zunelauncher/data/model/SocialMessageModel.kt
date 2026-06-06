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
    val icon: Bitmap?,
    val replyAction: ReplyAction?,
    val openIntent: PendingIntent?
)

data class ReplyAction(
    val pendingIntent: PendingIntent,
    val remoteInputResultKey: String
)
