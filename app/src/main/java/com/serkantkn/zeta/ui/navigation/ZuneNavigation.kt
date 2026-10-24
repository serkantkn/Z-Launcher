package com.serkantkn.zeta.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.serkantkn.zeta.data.model.HubId
import com.serkantkn.zeta.data.model.HubType

/**
 * Manages hub navigation stack for the launcher.
 * Hubs behave like applets in a backstack, and support Tablet Split Screen Mode.
 *
 * The state survives configuration changes and process death via [Saver]
 * (see [rememberZuneNavigationState]).
 */
class ZuneNavigationState {
    /** Stack of currently open hubs. */
    var hubStack by mutableStateOf<List<HubId>>(emptyList())
        private set

    /**
     * Hubs the user left with the Home key, most recently left first.
     *
     * They stay composed behind the Start screen — a page keeps its scroll, a half-typed note
     * keeps its text — and they are what the "running hubs" section on Start lists. Back is the
     * other way out of a hub and closes it, so it never adds anything here.
     */
    var backgroundHubs by mutableStateOf<List<HubId>>(emptyList())
        private set

    /** Every hub that is composed right now: the foreground stack plus what is running behind it. */
    val openHubs: List<HubId>
        get() = (hubStack + backgroundHubs).distinct()

    /** In tablet split screen mode, rightHub is docked on the right 50% pane. */
    var rightHub by mutableStateOf<HubId?>(null)
        private set

    /** In tablet split screen mode, leftHub is docked on the left 50% pane. */
    var leftHub by mutableStateOf<HubId?>(null)
        private set

    /** Indicates whether tablet dual-hub split mode is active. */
    var isSplitMode by mutableStateOf(false)
        private set

    /**
     * How the split shares the screen: the part the first pane on screen takes, 0-1 - the left one
     * on a tablet, the top one on a phone. Set by dragging the bar between them.
     */
    var splitFraction by mutableFloatStateOf(DEFAULT_SPLIT)
        private set

    /** The two panes the other way round from where they first stood. */
    var splitSwapped by mutableStateOf(false)
        private set

    /**
     * Which pane is in use - the last one touched: Back goes to it. True for the pane that was
     * opened beside the first hub ([leftHub], or Start while it is empty).
     */
    var splitFocusLeft by mutableStateOf(true)
        private set

    /** The hub in the pane in use; null when it is Start's. */
    val focusedSplitHub: HubId?
        get() = if (!isSplitMode) null else if (splitFocusLeft) leftHub else rightHub

    fun focusSplit(left: Boolean) {
        if (splitFocusLeft != left) splitFocusLeft = left
    }

    fun setSplit(fraction: Float) {
        splitFraction = fraction.coerceIn(MIN_SPLIT, 1f - MIN_SPLIT)
    }

    /** The two panes change places. */
    fun swapSplit() {
        splitSwapped = !splitSwapped
    }

    /**
     * One pane takes the whole screen. [left] is the pane that stays; Start staying means the split
     * is over and Start is in front. The other hub keeps running if [keepOther] - it was dragged
     * off or made way for a full screen - and is closed if not (Back).
     */
    fun keepSplitPane(left: Boolean, keepOther: Boolean = true) {
        val leaving = if (left) rightHub else leftHub
        if (left) {
            if (leftHub != null) {
                expandLeftSplitHubToFullscreen()
            } else {
                hubStack = emptyList()
                exitSplitMode()
            }
        } else {
            expandRightSplitHubToFullscreen()
        }
        if (leaving != null && keepOther && leaving.runsInBackground) {
            backgroundHubs = (listOf(leaving) + backgroundHubs).distinct().take(MAX_BACKGROUND_HUBS)
        }
    }

    /** Opens [hub] split, with Start beside it to choose the other half from. */
    fun openSplit(hub: HubId) {
        backgroundHubs = backgroundHubs - hub
        hubStack = listOf(hub)
        lastHub = hub
        enterSplitMode()
    }

    /** Currently active hub at the top of the stack, or null if on main pager. */
    val currentHub: HubId?
        get() = if (isSplitMode) (leftHub ?: rightHub) else hubStack.lastOrNull()

    /** Last active hub — used during exit animations. */
    var lastHub by mutableStateOf<HubId>(HubType.MUSIC)
        private set

    fun enterSplitMode() {
        val current = hubStack.lastOrNull()
        if (current != null) {
            rightHub = current
            leftHub = null
            splitFraction = DEFAULT_SPLIT
            splitSwapped = false
            // The empty pane is where the next hub is chosen: it is the one in use.
            splitFocusLeft = true
            isSplitMode = true
        }
    }

    fun exitSplitMode() {
        isSplitMode = false
        rightHub = null
        leftHub = null
    }

    /** Set on a television, where only the hubs made for one ([HubId.onTelevision]) open. */
    var television: Boolean = false

    fun openHub(hub: HubId) {
        // A notification, a shortcut or another app may still ask a TV for the phone hub.
        if (television && !hub.onTelevision) return
        // Coming back to a hub that was left running: it leaves the running list and is picked up
        // where it stood, because its composition was never thrown away.
        backgroundHubs = backgroundHubs - hub
        if (isSplitMode) {
            // Prevent opening duplicate hubs if already open in left or right pane, EXCEPT for INTERNET hub
            if (hub != HubType.INTERNET && (rightHub == hub || leftHub == hub)) {
                return
            }
            // A hub opened while the split is up goes into the pane in use, as Start does: the
            // one touched last. Into the first hub's pane only when that one is in use and the
            // other is still empty there is nowhere else to choose from.
            if (!splitFocusLeft && rightHub != null && leftHub != null) {
                rightHub = hub
            } else {
                leftHub = hub
                splitFocusLeft = true
            }
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

    /**
     * The Home key while a hub is open: the whole stack steps aside and keeps running.
     *
     * Returns true if there was anything to send back, so the caller can tell a press that went
     * Home from a press that was already home.
     */
    fun sendToBackground(): Boolean {
        if (hubStack.isEmpty() && !isSplitMode) return false
        val leaving = (hubStack.reversed() + listOfNotNull(leftHub, rightHub))
            .filter { it.runsInBackground }
        backgroundHubs = (leaving + backgroundHubs).distinct().take(MAX_BACKGROUND_HUBS)
        hubStack = emptyList()
        exitSplitMode()
        return true
    }

    /** Closes a hub that was left running, from the switcher. */
    fun stopHub(hub: HubId) {
        backgroundHubs = backgroundHubs - hub
        if (hubStack.contains(hub)) {
            val newStack = hubStack - hub
            hubStack = newStack
            newStack.lastOrNull()?.let { lastHub = it }
        }
    }

    fun popHub(): Boolean {
        if (isSplitMode) {
            // Back is the pane's in use: that pane goes, and the other has the screen. Back on
            // Start beside a hub puts the split away and leaves the hub.
            keepSplitPane(left = !splitFocusLeft, keepOther = false)
            return true
        }
        if (hubStack.size > 1) {
            val closing = hubStack.last()
            val newStack = hubStack.dropLast(1)
            hubStack = newStack
            backgroundHubs = backgroundHubs - closing
            lastHub = newStack.last()
            return true
        } else if (hubStack.isNotEmpty()) {
            backgroundHubs = backgroundHubs - hubStack.last()
            hubStack = emptyList()
            return true
        }
        return false
    }

    fun closeHub() {
        backgroundHubs = backgroundHubs - hubStack.toSet()
        hubStack = emptyList()
        exitSplitMode()
    }

    companion object {
        private const val NONE = ""

        /** The split's first share, and the least either pane may have. */
        const val DEFAULT_SPLIT = 0.5f
        const val MIN_SPLIT = 0.25f

        /** How many hubs may be left running at once. Each one holds on to its whole composition. */
        const val MAX_BACKGROUND_HUBS = 4

        /**
         * Persists the full navigation state as a flat list of Strings so it fits in a Bundle.
         * Layout: [hubStack joined by ",", rightHub, leftHub, isSplitMode, lastHub, background hubs, split fraction, swapped, focus].
         * Unknown keys (a hub removed in a future version, an app that was uninstalled) are skipped safely.
         */
        val Saver: Saver<ZuneNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.hubStack.joinToString(",") { it.key },
                    state.rightHub?.key ?: NONE,
                    state.leftHub?.key ?: NONE,
                    state.isSplitMode.toString(),
                    state.lastHub.key,
                    state.backgroundHubs.joinToString(",") { it.key },
                    state.splitFraction.toString(),
                    state.splitSwapped.toString(),
                    state.splitFocusLeft.toString()
                )
            },
            restore = { saved ->
                ZuneNavigationState().apply {
                    hubStack = saved.getOrNull(0)
                        ?.split(",")
                        ?.mapNotNull { it.toHubIdOrNull() }
                        ?: emptyList()
                    rightHub = saved.getOrNull(1)?.toHubIdOrNull()
                    leftHub = saved.getOrNull(2)?.toHubIdOrNull()
                    isSplitMode = saved.getOrNull(3)?.toBoolean() ?: false
                    lastHub = saved.getOrNull(4)?.toHubIdOrNull() ?: HubType.MUSIC
                    // What was running is remembered by name only: after process death the hubs
                    // are composed afresh, so the section still lists them but their contents
                    // start over. That is the honest best a saved Bundle can do.
                    backgroundHubs = saved.getOrNull(5)
                        ?.split(",")
                        ?.mapNotNull { it.toHubIdOrNull() }
                        ?.take(MAX_BACKGROUND_HUBS)
                        ?: emptyList()

                    splitFraction = saved.getOrNull(6)?.toFloatOrNull()?.coerceIn(MIN_SPLIT, 1f - MIN_SPLIT) ?: DEFAULT_SPLIT
                    splitSwapped = saved.getOrNull(7)?.toBoolean() ?: false
                    splitFocusLeft = saved.getOrNull(8)?.toBoolean() ?: true

                    // Split mode without a docked hub is meaningless; fall back to stack mode.
                    if (isSplitMode && rightHub == null && leftHub == null) {
                        isSplitMode = false
                    }
                }
            }
        )

        private fun String.toHubIdOrNull(): HubId? =
            if (isEmpty()) null else HubId.fromKey(this)
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
