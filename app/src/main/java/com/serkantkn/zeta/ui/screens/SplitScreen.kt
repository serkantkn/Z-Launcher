package com.serkantkn.zeta.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.serkantkn.zeta.ui.components.statusBarTop
import com.serkantkn.zeta.ui.components.LocalPaneEdges
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.HubId
import com.serkantkn.zeta.ui.components.PaneEdges
import com.serkantkn.zeta.ui.components.PaneScope
import com.serkantkn.zeta.ui.components.ZuneHubDefaults
import com.serkantkn.zeta.ui.components.ZuneWideNavWidth
import com.serkantkn.zeta.ui.navigation.ZuneNavigationState
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Two things on the screen at once: two hubs, or a hub and Start to choose the second from. Side
 * by side on a tablet, one over the other on a phone - the same split, laid out for the room.
 *
 * Each pane is a screen of its own to what is in it ([PaneScope]): a hub asking how wide the
 * screen is hears the pane's width, and lays itself out for a tablet or a phone by that. The bar
 * between them moves to share the screen otherwise - a third, a half, two thirds - and dragged to
 * an edge it gives the whole screen to the other pane; touched twice, the panes change places.
 * Touching a pane makes it the one in use: Back goes to it.
 */
@Composable
internal fun SplitScreen(
    navState: ZuneNavigationState,
    /** One over the other (a phone) rather than side by side (a tablet). */
    vertical: Boolean,
    /** 0-1 while the split is coming in. */
    appear: () -> Float,
    modifier: Modifier = Modifier,
    /** Start, in the pane that has no hub yet. */
    start: @Composable () -> Unit,
    /**
     * What stands above Start in that pane: the hubs already running, offered first. Above it, not
     * over it - over it, it hid the top of Start, which can scroll no further up to show it.
     */
    startAbove: (@Composable () -> Unit)? = null,
    /** A hub, with its ground. */
    hub: @Composable (HubId) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    // The pane opened beside the first hub (leftHub, or Start), and the first hub's own.
    val inOrder = if (vertical) listOf(false, true) else listOf(true, false)
    val order = if (navState.splitSwapped) inOrder.reversed() else inOrder
    val fraction = navState.splitFraction
    var dragging by remember { mutableStateOf<Float?>(null) }
    val bothHubs = navState.leftHub != null && navState.rightHub != null

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val total = if (vertical) maxHeight else maxWidth
        val cross = if (vertical) maxWidth else maxHeight
        val totalPx = with(density) { total.toPx() }.coerceAtLeast(1f)
        val firstSize = (total - DIVIDER) * fraction
        val secondSize = total - DIVIDER - firstSize

        val swapLabel = stringResource(R.string.split_swap)
        val dragState = rememberDraggableState { delta ->
            dragging = ((dragging ?: navState.splitFraction) + delta / totalPx).coerceIn(0.04f, 0.96f)
        }
        // The bar moves under the finger and settles where it is let go; touched twice, the panes
        // change places.
        val barDrag = Modifier
            .semantics { contentDescription = swapLabel }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { navState.swapSplit() }) }
            .draggable(
                orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
                state = dragState,
                onDragStopped = {
                    val at = dragging ?: return@draggable
                    dragging = null
                    when {
                        // Past a pane's least, it is gone and the other has the screen.
                        at < CLOSE_BELOW -> navState.keepSplitPane(left = order[1])
                        at > 1f - CLOSE_BELOW -> navState.keepSplitPane(left = order[0])
                        else -> navState.setSplit(SNAPS.minBy { abs(it - at) })
                    }
                }
            )

        @Composable
        fun Pane(newPane: Boolean, size: Dp, first: Boolean) {
            val paneHub = if (newPane) navState.leftHub else navState.rightHub
            val focused = navState.splitFocusLeft == newPane
            val edges = if (!vertical) PaneEdges() else PaneEdges(top = first, bottom = !first)
            val widthDp = (if (vertical) cross else size).value.roundToInt()
            val heightDp = (if (vertical) size else cross).value.roundToInt()
            val paneModifier = Modifier
                .then(if (vertical) Modifier.fillMaxWidth().height(size) else Modifier.fillMaxHeight().width(size))
                .clipToBounds()
                .graphicsLayer { alpha = appear() }
                // The pane touched is the pane in use; the touch goes on to whatever it was for.
                .pointerInput(newPane) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed && !it.previousPressed }) navState.focusSplit(newPane)
                        }
                    }
                }
                // Which of two hubs Back goes to: a line of the accent along the bar beside it.
                .drawWithContent {
                    drawContent()
                    if (bothHubs && focused) {
                        val t = FOCUS_LINE.toPx()
                        val atStart = !first
                        val area = this.size
                        if (vertical) {
                            drawRect(zuneColors.accentColor, Offset(0f, if (atStart) 0f else area.height - t), Size(area.width, t))
                        } else {
                            drawRect(zuneColors.accentColor, Offset(if (atStart) 0f else area.width - t, 0f), Size(t, area.height))
                        }
                    }
                }
            PaneScope(
                widthDp = widthDp,
                heightDp = heightDp,
                edges = edges,
                // Start keeps the phone's pages in a pane, whatever its width.
                wide = paneHub != null && widthDp >= PANE_WIDE_FROM_DP
            ) { consumed ->
                Box(modifier = paneModifier.then(consumed)) {
                    if (paneHub == null) {
                        if (startAbove == null) {
                            start()
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                startAbove()
                                // Under it, Start no longer reaches the top of the pane.
                                CompositionLocalProvider(LocalPaneEdges provides edges.copy(top = false)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .consumeWindowInsets(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                                    ) { start() }
                                }
                            }
                        }
                    } else {
                        hub(paneHub)
                        PaneButtons(
                            vertical = vertical,
                            onSwap = { navState.swapSplit() },
                            onExpand = { navState.keepSplitPane(left = newPane) },
                            onClose = {
                                navState.focusSplit(newPane)
                                navState.keepSplitPane(left = !newPane, keepOther = false)
                            },
                            // Under the status bar: touches on the bar itself go to the system.
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarTop())
                        )
                    }
                }
            }
        }

        @Composable
        fun Bar() {
            val barModifier = Modifier
                .then(if (vertical) Modifier.fillMaxWidth().height(DIVIDER) else Modifier.fillMaxHeight().width(DIVIDER))
                .background(if (zuneColors.isDark) Color.Black else Color(0xFF202020))
            Box(modifier = barModifier.then(barDrag), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .then(if (vertical) Modifier.size(width = HANDLE_LENGTH, height = 4.dp) else Modifier.size(width = 4.dp, height = HANDLE_LENGTH))
                        .background(zuneColors.accentColor, RoundedCornerShape(2.dp))
                )
            }
        }

        if (vertical) {
            Column(modifier = Modifier.fillMaxSize()) {
                Pane(order[0], firstSize, first = true)
                Bar()
                Pane(order[1], secondSize, first = false)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Pane(order[0], firstSize, first = true)
                Bar()
                Pane(order[1], secondSize, first = false)
            }
        }

        // What the finger takes besides the bar: a handle a finger wide round its middle. The rest
        // of a pane's edge stays the pane's (an alphabet down the side, a scroll bar).
        val shownAt = dragging ?: fraction
        val barCentre = (total - DIVIDER) * shownAt + DIVIDER / 2
        Box(
            modifier = Modifier
                .offset {
                    val c = barCentre.roundToPx()
                    val along = ((cross - HANDLE_GRAB) / 2).roundToPx()
                    if (vertical) IntOffset(along, c - (HANDLE_TOUCH / 2).roundToPx())
                    else IntOffset(c - (HANDLE_TOUCH / 2).roundToPx(), along)
                }
                .then(if (vertical) Modifier.size(width = HANDLE_GRAB, height = HANDLE_TOUCH) else Modifier.size(width = HANDLE_TOUCH, height = HANDLE_GRAB))
                .then(barDrag)
        )
        // While it is being dragged: where it will go.
        if (dragging != null) {
            Box(
                modifier = Modifier
                    .offset {
                        val c = (barCentre - DIVIDER / 2).roundToPx()
                        if (vertical) IntOffset(0, c) else IntOffset(c, 0)
                    }
                    .then(if (vertical) Modifier.fillMaxWidth().height(DIVIDER) else Modifier.fillMaxHeight().width(DIVIDER))
                    .background(zuneColors.accentColor.copy(alpha = 0.85f))
            )
        }
        val at = dragging
        if (at != null && (at < CLOSE_BELOW || at > 1f - CLOSE_BELOW)) {
            val closingFirst = at < CLOSE_BELOW
            val veilStart = if (closingFirst) 0.dp else barCentre
            val veilSize = if (closingFirst) barCentre else total - barCentre
            Box(
                modifier = Modifier
                    .offset { if (vertical) IntOffset(0, veilStart.roundToPx()) else IntOffset(veilStart.roundToPx(), 0) }
                    .then(if (vertical) Modifier.fillMaxWidth().height(veilSize) else Modifier.fillMaxHeight().width(veilSize))
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }
    }
}

/**
 * Over a hub in a pane: a small handle at the top, which touched opens what can be done with the
 * pane - change places, take the whole screen, close - and closes again once one is chosen or a
 * few moments pass. Always open, the three would stand over the hub's own title.
 */
@Composable
private fun PaneButtons(
    vertical: Boolean,
    onSwap: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val ink = if (LocalZuneColors.current.isDark) Color.White else Color.Black
    val ground = if (LocalZuneColors.current.isDark) Color.Black else Color.White
    if (open) {
        LaunchedEffect(Unit) {
            delay(PANE_BUTTONS_STAY_MILLIS)
            open = false
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = PANE_HANDLE_HEIGHT)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.split_options)
                ) { open = !open },
            contentAlignment = Alignment.Center
        ) {
            // On a little of the ground, so it reads as a handle where a hub's giant name runs
            // under it.
            Box(
                modifier = Modifier
                    .background(ground.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = HANDLE_LENGTH, height = 4.dp)
                        .background(ink.copy(alpha = if (open) 0.9f else 0.6f), RoundedCornerShape(2.dp))
                )
            }
        }
        AnimatedVisibility(visible = open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(
                    Triple(if (vertical) Icons.Default.SwapVert else Icons.Default.SwapHoriz, R.string.split_swap, onSwap),
                    Triple(Icons.Default.Fullscreen, R.string.launcher_fullscreen, onExpand),
                    Triple(Icons.Default.Close, R.string.launcher_close_hub, onClose)
                ).forEach { (icon, label, action) ->
                    IconButton(
                        onClick = { open = false; action() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = icon, contentDescription = stringResource(label), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * From this width a hub in a pane lays itself out as on a tablet. A whole screen is a tablet from
 * 600dp, but a hub there has its headings, its list and a pane beside them, and a pane of a split
 * narrower than all three would squeeze the last to a sliver: below it, the hub stands as on a
 * phone and opens what it opens over its list.
 */
private val PANE_WIDE_FROM_DP = (ZuneWideNavWidth + ZuneHubDefaults.WideListMinWidth + ZuneHubDefaults.WidePaneMinWidth).value.toInt()

/** The bar between the panes, and the part of it the finger takes. */
private val DIVIDER = 8.dp
private val HANDLE_LENGTH = 40.dp
private val HANDLE_TOUCH = 32.dp
private val HANDLE_GRAB = 120.dp

/** The line beside the pane in use, when both hold a hub. */
private val FOCUS_LINE = 3.dp

/** The shares a pane settles at once let go. */
private val SNAPS = listOf(1f / 3f, 0.5f, 2f / 3f)

/** Dragged past this share, a pane closes. */
private const val CLOSE_BELOW = 0.18f

/** The height a pane's handle takes for the finger. */
private val PANE_HANDLE_HEIGHT = 24.dp

/** How long a pane's buttons stay open untouched. */
private const val PANE_BUTTONS_STAY_MILLIS = 4_000L
