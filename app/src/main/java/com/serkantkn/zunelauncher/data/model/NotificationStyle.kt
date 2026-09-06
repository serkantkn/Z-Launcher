package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R

enum class NotificationStyle(@StringRes val titleRes: Int) {
    WINDOWS_PHONE(R.string.notification_style_wp),
    SYSTEM(R.string.notification_style_system)
}
