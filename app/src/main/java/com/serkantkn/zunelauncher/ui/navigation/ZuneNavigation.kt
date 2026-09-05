package com.serkantkn.zunelauncher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.serkantkn.zunelauncher.data.model.HubType

/**
 * Manages hub navigation stack for the launcher.
 * Hubs behave like applets in a backstack, and support Tablet Split Screen Mode.
 *
 * The state survives configuration changes and process death via [Saver]
 * (see [rememberZuneNavigationState]).
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

    companion object {
        private const val NONE = ""

        /**
         * Persists the full navigation state as a flat list of Strings so it fits in a Bundle.
         * Layout: [hubStack joined by ",", rightHub, leftHub, isSplitMode, lastHub].
         * Unknown enum names (e.g. a hub removed in a future version) are skipped safely.
         */
        val Saver: Saver<ZuneNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.hubStack.joinToString(",") { it.name },
                    state.rightHub?.name ?: NONE,
                    state.leftHub?.name ?: NONE,
                    state.isSplitMode.toString(),
                    state.lastHub.name
                )
            },
            restore = { saved ->
                ZuneNavigationState().apply {
                    hubStack = saved.getOrNull(0)
                        ?.split(",")
                        ?.mapNotNull { it.toHubTypeOrNull() }
                        ?: emptyList()
                    rightHub = saved.getOrNull(1)?.toHubTypeOrNull()
                    leftHub = saved.getOrNull(2)?.toHubTypeOrNull()
                    isSplitMode = saved.getOrNull(3)?.toBoolean() ?: false
                    lastHub = saved.getOrNull(4)?.toHubTypeOrNull() ?: HubType.MUSIC

                    // Split mode without a docked hub is meaningless; fall back to stack mode.
                    if (isSplitMode && rightHub == null && leftHub == null) {
                        isSplitMode = false
                    }
                }
            }
        )

        private fun String.toHubTypeOrNull(): HubType? =
            if (isEmpty()) null else HubType.entries.firstOrNull { it.name == this }
    }
}

/**
 * Remembers the navigation state across recompositions, configuration changes and
 * process death. Restoring from a saved Bundle re-opens the same hub (or split panes)
 * the user was on.
 */
@Composable
fun rememberZuneNavigationState(): ZuneNavigationState {
    return rememberSaveable(saver = ZuneNavigationState.Saver) { ZuneNavigationState() }
}
