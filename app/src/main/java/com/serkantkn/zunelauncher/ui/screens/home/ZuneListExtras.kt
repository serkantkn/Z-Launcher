package com.serkantkn.zunelauncher.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.model.Quickplay
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.components.TileIconImage
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.TileIconFace
import kotlinx.coroutines.delay

// ════════════════════════════════════════════════════════════
// THE LINE UNDER A HUB'S NAME
// ════════════════════════════════════════════════════════════

/**
 * What a hub has to say under its name in the Zune list: a line of small type, and a count that
 * sits at the end of the word. Either may be missing; both missing means the hub says nothing.
 *
 * This is the live tile's face in the list's own language. The words come from the same numbers
 * the tiles are drawn from, so the two never disagree.
 */
data class HubLiveLine(val text: String? = null, val count: Int = 0) {
    val hasNews: Boolean get() = !text.isNullOrBlank() || count > 0

    companion object {
        val NONE = HubLiveLine()
    }
}

/** The small count after the word, in the accent, the way the Zune HD marked "new". */
@Composable
fun ZuneHubCount(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Text(
        text = if (count > 99) "99+" else count.toString(),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 18.sp),
        color = LocalZuneColors.current.accentColor,
        modifier = modifier
    )
}

/**
 * The line itself. When its words change, the old ones slide up and out and the new ones come in
 * from below — the tile's slide, an inch high.
 */
@Composable
fun ZuneHubLiveLine(text: String?, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val animate = LocalAnimationsEnabled.current
    AnimatedContent(
        targetState = text.orEmpty(),
        transitionSpec = {
            if (animate) {
                (slideInVertically(tween(LINE_TURN_MILLIS)) { it } + fadeIn(tween(LINE_TURN_MILLIS)))
                    .togetherWith(slideOutVertically(tween(LINE_TURN_MILLIS)) { -it } + fadeOut(tween(LINE_TURN_MILLIS)))
            } else {
                fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
            }
        },
        label = "zune_live_line",
        modifier = modifier
    ) { line ->
        if (line.isNotBlank()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 0.dp, bottom = 6.dp)
            )
        }
    }
}

/**
 * The colour a hub's name is drawn in: the ink, except that a name with news behind it arrives in
 * the accent and settles to the ink over a moment — a glint, not a label. Only news that is there
 * when the list comes in counts, give or take the moment the phone takes to answer; news that
 * arrives later does not set it off, so the list never blinks while it is being read.
 */
@Composable
fun rememberHubNameColour(hasNews: Boolean, ink: Color): Color {
    val accent = LocalZuneColors.current.accentColor
    val animate = LocalAnimationsEnabled.current
    val arrivedAt = remember { System.currentTimeMillis() }
    var glinted by remember { mutableStateOf(false) }
    var glint by remember { mutableStateOf(false) }
    LaunchedEffect(hasNews) {
        if (!hasNews || glinted || !animate) return@LaunchedEffect
        if (System.currentTimeMillis() - arrivedAt > GLINT_WINDOW_MILLIS) return@LaunchedEffect
        glinted = true
        glint = true
        delay(GLINT_HOLD_MILLIS)
        glint = false
    }
    val colour by animateColorAsState(
        targetValue = if (glint) accent else ink,
        animationSpec = tween(GLINT_FADE_MILLIS),
        label = "zune_hub_glint"
    )
    return colour
}

private const val LINE_TURN_MILLIS = 300
private const val GLINT_HOLD_MILLIS = 600L

/** How long after the list appears news may still arrive and be greeted. */
private const val GLINT_WINDOW_MILLIS = 2_500L
private const val GLINT_FADE_MILLIS = 700

// ════════════════════════════════════════════════════════════
// THE END OF THE LIST
// ════════════════════════════════════════════════════════════

/** The two small words the Zune HD kept at the foot of its list. */
@Composable
fun ZuneListEnd(onApps: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        EndWord(stringResource(R.string.apps_hub), onApps)
        Text(
            text = "·",
            color = zuneColors.textDim,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        EndWord(stringResource(R.string.settings_hub), onSettings)
    }
}

@Composable
private fun EndWord(text: String, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
        color = zuneColors.textMuted,
        modifier = Modifier
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 6.dp)
    )
}
