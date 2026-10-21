package com.serkantkn.zeta.ui.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.link.LinkStore
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A line of the dashboard's system settings: one with choices, shown in the grey pane beside the
 * list, or one that opens the TV's own settings page for it.
 */
internal class XboxSystemLine(
    val label: String,
    val explain: String,
    val options: List<String> = emptyList(),
    val chosen: Int = -1,
    val choose: (Int) -> Unit = {},
    /** The choice that goes on to the TV's pictures, to pick one. */
    val pictureOption: Int = -1,
    /** What is set now, when the choices do not say it: the minutes left on the sleep timer. */
    val now: String? = null,
    val open: (() -> Unit)? = null
)

/** The lines, read afresh as they change. */
@Composable
internal fun xboxSystemLines(): List<XboxSystemLine> {
    val context = LocalContext.current
    val theme = XboxTheme.of(TvDesign.theme.collectAsState().value)
    val saver = TvSaver.entries.firstOrNull { it.name == TvDesign.saver.collectAsState().value } ?: TvSaver.DIM
    val saverMinutes by TvDesign.saverMinutes.collectAsState()
    val visualizer by TvDesign.visualizer.collectAsState()
    val sleepEnds by TvSleep.endsAt.collectAsState()
    val sleepChosen by TvSleep.chosen.collectAsState()
    val minutesText = { m: Int -> context.getString(R.string.xbox_minutes, m) }
    val sleepLeft = if (sleepEnds > 0L) context.getString(R.string.xbox_sleep_left, TvSleep.minutesLeft()) else null
    fun settings(action: String): () -> Unit = {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { ZuneLog.w("XboxSystem", "could not open $action", it) }
    }
    return listOf(
        XboxSystemLine(
            stringResource(R.string.xbox_sys_theme), stringResource(R.string.xbox_sys_theme_sub),
            options = listOf(
                stringResource(R.string.xbox_theme_grey), stringResource(R.string.xbox_theme_night), stringResource(R.string.xbox_theme_purple),
                stringResource(R.string.xbox_theme_orange), stringResource(R.string.xbox_theme_black), stringResource(R.string.xbox_theme_picture)
            ),
            chosen = theme.ordinal,
            choose = { i -> TvDesign.setTheme(XboxTheme.entries[i]) },
            pictureOption = XboxTheme.PICTURE.ordinal
        ),
        XboxSystemLine(
            stringResource(R.string.xbox_sys_saver), stringResource(R.string.xbox_sys_saver_sub),
            options = listOf(stringResource(R.string.xbox_saver_off), stringResource(R.string.xbox_saver_dim), stringResource(R.string.xbox_saver_photos)),
            chosen = saver.ordinal,
            choose = { i -> TvDesign.setSaver(TvSaver.entries[i]) }
        ),
        XboxSystemLine(
            stringResource(R.string.xbox_sys_saver_after), stringResource(R.string.xbox_sys_saver_after_sub),
            options = SAVER_MINUTES.map(minutesText),
            chosen = SAVER_MINUTES.indexOf(saverMinutes),
            choose = { i -> TvDesign.setSaverMinutes(SAVER_MINUTES[i]) }
        ),
        XboxSystemLine(
            stringResource(R.string.xbox_sys_visualizer), stringResource(R.string.xbox_sys_visualizer_sub),
            options = listOf(stringResource(R.string.xbox_on), stringResource(R.string.xbox_off)),
            chosen = if (visualizer) 0 else 1,
            choose = { i -> TvDesign.setVisualizer(i == 0) }
        ),
        XboxSystemLine(
            stringResource(R.string.xbox_sys_sleep), stringResource(R.string.xbox_sys_sleep_sub),
            options = listOf(stringResource(R.string.xbox_saver_off)) + SLEEP_MINUTES.map(minutesText),
            chosen = if (sleepEnds > 0L) SLEEP_MINUTES.indexOf(sleepChosen) + 1 else 0,
            choose = { i -> TvSleep.set(context, if (i == 0) 0 else SLEEP_MINUTES[i - 1]) },
            now = sleepLeft
        ),
        XboxSystemLine(stringResource(R.string.xbox_sys_display), stringResource(R.string.xbox_sys_display_sub), open = settings(Settings.ACTION_DISPLAY_SETTINGS)),
        XboxSystemLine(stringResource(R.string.xbox_sys_sound), stringResource(R.string.xbox_sys_sound_sub), open = settings(Settings.ACTION_SOUND_SETTINGS)),
        XboxSystemLine(stringResource(R.string.xbox_sys_network), stringResource(R.string.xbox_sys_network_sub), open = settings(Settings.ACTION_WIFI_SETTINGS)),
        XboxSystemLine(stringResource(R.string.xbox_sys_remotes), stringResource(R.string.xbox_sys_remotes_sub), open = settings(Settings.ACTION_BLUETOOTH_SETTINGS)),
        XboxSystemLine(stringResource(R.string.xbox_sys_date), stringResource(R.string.xbox_sys_date_sub), open = settings(Settings.ACTION_DATE_SETTINGS)),
        XboxSystemLine(stringResource(R.string.xbox_sys_storage), stringResource(R.string.xbox_sys_storage_sub), open = settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        XboxSystemLine(stringResource(R.string.xbox_sys_about), stringResource(R.string.xbox_sys_about_sub), open = settings(Settings.ACTION_DEVICE_INFO_SETTINGS))
    )
}

/**
 * Where the remote is on the system settings: a line of the list, one of its choices in the pane,
 * or a picture among the TV's when the "picture" theme is being chosen.
 */
internal class XboxSystemState {
    var at by mutableIntStateOf(0)
    var inPane by mutableStateOf(false)
    var optionAt by mutableIntStateOf(0)
    /** Choosing a picture for the theme; null while not. */
    var picking by mutableStateOf(false)
    var photos by mutableStateOf<List<Uri>?>(null)
    var photoAt by mutableIntStateOf(0)

    fun reset() {
        at = 0
        inPane = false
        picking = false
        photos = null
    }

    /** Back: out of the pictures, out of the pane; false when there is nothing left but the page. */
    fun back(): Boolean = when {
        picking -> { picking = false; true }
        inPane -> { inPane = false; true }
        else -> false
    }

    fun move(key: Key, lines: List<XboxSystemLine>) {
        val line = lines.getOrNull(at) ?: return
        when {
            picking -> {
                val count = photos?.size ?: 0
                when (key) {
                    Key.DirectionLeft -> if (photoAt % PHOTO_COLUMNS == 0) picking = false else photoAt--
                    Key.DirectionRight -> if (photoAt % PHOTO_COLUMNS < PHOTO_COLUMNS - 1 && photoAt + 1 < count) photoAt++
                    Key.DirectionUp -> if (photoAt >= PHOTO_COLUMNS) photoAt -= PHOTO_COLUMNS
                    Key.DirectionDown -> if (photoAt + PHOTO_COLUMNS < count) photoAt += PHOTO_COLUMNS
                    else -> {}
                }
            }
            inPane -> when (key) {
                Key.DirectionUp -> optionAt = (optionAt - 1).coerceAtLeast(0)
                Key.DirectionDown -> optionAt = (optionAt + 1).coerceAtMost(line.options.lastIndex)
                Key.DirectionLeft -> inPane = false
                else -> {}
            }
            else -> when (key) {
                Key.DirectionUp -> at = (at - 1).coerceAtLeast(0)
                Key.DirectionDown -> at = (at + 1).coerceAtMost(lines.lastIndex)
                Key.DirectionRight -> enter(line)
                else -> {}
            }
        }
    }

    fun choose(lines: List<XboxSystemLine>) {
        val line = lines.getOrNull(at) ?: return
        when {
            picking -> photos?.getOrNull(photoAt)?.let { uri ->
                TvDesign.setTheme(XboxTheme.PICTURE, uri.toString())
                picking = false
                inPane = false
            }
            inPane -> if (optionAt == line.pictureOption) {
                photoAt = 0
                picking = true
            } else {
                line.choose(optionAt)
                inPane = false
            }
            line.open != null -> line.open.invoke()
            else -> enter(line)
        }
    }

    private fun enter(line: XboxSystemLine) {
        if (line.options.isEmpty()) return
        optionAt = line.chosen.coerceAtLeast(0)
        inPane = true
    }
}

/**
 * The dashboard's system settings, as the console's were: a white list at the left with the line
 * the remote is on in green, a grey pane beside it saying what the line does and holding its
 * choices - the one set ticked - and, for the TV's own settings, opening them.
 */
@Composable
internal fun XboxSystemPage(lines: List<XboxSystemLine>, state: XboxSystemState, u: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val base = MaterialTheme.typography.titleMedium
    fun style(px: Float) = base.copy(
        fontFamily = SelawikFamily, fontWeight = FontWeight.Normal, fontSize = with(density) { (u * px).toSp() }
    )
    LaunchedEffect(state.picking) {
        if (state.picking && state.photos == null) state.photos = withContext(Dispatchers.IO) { tvPhotos(context, PHOTO_COUNT) }
    }
    val line = lines.getOrNull(state.at) ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.xbox_system_settings),
            style = style(32f).copy(shadow = xboxShadow),
            color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = u * 50f, end = u * 95f)
        )
        Column(
            modifier = Modifier
                .offset(x = u * 104f, y = u * 112f)
                .width(u * LIST_W)
                .height(u * PANEL_H)
                .background(Xbox.Panel)
        ) {
            lines.forEachIndexed { i, l ->
                val here = i == state.at
                Text(
                    l.label,
                    style = style(20f),
                    color = if (here && !state.inPane && !state.picking) Color.White else Xbox.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                !here -> Color.Transparent
                                state.inPane || state.picking -> Color(0xFFC9CFCF)
                                else -> Xbox.GreenLine
                            }
                        )
                        .padding(horizontal = u * 22f, vertical = u * 6f)
                )
            }
        }
        Column(
            modifier = Modifier
                .offset(x = u * (104f + LIST_W), y = u * 112f)
                .width(u * PANE_W)
                .height(u * PANEL_H)
                .background(Xbox.PanelSide)
                .padding(u * 26f)
        ) {
            Text(line.label, style = style(26f).copy(shadow = xboxShadow), color = Color.White)
            Spacer(modifier = Modifier.height(u * 8f))
            Text(line.explain, style = style(17f), color = Color.White.copy(alpha = 0.92f), maxLines = 4, overflow = TextOverflow.Ellipsis)
            line.now?.let { Text(it, style = style(17f), color = Color.White, modifier = Modifier.padding(top = u * 6f)) }
            Spacer(modifier = Modifier.height(u * 14f))
            when {
                state.picking -> PhotoGrid(state, u, ::style)
                line.options.isNotEmpty() -> Column(modifier = Modifier.width(u * 300f).background(Xbox.Panel)) {
                    line.options.forEachIndexed { j, option ->
                        val here = state.inPane && j == state.optionAt
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (here) Xbox.GreenLine else Color.Transparent)
                                .padding(horizontal = u * 14f, vertical = u * 4f)
                        ) {
                            Text(option, style = style(18f), color = if (here) Color.White else Xbox.Ink, modifier = Modifier.weight(1f))
                            if (j == line.chosen) Icon(Icons.Default.Check, null, tint = if (here) Color.White else Xbox.Green, modifier = Modifier.size(u * 20f))
                        }
                    }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    XboxLegendItem("A", Xbox.ButtonA, stringResource(R.string.xbox_open_tv_settings))
                }
            }
        }
        XboxLegend(modifier = Modifier.align(Alignment.BottomStart).padding(start = u * 95f, bottom = u * 48f))
    }
}

@Composable
private fun PhotoGrid(state: XboxSystemState, u: Dp, style: (Float) -> androidx.compose.ui.text.TextStyle) {
    val context = LocalContext.current
    val photos = state.photos
    when {
        photos == null -> {}
        photos.isEmpty() -> Text(stringResource(R.string.xbox_no_pictures), style = style(17f), color = Color.White)
        else -> {
            // A window of rows around the one the remote is on.
            val row = state.photoAt / PHOTO_COLUMNS
            val first = (row - 1).coerceAtLeast(0) * PHOTO_COLUMNS
            Column {
                photos.drop(first).take(PHOTO_COLUMNS * PHOTO_ROWS).chunked(PHOTO_COLUMNS).forEachIndexed { r, chunk ->
                    Row {
                        chunk.forEachIndexed { c, uri ->
                            val index = first + r * PHOTO_COLUMNS + c
                            val here = index == state.photoAt
                            val thumb by produceState<ImageBitmap?>(null, uri) {
                                value = withContext(Dispatchers.IO) { tvPhotoThumb(context, uri) }
                            }
                            Box(
                                modifier = Modifier
                                    .padding(u * 3f)
                                    .size(u * 120f, u * 72f)
                                    .graphicsLayer {
                                        val grow = if (here) 1.08f else 1f
                                        scaleX = grow
                                        scaleY = grow
                                    }
                                    .background(Color(0xFF6E7476))
                                    .border(if (here) 3.dp else 0.dp, if (here) Xbox.GreenLit else Color.Transparent)
                            ) {
                                thumb?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(if (here) 3.dp else 0.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The profile, where the dashboard kept the gamertag and its picture: the TV's name and the phone
 * paired with it beside a square picture; lit when the remote is on it.
 */
@Composable
internal fun XboxProfile(lit: Boolean, u: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val name = androidx.compose.runtime.remember(context) { LinkStore.deviceName(context) }
    val peers by androidx.compose.runtime.remember(context) { LinkStore.attach(context) }.peers.collectAsState()
    val phone = peers.firstOrNull()?.name
    val base = MaterialTheme.typography.titleMedium
    fun style(px: Float) = base.copy(
        fontFamily = SelawikFamily, fontWeight = FontWeight.Normal, fontSize = with(density) { (u * px).toSp() }, shadow = xboxShadow
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.graphicsLayer {
            val grow = if (lit) 1.06f else 1f
            scaleX = grow
            scaleY = grow
        }
    ) {
        XboxClock()
        Spacer(modifier = Modifier.width(u * 26f))
        Column(horizontalAlignment = Alignment.End) {
            Text(name, style = style(21f), color = Color.White, maxLines = 1)
            Text(
                if (phone != null) stringResource(R.string.xbox_profile_phone, phone) else stringResource(R.string.xbox_profile_no_phone),
                style = style(15f),
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(u * 12f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(u * 58f)
                .background(if (lit) Xbox.GreenLit else Xbox.Green)
                .border(if (lit) 2.dp else 0.dp, if (lit) Color.White else Color.Transparent)
        ) {
            Icon(Icons.Default.Tv, null, tint = Color.White, modifier = Modifier.size(u * 32f))
        }
    }
}

private const val LIST_W = 440f
private const val PANE_W = 640f
private const val PANEL_H = 470f
private const val PHOTO_COLUMNS = 4
private const val PHOTO_ROWS = 3
private const val PHOTO_COUNT = 48
private val SAVER_MINUTES = listOf(5, 10, 20, 30)
private val SLEEP_MINUTES = listOf(15, 30, 60, 90, 120)
