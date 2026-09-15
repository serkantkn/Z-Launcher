package com.serkantkn.zunelauncher.ui.screens.messaging

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import java.util.Locale

/**
 * The pieces the Messaging hub is built from: a conversation as it appears in the list, the
 * person's face beside it, the search box, and the small sheets that a long press brings up.
 */

// ── Search ──────────────────────────────────────────────────────────────────

@Composable
internal fun MessageSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    val zuneColors = LocalZuneColors.current
    val bgColor = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val textColor = MaterialTheme.colorScheme.onBackground
    val hintColor = zuneColors.textDim
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(100)
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(bgColor, RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = hint ?: stringResource(R.string.msg_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = hintColor
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                    singleLine = true,
                    cursorBrush = SolidColor(zuneColors.accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}

// ── A conversation in the list ──────────────────────────────────────────────

/** The word a [ConversationStamp] stands for, in the reader's own language. */
@Composable
internal fun stampText(stamp: ConversationStamp): String = when (stamp.kind) {
    StampKind.YESTERDAY -> stringResource(R.string.common_yesterday)
    else -> stamp.text
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationItem(
    conversation: SmsConversationModel,
    now: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val unread = conversation.unreadCount > 0 || !conversation.isRead
    val stamp = remember(now, conversation.timestamp) {
        conversationStamp(now, conversation.timestamp, Locale.getDefault())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MessageAvatar(
            name = conversation.title,
            photoUri = conversation.photoUri,
            seed = conversation.threadId.toString(),
            unread = unread
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stampText(stamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) zuneColors.accentColor else zuneColors.textMuted,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.hasDraft) {
                    Text(
                        text = stringResource(R.string.msg_draft_label) + ": ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.accentColor
                    )
                }
                Text(
                    text = if (conversation.hasDraft) conversation.draft else conversation.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) MaterialTheme.colorScheme.onBackground else zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (conversation.unreadCount > 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(zuneColors.accentColor, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * A person's face beside their conversation, or their initial when there is no picture.
 *
 * The colour is picked from the conversation rather than at random, so the same person keeps the
 * same square every time the list is drawn.
 */
@Composable
internal fun MessageAvatar(
    name: String,
    photoUri: String?,
    seed: String,
    unread: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 44.dp
) {
    val zuneColors = LocalZuneColors.current
    val fallbackColor = remember(seed) {
        val colors = listOf(ZuneColors.Pink, ZuneColors.Orange, ZuneColors.Blue, ZuneColors.Green)
        colors[kotlin.math.abs(seed.hashCode()) % colors.size]
    }
    val shape = if (unread) RoundedCornerShape(2.dp) else CircleShape

    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(fallbackColor, shape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(if (unread) zuneColors.accentColor else fallbackColor, shape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}

// ── Sheets ──────────────────────────────────────────────────────────────────

/** One line in a sheet: a thing that can be done, and nothing else. */
@Composable
internal fun SheetAction(label: String, color: Color? = null, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = color ?: MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

/** The sheet a long press brings up, in the Windows Phone way: a list, from the bottom. */
@Composable
internal fun MessagingSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.background)
                // A sheet with a field in it has the keyboard come up underneath it; without
                // this the sheet's own buttons sit behind the keys and cannot be pressed.
                .imePadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            content()
        }
    }
}

/** What is known about one message, and nothing to decide: one way out, not two. */
@Composable
internal fun MessageDetailsDialog(title: String, lines: List<String>, onDismiss: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_close_cap),
                onClick = { dismissWithAnim { onDismiss() } },
                borderColor = zuneColors.accentColor
            )
        }
    ) {
        Column {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

// ── Standing notices ────────────────────────────────────────────────────────

@Composable
internal fun MessagePermissionCard(onGrant: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = if (zuneColors.isDark) Color(0xFF181818) else Color(0xFFF2F2F2),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.msg_permission_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.msg_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = zuneColors.accentColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text(stringResource(R.string.msg_permission_button))
            }
        }
    }
}

/**
 * The standing note that the launcher is not the phone's messaging app.
 *
 * Reading works either way; marking read, deleting and keeping drafts do not, and saying so once
 * at the top is kinder than letting each of those fail in turn.
 */
@Composable
internal fun DefaultAppNotice(onMakeDefault: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(zuneColors.accentColor.copy(alpha = 0.14f))
            .clickable(onClick = onMakeDefault)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.msg_default_app_prompt),
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.textMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.msg_make_default),
            style = MaterialTheme.typography.labelLarge,
            color = zuneColors.accentColor
        )
    }
}
