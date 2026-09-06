package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R

enum class VolumeBarStyle(@StringRes val titleRes: Int) {
    WINDOWS_PHONE(R.string.volume_bar_wp),
    SYSTEM(R.string.volume_bar_system)
}
