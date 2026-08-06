package com.serkantkn.zunelauncher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.serkantkn.zunelauncher.data.model.HubType

/**
 * Manages hub navigation stack for the launcher.
 * Hubs behave like applets in a backstack, and support Tablet Split Screen Mode.
 */
class ZuneNavigationState {
    /** Stack of currently open hubs. */
    var hubStack by mutableStateOf<List<HubType>>(emptyList())
        private set

    /** In tablet split screen mode, rightHub is docked on the right 50% pane. */
    var rightHub by mutableStateOf<HubType?>(null)
        private set

    /** In tablet split screen mode, leftHub is docked on the left 50% pane. */
    var leftHub by mutableStateOf<HubType?>(null)
        private set

    /** Indicates whether tablet dual-hub split mode is active. */
    var isSplitMode by mutableStateOf(false)
        private set

    /** Currently active hub at the top of the stack, or null if on main pager. */
    val currentHub: HubType?
        get() = if (isSplitMode) (leftHub ?: rightHub) else hubStack.lastOrNull()

    /** Last active hub — used during exit animations. */
    var lastHub by mutableStateOf(HubType.MUSIC)
        private set

    fun enterSplitMode() {
        val current = hubStack.lastOrNull()
        if (current != null) {
            rightHub = current
            leftHub = null
            isSplitMode = true
        }
    }

    fun exitSplitMode() {
        isSplitMode = false
        rightHub = null
        leftHub = null
    }

    fun openHub(hub: HubType) {
        if (isSplitMode) {
            // Prevent opening duplicate hubs if already open in left or right pane, EXCEPT for INTERNET hub
            if (hub != HubType.INTERNET && (rightHub == hub || leftHub == hub)) {
                return
            }
            leftHub = hub
        } else {
            if (currentHub != hub) {
                lastHub = hub
                hubStack = hubStack + hub
            }
        }
    }

    fun expandRightSplitHubToFullscreen() {
        val target = rightHub
        if (target != null) {
            hubStack = listOf(target)
            exitSplitMode()
        }
    }

    fun expandLeftSplitHubToFullscreen() {
        val target = leftHub
        if (target != null) {
            hubStack = listOf(target)
            exitSplitMode()
        }
    }

    fun closeRightSplitHub() {
        rightHub = null
        if (leftHub != null) {
            rightHub = leftHub
            leftHub = null
        } else {
            closeHub()
        }
    }

    fun closeLeftSplitHub() {
        leftHub = null
        if (rightHub == null) {
            closeHub()
        }
    }

    fun popHub(): Boolean {
        if (isSplitMode) {
            closeHub()
            return true
        }
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
        exitSplitMode()
    }
}

@Composable
fun rememberZuneNavigationState(): ZuneNavigationState {
    return remember { ZuneNavigationState() }
}
