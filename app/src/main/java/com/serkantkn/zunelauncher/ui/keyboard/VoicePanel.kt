package com.serkantkn.zunelauncher.ui.keyboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.KeyboardLayouts

/** What the dictation panel is doing right now. */
enum class VoiceState { NEEDS_PERMISSION, READY, LISTENING, ERROR }

/**
 * Dictation. The recogniser itself lives in the service; this panel only shows the Windows Phone
 * listening circle and the partial text as it arrives.
 */
@Composable
fun VoicePanel(
    state: VoiceState,
    partialText: String,
    palette: KeyboardPalette,
    height: Dp,
    rowHeight: Dp,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestPermission: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    val transition = rememberInfiniteTransition(label = "voice")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VoiceState.LISTENING) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(palette.board)
            .padding(bottom = bottomPadding)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(pulse)
                    .size(72.dp)
                    .background(
                        color = if (state == VoiceState.LISTENING) palette.accent else palette.key,
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        when (state) {
                            VoiceState.NEEDS_PERMISSION -> onRequestPermission()
                            VoiceState.LISTENING -> onStop()
                            else -> onStart()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (state == VoiceState.LISTENING) Color.White else palette.text,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = when (state) {
                    VoiceState.NEEDS_PERMISSION -> stringResource(R.string.keyboard_voice_permission)
                    VoiceState.READY -> stringResource(R.string.keyboard_voice_ready)
                    VoiceState.LISTENING -> stringResource(R.string.keyboard_voice_listening)
                    VoiceState.ERROR -> stringResource(R.string.keyboard_voice_error)
                },
                color = palette.muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp)
            )

            if (partialText.isNotBlank()) {
                Text(
                    text = partialText,
                    color = palette.text,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
            PanelKey(palette = palette, modifier = Modifier.weight(1.5f), onClick = onClose) {
                Text(text = KeyboardLayouts.LETTERS_LABEL, color = palette.text, fontSize = 13.sp)
            }
            PanelKey(palette = palette, modifier = Modifier.weight(5f), onClick = onClose) {
                Text(
                    text = stringResource(R.string.keyboard_panel_back),
                    color = palette.muted,
                    fontSize = 13.sp
                )
            }
        }
    }
}
