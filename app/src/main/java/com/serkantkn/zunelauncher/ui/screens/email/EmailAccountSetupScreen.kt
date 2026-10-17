package com.serkantkn.zunelauncher.ui.screens.email

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.data.model.EmailAddress
import com.serkantkn.zunelauncher.data.model.EmailAuthType
import com.serkantkn.zunelauncher.data.model.EmailProviderPreset
import com.serkantkn.zunelauncher.data.model.EmailProviders
import com.serkantkn.zunelauncher.data.model.EmailSecurity
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.screens.settings.SettingPill
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * "hesap ekle": provider list first (Windows Phone "add an account" style), then the
 * credentials form. Advanced IMAP/SMTP fields are shown for custom accounts or on request.
 * Editing an existing account skips the provider step.
 */
@Composable
internal fun EmailAccountSetupScreen(
    existing: EmailAccount?,
    viewModel: EmailHubViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val textColor = MaterialTheme.colorScheme.onBackground
    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    var provider by remember { mutableStateOf<EmailProviderPreset?>(existing?.let { EmailProviders.forAddress(it.email) }) }
    var email by rememberSaveable { mutableStateOf(existing?.email ?: "") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf(existing?.displayName ?: "") }
    var username by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var imapHost by rememberSaveable { mutableStateOf(existing?.imapHost ?: "") }
    var imapPort by rememberSaveable { mutableStateOf(existing?.imapPort?.toString() ?: "993") }
    var imapSecurity by remember { mutableStateOf(existing?.imapSecurity ?: EmailSecurity.SSL) }
    var smtpHost by rememberSaveable { mutableStateOf(existing?.smtpHost ?: "") }
    var smtpPort by rememberSaveable { mutableStateOf(existing?.smtpPort?.toString() ?: "587") }
    var smtpSecurity by remember { mutableStateOf(existing?.smtpSecurity ?: EmailSecurity.STARTTLS) }
    var showAdvanced by rememberSaveable { mutableStateOf(existing != null && EmailProviders.forAddress(existing.email) == EmailProviders.CUSTOM) }
    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Signed in with Google once, an option no longer offered: it asks for an app password now.
    val wasGoogle = existing?.isGoogleOAuth == true
    val context = LocalContext.current

    fun applyPreset(preset: EmailProviderPreset) {
        provider = preset
        if (preset != EmailProviders.CUSTOM) {
            imapHost = preset.imapHost; imapPort = preset.imapPort.toString(); imapSecurity = preset.imapSecurity
            smtpHost = preset.smtpHost; smtpPort = preset.smtpPort.toString(); smtpSecurity = preset.smtpSecurity
        } else {
            showAdvanced = true
        }
    }

    val requiredMissing = stringResource(R.string.email_setup_fill_required)
    val badAddress = stringResource(R.string.email_setup_bad_address)

    fun submit() {
        val address = email.trim()
        if (address.isBlank() || (password.isBlank() && (existing == null || wasGoogle))) { error = requiredMissing; return }
        if (!EmailAddress.isValid(address)) { error = badAddress; return }
        val guessed = if (imapHost.isBlank() || smtpHost.isBlank()) EmailProviders.forAddress(address) else null
        val account = EmailAccount(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            displayName = displayName.trim(),
            email = address,
            imapHost = imapHost.trim().ifBlank { guessed?.imapHost ?: "" },
            imapPort = imapPort.trim().toIntOrNull() ?: 993,
            imapSecurity = imapSecurity,
            smtpHost = smtpHost.trim().ifBlank { guessed?.smtpHost ?: "" },
            smtpPort = smtpPort.trim().toIntOrNull() ?: 587,
            smtpSecurity = smtpSecurity,
            username = username.trim().ifBlank { address },
            authType = EmailAuthType.PASSWORD,
            signature = existing?.signature ?: "",
            syncMinutes = existing?.syncMinutes ?: 15,
            notificationsEnabled = existing?.notificationsEnabled ?: true,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        if (account.imapHost.isBlank() || account.smtpHost.isBlank()) { error = requiredMissing; showAdvanced = true; return }
        isBusy = true; error = null
        viewModel.addOrUpdateAccount(account, password.takeIf { it.isNotBlank() }) { result ->
            isBusy = false
            if (result == null) onClose() else error = result
        }
    }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            EmailScreenHeader(section = stringResource(if (existing == null) R.string.email_add_account else R.string.email_edit_account), onClose = { onClose() })

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                if (provider == null) {
                    // ── Step 1: provider list ────────────────────────
                    Text(
                        text = stringResource(R.string.email_setup_pick_provider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    EmailProviders.all.forEach { preset ->
                        Text(
                            text = if (preset == EmailProviders.CUSTOM) stringResource(R.string.email_setup_other_account) else preset.name.lowercase(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
                            color = textColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyPreset(preset) }
                                .padding(vertical = 10.dp)
                        )
                    }
                } else {
                    // ── Step 2: credentials ──────────────────────────
                    Text(
                        text = (provider?.name ?: "").lowercase(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
                        color = textColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    EmailTextField(
                        label = stringResource(R.string.email_setup_address),
                        value = email,
                        onValueChange = { email = it; if (provider == EmailProviders.CUSTOM && username.isBlank()) Unit },
                        placeholder = "ad@ornek.com",
                        keyboardType = KeyboardType.Email,
                        enabled = existing == null
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (wasGoogle) {
                        Text(
                            text = stringResource(R.string.email_google_moved_to_password),
                            style = MaterialTheme.typography.bodyMedium,
                            color = zuneColors.accentColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    run {
                        EmailTextField(
                            label = stringResource(if (existing == null) R.string.email_setup_password else R.string.email_setup_new_password),
                            value = password,
                            onValueChange = { password = it },
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            imeAction = ImeAction.Done,
                            onDone = { submit() }
                        )
                        provider?.hintRes?.let { hint ->
                            Text(
                                text = stringResource(hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = zuneColors.accentColor,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        // Gmail asks for an app password; this is the page that makes one.
                        if (provider == EmailProviders.GMAIL) {
                            Text(
                                text = stringResource(R.string.email_gmail_make_app_password),
                                style = MaterialTheme.typography.bodyMedium,
                                color = zuneColors.accentLink,
                                modifier = Modifier
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(GMAIL_APP_PASSWORDS)
                                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    EmailTextField(
                        label = stringResource(R.string.email_setup_display_name),
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = stringResource(R.string.email_setup_display_name_hint)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(if (showAdvanced) R.string.email_setup_hide_advanced else R.string.email_setup_show_advanced),
                        style = MaterialTheme.typography.labelLarge,
                        color = zuneColors.accentColor,
                        modifier = Modifier.clickable { showAdvanced = !showAdvanced }
                    )
                    if (showAdvanced) {
                        Spacer(modifier = Modifier.height(12.dp))
                        EmailTextField(label = stringResource(R.string.email_setup_username), value = username, onValueChange = { username = it }, placeholder = email)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(stringResource(R.string.email_setup_incoming), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light), color = textColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        ServerFields(imapHost, { imapHost = it }, imapPort, { imapPort = it }, imapSecurity, { imapSecurity = it })
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(stringResource(R.string.email_setup_outgoing), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light), color = textColor)
                        Spacer(modifier = Modifier.height(8.dp))
                        ServerFields(smtpHost, { smtpHost = it }, smtpPort, { smtpPort = it }, smtpSecurity, { smtpSecurity = it })
                    }

                    error?.let {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = zuneColors.accentColor)
                    }
                    if (isBusy) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = zuneColors.accentColor, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.email_setup_checking), color = zuneColors.textMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        WindowsPhoneBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
            actions = buildList {
                if (provider != null) add(WpBarAction(Icons.Default.Check, stringResource(R.string.email_setup_sign_in)) { if (!isBusy) submit() })
                add(WpBarAction(Icons.Default.Close, stringResource(R.string.common_cancel)) { if (provider != null && existing == null) { provider = null; error = null } else onClose() })
            }
        )
    }
}

@Composable
private fun ServerFields(
    host: String, onHost: (String) -> Unit,
    port: String, onPort: (String) -> Unit,
    security: EmailSecurity, onSecurity: (EmailSecurity) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EmailTextField(label = stringResource(R.string.email_setup_server), value = host, onValueChange = onHost, modifier = Modifier.weight(0.7f), keyboardType = KeyboardType.Uri)
        EmailTextField(label = stringResource(R.string.email_setup_port), value = port, onValueChange = { onPort(it.filter { c -> c.isDigit() }.take(5)) }, modifier = Modifier.weight(0.3f), keyboardType = KeyboardType.Number)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EmailSecurity.entries.forEach { option ->
            SettingPill(label = stringResource(option.titleRes), selected = security == option, onClick = { onSecurity(option) })
        }
    }
}

/** Google's own page for making an app password. */
private const val GMAIL_APP_PASSWORDS = "https://myaccount.google.com/apppasswords"
