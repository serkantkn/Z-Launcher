package com.serkantkn.zunelauncher.ui.screens.files

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch
import kotlin.math.abs

// ════════════════════════════════════════════════════════════
// A KEYBOARD AND A MOUSE
// ════════════════════════════════════════════════════════════
//
// A phone in a dock and a tablet with a cover keyboard both want the hub to answer the keys a
// desktop's file manager answers, and a mouse's right button to mean what a long press means.
// Phone and tablet answer the same keys the same way; only what comes up differs — the phone's
// sheet where the tablet's bar shows what can be done.
//
// What a key means is decided here, as a plain table the tests read; what it then does is the
// hub's, which already knows how to copy, ask before deleting and go back.

/** What a key asks of the hub. */
internal sealed interface FilesKeyCommand {
    data object Copy : FilesKeyCommand
    data object Cut : FilesKeyCommand
    data object Paste : FilesKeyCommand
    data object SelectAll : FilesKeyCommand
    data object Delete : FilesKeyCommand
    /** Shift+Delete. Asks first all the same; a recycle bin is what will tell the two apart. */
    data object DeletePermanently : FilesKeyCommand
    data object Rename : FilesKeyCommand
    data object Open : FilesKeyCommand
    data object Back : FilesKeyCommand
    data object Up : FilesKeyCommand
    data object Refresh : FilesKeyCommand
    data object Search : FilesKeyCommand
    data object Dismiss : FilesKeyCommand
    data object Toggle : FilesKeyCommand
    data class Move(val direction: CursorDirection, val extend: Boolean) : FilesKeyCommand
}

internal enum class CursorDirection { LEFT, RIGHT, UP, DOWN }

/**
 * The table: the desktop's own keys, so nothing has to be learnt. Null for a key the hub leaves
 * alone, which then goes on to whatever else wants it.
 */
internal fun filesKeyCommand(key: Key, ctrl: Boolean, shift: Boolean, alt: Boolean): FilesKeyCommand? {
    if (ctrl && !alt) {
        return when (key) {
            Key.C -> FilesKeyCommand.Copy
            Key.X -> FilesKeyCommand.Cut
            Key.V -> FilesKeyCommand.Paste
            Key.A -> FilesKeyCommand.SelectAll
            Key.F -> FilesKeyCommand.Search
            else -> null
        }
    }
    if (alt) {
        // Alt with ctrl is the system's (and a keyboard's AltGr); leave it.
        if (ctrl) return null
        return when (key) {
            Key.DirectionLeft -> FilesKeyCommand.Back
            Key.DirectionUp -> FilesKeyCommand.Up
            else -> null
        }
    }
    return when (key) {
        // Compose's Delete is the key marked Delete; Backspace is the one above Enter.
        Key.Delete -> if (shift) FilesKeyCommand.DeletePermanently else FilesKeyCommand.Delete
        Key.F2 -> FilesKeyCommand.Rename
        Key.Enter, Key.NumPadEnter -> FilesKeyCommand.Open
        Key.Backspace -> FilesKeyCommand.Back
        Key.F5 -> FilesKeyCommand.Refresh
        Key.F3 -> FilesKeyCommand.Search
        Key.Escape -> FilesKeyCommand.Dismiss
        Key.Spacebar -> FilesKeyCommand.Toggle
        Key.DirectionLeft -> FilesKeyCommand.Move(CursorDirection.LEFT, shift)
        Key.DirectionRight -> FilesKeyCommand.Move(CursorDirection.RIGHT, shift)
        Key.DirectionUp -> FilesKeyCommand.Move(CursorDirection.UP, shift)
        Key.DirectionDown -> FilesKeyCommand.Move(CursorDirection.DOWN, shift)
        else -> null
    }
}

/**
 * How the items on screen are laid out, for the arrow keys. [span] is how many share a line: the
 * columns of the phone's grid, the rows of the tablet's sideways one, 1 for a list. [sideways] is
 * the tablet's grid, filled a column at a time and running off to the right.
 */
internal data class FilesGridShape(val span: Int, val sideways: Boolean) {
    companion object {
        val LIST = FilesGridShape(span = 1, sideways = false)
    }
}

/**
 * Where the cursor goes from [index] among [count] items. Along the way the items are filled
 * (down a list or a sideways grid's column, across a grid's row) one step is the next item, and
 * it carries on into the next line as reading would. Across, it is a whole line; a step into a
 * last line that is not full lands on its last item, and off the edge it stays put.
 *
 * No cursor yet (-1) starts at the first item.
 */
internal fun moveCursor(index: Int, count: Int, direction: CursorDirection, shape: FilesGridShape): Int {
    if (count <= 0) return -1
    if (index !in 0 until count) return 0
    val span = shape.span.coerceAtLeast(1)
    // "Along" is the order the grid fills in; "across" jumps a whole line.
    val (alongBack, alongOn, acrossBack, acrossOn) = if (shape.sideways) {
        listOf(CursorDirection.UP, CursorDirection.DOWN, CursorDirection.LEFT, CursorDirection.RIGHT)
    } else {
        listOf(CursorDirection.LEFT, CursorDirection.RIGHT, CursorDirection.UP, CursorDirection.DOWN)
    }
    // A plain list has no lines across: left and right have nowhere to go.
    if (span == 1 && !shape.sideways && (direction == CursorDirection.LEFT || direction == CursorDirection.RIGHT)) {
        return index
    }
    return when (direction) {
        alongBack -> (index - 1).coerceAtLeast(0)
        alongOn -> (index + 1).coerceAtMost(count - 1)
        acrossBack -> if (index - span >= 0) index - span else index
        acrossOn -> when {
            index + span < count -> index + span
            // The last line is short: its last item, if there is a line there at all.
            (count - 1) / span > index / span -> count - 1
            else -> index
        }
        else -> index
    }
}

/**
 * The paths from [from] to [to] inclusive, in the order shown — what Shift with a click or an
 * arrow chooses. Without [from] on screen it is [to] alone.
 */
internal fun rangeBetween(paths: List<String>, from: String?, to: String): List<String> {
    val end = paths.indexOf(to)
    if (end < 0) return emptyList()
    val start = from?.let { paths.indexOf(it) }?.takeIf { it >= 0 } ?: return listOf(to)
    return paths.subList(minOf(start, end), maxOf(start, end) + 1).toList()
}

// ── The state ────────────────────────────────────────────────────────────────

/**
 * The hub's keyboard: where its cursor is, where a Shift range starts, whether a text field has
 * the keys, and what a click with a button or a modifier held does. One per hub, handed down
 * through [LocalFilesKeyboard] so the rows and tiles on either layout can reach it without every
 * view in between carrying it.
 */
@Stable
internal class FilesKeyboard {
    /** The item the arrow keys are on, by path, so a reload or a re-sort does not move it. */
    var focusedPath by mutableStateOf<String?>(null)
    /** Where a Shift range starts: the last item clicked or moved to without Shift. */
    var anchorPath by mutableStateOf<String?>(null)
    /**
     * Whether the cursor is drawn. Windows drew its focus rectangle only once the keyboard was
     * in use; a hand on the screen has no need of it.
     */
    var cursorShown by mutableStateOf(false)
    /** A search box or a name being typed has the keys; the hub keeps its hands off them. */
    var typing by mutableStateOf(false)
    /** How the showing view lays its items out, reported by the view itself. */
    var shape by mutableStateOf(FilesGridShape.LIST)

    /** The hub's own focus, which is where the keys arrive when nothing else is taking them. */
    val hubFocus = FocusRequester()
    /** The search box, for Ctrl+F. */
    val searchFocus = FocusRequester()

    // Set by the hub: what a click with a modifier or the second button does.
    var onSecondaryClick: (FileItemModel) -> Unit = {}
    var onCtrlClick: (FileItemModel) -> Unit = {}
    var onShiftClick: (FileItemModel) -> Unit = {}

    fun takeFocus() {
        runCatching { hubFocus.requestFocus() }
    }

    /**
     * Ctrl+F asked for the search box. The phone's is not there until its bar opens, so the box
     * takes the keys itself once it is, and puts this back.
     */
    var searchWanted by mutableStateOf(false)

    /**
     * Puts the cursor on [path]. Without [extend] that is also where the next range starts; with
     * it and no range begun, the range starts where the cursor was.
     */
    fun moveTo(path: String, extend: Boolean = false) {
        if (!extend) anchorPath = path else if (anchorPath == null) anchorPath = focusedPath ?: path
        focusedPath = path
    }

    /** A new folder: nothing in it has been pointed at yet. */
    fun reset() {
        focusedPath = null
        anchorPath = null
    }
}

internal val LocalFilesKeyboard = staticCompositionLocalOf<FilesKeyboard?> { null }

/**
 * Wraps a key handler for the hub's root so that only presses are acted on, the release of a
 * key the hub took is taken too (an Escape released unclaimed would otherwise reach Android as
 * Back and leave the hub), and nothing is taken while a text field has the keys.
 */
internal class FilesKeyDispatcher(private val keyboard: FilesKeyboard) {
    private val held = mutableSetOf<Key>()

    fun dispatch(event: KeyEvent, handle: (FilesKeyCommand) -> Boolean): Boolean {
        if (keyboard.typing) return false
        return when (event.type) {
            KeyEventType.KeyDown -> {
                val command = filesKeyCommand(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed)
                    ?: return false
                val handled = handle(command)
                if (handled) held += event.key
                handled
            }
            KeyEventType.KeyUp -> held.remove(event.key)
            else -> false
        }
    }
}

/**
 * The hub's outermost box: where its keys arrive, and what hands [keyboard] down to the rows and
 * tiles. [onKey] says whether the hub did something with a key; one it did not goes on as usual.
 */
@Composable
internal fun FilesKeyboardRoot(
    keyboard: FilesKeyboard,
    onKey: (FilesKeyCommand) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val dispatcher = remember(keyboard) { FilesKeyDispatcher(keyboard) }
    val currentOnKey by rememberUpdatedState(onKey)
    // A television's remote is not a keyboard: its arrows move the focus between the hub's own
    // rows and buttons, as everywhere else on a TV, and the hub does not take them for itself.
    val isTv = com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current
    if (isTv) {
        CompositionLocalProvider(LocalFilesKeyboard provides keyboard) {
            Box(modifier = modifier, content = content)
        }
        return
    }
    // The keys come to the hub from the activity itself, whatever holds Compose's focus. The hub's
    // own focus was not to be relied on: Start stays composed behind the hub on a tablet, and
    // something there took the focus back moments after the hub was given it, so the keyboard
    // did nothing until the screen was touched. A text field in the hub still types as usual
    // (the dispatcher stands aside while [FilesKeyboard.typing]), and a dialog is a window of
    // its own that the activity never sees keys for.
    DisposableEffect(dispatcher) {
        val route: (android.view.KeyEvent) -> Boolean = { native ->
            dispatcher.dispatch(KeyEvent(native)) { command -> currentOnKey(command) }
        }
        HardwareKeys.handler = route
        onDispose { if (HardwareKeys.handler === route) HardwareKeys.handler = null }
    }
    // The hub has the keys when it opens, and again whenever a text field lets them go. Taken
    // once was not enough: while the hub is still coming up the launcher around it settles too,
    // and the focus it had been given went with that a moment later, leaving a keyboard that did
    // nothing until the screen was touched. So it is asked for again until it holds.
    var hubHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(keyboard.typing) {
        if (keyboard.typing) return@LaunchedEffect
        repeat(FOCUS_TRIES) {
            if (!keyboard.typing && !hubHasFocus) keyboard.takeFocus()
            delay(FOCUS_RETRY_MILLIS)
        }
    }
    CompositionLocalProvider(LocalFilesKeyboard provides keyboard) {
        Box(
            modifier = modifier
                .onPreviewKeyEvent { dispatcher.dispatch(it) { command -> currentOnKey(command) } }
                .pointerInput(keyboard) {
                    // A press anywhere takes back keys that went nowhere — a button that had them
                    // and has gone — but never from a field being typed in.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && !keyboard.typing) keyboard.takeFocus()
                        }
                    }
                }
                .focusRequester(keyboard.hubFocus)
                .onFocusChanged { hubHasFocus = it.hasFocus }
                .focusTarget(),
            content = content
        )
    }
}

// ── The modifiers ────────────────────────────────────────────────────────────

/**
 * A text field the hub must leave its keys to; with [search] it is also where Ctrl+F goes.
 */
internal fun Modifier.filesTypingField(search: Boolean = false): Modifier = composed {
    val keyboard = LocalFilesKeyboard.current ?: return@composed Modifier
    if (search) {
        LaunchedEffect(keyboard.searchWanted) {
            if (!keyboard.searchWanted) return@LaunchedEffect
            keyboard.searchWanted = false
            // A frame for a box that has only just come in to be ready to take the keys.
            withFrameNanos { }
            runCatching { keyboard.searchFocus.requestFocus() }
        }
    }
    // A field that goes away while it has the keys says nothing on its way out; this does.
    DisposableEffect(keyboard) { onDispose { keyboard.typing = false } }
    (if (search) Modifier.focusRequester(keyboard.searchFocus) else Modifier)
        .onFocusChanged { keyboard.typing = it.hasFocus }
}

/**
 * One file or folder, on either layout: the mouse's second button, a click with Ctrl or Shift
 * held, and the keyboard's focus rectangle drawn round it. Placed before the item's own
 * clickable, so a click this takes never reaches it as a tap.
 */
internal fun Modifier.filesItem(item: FileItemModel): Modifier = composed {
    val keyboard = LocalFilesKeyboard.current ?: return@composed Modifier
    val path = item.file.absolutePath
    val dark = LocalZuneColors.current.isDark
    val focused = keyboard.cursorShown && keyboard.focusedPath == path
    this
        .pointerInput(item, keyboard) {
            awaitPointerEventScope {
                while (true) {
                    // The first pass, before the item's clickable sees the press.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Press) continue
                    val modifiers = event.keyboardModifiers
                    val taken = when {
                        event.buttons.isSecondaryPressed -> keyboard.onSecondaryClick
                        modifiers.isCtrlPressed -> keyboard.onCtrlClick
                        modifiers.isShiftPressed -> keyboard.onShiftClick
                        else -> null
                    }
                    // Any press here gives the keys back to the hub, out of a search box left
                    // focused, and puts the cursor where the pointer was.
                    keyboard.takeFocus()
                    keyboard.cursorShown = false
                    if (taken != null) {
                        event.changes.forEach { it.consume() }
                        taken(item)
                    } else {
                        keyboard.moveTo(path)
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                // Windows 8's focus rectangle: a dotted line in the ink, just inside the item.
                val stroke = 2.dp.toPx()
                drawRect(
                    color = if (dark) Color.White else Color.Black,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke))
                    )
                )
            }
        }
}

/**
 * The tablet's grid runs sideways, but a mouse's wheel turns up and down; turned, it moves the
 * grid along as Windows 8's Start did. A wheel that already moves it sideways is left alone.
 */
internal fun Modifier.filesWheelScrollsSideways(state: LazyGridState): Modifier = composed {
    val scope = rememberCoroutineScope()
    this.pointerInput(state) {
        val step = WHEEL_STEP.toPx()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val delta = change.scrollDelta
                if (abs(delta.y) <= abs(delta.x)) continue
                change.consume()
                scope.launch { state.scrollBy(delta.y * step) }
            }
        }
    }
}

/** Reports a list's shape to the keyboard and keeps its cursor in view. */
@Composable
internal fun FilesCursorFollower(items: List<FileItemModel>, state: LazyListState, firstItem: Int = 0) {
    val keyboard = LocalFilesKeyboard.current ?: return
    LaunchedEffect(keyboard, state) { keyboard.shape = FilesGridShape.LIST }
    val index = keyboard.focusedPath?.let { path -> items.indexOfFirst { it.file.absolutePath == path } } ?: -1
    LaunchedEffect(index, keyboard.cursorShown) {
        if (index < 0 || !keyboard.cursorShown) return@LaunchedEffect
        val target = index + firstItem
        val info = state.layoutInfo
        val end = info.viewportEndOffset - info.afterContentPadding
        val seen = info.visibleItemsInfo.firstOrNull { it.index == target }
        when {
            seen != null && seen.offset < 0 -> state.scrollBy(seen.offset.toFloat())
            seen != null && seen.offset + seen.size > end -> state.scrollBy((seen.offset + seen.size - end).toFloat())
            seen != null -> Unit
            target < state.firstVisibleItemIndex -> state.scrollToItem(target)
            else -> {
                // Below: brought to the bottom edge, as a step down would, not flung to the top.
                state.scrollToItem(target)
                val landed = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
                state.scrollBy((landed.offset + landed.size - end).toFloat())
            }
        }
    }
}

/** Reports a grid's shape to the keyboard and keeps its cursor in view. */
@Composable
internal fun FilesCursorFollower(items: List<FileItemModel>, state: LazyGridState, firstItem: Int = 0) {
    val keyboard = LocalFilesKeyboard.current ?: return
    LaunchedEffect(keyboard, state) {
        snapshotFlow { state.layoutInfo.maxSpan to (state.layoutInfo.orientation == Orientation.Horizontal) }
            .collect { (span, sideways) -> keyboard.shape = FilesGridShape(span.coerceAtLeast(1), sideways) }
    }
    val index = keyboard.focusedPath?.let { path -> items.indexOfFirst { it.file.absolutePath == path } } ?: -1
    LaunchedEffect(index, keyboard.cursorShown) {
        if (index < 0 || !keyboard.cursorShown) return@LaunchedEffect
        val target = index + firstItem
        val info = state.layoutInfo
        val sideways = info.orientation == Orientation.Horizontal
        val end = info.viewportEndOffset - info.afterContentPadding
        val seen = info.visibleItemsInfo.firstOrNull { it.index == target }
        fun startOf(offset: androidx.compose.ui.unit.IntOffset) = if (sideways) offset.x else offset.y
        fun lengthOf(size: androidx.compose.ui.unit.IntSize) = if (sideways) size.width else size.height
        when {
            seen != null && startOf(seen.offset) < 0 -> state.scrollBy(startOf(seen.offset).toFloat())
            seen != null && startOf(seen.offset) + lengthOf(seen.size) > end ->
                state.scrollBy((startOf(seen.offset) + lengthOf(seen.size) - end).toFloat())
            seen != null -> Unit
            target < state.firstVisibleItemIndex -> state.scrollToItem(target)
            else -> {
                state.scrollToItem(target)
                val landed = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
                state.scrollBy((startOf(landed.offset) + lengthOf(landed.size) - end).toFloat())
            }
        }
    }
}

/** How far one notch of a mouse's wheel moves the tablet's sideways grid. */
private val WHEEL_STEP = 96.dp

/**
 * Where the activity hands hardware keys first: the hub that is open and wants them, if any.
 * Read in MainActivity.dispatchKeyEvent.
 */
object HardwareKeys {
    @Volatile
    var handler: ((android.view.KeyEvent) -> Boolean)? = null
}

/** How many times, and how far apart, the hub asks for the keys as it opens. */
private const val FOCUS_TRIES = 6
private const val FOCUS_RETRY_MILLIS = 200L
