package com.serkantkn.zunelauncher.ui.screens.email

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAddress
import com.serkantkn.zunelauncher.data.model.EmailDraft
import com.serkantkn.zunelauncher.data.model.EmailDraftKind
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * Composer: to / cc / bcc (cc+bcc collapsed until requested), subject, body, attachment chips,
 * quoted original for replies and the send / attach / save / discard bottom bar. Recipient
 * suggestions come from addresses seen in cached mail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmailComposeScreen(
    draft: EmailDraft,
    viewModel: EmailHubViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    val isSending by viewModel.isSending.collectAsState()
    val knownAddresses by viewModel.knownAddresses.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val account = accounts.firstOrNull { it.id == draft.accountId }
    var showCcBcc by remember(draft.id) { mutableStateOf(draft.cc.isNotBlank() || draft.bcc.isNotBlank()) }
    var activeField by remember { mutableStateOf("to") }
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun change(updated: EmailDraft) = viewModel.updateDraft(updated)

    val pickAttachments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            change(draft.copy(attachmentUris = (draft.attachmentUris + uris.map { it.toString() }).distinct()))
        }
    }

    // Suggestions for the token being typed in the active address field
    val activeValue = when (activeField) { "cc" -> draft.cc; "bcc" -> draft.bcc; else -> draft.to }
    val currentToken = activeValue.substringAfterLast(',').trim()
    val suggestions = remember(currentToken, knownAddresses, activeValue) {
        val present = EmailAddress.parseList(activeValue).map { it.address.lowercase() }.toSet()
        if (currentToken.length < 2 || currentToken.lowercase() in present) emptyList()
        else knownAddresses.filter { it.address.lowercase() !in present && (it.address.contains(currentToken, true) || it.name.contains(currentToken, true)) }.take(5)
    }

    fun acceptSuggestion(address: EmailAddress) {
        val prefix = activeValue.substringBeforeLast(',', "").let { if (it.isBlank()) "" else "$it, " }
        val newValue = prefix + address.toString() + ", "
        change(when (activeField) { "cc" -> draft.copy(cc = newValue); "bcc" -> draft.copy(bcc = newValue); else -> draft.copy(to = newValue) })
    }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    text = "  >  " + stringResource(
                        when (draft.kind) {
                            EmailDraftKind.NEW -> R.string.email_compose_new
                            EmailDraftKind.REPLY, EmailDraftKind.REPLY_ALL -> R.string.email_compose_reply
                            EmailDraftKind.FORWARD -> R.string.email_compose_forward
                        }
                    ),
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
                Spacer(modifier = Modifier.height(8.dp))
                if (account != null) {
                    Text(
                        text = stringResource(R.string.email_from_label).lowercase() + ": " + account.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = zuneColors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                EmailTextField(
                    label = stringResource(R.string.email_to_label).lowercase(),
                    value = draft.to,
                    onValueChange = { activeField = "to"; change(draft.copy(to = it)) },
                    placeholder = stringResource(R.string.email_recipient_hint),
                    keyboardType = KeyboardType.Email,
                    trailing = if (!showCcBcc) {
                        {
                            Text(
                                text = stringResource(R.string.email_cc_bcc),
                                style = MaterialTheme.typography.labelMedium,
                                color = zuneColors.accentColor,
                                modifier = Modifier.clickable { showCcBcc = true }.padding(start = 8.dp)
                            )
                        }
                    } else null
                )
                if (activeField == "to") SuggestionRow(suggestions) { acceptSuggestion(it) }
                if (showCcBcc) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EmailTextField(label = stringResource(R.string.email_cc_label).lowercase(), value = draft.cc, onValueChange = { activeField = "cc"; change(draft.copy(cc = it)) }, keyboardType = KeyboardType.Email)
                    if (activeField == "cc") SuggestionRow(suggestions) { acceptSuggestion(it) }
                    Spacer(modifier = Modifier.height(8.dp))
                    EmailTextField(label = stringResource(R.string.email_bcc_label).lowercase(), value = draft.bcc, onValueChange = { activeField = "bcc"; change(draft.copy(bcc = it)) }, keyboardType = KeyboardType.Email)
                    if (activeField == "bcc") SuggestionRow(suggestions) { acceptSuggestion(it) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                EmailTextField(
                    label = stringResource(R.string.email_subject_label).lowercase(),
                    value = draft.subject,
                    onValueChange = { activeField = "subject"; change(draft.copy(subject = it)) },
                    placeholder = stringResource(R.string.email_subject_hint)
                )

                if (draft.attachmentUris.isNotEmpty() || draft.forwardedAttachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.attachmentUris.forEach { uriString ->
                            val name = remember(uriString) { displayName(context, uriString) }
                            EmailChip(
                                text = name,
                                onClick = { change(draft.copy(attachmentUris = draft.attachmentUris - uriString)) },
                                trailing = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove), tint = zuneColors.textMuted, modifier = Modifier.size(14.dp)) }
                            )
                        }
                        draft.forwardedAttachments.forEach { att ->
                            EmailChip(
                                text = "${att.name} · ${formatSize(att.size)}",
                                onClick = { change(draft.copy(forwardedAttachments = draft.forwardedAttachments - att)) },
                                trailing = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_remove), tint = zuneColors.textMuted, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
                    if (draft.body.isEmpty()) {
                        Text(stringResource(R.string.email_body_hint), color = zuneColors.textDim, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp))
                    }
                    BasicTextField(
                        value = draft.body,
                        onValueChange = { activeField = "body"; change(draft.copy(body = it)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Light),
                        cursorBrush = SolidColor(zuneColors.accentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                    )
                }
                if (account != null && account.signature.isNotBlank()) {
                    Text(text = account.signature, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light), color = zuneColors.textMuted, modifier = Modifier.padding(top = 8.dp))
                }
                if (draft.quotedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, zuneColors.textMuted.copy(alpha = 0.35f)), RoundedCornerShape(0.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = draft.quotedText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light, lineHeight = 18.sp),
                            color = zuneColors.textMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        if (isSending) {
            Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = zuneColors.accentColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(stringResource(R.string.email_sending), color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        WindowsPhoneBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
            actions = listOf(
                WpBarAction(Icons.Default.Send, stringResource(R.string.send)) { if (!isSending) viewModel.send { ok -> if (ok) onClose() } },
                WpBarAction(Icons.Default.AttachFile, stringResource(R.string.email_attach)) { pickAttachments.launch(arrayOf("*/*")) },
                WpBarAction(Icons.Default.Save, stringResource(R.string.common_save)) { viewModel.saveDraftAndClose(); onClose() },
                WpBarAction(Icons.Default.Close, stringResource(R.string.email_discard)) { if (draft.isBlank) { viewModel.discardDraft(); onClose() } else showDiscardDialog = true }
            ),
            menuItems = buildList {
                if (!showCcBcc) add(WpBarMenuItem(stringResource(R.string.email_show_cc_bcc)) { showCcBcc = true })
            }
        )
    }

    if (showDiscardDialog) {
        EmailConfirmDialog(
            title = stringResource(R.string.email_discard_cap),
            message = stringResource(R.string.email_discard_message),
            confirmText = stringResource(R.string.email_discard_cap),
            onConfirm = { showDiscardDialog = false; viewModel.discardDraft(); onClose() },
            onDismiss = { showDiscardDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(suggestions: List<EmailAddress>, onPick: (EmailAddress) -> Unit) {
    if (suggestions.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        suggestions.forEach { address -> EmailChip(text = address.toString(), onClick = { onPick(address) }) }
    }
}

private fun displayName(context: android.content.Context, uriString: String): String {
    val uri = android.net.Uri.parse(uriString)
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: uri.lastPathSegment ?: "file"
        }
    }
    return uri.lastPathSegment ?: "file"
}
