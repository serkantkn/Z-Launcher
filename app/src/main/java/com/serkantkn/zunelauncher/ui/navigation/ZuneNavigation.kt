package com.serkantkn.zunelauncher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serkantkn.zunelauncher.data.model.HubType

/**
 * Manages hub navigation state for the launcher.
 * Used for opening/closing hub detail screens as overlays.
 */
class ZuneNavigationState {
    /** Currently opened hub, or null if showing the main pager. */
    var currentHub by mutableStateOf<HubType?>(null)
        private set

    /** Last opened hub — used to retain content during exit animation. */
    var lastHub by mutableStateOf(HubType.MUSIC)
        private set

    fun openHub(hub: HubType) {
        lastHub = hub
        currentHub = hub
    }

    fun closeHub() {
        currentHub = null
    }
}

@Composable
fun rememberZuneNavigationState(): ZuneNavigationState {
    return remember { ZuneNavigationState() }
}
