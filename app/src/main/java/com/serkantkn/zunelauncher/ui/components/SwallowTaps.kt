package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Keeps taps from going any further - a panel over a scrim that closes it, a bar over a page -
 * without making the panel something to stop on. An empty clickable did the first, but it also
 * took the focus a TV's remote moves, so the remote would land on a whole bar or a whole dialog
 * instead of the buttons in it.
 */
fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }
