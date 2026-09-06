package com.serkantkn.zunelauncher.ui.screens.email

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.data.model.EmailFolder
import com.serkantkn.zunelauncher.data.model.EmailFolderType
import com.serkantkn.zunelauncher.data.model.EmailMessage
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.screens.notes.WpCheckBox
import com.serkantkn.zunelauncher.ui.screens.notes.WpRadioRow
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ════════════════════════════════════════════════════════════
// FORMATTING
// ════════════════════════════════════════════════════════════

/** "14:05" today, "Sal" this week, "12 Ağu" this year, "12.08.24" otherwise — the WP list date. */
internal fun formatListDate(millis: Long): String {
    if (millis <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val locale = Locale.getDefault()
    return when {
        sameDay -> SimpleDateFormat("HH:mm", locale).format(Date(millis))
        now.timeInMillis - millis < 6L * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEE", locale).format(Date(millis))
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> SimpleDateFormat("d MMM", locale).format(Date(millis))
        else -> SimpleDateFormat("dd.MM.yy", locale).format(Date(millis))
    }
}

internal fun formatFullDate(millis: Long): String =
    if (millis <= 0L) "" else SimpleDateFormat("d MMMM yyyy, EEEE HH:mm", Locale.getDefault()).format(Date(millis))

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Folder display name: localized for special folders, the IMAP name for the rest. */
@Composable
internal fun folderTitle(folder: EmailFolder?): String {
    if (folder == null) return stringResource(R.string.email_folder_inbox)
    return if (folder.type == EmailFolderType.OTHER) folder.name.lowercase() else stringResource(folder.type.titleRes)
}

// ════════════════════════════════════════════════════════════
// LIST ROW
// ════════════════════════════════════════════════════════════

/**
 * One message in the list, Windows Phone Mail style: sender in light 20sp, subject in accent
 * when unread, preview line muted, date on the right. Long press starts selection mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EmailMessageRow(
    message: EmailMessage,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showAccountBadge: Boolean,
    accountName: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val unread = !message.isRead
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (isSelectionMode) {
            WpCheckBox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 14.dp, top = 6.dp),
                size = 20.dp
            )
        } else {
            // Unread marker: a thin accent bar on the left edge (WP live tile language)
            Box(
                modifier = Modifier
                    .padding(end = 12.dp, top = 8.dp)
                    .width(4.dp)
                    .height(40.dp)
                    .background(if (unread) zuneColors.accentColor else Color.Transparent)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.from.display.ifBlank { message.from.address },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = if (unread) FontWeight.Normal else FontWeight.Light, fontSize = 20.sp),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatListDate(message.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) zuneColors.accentColor else zuneColors.textMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (message.isAnswered) {
                    Icon(Icons.Default.Reply, contentDescription = null, tint = zuneColors.textMuted, modifier = Modifier.padding(end = 4.dp).size(14.dp))
                }
                Text(
                    text = message.subject.ifBlank { stringResource(R.string.email_no_subject) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp),
                    color = if (unread) zuneColors.accentColor else textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (message.hasAttachments) {
                    Icon(Icons.Default.AttachFile, contentDescription = stringResource(R.string.email_attachment), tint = zuneColors.textMuted, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                }
                if (message.isFlagged) {
                    Icon(Icons.Default.Flag, contentDescription = stringResource(R.string.email_flagged), tint = zuneColors.accentColor, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                }
            }
            val preview = message.snippet.ifBlank { if (message.hasBody) "" else "" }
            if (preview.isNotBlank() || showAccountBadge) {
                Text(
                    text = buildString {
                        if (showAccountBadge && accountName != null) append(accountName).append(" · ")
                        append(preview)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light, fontSize = 13.sp),
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// INPUT
// ════════════════════════════════════════════════════════════

/** Windows Phone style labelled text box (muted caption + 1.5dp bordered field). */
@Composable
internal fun EmailTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val borderColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.5.dp, borderColor.copy(alpha = if (enabled) borderColor.alpha else 0.3f)), RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = zuneColors.textDim, style = MaterialTheme.typography.bodyLarge)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    cursorBrush = SolidColor(zuneColors.accentColor),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            trailing?.invoke()
        }
    }
}

// ════════════════════════════════════════════════════════════
// HTML BODY
// ════════════════════════════════════════════════════════════

/**
 * Renders an HTML mail body in a WebView that sizes itself to its content (the parent column
 * scrolls). JavaScript is off, remote images are blocked until [showImages], links open in the
 * default browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EmailHtmlBody(html: String, showImages: Boolean, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val css = remember(zuneColors.isDark, zuneColors.accentColor) {
        val fg = "#%06X".format(0xFFFFFF and textColor.toArgb())
        val accent = "#%06X".format(0xFFFFFF and zuneColors.accentColor.toArgb())
        """<meta name="viewport" content="width=device-width, initial-scale=1"><style>
           html,body{background:transparent!important;color:$fg;font-family:sans-serif;font-size:15px;line-height:1.45;margin:0;padding:0;word-wrap:break-word;overflow-wrap:anywhere}
           a{color:$accent} img{max-width:100%!important;height:auto!important} table{max-width:100%!important} blockquote{border-left:2px solid $accent;margin:8px 0;padding-left:10px;color:$fg;opacity:.85}
           pre{white-space:pre-wrap}
           </style>"""
    }
    val page = remember(html, css) {
        val safe = html.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
        if (safe.contains("<head", ignoreCase = true)) safe.replaceFirst(Regex("(?i)<head[^>]*>"), "$0$css") else "<html><head>$css</head><body>$safe</body></html>"
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                settings.blockNetworkImage = !showImages
                settings.loadsImagesAutomatically = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url ?: return true
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        return true
                    }
                }
                tag = page
                loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        },
        update = { view ->
            val imagesChanged = view.settings.blockNetworkImage == showImages
            view.settings.blockNetworkImage = !showImages
            if (view.tag != page || imagesChanged) {
                view.tag = page
                view.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
            }
        }
    )
}

// ════════════════════════════════════════════════════════════
// DIALOGS
// ════════════════════════════════════════════════════════════

@Composable
internal fun EmailConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            ZuneDialogButton(text = confirmText, onClick = { dismissWithAnim { onConfirm() } }, borderColor = zuneColors.accentColor)
        },
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_cancel_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
    }
}

/** Picks a mailbox: every account plus "all inboxes" when there is more than one. */
@Composable
internal fun EmailAccountPickerDialog(
    accounts: List<EmailAccount>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.email_accounts_cap),
        confirmButton = {
            ZuneDialogButton(text = stringResource(R.string.email_add_account_cap), onClick = { dismissWithAnim { onAddAccount() } }, borderColor = zuneColors.accentColor)
        },
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_close_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Column {
            if (accounts.size > 1) {
                WpRadioRow(
                    text = stringResource(R.string.email_all_inboxes),
                    selected = selectedId == UNIFIED_ACCOUNT_ID,
                    onClick = { dismissWithAnim { onSelect(UNIFIED_ACCOUNT_ID) } }
                )
            }
            accounts.forEach { account ->
                WpRadioRow(
                    text = "${account.shortName} · ${account.email}",
                    selected = selectedId == account.id,
                    onClick = { dismissWithAnim { onSelect(account.id) } }
                )
            }
        }
    }
}

/** Picks a target folder for "taşı". */
@Composable
internal fun EmailFolderPickerDialog(
    folders: List<EmailFolder>,
    currentFolder: String,
    onSelect: (EmailFolder) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.email_move_to_cap),
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_cancel_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Column {
            folders.filter { it.fullName != currentFolder }.forEach { folder ->
                WpRadioRow(
                    text = folderTitle(folder),
                    selected = false,
                    onClick = { dismissWithAnim { onSelect(folder) } }
                )
            }
        }
    }
}

/** Single-line text prompt (signature, display name). */
@Composable
internal fun EmailTextPromptDialog(
    title: String,
    initial: String,
    placeholder: String,
    singleLine: Boolean = true,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var value by rememberSaveable { mutableStateOf(initial) }
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            ZuneDialogButton(text = stringResource(R.string.common_save_cap), onClick = { dismissWithAnim { onSave(value) } }, borderColor = zuneColors.accentColor)
        },
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.common_cancel_cap), onClick = { dismissWithAnim { onDismiss() } }, borderColor = zuneColors.textMuted)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)), RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Small accent "chip" used for attachments and suggestions. */
@Composable
internal fun EmailChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .border(BorderStroke(1.dp, zuneColors.textMuted.copy(alpha = 0.6f)), RoundedCornerShape(0.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        trailing?.invoke()
    }
}
