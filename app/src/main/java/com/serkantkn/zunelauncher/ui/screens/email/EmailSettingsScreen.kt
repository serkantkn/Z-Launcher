package com.serkantkn.zunelauncher.ui.screens.email

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.screens.settings.SettingChoiceRow
import com.serkantkn.zunelauncher.ui.screens.settings.SettingGroup
import com.serkantkn.zunelauncher.ui.screens.settings.SettingSwitchRow
import com.serkantkn.zunelauncher.ui.screens.settings.SystemSettingRow
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

private val SYNC_OPTIONS = listOf(5, 15, 30, 60, 0)

/**
 * Email settings: per-account sync interval, notifications, signature, credentials and
 * removal, plus the system notification permission. Uses the settings hub components.
 */
@Composable
internal fun EmailSettingsScreen(
    accounts: List<EmailAccount>,
    viewModel: EmailHubViewModel,
    onClose: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (EmailAccount) -> Unit,
    modifier: Modifier = Modifier,
    bottomBarModifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    var signatureFor by remember { mutableStateOf<EmailAccount?>(null) }
    var removeFor by remember { mutableStateOf<EmailAccount?>(null) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val notificationsGranted = remember(permissionTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }

    Box(modifier = modifier.fillMaxSize()) {
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
                    text = "  >  " + stringResource(R.string.common_settings),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp, letterSpacing = 1.sp),
                    color = zuneColors.textMuted
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(modifier = Modifier.height(0.dp))
                SettingGroup(title = stringResource(R.string.email_notifications_group)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SystemSettingRow(
                            title = stringResource(R.string.email_system_notifications),
                            subtitle = stringResource(if (notificationsGranted) R.string.settings_permission_granted else R.string.email_system_notifications_sub),
                            isActive = notificationsGranted,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        )
                    }
                }

                accounts.forEach { account ->
                    SettingGroup(title = "${account.shortName} · ${account.email}") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingSwitchRow(
                                title = stringResource(R.string.email_new_mail_notifications),
                                subtitle = stringResource(R.string.email_new_mail_notifications_sub),
                                checked = account.notificationsEnabled,
                                onCheckedChange = { viewModel.updateAccountSettings(account.copy(notificationsEnabled = it)) }
                            )
                            Text(
                                text = stringResource(R.string.email_sync_interval),
                                style = MaterialTheme.typography.labelMedium,
                                color = zuneColors.textMuted,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            SYNC_OPTIONS.forEach { minutes ->
                                SettingChoiceRow(
                                    title = if (minutes == 0) stringResource(R.string.email_sync_manual) else stringResource(R.string.email_sync_every_minutes, minutes),
                                    subtitle = "",
                                    selected = account.syncMinutes == minutes,
                                    onClick = { viewModel.updateAccountSettings(account.copy(syncMinutes = minutes)) }
                                )
                            }
                            SystemSettingRow(
                                title = stringResource(R.string.email_signature),
                                subtitle = account.signature.ifBlank { stringResource(R.string.email_signature_none) },
                                onClick = { signatureFor = account }
                            )
                            SystemSettingRow(
                                title = stringResource(R.string.email_edit_account),
                                subtitle = stringResource(R.string.email_edit_account_sub),
                                onClick = { onEditAccount(account) }
                            )
                            SystemSettingRow(
                                title = stringResource(R.string.email_remove_account),
                                subtitle = stringResource(R.string.email_remove_account_sub),
                                onClick = { removeFor = account }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        WindowsPhoneBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
            actions = listOf(
                WpBarAction(Icons.Default.Add, stringResource(R.string.email_add_account)) { onAddAccount() },
                WpBarAction(Icons.Default.Close, stringResource(R.string.common_close)) { onClose() }
            )
        )
    }

    signatureFor?.let { account ->
        EmailTextPromptDialog(
            title = stringResource(R.string.email_signature_cap),
            initial = account.signature,
            placeholder = stringResource(R.string.email_signature_hint),
            singleLine = false,
            onSave = { viewModel.updateAccountSettings(account.copy(signature = it.trim())); signatureFor = null },
            onDismiss = { signatureFor = null }
        )
    }
    removeFor?.let { account ->
        EmailConfirmDialog(
            title = stringResource(R.string.email_remove_account_cap),
            message = stringResource(R.string.email_remove_account_message, account.email),
            confirmText = stringResource(R.string.common_remove_cap),
            onConfirm = { viewModel.removeAccount(account.id); removeFor = null },
            onDismiss = { removeFor = null }
        )
    }
}
