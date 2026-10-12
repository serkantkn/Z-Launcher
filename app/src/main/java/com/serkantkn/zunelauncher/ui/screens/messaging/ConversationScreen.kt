package com.serkantkn.zunelauncher.ui.screens.messaging

import androidx.compose.foundation.ExperimentalFoundationApi
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
    onOpenAttachment: (MmsAttachment) -> Unit = {}
) {
    val zuneColors = LocalZuneColors.current
    // A tablet shows the conversation beside the list, under the hub's own headings: its header
    // and the box to write in are drawn the Windows 8 way there, the rest is the same.
    val wide = LocalIsWideScreen.current
    val listState = rememberLazyListState()
    val days = remember(messages) { groupMessagesByDay(messages) }
    val count = remember(input) { messageCount(input) }

    // A conversation behind another app is not being read; a message arriving then is announced.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { onVisibilityChange(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onVisibilityChange(true) }

    LaunchedEffect(messages.size, conversation.threadId) {
        if (messages.isNotEmpty()) {
            runCatching { listState.scrollToItem(messages.size + days.size) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(if (wide) PaddingValues(bottom = 12.dp) else PaddingValues(16.dp))
    ) {
        // ── Who this is with ──
        if (wide) {
            WideConversationHeader(conversation = conversation, onCall = onCall, onClose = onClose)
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
                modifier = Modifier.weight(1f),
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
                            onOpenAttachment = onOpenAttachment
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

        // ── Writing ──
        if (wide) {
            MetroComposer(input = input, onInputChange = onInputChange, onSend = onSend)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(stringResource(R.string.msg_type_hint), color = zuneColors.textMuted)
                    },
                    modifier = Modifier.weight(1f),
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
                    onClick = onSend,
                    enabled = input.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (input.isNotBlank()) zuneColors.accentColor else zuneColors.textDim,
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
    onOpenAttachment: (MmsAttachment) -> Unit = {}
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
                .widthIn(max = maxWidth)
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
                        text = message.body,
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
        MessageDelivery.FAILED -> Icons.Default.ErrorOutline
        MessageDelivery.NONE -> null
    } ?: return

    val description = when (delivery) {
        MessageDelivery.SENDING -> stringResource(R.string.msg_sending)
        MessageDelivery.SENT -> stringResource(R.string.msg_sent)
        MessageDelivery.DELIVERED -> stringResource(R.string.msg_delivered)
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
private fun WideConversationHeader(conversation: SmsConversationModel, onCall: () -> Unit, onClose: () -> Unit) {
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
private fun MetroComposer(input: String, onInputChange: (String) -> Unit, onSend: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val ink = MaterialTheme.colorScheme.onBackground
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textColour = if (focused) Color.Black else ink
    Row(
        modifier = Modifier.fillMaxWidth().imePadding(),
        verticalAlignment = Alignment.Bottom
    ) {
        // The whole box is the field, so a tap anywhere in it - not only on the line of text -
        // puts the cursor there.
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColour),
            cursorBrush = SolidColor(zuneColors.accentColor),
            maxLines = 6,
            interactionSource = interaction,
            modifier = Modifier.weight(1f),
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
                enabled = input.isNotBlank(),
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
