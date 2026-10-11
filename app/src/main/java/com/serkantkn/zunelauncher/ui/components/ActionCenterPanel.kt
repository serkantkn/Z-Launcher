package com.serkantkn.zunelauncher.ui.components

import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.repository.ActionCenterNotifications
import com.serkantkn.zunelauncher.data.repository.PanelGroup
import com.serkantkn.zunelauncher.data.repository.PanelNotification
import com.serkantkn.zunelauncher.data.service.QuickReplyOverlay
import com.serkantkn.zunelauncher.data.service.ThirdPartyMediaController
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.QuickAction
import com.serkantkn.zunelauncher.util.QuickActions
import com.serkantkn.zunelauncher.util.QuickOutcome
import com.serkantkn.zunelauncher.util.TimeText
import com.serkantkn.zunelauncher.util.Torch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Windows Phone action center, as it comes over whatever is on screen.
 *
 * On a phone it is Windows 10 Mobile's sheet from the top: the clock, the date and the battery;
 * the quick-action squares, four and then four more when expanded; brightness, the music that
 * is playing, and the notifications grouped by app — newest app first — each one opened by a
 * tap, swiped away to the right, or answered in place where the app allows it.
 *
 * On a tablet a sheet the width of the screen is mostly empty air, so it is Windows 10's action
 * center from tablet mode instead: a column down the right-hand edge with the notifications on
 * top and the quick actions, as low wide tiles, at the foot where the thumb is. The rest of the
 * screen dims, and a tap there puts the column away.
 *
 * The panel places itself from [progress] (0 away, 1 open), so the finger on the status bar
 * and the finger on the panel move the same thing; [onDragBy] takes a change as a fraction of
 * the way, and [onDragEnd] a velocity in px/s, positive towards open. While it is not [active]
 * — built ahead of time and waiting hidden — the clock and the music card stand still. [cover]
 * (0..1) darkens the dimmed part of the screen nearly to black for a moment, to hide Android's
 * shade while it is being pushed back up underneath.
 */
@Composable
fun ActionCenterPanel(
    active: Boolean,
    progress: () -> Float,
    cover: () -> Float = { 0f },
    onDragBy: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onClose: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val sheet = if (zuneColors.isDark) Color(0xFF1B1B1B) else Color(0xFFF2F2F2)
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    // A slow tick refreshes the clock and every switch's state, so a square turned on from
    // Android's own sheet shows as on when the panel is looked at again.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var stateTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        Torch.watch(context)
        withContext(Dispatchers.Default) { ActionCenterNotifications.refreshNow() }
        while (true) {
            now = System.currentTimeMillis()
            stateTick++
            delay(1_000L)
        }
    }

    val battery = remember(now / 30_000L) {
        (context.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }
    val date = remember(now / 60_000L, locale) {
        SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now)).lowercase(locale)
    }
    val groups by ActionCenterNotifications.groups.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    // The panel is built once and kept (see ActionCenterOverlay), so its list would come back
    // where it was left — and a notification that arrived meanwhile lands above the fold. Each
    // time it comes down, it starts from the newest.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(active) { if (active) listState.scrollToItem(0) }

    // The music card reads the playing session through the notification listener.
    val media = remember { ThirdPartyMediaController(context.applicationContext) }
    DisposableEffect(media, active) {
        if (active) runCatching { media.startListening() }
        onDispose { if (active) runCatching { media.stopListening() } }
    }
    val mediaState by media.mediaState.collectAsState()

    fun leave(after: () -> Unit) {
        onClose()
        after()
    }

    val pressed: (QuickOutcome) -> Unit = { outcome ->
        if (outcome == QuickOutcome.OPENED_ELSEWHERE) onClose()
        stateTick++
    }

    val openAllSettings = {
        leave {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_OPEN_HUB, HubType.SETTINGS.name)
            )
        }
    }

    // ── The pieces both layouts are built from ──

    val clock: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = TimeText.format(now, locale = locale),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 44.sp),
                color = ink
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = ink.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp).weight(1f)
            )
            battery?.let {
                Text(
                    text = java.text.NumberFormat.getPercentInstance(locale).format(it / 100.0),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    color = ink.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    val expandLink: @Composable () -> Unit = {
        LinkText(
            stringResource(if (expanded) R.string.action_center_collapse else R.string.action_center_expand),
            ink.copy(alpha = 0.75f)
        ) { expanded = !expanded }
    }

    val music: @Composable () -> Unit = {
        if (mediaState.title.isNotBlank()) {
            MediaCard(
                title = mediaState.title,
                artist = mediaState.artist,
                art = mediaState.albumArt,
                isPlaying = mediaState.isPlaying,
                ink = ink,
                onOpen = {
                    val session = mediaState.controller?.sessionActivity
                    if (session != null) leave { runCatching { session.send() } }
                },
                onPrevious = { media.skipToPrevious() },
                onPlayPause = { media.playPause() },
                onNext = { media.skipToNext() }
            )
            Divider(ink)
        }
    }

    val hasClearable = groups.any { g -> g.items.any { it.clearable } }

    val notifications: @Composable (Modifier, NestedScrollConnection?) -> Unit = { modifier, scroll ->
        Box(modifier = modifier) {
            when {
                !ActionCenterNotifications.isListening -> Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.action_center_no_access),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                        color = ink.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinkText(stringResource(R.string.action_center_grant), zuneColors.accentLink) {
                        leave {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                }
                groups.isEmpty() -> Text(
                    text = stringResource(R.string.action_center_empty),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp),
                    color = ink.copy(alpha = 0.55f),
                    modifier = Modifier.padding(16.dp)
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().then(if (scroll != null) Modifier.nestedScroll(scroll) else Modifier)
                ) {
                    groups.forEach { group ->
                        item(key = "group_" + group.key) {
                            GroupHeader(group, ink) { ActionCenterNotifications.dismissGroup(group) }
                        }
                        items(group.items, key = { it.key }) { item ->
                            NotificationRow(
                                item = item,
                                now = now,
                                locale = locale,
                                ink = ink,
                                onOpen = { leave { ActionCenterNotifications.open(context, item) } },
                                onDismiss = { ActionCenterNotifications.dismiss(item) },
                                onReply = {
                                    // The reply box sits below the keyboard; the panel would hide both.
                                    if (QuickReplyOverlay.show(context, item)) {
                                        onClose()
                                    } else {
                                        leave { ActionCenterNotifications.open(context, item) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_PANEL_FROM
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Behind the panel the screen dims as it comes; a tap on the dimmed part puts it away.
        val dim = if (wide) 0.45f else 0.35f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress() * (dim + (0.94f - dim) * cover()) }
                .background(Color.Black)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() }
        )

        if (wide) {
            val panelWidth = SIDE_PANEL_WIDTH.coerceAtMost(maxWidth * 0.6f)
            SidePanel(
                width = panelWidth,
                sheet = sheet,
                ink = ink,
                progress = progress,
                onDragBy = onDragBy,
                onDragEnd = onDragEnd,
                clock = clock,
                music = music,
                notifications = notifications,
                hasClearable = hasClearable,
                expanded = expanded,
                expandLink = expandLink,
                stateTick = stateTick,
                onPressed = pressed,
                onAllSettings = openAllSettings,
                onAskPermission = { leave { QuickActions.askToWriteSettings(context) } }
            )
        } else {
            PhonePanel(
                heightPx = heightPx,
                sheet = sheet,
                ink = ink,
                progress = progress,
                onDragBy = onDragBy,
                onDragEnd = onDragEnd,
                clock = clock,
                music = music,
                notifications = notifications,
                hasClearable = hasClearable,
                expanded = expanded,
                expandLink = expandLink,
                stateTick = stateTick,
                onPressed = pressed,
                onAllSettings = openAllSettings,
                onAskPermission = { leave { QuickActions.askToWriteSettings(context) } }
            )
        }
    }
}

/** From this width on the panel is the side column rather than the sheet from the top. */
private val WIDE_PANEL_FROM = 600.dp

/** Windows 10's action center was about this wide on a tablet. */
private val SIDE_PANEL_WIDTH = 400.dp

@Composable
private fun Divider(ink: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.12f)))
}

/** The phone's sheet, from the top. */
@Composable
private fun PhonePanel(
    heightPx: Float,
    sheet: Color,
    ink: Color,
    progress: () -> Float,
    onDragBy: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    clock: @Composable () -> Unit,
    music: @Composable () -> Unit,
    notifications: @Composable (Modifier, NestedScrollConnection?) -> Unit,
    hasClearable: Boolean,
    expanded: Boolean,
    expandLink: @Composable () -> Unit,
    stateTick: Int,
    onPressed: (QuickOutcome) -> Unit,
    onAllSettings: () -> Unit,
    onAskPermission: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val dragState = rememberDraggableState { delta -> onDragBy(delta / heightPx) }
    val sheetDrag = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> onDragEnd(velocity) }
    )

    // A push up past the end of the list moves the sheet itself.
    val listToSheet = remember(heightPx) {
        object : NestedScrollConnection {
            var movedSheet = false
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                movedSheet = true
                onDragBy(available.y / heightPx)
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!movedSheet) return Velocity.Zero
                movedSheet = false
                onDragEnd(available.y)
                return available
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -(1f - progress()) * size.height }
            .background(sheet)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            .padding(top = statusTop + 8.dp, bottom = navBottom)
    ) {
        // ── Clock, date, battery; all settings and expand ──
        Column(modifier = sheetDrag.padding(horizontal = 16.dp)) {
            clock()
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
                LinkText(stringResource(R.string.action_center_all_settings), zuneColors.accentLink, onAllSettings)
                Spacer(modifier = Modifier.weight(1f))
                expandLink()
            }

            // ── Quick actions ──
            QuickRow(QuickAction.FIRST_ROW, stateTick, ink, onPressed)
            AnimatedVisibility(visible = expanded, enter = expandVertically(tween(200)), exit = shrinkVertically(tween(160))) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    QuickRow(QuickAction.SECOND_ROW, stateTick, ink, onPressed)
                }
            }

            // ── Brightness ──
            BrightnessRow(stateTick, ink, onAskPermission = onAskPermission)
        }

        Box(modifier = Modifier.padding(top = 6.dp)) { Divider(ink) }

        music()

        // ── Notifications ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.action_center_notifications),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
                color = ink,
                modifier = Modifier.weight(1f)
            )
            if (hasClearable) {
                LinkText(stringResource(R.string.action_center_clear_all), zuneColors.accentLink) {
                    ActionCenterNotifications.clearAll()
                }
            }
        }

        notifications(Modifier.weight(1f).fillMaxWidth(), listToSheet)

        // The bar to push it back up, as Windows Phone drew it at the foot of the sheet.
        Box(
            modifier = sheetDrag.fillMaxWidth().padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(width = 48.dp, height = 4.dp).background(ink.copy(alpha = 0.45f)))
        }
    }
}

/**
 * The tablet's column down the right-hand edge, after Windows 10 in tablet mode: the clock and
 * the notifications above, and at the foot the links, the quick actions as low wide tiles four
 * across, and brightness. A drag to the right on its head or foot sends it back off the edge.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SidePanel(
    width: androidx.compose.ui.unit.Dp,
    sheet: Color,
    ink: Color,
    progress: () -> Float,
    onDragBy: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    clock: @Composable () -> Unit,
    music: @Composable () -> Unit,
    notifications: @Composable (Modifier, NestedScrollConnection?) -> Unit,
    hasClearable: Boolean,
    expanded: Boolean,
    expandLink: @Composable () -> Unit,
    stateTick: Int,
    onPressed: (QuickOutcome) -> Unit,
    onAllSettings: () -> Unit,
    onAskPermission: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var widthPx by remember { mutableFloatStateOf(1f) }

    // Right is away: the fraction and the velocity are turned round so positive still means open.
    val dragState = rememberDraggableState { delta -> onDragBy(-delta / widthPx) }
    val sideDrag = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Horizontal,
        onDragStopped = { velocity -> onDragEnd(-velocity) }
    )

    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .width(width)
            .fillMaxHeight()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { translationX = (1f - progress()) * size.width }
            .background(sheet)
            .drawBehind { drawRect(ink.copy(alpha = 0.14f), size = androidx.compose.ui.geometry.Size(1.dp.toPx(), size.height)) }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            .padding(top = statusTop + 8.dp, bottom = navBottom + 12.dp)
    ) {
        // ── Head: clock and date, and the notifications' own links ──
        Column(modifier = sideDrag.padding(horizontal = 16.dp)) {
            clock()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.action_center_notifications),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
                    color = ink,
                    modifier = Modifier.weight(1f)
                )
                if (hasClearable) {
                    LinkText(stringResource(R.string.action_center_clear_all), zuneColors.accentLink) {
                        ActionCenterNotifications.clearAll()
                    }
                }
            }
        }
        Divider(ink)

        notifications(Modifier.weight(1f).fillMaxWidth(), null)

        Divider(ink)
        music()

        // ── Foot: links, quick actions, brightness ──
        Column(modifier = sideDrag.padding(start = 16.dp, end = 16.dp, top = 6.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                expandLink()
                Spacer(modifier = Modifier.weight(1f))
                LinkText(stringResource(R.string.action_center_all_settings), zuneColors.accentLink, onAllSettings)
            }
            QuickTileRow(QuickAction.FIRST_ROW, stateTick, ink, onPressed)
            AnimatedVisibility(visible = expanded, enter = expandVertically(tween(200)), exit = shrinkVertically(tween(160))) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    QuickTileRow(QuickAction.SECOND_ROW, stateTick, ink, onPressed)
                }
            }
            BrightnessRow(stateTick, ink, onAskPermission = onAskPermission)
        }
    }
}

// ════════════════════════════════════════════════════════════
// QUICK ACTIONS
// ════════════════════════════════════════════════════════════

@Composable
private fun QuickRow(actions: List<QuickAction>, tick: Int, ink: Color, onPressed: (QuickOutcome) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { action ->
            QuickSquare(action, tick, ink, onPressed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickSquare(action: QuickAction, tick: Int, ink: Color, onPressed: (QuickOutcome) -> Unit, modifier: Modifier) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val on = remember(tick, action) { QuickActions.isOn(context, action) }
    val off = if (zuneColors.isDark) Color(0xFF333333) else Color(0xFFDADADA)
    val glyph = if (on) Color.White else ink
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .background(if (on) zuneColors.accentColor else off)
            .wpTilt(interactionSource = source)
            .clickable(indication = null, interactionSource = source) {
                onPressed(QuickActions.press(context, action))
            }
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            painter = quickIcon(action),
            contentDescription = null,
            tint = glyph,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(action.labelRes),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp),
            color = glyph,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The tablet's tiles: Windows 10's low, wide ones, the glyph above and the name below. */
@Composable
private fun QuickTileRow(actions: List<QuickAction>, tick: Int, ink: Color, onPressed: (QuickOutcome) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        actions.forEach { action ->
            QuickTile(action, tick, ink, onPressed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickTile(action: QuickAction, tick: Int, ink: Color, onPressed: (QuickOutcome) -> Unit, modifier: Modifier) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val on = remember(tick, action) { QuickActions.isOn(context, action) }
    val off = if (zuneColors.isDark) Color(0xFF333333) else Color(0xFFDADADA)
    val glyph = if (on) Color.White else ink
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(68.dp)
            .background(if (on) zuneColors.accentColor else off)
            .wpTilt(interactionSource = source)
            .clickable(indication = null, interactionSource = source) {
                onPressed(QuickActions.press(context, action))
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            painter = quickIcon(action),
            contentDescription = null,
            tint = glyph,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(action.labelRes),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 13.sp),
            color = glyph,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun quickIcon(action: QuickAction): Painter = when (action) {
    QuickAction.WIFI -> painterResource(R.drawable.ic_wp_wifi)
    QuickAction.BLUETOOTH -> painterResource(R.drawable.ic_wp_bluetooth)
    QuickAction.FLASHLIGHT -> painterResource(R.drawable.ic_wp_flash)
    QuickAction.ROTATION_LOCK -> rememberVectorPainter(Icons.Default.ScreenLockRotation)
    QuickAction.MOBILE_DATA -> rememberVectorPainter(Icons.Default.SignalCellularAlt)
    QuickAction.AIRPLANE -> painterResource(R.drawable.ic_wp_airplane)
    QuickAction.LOCATION -> painterResource(R.drawable.ic_wp_location)
    QuickAction.QUIET_HOURS -> painterResource(R.drawable.ic_wp_moon)
}

@Composable
private fun BrightnessRow(tick: Int, ink: Color, onAskPermission: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val canWrite = remember(tick) { QuickActions.canWriteSettings(context) }
    val auto = remember(tick) { QuickActions.isAutoBrightness(context) }
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(QuickActions.brightness(context)) }
    LaunchedEffect(tick) { if (!dragging) value = QuickActions.brightness(context) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_wp_sun), contentDescription = null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        if (canWrite) {
            WpThinSlider(
                value = value,
                accent = zuneColors.accentColor,
                track = ink.copy(alpha = 0.2f),
                onChange = {
                    dragging = true
                    value = it
                    QuickActions.setBrightness(context, it)
                },
                onFinished = { dragging = false },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text(
                text = stringResource(R.string.action_center_brightness_auto),
                style = MaterialTheme.typography.labelLarge,
                color = if (auto) Color.White else ink.copy(alpha = 0.8f),
                modifier = Modifier
                    .background(if (auto) zuneColors.accentColor else Color.Transparent)
                    .border(1.dp, if (auto) zuneColors.accentColor else ink.copy(alpha = 0.4f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        QuickActions.setAutoBrightness(context, !auto)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.action_center_brightness_permission),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.accentLink,
                modifier = Modifier
                    .padding(start = 10.dp, top = 12.dp, bottom = 12.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onAskPermission() }
            )
        }
    }
}

/**
 * Windows Phone's slider: a thin line, filled in the accent up to the value, with a narrow
 * upright block for a thumb. A tap jumps there; a drag follows the finger.
 */
@Composable
private fun WpThinSlider(
    value: Float,
    accent: Color,
    track: Color,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableIntStateOf(1) }
    Box(
        modifier = modifier
            .height(32.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange((offset.x / width).coerceIn(0f, 1f))
                    onFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = onFinished,
                    onDragCancel = onFinished
                ) { change, _ ->
                    change.consume()
                    onChange((change.position.x / width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(track))
        Box(modifier = Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).background(accent))
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val thumbX = (maxWidth - 10.dp) * value.coerceIn(0f, 1f)
            Box(modifier = Modifier.offset(x = thumbX).size(width = 10.dp, height = 24.dp).background(accent))
        }
    }
}

// ════════════════════════════════════════════════════════════
// MUSIC
// ════════════════════════════════════════════════════════════

@Composable
private fun MediaCard(
    title: String,
    artist: String,
    art: android.graphics.Bitmap?,
    isPlaying: Boolean,
    ink: Color,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp).background(ink.copy(alpha = 0.1f))) {
            art?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) {
                Text(artist, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        RoundGlyph(Icons.Default.SkipPrevious, ink, onPrevious)
        Spacer(modifier = Modifier.width(8.dp))
        RoundGlyph(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, ink, onPlayPause)
        Spacer(modifier = Modifier.width(8.dp))
        RoundGlyph(Icons.Default.SkipNext, ink, onNext)
    }
}

@Composable
private fun RoundGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, ink: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(1.5.dp, ink.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
    }
}

// ════════════════════════════════════════════════════════════
// NOTIFICATIONS
// ════════════════════════════════════════════════════════════

@Composable
private fun GroupHeader(group: PanelGroup, ink: Color, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val hub = group.hub
        if (hub != null) {
            // The launcher's own: the hub's glyph on a small square of the accent, a tile in
            // miniature, rather than the launcher's icon over every one of them.
            Box(
                modifier = Modifier.size(18.dp).background(LocalZuneColors.current.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(getHubIcon(hub), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            group.appIcon?.let {
                Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
        Text(
            text = group.appName,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 0.5.sp),
            color = ink.copy(alpha = 0.65f),
            modifier = Modifier.weight(1f)
        )
        if (group.items.any { it.clearable }) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = ink.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(32.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClear)
                    .padding(7.dp)
            )
        }
    }
}

@Composable
private fun NotificationRow(
    item: PanelNotification,
    now: Long,
    locale: Locale,
    ink: Color,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onReply: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (offset.value / width).coerceIn(0f, 1f) }
            .then(
                if (item.clearable) {
                    Modifier.pointerInput(item.key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offset.value > width * 0.4f) {
                                        offset.animateTo(width.toFloat(), tween(160))
                                        onDismiss()
                                    } else {
                                        offset.animateTo(0f, tween(160))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f, tween(160)) } },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
                            }
                        )
                    }
                } else Modifier
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(3.dp).height(36.dp).background(zuneColors.accentColor))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (item.subText.isNotBlank()) {
                    Text(
                        text = item.subText,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = ink.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.title.isNotBlank()) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = ink.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.reply != null) {
                    Text(
                        text = stringResource(R.string.action_center_reply),
                        style = MaterialTheme.typography.labelLarge,
                        color = zuneColors.accentLink,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onReply)
                            .padding(vertical = 4.dp)
                    )
                }
            }
            Text(
                text = whenText(item.postTime, now, locale),
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun whenText(postTime: Long, now: Long, locale: Locale): String {
    if (now - postTime < 60_000L) return stringResource(R.string.action_center_now)
    val then = Calendar.getInstance().apply { timeInMillis = postTime }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) TimeText.format(postTime, locale = locale)
    else SimpleDateFormat("d MMM", locale).format(Date(postTime)).lowercase(locale)
}

@Composable
private fun LinkText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp),
        color = color,
        modifier = Modifier
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 6.dp)
    )
}

// ════════════════════════════════════════════════════════════
// REPLY
// ════════════════════════════════════════════════════════════

/** The reply box, in its own window below the keyboard (see QuickReplyOverlay). */
@Composable
fun QuickReplySheet(
    appName: String,
    title: String,
    text: String,
    onSend: (String) -> Unit,
    onCancel: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val sheet = if (zuneColors.isDark) Color(0xFF1B1B1B) else Color(0xFFF2F2F2)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var message by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sheet)
            .padding(start = 16.dp, end = 16.dp, top = statusTop + 10.dp, bottom = 12.dp)
    ) {
        Text(appName, style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.6f))
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1)
        if (text.isNotBlank()) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(10.dp))
        BasicTextField(
            value = message,
            onValueChange = { message = it },
            textStyle = TextStyle(color = ink, fontSize = 16.sp),
            cursorBrush = SolidColor(zuneColors.accentColor),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, zuneColors.accentColor)
                        .padding(10.dp)
                ) {
                    if (message.isEmpty()) {
                        Text(stringResource(R.string.action_center_reply_hint), color = ink.copy(alpha = 0.4f), fontSize = 16.sp)
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            LinkText(stringResource(R.string.action_center_cancel), ink.copy(alpha = 0.7f), onCancel)
            Spacer(modifier = Modifier.width(20.dp))
            LinkText(stringResource(R.string.action_center_reply_send), zuneColors.accentLink) {
                if (message.isNotBlank()) onSend(message.trim())
            }
        }
    }
}
