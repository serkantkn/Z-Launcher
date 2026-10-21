package com.serkantkn.zeta.ui.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.service.ActiveMediaState
import com.serkantkn.zeta.ui.theme.findActivity

/** A line in the guide: its words, and what it does (none for a line that only tells). */
private class GuideLine(val label: String, val run: (() -> Unit)?, val header: Boolean = false)

/**
 * The Xbox guide, as the console's guide button brought it over whatever was on: a small panel in
 * the middle, its name at the top, its tabs standing as narrow upright strips - "apps", "media",
 * "settings" - and the chosen tab's list on white beside them, the line the remote is on in green.
 * It takes the remote while it is up; ≡ or Back put it away.
 */
@Composable
internal fun XboxGuidePanel(
    media: ActiveMediaState,
    hubOpen: Boolean,
    onOpenHub: (HubType) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val items by XboxShelf.items.collectAsState()
    val recent by XboxShelf.recent.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var at by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        focus.tvTake()
    }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(GUIDE_MILLIS, easing = FastOutSlowInEasing)) }

    fun go(action: () -> Unit): () -> Unit = {
        onClose()
        action()
    }
    fun cards(kind: XboxCards) = go {
        // Out of the hub first, so the page opens on the dashboard.
        if (hubOpen) com.serkantkn.zeta.data.repository.HomeKeyBridge.press()
        XboxShelf.request(kind)
    }

    val tabs = listOf(
        stringResource(R.string.xbox_guide_apps),
        stringResource(R.string.xbox_guide_media),
        stringResource(R.string.xbox_guide_settings)
    )
    val nothing = stringResource(R.string.xbox_nothing_playing)
    // The launcher's own music first: it is seen without notification access.
    val song = rememberTvSong(media)
    val ownMusic = com.serkantkn.zeta.data.service.LauncherPlayback.state.collectAsState().value.let { it.hasQueue && it.title == song?.title }
    val playingLine = song?.let {
        stringResource(
            if (it.playing) R.string.xbox_now_playing else R.string.xbox_paused_line,
            listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" · ")
        )
    } ?: nothing
    val lines: List<GuideLine> = when (tab) {
        0 -> buildList {
            add(GuideLine(stringResource(R.string.xbox_search), cards(XboxCards.SEARCH)))
            recent.mapNotNull { items[it] }.take(RECENT_LINES).forEach { add(GuideLine(it.label, go { it.open() })) }
            add(GuideLine(stringResource(R.string.xbox_pins), cards(XboxCards.PINS)))
            add(GuideLine(stringResource(R.string.xbox_recent), cards(XboxCards.RECENT)))
            add(GuideLine(stringResource(R.string.xbox_my_apps), cards(XboxCards.APPS)))
        }
        1 -> buildList {
            add(GuideLine(playingLine, null, header = true))
            val controls = media.controller?.transportControls
            val playPause: (() -> Unit)? = when {
                ownMusic -> { { com.serkantkn.zeta.data.service.LauncherPlayback.playPause() } }
                controls != null -> { { if (media.isPlaying) controls.pause() else controls.play() } }
                else -> null
            }
            val next: (() -> Unit)? = when {
                ownMusic -> { { com.serkantkn.zeta.data.service.LauncherPlayback.skipToNext() } }
                controls != null -> { { controls.skipToNext() } }
                else -> null
            }
            add(GuideLine(stringResource(R.string.xbox_play_pause), playPause))
            add(GuideLine(stringResource(R.string.xbox_next), next))
            // The music's own screen, at once, while there is a song.
            add(GuideLine(stringResource(R.string.xbox_visualizer), if (song != null) go { TvIdle.show(TvIdle.Shown.MUSIC) } else null))
            add(GuideLine(stringResource(R.string.zune_row_music), go { onOpenHub(HubType.MUSIC) }))
            items["hub:videos"]?.let { v -> add(GuideLine(v.label, go { v.open() })) }
            add(GuideLine(stringResource(HubType.PICTURES.titleRes), go { onOpenHub(HubType.PICTURES) }))
        }
        else -> buildList {
            add(GuideLine(stringResource(R.string.xbox_system_settings), go {
                if (hubOpen) com.serkantkn.zeta.data.repository.HomeKeyBridge.press()
                XboxShelf.pendingSystem.value = true
            }))
            add(GuideLine(stringResource(R.string.xbox_launcher_settings_full), go { onOpenHub(HubType.SETTINGS) }))
            items["hub:android_settings"]?.let { s -> add(GuideLine(s.label, go { s.open() })) }
            add(GuideLine(stringResource(R.string.vista_sleep), go { shutdownAction(context, 2) }))
            add(GuideLine(stringResource(R.string.vista_tv_off), go { shutdownAction(context, 1) }))
            add(GuideLine(stringResource(R.string.xbox_exit), go { context.findActivity()?.moveTaskToBack(true) }))
        }
    }
    // The remote stands only on lines that do something.
    fun firstLive() = lines.indexOfFirst { it.run != null }.coerceAtLeast(0)
    LaunchedEffect(tab) { at = firstLive() }

    var backDown by remember { mutableStateOf(false) }
    val keys = Modifier.onPreviewKeyEvent { event ->
        val down = event.type == KeyEventType.KeyDown
        when (event.key) {
            Key.Back -> {
                if (down) backDown = true else if (backDown) { backDown = false; onClose() }
                true
            }
            Key.DirectionUp -> {
                if (down) (at - 1 downTo 0).firstOrNull { lines[it].run != null }?.let { at = it }
                true
            }
            Key.DirectionDown -> {
                if (down) (at + 1..lines.lastIndex).firstOrNull { lines[it].run != null }?.let { at = it }
                true
            }
            Key.DirectionLeft -> {
                if (down && tab > 0) tab--
                true
            }
            Key.DirectionRight -> {
                if (down && tab < tabs.lastIndex) tab++
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (!down) lines.getOrNull(at)?.run?.invoke()
                true
            }
            else -> false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value }
            .background(Color.Black.copy(alpha = 0.55f))
            .focusRequester(focus)
            .tvOwnFocus()
            .then(keys)
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(minOf(maxWidth * 0.52f, 600.dp))
                .graphicsLayer {
                    val grow = 0.94f + 0.06f * shown.value
                    scaleX = grow
                    scaleY = grow
                }
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(
                    stringResource(R.string.xbox_guide_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = SelawikFamily, fontSize = 21.sp, shadow = xboxShadow),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                XboxClock()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GUIDE_HEIGHT)
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.3f), topLeft = Offset(0f, 8f), size = size) }
            ) {
                tabs.forEachIndexed { i, name -> if (i <= tab) GuideTab(name, chosen = i == tab) }
                Column(modifier = Modifier.weight(1f).fillMaxHeight().background(Xbox.Panel)) {
                    lines.forEachIndexed { i, line ->
                        val chosen = i == at && line.run != null
                        Text(
                            line.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = SelawikFamily,
                                fontSize = 17.sp,
                                fontWeight = if (line.header) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = when {
                                chosen -> Color.White
                                line.header -> Xbox.Ink
                                line.run == null -> Xbox.InkDim.copy(alpha = 0.6f)
                                else -> Xbox.Ink
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (chosen) Xbox.Green else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
                tabs.forEachIndexed { i, name -> if (i > tab) GuideTab(name, chosen = false) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                XboxLegendItem("A", Xbox.ButtonA, stringResource(R.string.xbox_select))
                XboxLegendItem("B", Xbox.ButtonB, stringResource(R.string.xbox_back))
                XboxLegendItem("≡", Color(0xFF6E7476), stringResource(R.string.xbox_close_guide))
            }
        }
    }
}

/** A tab of the guide: a narrow upright strip, its name running up it. */
@Composable
private fun GuideTab(name: String, chosen: Boolean) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .fillMaxHeight()
            .background(if (chosen) Color(0xFF6A7174) else Color(0xFF4E5557))
            .padding(top = 10.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = SelawikFamily, fontSize = 15.sp),
            color = if (chosen) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // Turned to run upwards, as the guide's tabs did.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = 1000.dp.roundToPx()))
                    layout(placeable.height, placeable.width) {
                        placeable.placeWithLayer(-(placeable.width - placeable.height) / 2, (placeable.width - placeable.height) / 2) {
                            rotationZ = -90f
                        }
                    }
                }
        )
    }
}

private val GUIDE_HEIGHT = 260.dp
private const val GUIDE_MILLIS = 180
private const val RECENT_LINES = 4
