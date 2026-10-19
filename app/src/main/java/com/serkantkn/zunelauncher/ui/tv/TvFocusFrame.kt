package com.serkantkn.zunelauncher.ui.tv

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Where the remote is, for the whole launcher at once.
 *
 * The hubs were made for fingers: a row is pressed, not pointed at, and most of them deliberately
 * draw nothing when touched (Metro leans instead). On a television the focus is the only pointer
 * there is, so this draws the Xbox's white frame round whatever holds it - every row, heading,
 * bar command and tile - without each of them having to learn to. Android tells where the focused
 * thing is ([android.view.View.getFocusedRect]); the frame follows it frame by frame while the
 * remote is in use (lists scroll after a press), and checks a few times a second otherwise.
 *
 * Things that show their own focus - Start's tiles and words - say so with [tvOwnFocus] and are
 * left alone.
 */
@Composable
fun TvFocusFrame(modifier: Modifier = Modifier) {
    val view = LocalView.current
    var frame by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(view) {
        val rect = android.graphics.Rect()
        while (true) {
            val busy = SystemClock.uptimeMillis() - TvFocus.lastKeyAt < BUSY_MILLIS
            if (busy) withFrameNanos { } else delay(IDLE_MILLIS)
            frame = if (view.hasFocus() && TvFocus.ownFocus == 0) {
                rect.setEmpty()
                view.getFocusedRect(rect)
                if (rect.isEmpty || rect.width() >= view.width && rect.height() >= view.height) null
                else Rect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
            } else {
                null
            }
        }
    }
    Canvas(modifier = modifier) {
        val box = frame ?: return@Canvas
        val stroke = FRAME_WIDTH.toPx()
        val out = FRAME_OUTSET.toPx() + stroke / 2
        drawRect(
            color = Color.White,
            topLeft = Offset(box.left - out, box.top - out),
            size = Size(box.width + out * 2, box.height + out * 2),
            style = Stroke(width = stroke)
        )
    }
}

/** What [TvFocusFrame] needs to know from outside the composition. */
object TvFocus {
    /** When a key last arrived; the frame follows closely for a moment after. */
    @Volatile
    var lastKeyAt = 0L

    /**
     * Where the focus stood when the centre button was last pressed: the row that opened
     * whatever is now open, taken before anything moved (a hub hinges away as its page opens,
     * and its rows with it).
     */
    @Volatile
    var pressedAt: android.graphics.Rect? = null

    /** Called for every key the launcher's windows receive. */
    fun onKey(view: android.view.View?, event: android.view.KeyEvent) {
        lastKeyAt = android.os.SystemClock.uptimeMillis()
        if (event.action != android.view.KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> pressedAt = view?.let { focusedRectOf(it) }
        }
    }

    /** How many things that draw their own focus hold it now: 0 or 1, counted to be safe. */
    @Volatile
    var ownFocus = 0
}

/** Marks something that shows its own focus, so [TvFocusFrame] does not frame it again. */
fun Modifier.tvOwnFocus(): Modifier = composed {
    // Counted rather than set, so it does not matter whether the one losing the focus or the one
    // gaining it hears first. Placed before the clickable whose focus it watches.
    var held by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (held) TvFocus.ownFocus = (TvFocus.ownFocus - 1).coerceAtLeast(0) }
    }
    onFocusChanged { state ->
        if (state.isFocused && !held) {
            held = true
            TvFocus.ownFocus++
        } else if (!state.isFocused && held) {
            held = false
            TvFocus.ownFocus = (TvFocus.ownFocus - 1).coerceAtLeast(0)
        }
    }
}

private val FRAME_WIDTH = 3.dp
private val FRAME_OUTSET = 2.dp
private const val BUSY_MILLIS = 900L
private const val IDLE_MILLIS = 120L

/**
 * Keeps the focus out of what cannot be seen: Start under an open hub, the hubs left running out
 * of sight. They stay composed (that is what keeps them where they were), and without this a
 * remote's arrow could walk straight into them.
 */
fun Modifier.tvFocusFence(closed: Boolean): Modifier =
    if (!closed) this
    else this.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup()

/**
 * Asks for the focus until it is given, for a little while. A hub that has just opened can still
 * be on its way from where it was kept out of sight - behind a fence that turns the focus away -
 * when it first asks, and a single request then quietly comes to nothing.
 */
suspend fun androidx.compose.ui.focus.FocusRequester.tvTake(): Boolean {
    delay(TAKE_FIRST_MILLIS)
    repeat(TAKE_TRIES) {
        if (runCatching { requestFocus() }.getOrDefault(false)) return true
        delay(TAKE_RETRY_MILLIS)
    }
    return false
}

private const val TAKE_FIRST_MILLIS = 120L
private const val TAKE_TRIES = 15
private const val TAKE_RETRY_MILLIS = 100L

/** Where the focus is, in the launcher view's own coordinates; null when nothing holds it. */
fun focusedRectOf(view: android.view.View): android.graphics.Rect? {
    if (!view.hasFocus()) return null
    val rect = android.graphics.Rect()
    view.getFocusedRect(rect)
    return rect.takeUnless { it.isEmpty }
}

/**
 * Walks the focus, a step at a time as the remote would, to whatever stands at [target]: back
 * from a page, to the row that opened it. Compose keeps no way to name that row from outside, but
 * the place it was is known, and the arrows lead there - sideways first, so the walk leaves a
 * tablet hub's headings at once rather than turning its pages on the way. Gives up when a step
 * goes nowhere.
 */
suspend fun tvWalkFocusTo(
    view: android.view.View,
    focusManager: androidx.compose.ui.focus.FocusManager,
    target: android.graphics.Rect
) {
    // A place already stood on means the walk is going round in a circle: it stops there.
    val seen = mutableSetOf<android.graphics.Rect>()
    repeat(WALK_STEPS) {
        // A step can scroll a list to keep the new row in sight: the place is read once it has.
        delay(WALK_SETTLE_MILLIS)
        val now = focusedRectOf(view) ?: return
        if (!seen.add(now)) return
        // Sideways while the focus is not yet over the target's columns, then up or down to it.
        val x = now.centerX()
        val y = now.centerY()
        val direction = when {
            x < target.left -> androidx.compose.ui.focus.FocusDirection.Right
            x > target.right -> androidx.compose.ui.focus.FocusDirection.Left
            y < target.top -> androidx.compose.ui.focus.FocusDirection.Down
            y > target.bottom -> androidx.compose.ui.focus.FocusDirection.Up
            else -> return
        }
        if (!focusManager.moveFocus(direction)) return
    }
}

private const val WALK_STEPS = 30
private const val WALK_SETTLE_MILLIS = 90L
