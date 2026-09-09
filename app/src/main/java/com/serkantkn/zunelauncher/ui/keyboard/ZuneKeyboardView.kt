package com.serkantkn.zunelauncher.ui.keyboard

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.KeyAction
import com.serkantkn.zunelauncher.data.model.KeyboardKey
import com.serkantkn.zunelauncher.data.model.KeyboardLayout
import com.serkantkn.zunelauncher.data.model.KeyboardPage
import com.serkantkn.zunelauncher.data.model.OneHandedMode
import com.serkantkn.zunelauncher.data.model.ShiftState
import com.serkantkn.zunelauncher.util.KeyboardLayouts
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** How long a key must be held before it repeats or opens its alternates, and the repeat rate. */
private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 55L
private const val LONG_PRESS_MS = 330L

private val KeyGap = 2.dp

/** How far the finger travels on the space bar (or backspace) for one cursor step / word delete. */
private val SlideStep = 26.dp

/**
 * The Windows Phone keyboard: flat square keys with no rounded corners, a pressed key filled in
 * the launcher's accent colour, and an enlarged character bubble above the finger.
 *
 * Gestures follow the usual soft-keyboard conventions: hold a key for its accents (or the digit
 * printed on the top row), slide along the space bar to move the caret, and swipe left on
 * backspace to delete whole words.
 *
 * Purely presentational — every tap is handed to the callbacks and the hosting service decides
 * what it does to the edited text.
 */
@Composable
fun ZuneKeyboardView(
    layout: KeyboardLayout,
    shiftState: ShiftState,
    enterLabel: String,
    spaceLabel: String,
    languageLabel: String,
    palette: KeyboardPalette,
    keyHeight: Dp,
    keyPreviewEnabled: Boolean,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    split: Boolean,
    onKey: (KeyboardKey) -> Unit,
    onAlternate: (String) -> Unit,
    onCursorStep: (Int) -> Unit,
    onDeleteWord: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val slideStepPx = with(density) { SlideStep.toPx() }

    val playFeedback: () -> Unit = {
        if (vibrationEnabled) {
            view.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
        if (soundEnabled) audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
    }

    var boardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var preview by remember { mutableStateOf<KeyPreview?>(null) }
    var alternates by remember { mutableStateOf<AlternatePopup?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.board)
            .onGloballyPositioned { boardCoordinates = it }
    ) {
        Column(
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            layout.rows.forEach { row ->
                val splitAfter = if (split) splitIndex(row.keys.map { it.weight }) else -1
                Row(modifier = Modifier.fillMaxWidth().height(keyHeight)) {
                    if (row.leadingWeight > 0f) Spacer(Modifier.weight(row.leadingWeight))
                    row.keys.forEachIndexed { index, key ->
                        val label = displayLabel(key, layout, shiftState, enterLabel, spaceLabel, languageLabel)
                        KeyCell(
                            key = key,
                            label = label,
                            shiftState = shiftState,
                            palette = palette,
                            slideStepPx = slideStepPx,
                            onPress = { coordinates ->
                                playFeedback()
                                if (key.action != KeyAction.SPACE) onKey(key)
                                val board = boardCoordinates
                                preview = if (keyPreviewEnabled && key.action == KeyAction.CHARACTER &&
                                    board != null && coordinates != null
                                ) {
                                    KeyPreview(label, board.localBoundingBoxOf(coordinates))
                                } else {
                                    null
                                }
                            },
                            onTapUp = { if (key.action == KeyAction.SPACE) onKey(key) },
                            onRepeat = {
                                playFeedback()
                                onKey(key)
                            },
                            onLongPress = { coordinates ->
                                val board = boardCoordinates
                                if (key.altKeys.isNotEmpty() && board != null && coordinates != null) {
                                    playFeedback()
                                    preview = null
                                    alternates = AlternatePopup(
                                        boardWidth = board.size.width.toFloat(),
                                        options = (listOf(label) + key.altKeys.map {
                                            if (shiftState != ShiftState.OFF && layout.page == KeyboardPage.LETTERS) {
                                                KeyboardLayouts.shifted(it, layout.language)
                                            } else {
                                                it
                                            }
                                        }),
                                        selected = 0,
                                        bounds = board.localBoundingBoxOf(coordinates)
                                    )
                                }
                            },
                            onSlide = { steps ->
                                when (key.action) {
                                    KeyAction.SPACE -> onCursorStep(steps)
                                    KeyAction.BACKSPACE -> if (steps < 0) repeat(-steps) { onDeleteWord() }
                                    else -> Unit
                                }
                            },
                            onMove = { x ->
                                alternates?.let { popup ->
                                    val index = popup.indexAt(x, with(density) { AlternateKeyWidth.toPx() })
                                    if (index != popup.selected) alternates = popup.copy(selected = index)
                                }
                            },
                            onRelease = {
                                alternates?.let { popup ->
                                    if (popup.selected > 0) onAlternate(popup.options[popup.selected])
                                }
                                alternates = null
                                preview = null
                            },
                            modifier = Modifier.weight(key.weight)
                        )
                        if (index == splitAfter) Spacer(Modifier.weight(SPLIT_GAP_WEIGHT * row.totalWeight))
                    }
                    if (row.trailingWeight > 0f) Spacer(Modifier.weight(row.trailingWeight))
                }
            }
        }

        alternates?.let { popup ->
            AlternateStrip(popup = popup, palette = palette)
        } ?: preview?.let { shown ->
            KeyPreviewBubble(preview = shown, palette = palette)
        }
    }
}

/**
 * Shrinks the board towards one side for one-handed typing and puts the "swap side" and "back to
 * full width" buttons in the freed space.
 */
@Composable
fun OneHandedFrame(
    mode: OneHandedMode,
    palette: KeyboardPalette,
    onSwapSide: () -> Unit,
    onExit: () -> Unit,
    content: @Composable () -> Unit
) {
    if (mode == OneHandedMode.OFF) {
        content()
        return
    }
    // The row must wrap the keyboard's own height: anything that fills the height here would
    // stretch the whole IME window to the size of the screen.
    Row(
        modifier = Modifier.fillMaxWidth().background(palette.board),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mode == OneHandedMode.RIGHT) OneHandedSideBar(palette, onSwapSide, onExit)
        Box(modifier = Modifier.weight(1f)) { content() }
        if (mode == OneHandedMode.LEFT) OneHandedSideBar(palette, onSwapSide, onExit)
    }
}

@Composable
private fun RowScope.OneHandedSideBar(
    palette: KeyboardPalette,
    onSwapSide: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.weight(0.16f).padding(4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf(Icons.Default.SwapHoriz to onSwapSide, Icons.Default.Close to onExit).forEach { (icon, action) ->
            Box(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .size(40.dp)
                    .background(palette.key)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = action
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = palette.text)
            }
        }
    }
}

/** The enlarged character that pops above the pressed key, clamped inside the keyboard. */
@Composable
private fun KeyPreviewBubble(preview: KeyPreview, palette: KeyboardPalette) {
    val density = LocalDensity.current
    val height = preview.bounds.height * 1.15f
    val top = (preview.bounds.top - height - with(density) { 4.dp.toPx() }).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .offset { IntOffset(preview.bounds.left.roundToInt(), top.roundToInt()) }
            .size(
                width = with(density) { preview.bounds.width.toDp() },
                height = with(density) { height.toDp() }
            )
            .background(palette.key),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = preview.label,
            color = palette.text,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1
        )
    }
}

private val AlternateKeyWidth = 46.dp

/** Accents (or the digit of the top row) shown while a key is held down. */
@Composable
private fun AlternateStrip(popup: AlternatePopup, palette: KeyboardPalette) {
    val density = LocalDensity.current
    val keyWidth = with(density) { AlternateKeyWidth.toPx() }
    val height = popup.bounds.height
    val top = (popup.bounds.top - height - with(density) { 4.dp.toPx() }).coerceAtLeast(0f)

    Box(
        modifier = Modifier.offset {
            IntOffset(popup.stripLeft(keyWidth).roundToInt(), top.roundToInt())
        }
    ) {
        Row(
            modifier = Modifier
                .height(with(density) { height.toDp() })
                .background(palette.key)
        ) {
            popup.options.forEachIndexed { index, option ->
                Box(
                    modifier = Modifier
                        .size(width = AlternateKeyWidth, height = with(density) { height.toDp() })
                        .background(if (index == popup.selected) palette.accent else palette.key),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (index == popup.selected) Color.White else palette.text,
                        fontSize = 20.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyCell(
    key: KeyboardKey,
    label: String,
    shiftState: ShiftState,
    palette: KeyboardPalette,
    slideStepPx: Float,
    onPress: (LayoutCoordinates?) -> Unit,
    onTapUp: () -> Unit,
    onRepeat: () -> Unit,
    onLongPress: (LayoutCoordinates?) -> Unit,
    onSlide: (Int) -> Unit,
    onMove: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // The gesture handler is installed once and reads the current key through these, so switching
    // boards mid-press cannot leave a key stuck in its pressed (accent filled) state.
    val currentKey by rememberUpdatedState(key)
    val currentPress by rememberUpdatedState(onPress)
    val currentTapUp by rememberUpdatedState(onTapUp)
    val currentRepeat by rememberUpdatedState(onRepeat)
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentSlide by rememberUpdatedState(onSlide)
    val currentMove by rememberUpdatedState(onMove)
    val currentRelease by rememberUpdatedState(onRelease)

    val shiftActive = key.action == KeyAction.SHIFT && shiftState != ShiftState.OFF
    val background = if (pressed || shiftActive) palette.accent else palette.key
    val foreground = if (pressed || shiftActive) Color.White else palette.text

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(KeyGap)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val action = currentKey.action
                        pressed = true
                        currentPress(coordinates)

                        var holdJob: Job? = null
                        var holding = false
                        var slideOffset = 0f
                        var slideSteps = 0
                        var slid = false
                        var pointerX = down.position.x

                        try {
                            when {
                                action == KeyAction.BACKSPACE -> holdJob = launch {
                                    delay(REPEAT_DELAY_MS)
                                    while (isActive) {
                                        currentRepeat()
                                        delay(REPEAT_INTERVAL_MS)
                                    }
                                }
                                currentKey.altKeys.isNotEmpty() -> holdJob = launch {
                                    delay(LONG_PRESS_MS)
                                    holding = true
                                    currentLongPress(coordinates)
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                pointerX += change.positionChange().x
                                if (holding) {
                                    currentMove(pointerX)
                                    continue
                                }
                                if (action != KeyAction.SPACE && action != KeyAction.BACKSPACE) continue
                                slideOffset += change.positionChange().x
                                val steps = (slideOffset / slideStepPx).toInt()
                                if (steps != slideSteps) {
                                    if (abs(steps) >= 1) {
                                        holdJob?.cancel()
                                        slid = true
                                        currentSlide(steps - slideSteps)
                                        slideSteps = steps
                                    }
                                }
                            }

                            if (action == KeyAction.SPACE && !slid && !holding) currentTapUp()
                        } finally {
                            holdJob?.cancel()
                            pressed = false
                            currentRelease()
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(background),
            contentAlignment = Alignment.Center
        ) {
            when (key.action) {
                KeyAction.BACKSPACE -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(22.dp)
                )
                KeyAction.SHIFT -> Icon(
                    imageVector = if (shiftState == ShiftState.LOCKED) Icons.Default.KeyboardCapslock else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(22.dp)
                )
                KeyAction.LANGUAGE -> Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(20.dp)
                )
                else -> Text(
                    text = label,
                    color = foreground,
                    fontSize = if (key.action == KeyAction.CHARACTER && label.length <= 1) 20.sp else 13.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** Resolves what a key shows right now: shift affects letters, space and enter carry captions. */
private fun displayLabel(
    key: KeyboardKey,
    layout: KeyboardLayout,
    shiftState: ShiftState,
    enterLabel: String,
    spaceLabel: String,
    languageLabel: String
): String = when (key.action) {
    KeyAction.SPACE -> spaceLabel
    KeyAction.ENTER -> enterLabel
    KeyAction.LANGUAGE -> languageLabel
    KeyAction.CHARACTER ->
        if (shiftState != ShiftState.OFF && layout.page == KeyboardPage.LETTERS && key.label.length == 1) {
            KeyboardLayouts.shifted(key.label, layout.language)
        } else {
            key.label
        }
    else -> key.label
}

/** Index of the last key of the left half when the board is split for two thumbs. */
internal fun splitIndex(weights: List<Float>): Int {
    if (weights.size < 4) return -1
    val half = weights.sum() / 2f
    var running = 0f
    weights.forEachIndexed { index, weight ->
        running += weight
        if (running >= half) return index
    }
    return weights.lastIndex - 1
}

private const val SPLIT_GAP_WEIGHT = 0.18f

private data class KeyPreview(val label: String, val bounds: Rect)

private data class AlternatePopup(
    val options: List<String>,
    val selected: Int,
    val bounds: Rect,
    val boardWidth: Float
) {
    /** Left edge of the strip: it starts at the key and slides back inside the board if needed. */
    fun stripLeft(keyWidth: Float): Float {
        val width = options.size * keyWidth
        return bounds.left.coerceIn(0f, (boardWidth - width).coerceAtLeast(0f))
    }

    /** [localX] is measured inside the pressed key, which starts at [bounds].left on the board. */
    fun indexAt(localX: Float, keyWidth: Float): Int {
        val boardX = bounds.left + localX
        return ((boardX - stripLeft(keyWidth)) / keyWidth).toInt().coerceIn(0, options.lastIndex)
    }
}
