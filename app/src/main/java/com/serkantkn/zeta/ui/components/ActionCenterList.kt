package com.serkantkn.zeta.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.repository.PanelAction
import com.serkantkn.zeta.data.repository.PanelGroup
import com.serkantkn.zeta.data.repository.PanelNotification
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a notification in the action center can be asked to do. */
internal class NotificationHandlers(
    val open: (PanelNotification) -> Unit,
    val dismiss: (PanelNotification) -> Unit,
    val reply: (PanelNotification) -> Unit,
    val press: (PanelNotification, PanelAction) -> Unit,
    val snooze: (PanelNotification, Long) -> Unit,
    val settings: (PanelNotification) -> Unit,
    val clearGroup: (PanelGroup) -> Unit
)

/** The notifications a group shows before it is opened out. */
private const val FOLDED_ITEMS = 2

/**
 * The notifications, as items of the action center's list: each app's in a card of its own, the
 * conversations first, a long group folded to its newest two, and what Android ranks as silent
 * folded together at the foot. [unfolded] remembers which groups (and "silent") are opened out.
 */
internal fun LazyListScope.notificationCards(
    groups: List<PanelGroup>,
    unfolded: SnapshotStateMap<String, Boolean>,
    now: Long,
    locale: Locale,
    ink: Color,
    handlers: NotificationHandlers
) {
    val loud = groups.filterNot { it.silent }
    val silent = groups.filter { it.silent }
    loud.forEach { group -> groupCard(group, unfolded, now, locale, ink, handlers) }
    if (silent.isNotEmpty()) {
        val count = silent.sumOf { it.items.size }
        item(key = "silent_header") {
            val open = unfolded[SILENT_KEY] == true
            FoldRow(
                text = stringResource(R.string.ac_silent, count),
                open = open,
                ink = ink,
                onClick = { unfolded[SILENT_KEY] = !open }
            )
        }
        if (unfolded[SILENT_KEY] == true) {
            silent.forEach { group -> groupCard(group, unfolded, now, locale, ink, handlers) }
        }
    }
}

private const val SILENT_KEY = "|silent|"

private fun LazyListScope.groupCard(
    group: PanelGroup,
    unfolded: SnapshotStateMap<String, Boolean>,
    now: Long,
    locale: Locale,
    ink: Color,
    handlers: NotificationHandlers
) {
    val open = unfolded[group.key] == true
    val shown = if (open) group.items else group.items.take(FOLDED_ITEMS)
    val hidden = group.items.size - shown.size
    item(key = "group_" + group.key) {
        CardPiece(ink, top = true, bottom = false) {
            GroupHeader(group, now, locale, ink) { handlers.clearGroup(group) }
        }
    }
    shown.forEachIndexed { index, notification ->
        item(key = notification.key) {
            val last = index == shown.lastIndex && hidden <= 0 && !(open && group.items.size > FOLDED_ITEMS)
            CardPiece(ink, top = false, bottom = last) {
                NotificationRow(notification, ink, handlers)
            }
        }
    }
    if (hidden > 0 || (open && group.items.size > FOLDED_ITEMS)) {
        item(key = "fold_" + group.key) {
            CardPiece(ink, top = false, bottom = true) {
                FoldRow(
                    text = if (open) stringResource(R.string.ac_less) else stringResource(R.string.ac_more, hidden),
                    open = open,
                    ink = ink,
                    onClick = { unfolded[group.key] = !open }
                )
            }
        }
    }
}

/**
 * A piece of a group's card: a group is several items of the list, so its card is drawn a piece
 * at a time - the same ground, a margin round the outside, a gap after the last piece.
 */
@Composable
private fun CardPiece(ink: Color, top: Boolean, bottom: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = if (top) 10.dp else 0.dp)
            .background(ink.copy(alpha = CARD_ALPHA))
            .padding(bottom = if (bottom) 6.dp else 0.dp)
    ) { content() }
}

/** How much darker (or lighter) a card is than the sheet: enough to hold a group together. */
private const val CARD_ALPHA = 0.055f

@Composable
private fun GroupHeader(group: PanelGroup, now: Long, locale: Locale, ink: Color, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val hub = group.hub
        if (hub != null) {
            // The launcher's own: the hub's glyph on a small square of the accent, a tile in
            // miniature, rather than the launcher's icon over every one of them.
            Box(
                modifier = Modifier.size(22.dp).background(LocalZuneColors.current.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(getHubIcon(hub), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        } else {
            group.appIcon?.let {
                Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(22.dp))
            } ?: Spacer(modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.appName,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = ink.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            // When, once for the group: its newest.
            Text(
                text = " · " + whenText(group.items.first().postTime, now, locale),
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
        if (group.items.any { it.clearable }) {
            val label = stringResource(R.string.ac_clear_group, group.appName)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = label }
                    .clickableNoRipple(onClear),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = ink.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(item: PanelNotification, ink: Color, handlers: NotificationHandlers) {
    val zuneColors = LocalZuneColors.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    var expanded by remember(item.key) { mutableStateOf(false) }
    var overflowing by remember(item.key) { mutableStateOf(false) }
    var menu by remember(item.key) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) }
            .then(
                if (item.clearable) {
                    // Away to either side.
                    Modifier.pointerInput(item.key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (abs(offset.value) > width * 0.4f) {
                                        offset.animateTo(if (offset.value > 0) width.toFloat() else -width.toFloat(), tween(160))
                                        handlers.dismiss(item)
                                    } else {
                                        offset.animateTo(0f, tween(160))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f, tween(160)) } },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + delta) }
                            }
                        )
                    }
                } else Modifier
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = { menu = !menu },
                onClick = { handlers.open(item) }
            )
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            item.avatar?.let { avatar ->
                Image(
                    bitmap = avatar.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
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
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.messages.size > 1) {
                    // A conversation: its last few messages, each with who wrote it.
                    val lines = if (expanded) item.messages else item.messages.takeLast(2)
                    if (lines.size < item.messages.size) overflowing = true
                    lines.forEach { m ->
                        Text(
                            text = if (m.sender.isNotBlank() && m.sender != item.title) "${m.sender}: ${m.text}" else m.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = ink.copy(alpha = 0.75f),
                            maxLines = if (expanded) 6 else 2,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (it.hasVisualOverflow) overflowing = true }
                        )
                    }
                } else if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = ink.copy(alpha = 0.75f),
                        maxLines = if (expanded) 20 else 3,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (it.hasVisualOverflow) overflowing = true }
                    )
                }
            }
            // Opens out what is cut short: the rest of the words, the earlier messages, the picture.
            if (overflowing || item.picture != null || expanded) {
                val label = stringResource(R.string.ac_show_more)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = label }
                        .clickableNoRipple { expanded = !expanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ink.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        item.picture?.let { picture ->
            Image(
                bitmap = picture.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = if (expanded) 320.dp else 120.dp)
            )
        }

        item.progress?.let { progress ->
            if (progress < 0f) {
                ZuneLoadingDots(modifier = Modifier.padding(top = 8.dp, end = 8.dp))
            } else {
                MetroProgressLine(progress = progress, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
            }
        }

        // The notification's own buttons, after the reply box when it has one.
        if (item.reply != null || item.actions.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.reply != null) {
                    ActionWord(stringResource(R.string.action_center_reply), zuneColors.accentLink) { handlers.reply(item) }
                }
                item.actions.forEach { action ->
                    ActionWord(action.title.lowercase(), ink.copy(alpha = 0.85f)) { handlers.press(item, action) }
                }
            }
        }

        // A long press: put it off, or go to what decides whether the app may send it at all.
        AnimatedVisibility(visible = menu, enter = expandVertically(tween(160)), exit = shrinkVertically(tween(120))) {
            Row(
                modifier = Modifier.padding(top = 2.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.clearable) {
                    ActionWord(stringResource(R.string.ac_snooze_hour), ink.copy(alpha = 0.85f)) {
                        menu = false
                        handlers.snooze(item, HOUR_MILLIS)
                    }
                    ActionWord(stringResource(R.string.ac_snooze_tomorrow), ink.copy(alpha = 0.85f)) {
                        menu = false
                        handlers.snooze(item, untilTomorrowMorning())
                    }
                }
                ActionWord(stringResource(R.string.ac_notification_settings), ink.copy(alpha = 0.85f)) {
                    menu = false
                    handlers.settings(item)
                }
            }
        }
    }
}

private const val HOUR_MILLIS = 60 * 60 * 1000L

/** From now to nine tomorrow morning. */
private fun untilTomorrowMorning(): Long {
    val now = Calendar.getInstance()
    val then = (now.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return then.timeInMillis - now.timeInMillis
}

/** A button of a notification: a word, its touch area as tall as a finger needs. */
@Composable
private fun ActionWord(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clickableNoRipple(onClick)
            .padding(end = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = color,
            maxLines = 1
        )
    }
}

/** "+3 more", "show less", "silent notifications (4)": a line that opens or folds what is under it. */
@Composable
private fun FoldRow(text: String, open: Boolean, ink: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            color = ink.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = ink.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** What was just cleared, and the way to bring it back while there is still time. */
@Composable
internal fun UndoBar(count: Int, ink: Color, onUndo: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(ink.copy(alpha = 0.1f))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.ac_cleared, count),
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            modifier = Modifier.weight(1f)
        )
        ActionWord(stringResource(R.string.ac_undo), zuneColors.accentLink, onUndo)
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
