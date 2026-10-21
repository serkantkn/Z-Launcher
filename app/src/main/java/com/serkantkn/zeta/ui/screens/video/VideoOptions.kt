package com.serkantkn.zeta.ui.screens.video

import com.serkantkn.zeta.ui.components.swallowTaps
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Tracks
import com.serkantkn.zeta.R
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import java.util.Locale

/** One sound or subtitle track a film carries, as the options list shows it. */
internal data class TrackChoice(val label: String, val selected: Boolean, val group: Tracks.Group, val index: Int)

/** What the options list offers for the film playing, and how each is set now. */
internal data class VideoOptions(
    val audio: List<TrackChoice> = emptyList(),
    val text: List<TrackChoice> = emptyList(),
    val textOff: Boolean = true,
    val speed: Float = 1f,
    val fill: Boolean = false,
    val endAction: VideoEndAction = VideoEndAction.NEXT,
    val hasQueue: Boolean = false
)

/** What a choice in the options list does. */
internal interface VideoOptionsHandler {
    fun onAudio(choice: TrackChoice)
    /** A subtitle track, or null to turn subtitles off. */
    fun onText(choice: TrackChoice?)
    fun onAddSubtitle()
    fun onSpeed(value: Float)
    fun onFill(fill: Boolean)
    fun onEndAction(action: VideoEndAction)
}

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** "1×", "1,25×": a speed as the screen writes it, in the launcher's own way with decimals. */
internal fun speedLabel(speed: Float, locale: Locale = Locale.getDefault()): String {
    val format = java.text.DecimalFormat("0.##", java.text.DecimalFormatSymbols.getInstance(locale))
    return format.format(speed.toDouble()) + "×"
}

/**
 * The film's options, in a dark panel drawn in from the right edge over the picture: subtitles,
 * sound, speed, how the picture fits, what happens at the end, and the touch lock. Each list is
 * a column of words, the one in use in the accent - Windows Phone's list pickers, laid open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoOptionsPanel(
    options: VideoOptions,
    handler: VideoOptionsHandler,
    onLock: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = dismissSource, indication = null, onClick = onDismiss)
    ) {
        val width = minOf(maxWidth * 0.86f, 380.dp)
        val panelSource = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(width)
                .fillMaxHeight()
                .background(PANEL)
                // Taps on the panel itself stay on it.
                .swallowTaps()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 28.dp)
        ) {
            Heading(stringResource(R.string.video_subtitles))
            Choice(stringResource(R.string.video_subtitles_off), options.textOff) { handler.onText(null) }
            options.text.forEach { track ->
                Choice(track.label, !options.textOff && track.selected) { handler.onText(track) }
            }
            Choice(stringResource(R.string.video_subtitles_add), selected = false) { handler.onAddSubtitle() }

            if (options.audio.size > 1) {
                Heading(stringResource(R.string.video_audio))
                options.audio.forEach { track -> Choice(track.label, track.selected) { handler.onAudio(track) } }
            }

            Heading(stringResource(R.string.video_speed))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PLAYBACK_SPEEDS.forEach { speed ->
                    Choice(speedLabel(speed), options.speed == speed) { handler.onSpeed(speed) }
                }
            }

            Heading(stringResource(R.string.video_picture))
            Choice(stringResource(R.string.video_fit), !options.fill) { handler.onFill(false) }
            Choice(stringResource(R.string.video_fill), options.fill) { handler.onFill(true) }

            Heading(stringResource(R.string.video_at_end))
            Choice(stringResource(R.string.video_end_stop), options.endAction == VideoEndAction.STOP) { handler.onEndAction(VideoEndAction.STOP) }
            Choice(
                stringResource(R.string.video_end_next),
                options.endAction == VideoEndAction.NEXT,
                hint = if (options.hasQueue) null else stringResource(R.string.video_end_next_none)
            ) { handler.onEndAction(VideoEndAction.NEXT) }
            Choice(stringResource(R.string.video_end_repeat), options.endAction == VideoEndAction.REPEAT) { handler.onEndAction(VideoEndAction.REPEAT) }

            Heading(stringResource(R.string.video_touch))
            Choice(stringResource(R.string.video_lock), selected = false, onClick = onLock)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = LocalZuneColors.current.textMuted,
        modifier = Modifier.padding(top = 22.dp, bottom = 2.dp)
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, hint: String? = null, onClick: () -> Unit) {
    val accent = LocalZuneColors.current.accentColor
    Column(modifier = Modifier.clickable(onClick = onClick).padding(vertical = 7.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
            color = if (selected) accent else Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = LocalZuneColors.current.textMuted
            )
        }
    }
}

private val PANEL = Color(0xFF1F1F1F)
