package com.serkantkn.zunelauncher.ui.screens.messaging

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.SolidColor
import com.serkantkn.zunelauncher.data.model.MmsAttachment
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.MessageDelivery
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.SimCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One conversation, read from the top down.
 *
 * Messages are laid out by day rather than as one long run, and each one we sent carries what the
 * network later said about it — went out, arrived, or did not. The box at the bottom says how many
 * text messages what has been typed will actually cost, which is the part of sending nobody is
 * ever told until the bill arrives.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationScreen(
    conversation: SmsConversationModel,
    messages: List<SmsMessageModel>,
    input: String,
    onInputChange: (String) -> Unit,
    now: Long,
    sims: List<SimCard>,
    selectedSim: Int,
    onSelectSim: (Int) -> Unit,
    isBlocked: Boolean,
    onSend: () -> Unit,
    onClose: () -> Unit,
    onCall: () -> Unit,
    onMessageLongPress: (SmsMessageModel) -> Unit,
    modifier: Modifier = Modifier,
    onVisibilityChange: (Boolean) -> Unit = {},
    onOpenAttachment: (MmsAttachment) -> Unit = {},
    /** A search result this conversation was opened from: be at its message, its words marked. */
    find: ConversationFind? = null,
    onFindShown: () -> Unit = {},
    /** Pictures and clips going with the next message, which makes it a picture message. */
    attachments: List<android.net.Uri> = emptyList(),
    onAttach: () -> Unit = {},
    onRemoveAttachment: (android.net.Uri) -> Unit = {},
    /** A picture message announced but not fetched, to be fetched now. */
    onDownloadMms: (SmsMessageModel) -> Unit = {},
    /** Rows of waiting picture messages being fetched right now. */
    downloadingMms: Set<Long> = emptySet()
) {
    val zuneColors = LocalZuneColors.current
    // A tablet shows the conversation beside the list, under the hub's own headings: its header
    // and the box to write in are drawn the Windows 8 way there, the rest is the same.
    val wide = LocalIsWideScreen.current
    val listState = rememberLazyListState()
    val days = remember(messages) { groupMessagesByDay(messages) }
    val count = remember(input) { messageCount(input) }

    // ── The message flying from the box to its place ──
    // On send, what was typed leaves the box as it was set there and becomes a bubble on its way
    // down the list to where the message lands; the bubble in the list stays hidden until it has.
    var flight by remember(conversation.threadId) { mutableStateOf<SendFlight?>(null) }
    var fieldBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var screenOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var landingBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var listBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    // The message the flight is for: the newest one we sent with those words, sent since it left.
    val landing = flight?.let { f ->
        messages.lastOrNull { it.isOutgoing && it.body == f.text && it.timestamp >= f.startedAt - 2_000L }
    }
    val send: () -> Unit = {
        val from = fieldBounds
        if (input.isNotBlank() && attachments.isEmpty() && from != null) {
            landingBounds = null
            flight = SendFlight(input, from, System.currentTimeMillis())
        }
        onSend()
    }

    // A conversation behind another app is not being read; a message arriving then is announced.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { onVisibilityChange(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onVisibilityChange(true) }

    // ── Finding words in it ──
    var searching by remember(conversation.threadId) { mutableStateOf(false) }
    var query by remember(conversation.threadId) { mutableStateOf("") }
    // Which match is shown, by message, so a message arriving does not move the search.
    var currentId by remember(conversation.threadId) { mutableStateOf<Long?>(null) }
    val matches = remember(messages, query) {
        if (query.isBlank()) emptyList() else messages.filter { it.body.contains(query, ignoreCase = true) }.map { it.id }
    }
    val currentIndex = matches.indexOf(currentId)
    // Where each message sits in the list: a day's heading, then its messages.
    val rows = remember(days) {
        buildMap {
            var row = 0
            days.forEach { day ->
                row++
                day.messages.forEach { put(it.id, row++) }
            }
        }
    }
    fun closeSearch() {
        searching = false
        query = ""
        currentId = null
    }
    BackHandler(enabled = searching) { closeSearch() }

    // A search result opened this: once its messages are in, open the search at that message.
    LaunchedEffect(find, messages) {
        val wanted = find ?: return@LaunchedEffect
        if (messages.none { it.id == wanted.messageId }) return@LaunchedEffect
        searching = true
        query = wanted.query
        currentId = wanted.messageId
        onFindShown()
    }
    // Typing moves to the newest match; a match that went away gives way to the newest left.
    LaunchedEffect(matches) {
        if (currentId !in matches) currentId = matches.lastOrNull()
    }
    LaunchedEffect(currentId, rows) {
        val row = currentId?.let { rows[it] } ?: return@LaunchedEffect
        // A little down from the top, so the day heading over it shows too.
        val offset = -listState.layoutInfo.viewportSize.height / 4
        runCatching { listState.animateScrollToItem(row, offset) }
    }

    LaunchedEffect(messages.size, conversation.threadId) {
        // Searching, or about to be: the search decides where the list is.
        if (messages.isNotEmpty() && !searching && find == null) {
            // To the very end of the last message, however tall; and once more when its pictures
            // have loaded and made it taller, unless somebody has scrolled away meanwhile.
            runCatching { listState.scrollToItem(messages.size + days.size, Int.MAX_VALUE) }
            kotlinx.coroutines.delay(PICTURES_SETTLE_MILLIS)
            if (!listState.isScrollInProgress && listState.canScrollForward) {
                runCatching { listState.animateScrollToItem(messages.size + days.size, Int.MAX_VALUE) }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().onGloballyPositioned { screenOrigin = it.positionInRoot() }) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (wide) PaddingValues(bottom = 12.dp) else PaddingValues(16.dp))
    ) {
        // ── Who this is with ──
        if (wide) {
            WideConversationHeader(
                conversation = conversation,
                onCall = onCall,
                onClose = onClose,
                searching = searching,
                onSearch = { if (searching) closeSearch() else searching = true }
            )
        } else Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title.lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 32.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (conversation.isGroup) {
                        stringResource(R.string.msg_group_conversation)
                    } else {
                        conversation.address
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.textMuted
                )
            }

            IconButton(onClick = { if (searching) closeSearch() else searching = true }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.msg_find_in_conversation),
                    tint = if (searching) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground
                )
            }
            if (conversation.address.isNotBlank()) {
                IconButton(onClick = onCall) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = stringResource(R.string.msg_call),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_close_cap),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (searching) {
            ConversationSearchBar(
                query = query,
                onQueryChange = { query = it },
                position = if (currentIndex >= 0) currentIndex + 1 else 0,
                total = matches.size,
                // Up is further back in the conversation, down nearer now.
                onOlder = { if (currentIndex > 0) currentId = matches[currentIndex - 1] },
                onNewer = { if (currentIndex in 0 until matches.lastIndex) currentId = matches[currentIndex + 1] },
                onClose = { closeSearch() },
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (isBlocked) {
            Text(
                text = stringResource(R.string.msg_blocked_banner),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── The messages ──
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.msg_no_messages),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textMuted
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).onGloballyPositioned { listBounds = it.boundsInRoot() },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                days.forEach { day ->
                    item(key = "day-${day.dayStart}") {
                        DayHeading(now = now, dayStart = day.dayStart)
                    }
                    items(
                        count = day.messages.size,
                        key = { index -> day.messages[index].id }
                    ) { index ->
                        MessageBubble(
                            message = day.messages[index],
                            // In a group, who wrote it is half the message.
                            showSender = conversation.isGroup,
                            onLongClick = { onMessageLongPress(day.messages[index]) },
                            maxWidth = if (wide) 460.dp else 320.dp,
                            onOpenAttachment = onOpenAttachment,
                            marked = if (searching) query else "",
                            isCurrentMatch = searching && day.messages[index].id == currentId,
                            hidden = flight != null && day.messages[index].id == landing?.id,
                            onPlaced = if (day.messages[index].id == landing?.id) ({ landingBounds = it }) else null,
                            onDownload = { onDownloadMms(day.messages[index]) },
                            downloading = (day.messages[index].id - com.serkantkn.zunelauncher.data.repository.SmsRepository.MMS_ID_OFFSET) in downloadingMms
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Which SIM, when there is a choice ──
        if (sims.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.msg_sim_choose),
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.textMuted
                )
                Spacer(modifier = Modifier.width(8.dp))
                sims.forEach { sim ->
                    val chosen = selectedSim == sim.subscriptionId
                    Text(
                        text = sim.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (chosen) Color.White else zuneColors.textMuted,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .background(
                                if (chosen) zuneColors.accentColor else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                            .clickable { onSelectSim(if (chosen) -1 else sim.subscriptionId) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // ── How much of a message this is ──
        if (count.segments > 1 || count.isUnicode) {
            Text(
                text = if (count.segments > 1) {
                    stringResource(R.string.msg_segments_note, count.segments, count.remaining)
                } else {
                    stringResource(R.string.msg_unicode_note)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (count.segments > 1) zuneColors.accentColor else zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // ── What goes with it ──
        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments = attachments, onRemove = onRemoveAttachment, modifier = Modifier.padding(bottom = 8.dp))
        }

        // ── Writing ──
        val canSend = input.isNotBlank() || attachments.isNotEmpty()
        if (wide) {
            MetroComposer(
                input = input,
                onInputChange = onInputChange,
                onSend = send,
                canSend = canSend,
                onAttach = onAttach,
                onFieldPlaced = { fieldBounds = it }
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttach) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = stringResource(R.string.msg_attach),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(stringResource(R.string.msg_type_hint), color = zuneColors.textMuted)
                    },
                    modifier = Modifier.weight(1f).onGloballyPositioned { fieldBounds = it.boundsInRoot() },
                    shape = RoundedCornerShape(2.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = zuneColors.accentColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = send,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (canSend) zuneColors.accentColor else zuneColors.textDim,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.msg_send_cap),
                        tint = Color.White
                    )
                }
            }
        }
    }

    // ── The flying message, over everything ──
    flight?.let { current ->
        SendFlightBubble(
            flight = current,
            origin = screenOrigin,
            maxWidth = if (wide) 460.dp else 320.dp,
            // Where the message will land: its own bubble once the list has it, until then the
            // foot of the list, at the right where what we send goes.
            target = { landingBounds ?: listBounds?.let { list -> androidx.compose.ui.geometry.Rect(list.right - 1f, list.bottom - 1f, list.right, list.bottom) } },
            onLanded = { flight = null }
        )
    }
    }
}

@Composable
private fun DayHeading(now: Long, dayStart: Long) {
    val heading = remember(now, dayStart) { dayHeading(now, dayStart, Locale.getDefault()) }
    val text = when (heading.kind) {
        StampKind.TIME -> stringResource(R.string.common_today)
        StampKind.YESTERDAY -> stringResource(R.string.common_yesterday)
        else -> heading.text
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalZuneColors.current.textDim,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: SmsMessageModel,
    showSender: Boolean,
    onLongClick: () -> Unit,
    maxWidth: androidx.compose.ui.unit.Dp = 320.dp,
    onOpenAttachment: (MmsAttachment) -> Unit = {},
    /** Words being searched for, marked wherever they are in the message. */
    marked: String = "",
    /** The match the search is on: framed, so it stands out from the others. */
    isCurrentMatch: Boolean = false,
    /** Kept out of sight while the copy flying from the box has not arrived yet. */
    hidden: Boolean = false,
    /** Told where it is, for the flight to land on. */
    onPlaced: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onDownload: () -> Unit = {},
    downloading: Boolean = false
) {
    val zuneColors = LocalZuneColors.current
    val isOut = message.isOutgoing
    val bubbleColor = when {
        message.isFailed -> MaterialTheme.colorScheme.error
        isOut -> zuneColors.accentColor
        zuneColors.isDark -> Color(0xFF222222)
        else -> Color(0xFFE5E5E5)
    }
    val onBubble = if (isOut || message.isFailed || zuneColors.isDark) Color.White else Color.Black

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isOut) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier
                .graphicsLayer { alpha = if (hidden) 0f else 1f }
                .then(if (onPlaced != null) Modifier.onGloballyPositioned { onPlaced(it.boundsInRoot()) } else Modifier)
                .widthIn(max = maxWidth)
                .then(if (isCurrentMatch) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp)) else Modifier)
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val sender = message.senderName.ifBlank { message.address }
                if (showSender && !isOut && sender.isNotBlank()) {
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = zuneColors.accentColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                if (message.body.isNotBlank()) {
                    Text(
                        text = remember(message.body, marked) { markMatches(message.body, marked) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = onBubble
                    )
                }
                // What came with it: the pictures themselves, and a line for anything else.
                message.attachments.forEachIndexed { index, attachment ->
                    Box(modifier = Modifier.padding(top = if (index == 0 && message.body.isBlank()) 0.dp else 6.dp)) {
                        MessageAttachmentView(attachment = attachment, ink = onBubble, onOpen = onOpenAttachment)
                    }
                }
                if (message.mmsPending) {
                    PendingMms(message = message, ink = onBubble, downloading = downloading, onDownload = onDownload)
                }
                if (message.hasAttachment && message.attachments.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_attachment),
                        style = MaterialTheme.typography.labelSmall,
                        color = onBubble.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = if (message.body.isBlank()) 0.dp else 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = remember(message.timestamp) {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = onBubble.copy(alpha = 0.6f)
                    )
                    DeliveryMark(message.delivery, onBubble)
                }
            }
        }
    }
}

/** The one-glyph answer to "did it get there?", drawn only for messages we sent. */
@Composable
private fun DeliveryMark(delivery: MessageDelivery, tint: Color) {
    val icon = when (delivery) {
        MessageDelivery.SENDING -> Icons.Default.Schedule
        MessageDelivery.SENT -> Icons.Default.Done
        MessageDelivery.DELIVERED -> Icons.Default.DoneAll
        MessageDelivery.READ -> Icons.Default.Visibility
        MessageDelivery.FAILED -> Icons.Default.ErrorOutline
        MessageDelivery.NONE -> null
    } ?: return

    val description = when (delivery) {
        MessageDelivery.SENDING -> stringResource(R.string.msg_sending)
        MessageDelivery.SENT -> stringResource(R.string.msg_sent)
        MessageDelivery.DELIVERED -> stringResource(R.string.msg_delivered)
        MessageDelivery.READ -> stringResource(R.string.msg_read_receipt)
        MessageDelivery.FAILED -> stringResource(R.string.msg_failed)
        MessageDelivery.NONE -> ""
    }

    Spacer(modifier = Modifier.width(4.dp))
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tint.copy(alpha = 0.75f),
        modifier = Modifier.size(12.dp)
    )
}

// ── On a tablet ─────────────────────────────────────────────────────────────

/**
 * Who the conversation is with, as a tablet's pane heads itself: their face as a square, their
 * name in large light type, and calling and closing as ringed buttons rather than bare glyphs.
 */
@Composable
private fun WideConversationHeader(
    conversation: SmsConversationModel,
    onCall: () -> Unit,
    onClose: () -> Unit,
    searching: Boolean,
    onSearch: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        MessageAvatar(name = conversation.title, photoUri = conversation.photoUri, size = 56.dp, group = conversation.isGroup)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title.lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 36.sp,
                    letterSpacing = (-1).sp
                ),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (conversation.isGroup) {
                    stringResource(R.string.msg_group_conversation)
                } else {
                    conversation.address
                },
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RingButton(Icons.Default.Search, stringResource(R.string.msg_find_in_conversation), enabled = true, onClick = onSearch)
        Spacer(modifier = Modifier.width(14.dp))
        if (conversation.address.isNotBlank()) {
            RingButton(Icons.Default.Call, stringResource(R.string.msg_call), enabled = true, onClick = onCall)
            Spacer(modifier = Modifier.width(14.dp))
        }
        RingButton(Icons.Default.Close, stringResource(R.string.common_close_cap), enabled = true, onClick = onClose)
    }
}

/**
 * The box to write in, as Windows drew a text box: square, a grey field at rest, and white with
 * the accent round it while it is being typed into. Sending is the ringed button beside it.
 */
@Composable
private fun MetroComposer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    onAttach: () -> Unit,
    onFieldPlaced: (androidx.compose.ui.geometry.Rect) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textColour = if (focused) Color.Black else ink
    Row(
        modifier = Modifier.fillMaxWidth().imePadding(),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.padding(bottom = 4.dp)) {
            RingButton(Icons.Default.AttachFile, stringResource(R.string.msg_attach), enabled = true, size = 40.dp, onClick = onAttach)
        }
        Spacer(modifier = Modifier.width(14.dp))
        // The whole box is the field, so a tap anywhere in it - not only on the line of text -
        // puts the cursor there.
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColour),
            cursorBrush = SolidColor(zuneColors.accentColor),
            maxLines = 6,
            interactionSource = interaction,
            modifier = Modifier.weight(1f).onGloballyPositioned { onFieldPlaced(it.boundsInRoot()) },
            decorationBox = { field ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(if (focused) Color.White else ink.copy(alpha = 0.07f))
                        .border(2.dp, if (focused) zuneColors.accentColor else ink.copy(alpha = 0.3f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(R.string.msg_type_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (focused) Color.Black.copy(alpha = 0.45f) else zuneColors.textMuted
                        )
                    }
                    field()
                }
            }
        )
        Spacer(modifier = Modifier.width(14.dp))
        Box(modifier = Modifier.padding(bottom = 4.dp)) {
            RingButton(
                Icons.AutoMirrored.Filled.Send,
                stringResource(R.string.msg_send_cap),
                enabled = canSend,
                size = 40.dp,
                onClick = onSend
            )
        }
    }
}

/** A glyph in a thin ring, the Windows 8 button; faded and deaf while there is nothing to do. */
@Composable
internal fun RingButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.9f else 0.3f)
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .wpTilt(source)
            .border(1.5.dp, ink, CircleShape)
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = ink, modifier = Modifier.size(size * 0.55f))
    }
}

/** The words being searched for, marked as a highlighter marks them: dark type on yellow. */
internal fun markMatches(text: String, query: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        if (query.isBlank()) return@buildAnnotatedString
        var at = text.indexOf(query, ignoreCase = true)
        while (at >= 0) {
            addStyle(androidx.compose.ui.text.SpanStyle(background = MATCH_MARK, color = Color.Black), at, at + query.length)
            at = text.indexOf(query, at + query.length, ignoreCase = true)
        }
    }

private val MATCH_MARK = Color(0xFFFFD54F)

/**
 * The search over an open conversation: the words, how far through the matches it is, and up and
 * down through them - up further back in the conversation, down nearer now.
 */
@Composable
private fun ConversationSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    position: Int,
    total: Int,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .border(2.dp, zuneColors.accentColor)
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.msg_find_in_conversation), color = zuneColors.textMuted, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
                cursorBrush = SolidColor(zuneColors.accentColor),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onOlder() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        Text(
            text = when {
                query.isBlank() -> ""
                total == 0 -> stringResource(R.string.msg_find_none)
                else -> stringResource(R.string.msg_find_count, position, total)
            },
            style = MaterialTheme.typography.labelLarge,
            color = zuneColors.textMuted,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        IconButton(onClick = onOlder, enabled = position > 1) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.msg_find_older), tint = ink.copy(alpha = if (position > 1) 1f else 0.3f))
        }
        IconButton(onClick = onNewer, enabled = position in 1 until total) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.msg_find_newer), tint = ink.copy(alpha = if (position in 1 until total) 1f else 0.3f))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_cap), tint = ink)
        }
    }
}

/** A message on its way from the box to the list: its words, where they were, and when. */
internal class SendFlight(val text: String, val from: androidx.compose.ui.geometry.Rect, val startedAt: Long)

/**
 * The words that were in the box, turning into a bubble as they go to where the message lands:
 * the accent fills in behind them, the type takes the bubble's size and colour, and the whole
 * glides down - and, for a short message, over to the right - to the bubble waiting for it.
 */
@Composable
private fun SendFlightBubble(
    flight: SendFlight,
    origin: androidx.compose.ui.geometry.Offset,
    maxWidth: androidx.compose.ui.unit.Dp,
    target: () -> androidx.compose.ui.geometry.Rect?,
    onLanded: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    val progress = remember(flight) { androidx.compose.animation.core.Animatable(0f) }
    val animate = com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled.current
    LaunchedEffect(flight) {
        if (animate) {
            // Off at once and slowing as it lands, as Windows Phone moved things into place.
            progress.animateTo(1f, androidx.compose.animation.core.tween(FLIGHT_MILLIS, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        }
        onLanded()
    }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // It starts on the screen's own ground, so it covers the box's hint that shows as it empties.
    val ground = MaterialTheme.colorScheme.background
    Surface(
        color = androidx.compose.ui.graphics.lerp(ground, zuneColors.accentColor, progress.value.coerceIn(0f, 1f)),
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier
            .widthIn(max = maxWidth)
            .onGloballyPositioned { size = it.size }
            .graphicsLayer {
                val p = progress.value
                // It leaves with its words where they were in the box: the box keeps them about
                // 16dp in from its left edge and halfway down; the bubble about 12dp in and 22dp down.
                val start = androidx.compose.ui.geometry.Offset(
                    flight.from.left + 4.dp.toPx(),
                    flight.from.center.y - 22.dp.toPx()
                ) - origin
                // The bubble's own top-left at the end: right-aligned on where the message lands.
                val end = target()?.let { landed ->
                    androidx.compose.ui.geometry.Offset(landed.right - size.width, landed.bottom - size.height) - origin
                } ?: start
                translationX = start.x + (end.x - start.x) * p
                translationY = start.y + (end.y - start.y) * p
                // Words lift off the box a touch larger than they land.
                val scale = 1f + 0.06f * (1f - p)
                scaleX = scale; scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
    ) {
        Text(
            text = flight.text,
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.lerp(ink, Color.White, progress.value),
            modifier = Modifier.padding(12.dp).padding(bottom = with(density) { 14.sp.toDp() })
        )
    }
}

private const val FLIGHT_MILLIS = 420

/** Long enough for a message's pictures to have loaded and taken their height. */
private const val PICTURES_SETTLE_MILLIS = 600L

/** The pictures and clips chosen for the next message, each with a cross to take it off. */
@Composable
private fun AttachmentStrip(attachments: List<android.net.Uri>, onRemove: (android.net.Uri) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments.size, key = { attachments[it].toString() }) { index ->
            val uri = attachments[index]
            Box(modifier = Modifier.size(84.dp)) {
                coil.compose.AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f))
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(26.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { onRemove(uri) }
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.msg_remove_attachment), tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A picture message that is only announced so far: how big, and a way to fetch it. */
@Composable
private fun PendingMms(message: SmsMessageModel, ink: Color, downloading: Boolean, onDownload: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Image, contentDescription = null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.msg_mms_waiting) + if (message.mmsSize > 0) " · " + com.serkantkn.zunelauncher.util.formatFileSize(message.mmsSize) else "",
            style = MaterialTheme.typography.bodyMedium,
            color = ink
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (downloading) {
            Text(stringResource(R.string.msg_mms_downloading), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f))
        } else {
            Text(
                text = stringResource(R.string.msg_mms_download),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
                modifier = Modifier
                    .border(1.dp, ink.copy(alpha = 0.6f))
                    .clickable(onClick = onDownload)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
