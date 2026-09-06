package com.serkantkn.zunelauncher.ui.screens.email

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ReplyAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAttachment
import com.serkantkn.zunelauncher.data.model.EmailFolderType
import com.serkantkn.zunelauncher.data.model.EmailMessage
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.ZuneLog
import java.io.File

/**
 * Reading pane: breadcrumb header, subject, sender/recipients, body (HTML or text),
 * attachment chips and the reply / reply all / forward / delete bottom bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmailReadScreen(
    message: EmailMessage,
    isLoading: Boolean,
    viewModel: EmailHubViewModel,
    onClose: () -> Unit,
    onReply: (replyAll: Boolean) -> Unit,
    onForward: () -> Unit,
    onMove: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    var showImages by remember(message.key) { mutableStateOf(false) }
    var showDetails by remember(message.key) { mutableStateOf(false) }
    val hasRemoteImages = remember(message.bodyHtml) { message.bodyHtml?.contains(Regex("(?i)<img[^>]+src=\"?https?://")) == true }
    val folders by viewModel.folders.collectAsState()
    val isTrash = folders.firstOrNull { it.fullName == message.folder }?.type == EmailFolderType.TRASH

    fun openAttachment(attachment: EmailAttachment) {
        viewModel.downloadAttachment(message, attachment) { file -> openFile(context, file, attachment.mimeType) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Breadcrumb header
            Row(
                modifier = Modifier.padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 56.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hub_email),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 1.sp),
                    color = headerColor,
                    modifier = Modifier.clickable { onClose() }
                )
                Text(
                    text = "  >  " + folderTitle(folders.firstOrNull { it.fullName == message.folder }),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp, letterSpacing = 1.sp),
                    color = zuneColors.textMuted
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message.subject.ifBlank { stringResource(R.string.email_no_subject) },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp),
                    color = textColor
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.clickable { showDetails = !showDetails }) {
                    Text(
                        text = message.from.display,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = zuneColors.accentColor
                    )
                    if (message.from.name.isNotBlank()) {
                        Text(text = message.from.address, style = MaterialTheme.typography.bodySmall, color = zuneColors.textMuted)
                    }
                    Text(text = formatFullDate(message.date), style = MaterialTheme.typography.bodySmall, color = zuneColors.textMuted)
                    if (showDetails) {
                        Spacer(modifier = Modifier.height(4.dp))
                        AddressLine(stringResource(R.string.email_to_label), message.to.joinToString(", ") { it.toString() })
                        if (message.cc.isNotEmpty()) AddressLine(stringResource(R.string.email_cc_label), message.cc.joinToString(", ") { it.toString() })
                        if (message.size > 0) AddressLine(stringResource(R.string.email_size_label), formatSize(message.size))
                    } else if (message.to.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.email_to_label) + ": " + message.to.joinToString(", ") { it.display },
                            style = MaterialTheme.typography.bodySmall,
                            color = zuneColors.textMuted,
                            maxLines = 1
                        )
                    }
                }

                // Accent rule under the header, like the note editor
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(zuneColors.accentColor.copy(alpha = 0.6f)))
                Spacer(modifier = Modifier.height(14.dp))

                val visibleAttachments = message.attachments
                if (visibleAttachments.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleAttachments.forEach { attachment ->
                            EmailChip(
                                text = "${attachment.name} · ${formatSize(attachment.size)}",
                                onClick = { openAttachment(attachment) },
                                trailing = { Icon(Icons.Default.AttachFile, contentDescription = null, tint = zuneColors.textMuted, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                when {
                    isLoading && !message.hasBody -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = zuneColors.accentColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.email_loading_message), color = zuneColors.textMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    !message.bodyHtml.isNullOrBlank() -> {
                        if (hasRemoteImages && !showImages) {
                            Text(
                                text = stringResource(R.string.email_show_images),
                                style = MaterialTheme.typography.labelLarge,
                                color = zuneColors.accentColor,
                                modifier = Modifier.clickable { showImages = true }.padding(bottom = 10.dp)
                            )
                        }
                        EmailHtmlBody(html = message.bodyHtml, showImages = showImages)
                    }
                    else -> {
                        SelectionContainer {
                            Text(
                                text = message.bodyText ?: message.snippet,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light, fontSize = 15.sp, lineHeight = 22.sp),
                                color = textColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        WindowsPhoneBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
            actions = listOf(
                WpBarAction(Icons.Default.Reply, stringResource(R.string.email_reply)) { onReply(false) },
                WpBarAction(Icons.Default.ReplyAll, stringResource(R.string.email_reply_all)) { onReply(true) },
                WpBarAction(Icons.Default.Forward, stringResource(R.string.email_forward)) { onForward() },
                WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) { viewModel.delete(setOf(message.key)) }
            ),
            menuItems = buildList {
                add(WpBarMenuItem(stringResource(if (message.isFlagged) R.string.email_unflag else R.string.email_flag)) { viewModel.setFlagged(setOf(message.key), !message.isFlagged) })
                add(WpBarMenuItem(stringResource(R.string.email_mark_unread)) { viewModel.setRead(setOf(message.key), false); onClose() })
                add(WpBarMenuItem(stringResource(R.string.email_move)) { onMove() })
                if (!isTrash) {
                    add(WpBarMenuItem(stringResource(R.string.email_archive)) { viewModel.archive(setOf(message.key)) })
                    add(WpBarMenuItem(stringResource(R.string.email_mark_spam)) { viewModel.markSpam(setOf(message.key)) })
                }
            }
        )
    }
}

@Composable
private fun AddressLine(label: String, value: String) {
    val zuneColors = LocalZuneColors.current
    Row {
        Text(text = "$label: ", style = MaterialTheme.typography.bodySmall, color = zuneColors.textMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

private fun openFile(context: android.content.Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        ZuneLog.e("EmailReadScreen", "openFile failed", e)
    }
}
