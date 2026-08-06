package com.serkantkn.zunelauncher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serkantkn.zunelauncher.data.model.HubType

/**
 * Manages hub navigation stack for the launcher.
 * Hubs behave like applets in a backstack (e.g. PEOPLE -> MESSAGING -> back to PEOPLE).
 */
class ZuneNavigationState {
    /** Stack of currently open hubs. */
    var hubStack by mutableStateOf<List<HubType>>(emptyList())
        private set

    /** Currently active hub at the top of the stack, or null if on main pager. */
    val currentHub: HubType?
        get() = hubStack.lastOrNull()

    /** Last active hub — used during exit animations. */
    var lastHub by mutableStateOf(HubType.MUSIC)
        private set

    fun openHub(hub: HubType) {
        if (currentHub != hub) {
            lastHub = hub
            hubStack = hubStack + hub
        }
    }

    fun popHub(): Boolean {
        if (hubStack.size > 1) {
            val newStack = hubStack.dropLast(1)
            hubStack = newStack
            lastHub = newStack.last()
            return true
        } else if (hubStack.isNotEmpty()) {
            hubStack = emptyList()
            return true
        }
        return false
    }

    fun closeHub() {
        hubStack = emptyList()
    }
}

@Composable
fun rememberZuneNavigationState(): ZuneNavigationState {
    return remember { ZuneNavigationState() }
}
