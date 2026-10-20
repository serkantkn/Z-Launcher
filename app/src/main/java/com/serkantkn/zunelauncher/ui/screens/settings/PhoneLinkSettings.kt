package com.serkantkn.zunelauncher.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.link.FoundTv
import com.serkantkn.zunelauncher.data.link.LinkPeer
import com.serkantkn.zunelauncher.data.link.LinkProtocol
import com.serkantkn.zunelauncher.data.link.LinkStore
import com.serkantkn.zunelauncher.data.link.PhoneLink
import com.serkantkn.zunelauncher.data.link.TvLink
import com.serkantkn.zunelauncher.data.service.WpToastOverlay
import com.serkantkn.zunelauncher.ui.components.MetroTextField
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── The TV's side ─────────────────────────────────────────────────────────────────────────

/** On a TV: whether paired phones' notices show, adding a phone by a code, and the phones added. */
@Composable
internal fun TvLinkGroup() {
    val context = LocalContext.current
    LinkStore.attach(context)
    val show by LinkStore.showNotices.collectAsState()
    val phones by LinkStore.peers.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LinkPeer?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val inWindow = remember(tick) { WpToastOverlay.isAvailable(context) }

    SettingGroup(title = stringResource(R.string.tv_link_group)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingSwitchRow(
                title = stringResource(R.string.tv_link_show),
                subtitle = stringResource(R.string.tv_link_show_sub),
                checked = show,
                onCheckedChange = LinkStore::setShowNotices
            )
            SystemSettingRow(
                title = stringResource(R.string.tv_link_add),
                subtitle = stringResource(R.string.tv_link_add_sub),
                onClick = {
                    TvLink.startPairing(context)
                    adding = true
                }
            )
            phones.forEach { phone ->
                SystemSettingRow(
                    title = phone.name,
                    subtitle = stringResource(R.string.tv_link_phone_sub),
                    isActive = true,
                    onClick = { removing = phone }
                )
            }
            if (!inWindow) {
                Text(
                    text = stringResource(R.string.tv_link_needs_window),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZuneColors.current.textDim,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
        }
    }

    if (adding) TvPairingDialog(onClose = {
        TvLink.stopPairing()
        adding = false
    })
    removing?.let { phone ->
        RemoveDialog(
            title = stringResource(R.string.tv_link_remove_title, phone.name),
            body = stringResource(R.string.tv_link_remove_body),
            onRemove = { LinkStore.remove(phone.id) },
            onClose = { removing = null }
        )
    }
}

/** The code, large, for the phone to type; and how the pairing went. */
@Composable
private fun TvPairingDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val pairing by TvLink.pairing.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    // Added: the dialog says so and goes.
    LaunchedEffect(pairing?.paired) {
        if (pairing?.paired != null) {
            delay(DONE_SHOWN_MILLIS)
            onClose()
        }
    }
    val p = pairing
    val expired = p != null && p.paired == null && now > p.expiresAt
    ZuneFlipDialog(
        onDismissRequest = onClose,
        title = stringResource(R.string.tv_link_add),
        confirmButton = {
            if (p?.failed == true || expired) {
                ZuneDialogButton(text = stringResource(R.string.tv_link_new_code), onClick = { TvLink.startPairing(context) })
            }
        },
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.tv_link_close), onClick = { dismissWithAnim() })
        }
    ) {
        Column {
            Text(stringResource(R.string.tv_link_pair_body), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = p?.code?.chunked(3)?.joinToString(" ").orEmpty(),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light, fontSize = 56.sp, letterSpacing = 6.sp),
                color = if (p?.failed == true || expired) Color.White.copy(alpha = 0.35f) else Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            p?.let {
                Text(stringResource(R.string.tv_link_pair_name, it.name), style = MaterialTheme.typography.bodyMedium, color = LocalZuneColors.current.textMuted)
                if (it.addresses.isNotEmpty()) {
                    Text(
                        stringResource(R.string.tv_link_pair_address, it.addresses.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalZuneColors.current.textMuted
                    )
                }
            }
            val status = when {
                p?.paired != null -> stringResource(R.string.tv_link_pair_done, p.paired)
                p?.failed == true -> stringResource(R.string.tv_link_pair_failed)
                expired -> stringResource(R.string.tv_link_pair_expired)
                else -> null
            }
            status?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.titleMedium, color = LocalZuneColors.current.accentColor)
            }
        }
    }
}

// ── The phone's side ──────────────────────────────────────────────────────────────────────

/** On a phone: what is told to the TVs, adding a TV, and the TVs added. */
@Composable
internal fun PhoneLinkGroup() {
    val context = LocalContext.current
    LinkStore.attach(context)
    val send by LinkStore.send.collectAsState()
    val tvs by LinkStore.peers.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LinkPeer?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val missing = remember(tick) { LINK_PERMISSIONS.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED } }
    val hasNotificationAccess = remember(tick) { context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) }
    // Not the phone and SMS app: calls and texts are read from those apps' notifications.
    val ownsCallsAndTexts = remember(tick) {
        val telecom = context.getSystemService(android.telecom.TelecomManager::class.java)
        telecom?.defaultDialerPackage == context.packageName &&
            android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
    val ask = rememberPermissionAsk(missing.toTypedArray()) { tick++ }

    SettingGroup(title = stringResource(R.string.phone_link_group)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tvs.isNotEmpty()) {
                SettingSwitchRow(
                    title = stringResource(R.string.phone_link_send),
                    subtitle = stringResource(R.string.phone_link_send_sub),
                    checked = send.enabled,
                    onCheckedChange = { LinkStore.setSend(send.copy(enabled = it)) }
                )
                if (send.enabled) {
                    SettingSwitchRow(stringResource(R.string.phone_link_calls), stringResource(R.string.phone_link_calls_sub), send.calls) {
                        LinkStore.setSend(send.copy(calls = it))
                    }
                    SettingSwitchRow(stringResource(R.string.phone_link_sms), stringResource(R.string.phone_link_sms_sub), send.sms) {
                        LinkStore.setSend(send.copy(sms = it))
                    }
                    SettingSwitchRow(
                        stringResource(R.string.phone_link_apps),
                        stringResource(if (hasNotificationAccess || !send.apps) R.string.phone_link_apps_sub else R.string.phone_link_apps_no_access),
                        send.apps
                    ) {
                        LinkStore.setSend(send.copy(apps = it))
                        if (it && !hasNotificationAccess) {
                            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                    SettingSwitchRow(stringResource(R.string.phone_link_text), stringResource(R.string.phone_link_text_sub), send.text) {
                        LinkStore.setSend(send.copy(text = it))
                    }
                    if (!hasNotificationAccess && !ownsCallsAndTexts) {
                        SystemSettingRow(
                            title = stringResource(R.string.phone_link_notification_access),
                            subtitle = stringResource(R.string.phone_link_notification_access_sub),
                            onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        )
                    }
                    if (missing.isNotEmpty()) {
                        SystemSettingRow(
                            title = stringResource(R.string.settings_conn_permissions),
                            subtitle = stringResource(R.string.phone_link_permissions),
                            onClick = ask
                        )
                    }
                }
            }
            SystemSettingRow(
                title = stringResource(R.string.phone_link_add),
                subtitle = stringResource(R.string.phone_link_add_sub),
                onClick = { adding = true }
            )
            tvs.forEach { tv ->
                SystemSettingRow(
                    title = tv.name,
                    subtitle = stringResource(R.string.phone_link_tv_sub),
                    isActive = true,
                    onClick = { removing = tv }
                )
            }
        }
    }

    if (adding) PhonePairingDialog(onClose = {
        adding = false
        // A first TV: ask for what the link needs to hear calls and messages.
        if (missing.isNotEmpty() && LinkStore.peers.value.isNotEmpty()) ask()
    })
    removing?.let { tv ->
        RemoveDialog(
            title = stringResource(R.string.tv_link_remove_title, tv.name),
            body = stringResource(R.string.phone_link_remove_body),
            onRemove = { LinkStore.remove(tv.id) },
            onClose = { removing = null }
        )
    }
}

/** Finding the TV - from the network, or by its address typed in - and typing its code. */
@Composable
private fun PhonePairingDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val found by remember { PhoneLink.discover(context) }.collectAsState(initial = emptyList())
    var lookedLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(NONE_FOUND_AFTER_MILLIS)
        lookedLong = true
    }
    var chosen by remember { mutableStateOf<FoundTv?>(null) }
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val target: Pair<String, Int>? = when {
        address.isNotBlank() -> address.trim().let { a ->
            val host = a.substringBefore(':')
            val port = a.substringAfter(':', "").toIntOrNull() ?: LinkProtocol.DEFAULT_PORT
            host to port
        }
        chosen != null -> chosen!!.host to chosen!!.port
        found.size == 1 -> found.first().host to found.first().port
        else -> null
    }
    val texts = PairTexts(
        paired = stringResource(R.string.phone_link_paired, "%s"),
        wrong = stringResource(R.string.phone_link_wrong_code),
        notPairing = stringResource(R.string.phone_link_not_pairing),
        unreachable = stringResource(R.string.phone_link_unreachable),
        pairing = stringResource(R.string.phone_link_pairing)
    )

    ZuneFlipDialog(
        onDismissRequest = onClose,
        title = stringResource(R.string.phone_link_add),
        confirmButton = {
            ZuneDialogButton(text = stringResource(R.string.phone_link_pair), onClick = pair@{
                val (host, port) = target ?: return@pair
                if (busy || code.trim().length != 6) return@pair
                busy = true
                status = texts.pairing
                scope.launch {
                    val result = PhoneLink.pair(context, host, port, code)
                    busy = false
                    status = when (result) {
                        is PhoneLink.PairResult.Paired -> texts.paired.replace("%s", result.tv)
                        PhoneLink.PairResult.WrongCode -> texts.wrong
                        PhoneLink.PairResult.NotPairing -> texts.notPairing
                        PhoneLink.PairResult.Unreachable -> texts.unreachable
                    }
                    if (result is PhoneLink.PairResult.Paired) {
                        delay(DONE_SHOWN_MILLIS)
                        dismissWithAnim()
                    }
                }
            })
        },
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.tv_link_cancel), onClick = { dismissWithAnim() })
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when {
                found.isNotEmpty() -> found.forEach { tv ->
                    SettingChoiceRow(
                        title = tv.name,
                        subtitle = tv.host,
                        selected = address.isBlank() && (chosen?.id == tv.id || (chosen == null && found.size == 1)),
                        onClick = {
                            chosen = tv
                            address = ""
                        }
                    )
                }
                lookedLong -> Text(stringResource(R.string.phone_link_none), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                else -> Text(stringResource(R.string.phone_link_searching), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            MetroTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = stringResource(R.string.phone_link_address),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                textColor = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            MetroTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                placeholder = stringResource(R.string.phone_link_code),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textColor = Color.White
            )
            status?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalZuneColors.current.accentColor)
            }
        }
    }
}

private class PairTexts(val paired: String, val wrong: String, val notPairing: String, val unreachable: String, val pairing: String)

@Composable
private fun RemoveDialog(title: String, body: String, onRemove: () -> Unit, onClose: () -> Unit) {
    ZuneFlipDialog(
        onDismissRequest = onClose,
        title = title,
        confirmButton = {
            ZuneDialogButton(text = stringResource(R.string.tv_link_remove), onClick = { dismissWithAnim { onRemove() } })
        },
        dismissButton = {
            ZuneDialogButton(text = stringResource(R.string.tv_link_cancel), onClick = { dismissWithAnim() })
        }
    ) {
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

/**
 * What the phone needs to know a call is ringing, answered or missed, and to name who a number
 * belongs to. Not the call log or SMS: Google Play allows those only to the default phone and SMS
 * apps, and the launcher has them only when it is one - through that role, asked for elsewhere.
 */
private val LINK_PERMISSIONS = listOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_CONTACTS
)

private const val DONE_SHOWN_MILLIS = 1_400L
private const val NONE_FOUND_AFTER_MILLIS = 6_000L
