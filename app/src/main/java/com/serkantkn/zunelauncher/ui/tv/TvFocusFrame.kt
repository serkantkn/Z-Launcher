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
