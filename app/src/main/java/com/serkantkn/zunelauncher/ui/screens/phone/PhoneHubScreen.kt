package com.serkantkn.zunelauncher.ui.screens.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.SpeedDialEntry
import android.widget.Toast
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.BlockedNumber
import com.serkantkn.zunelauncher.util.CallGroup
import com.serkantkn.zunelauncher.util.PhoneCaller
import com.serkantkn.zunelauncher.util.PhoneNumbers
import com.serkantkn.zunelauncher.util.SimLine
import com.serkantkn.zunelauncher.util.Voicemail
import com.serkantkn.zunelauncher.util.isMissedType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen

/**
 * The phone hub: what has been called, who is worth keeping one press away, and the keypad.
 *
 * Every call placed from here is a real call. While the launcher is the phone's own dialler it
 * goes through Telecom and comes back to the launcher's call screen; while it is not, it is handed
 * to the dialler the phone does trust, and the banner at the top says so.
 */
@Composable
fun PhoneHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhoneViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current

    val callGroups by viewModel.callGroups.collectAsState()
    val missedOnly by viewModel.missedOnly.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val dialedNumber by viewModel.dialedNumber.collectAsState()
    val matchingContacts by viewModel.matchingContacts.collectAsState()
    val speedDial by viewModel.speedDial.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isDialerOpen by remember { mutableStateOf(false) }
    var isSpeedDialPickerOpen by remember { mutableStateOf(false) }
    var isBlockedListOpen by remember { mutableStateOf(false) }
    var isSimPreferenceOpen by remember { mutableStateOf(false) }
    /** A call waiting on the question "from which SIM?", on a phone where that is asked. */
    var callAwaitingSim by remember { mutableStateOf<PendingCall?>(null) }

    val simLines by viewModel.simLines.collectAsState()
    val preferredSim by viewModel.preferredSim.collectAsState()
    val blockedKeys by viewModel.blockedKeys.collectAsState()
    val blockedNumbers by viewModel.blockedNumbers.collectAsState()
    val canBlock by viewModel.canBlock.collectAsState()
    val hasVoicemail by viewModel.hasVoicemail.collectAsState()
    val voicemailWaiting by viewModel.voicemailWaiting.collectAsState()

    // The role can be granted and taken away outside the launcher, so it is read afresh each visit.
    var isDefaultDialer by remember { mutableStateOf(PhoneCaller.isDefaultDialer(context)) }
    val dialerRoleRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { isDefaultDialer = PhoneCaller.isDefaultDialer(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // The history needs these two. Knowing about SIMs and voicemail is worth asking for at the
        // same time, but a refusal there must not take the history away with it.
        hasPermissions = permissions[Manifest.permission.READ_CALL_LOG] == true &&
            permissions[Manifest.permission.READ_CONTACTS] == true
        if (hasPermissions) {
            viewModel.loadRecentCalls()
            viewModel.loadContacts()
        }
    }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.loadRecentCalls()
            viewModel.loadContacts()
        }
    }

    // The dialler role, the SIMs and the blocked list can all change outside the launcher, so
    // they are read afresh rather than remembered from last time.
    LaunchedEffect(isDefaultDialer, hasPermissions) {
        viewModel.loadSimLines()
        viewModel.loadVoicemail()
        viewModel.loadBlocked()
    }

    val pager = rememberLoopingPagerState(pageCount = 2)
    val page = pager.pagerState.currentPage % 2

    BackHandler {
        when {
            callAwaitingSim != null -> callAwaitingSim = null
            isSimPreferenceOpen -> isSimPreferenceOpen = false
            isBlockedListOpen -> isBlockedListOpen = false
            isSpeedDialPickerOpen -> isSpeedDialPickerOpen = false
            isDialerOpen -> {
                isDialerOpen = false
                viewModel.clearDialedNumber()
            }

            else -> onBack()
        }
    }

    fun placeCall(number: String, name: String, line: SimLine?) {
        PhoneCaller.call(context = context, number = number, name = name, account = line?.handle)
    }

    /**
     * On a phone with one SIM this is simply a call. On a phone with two it is a call from a
     * particular number, which is a question worth asking once and then remembering.
     */
    fun call(number: String, name: String = "") {
        val chosen = simLines.firstOrNull { it.key == preferredSim }
        when {
            simLines.size < 2 -> placeCall(number, name, null)
            chosen != null -> placeCall(number, name, chosen)
            else -> callAwaitingSim = PendingCall(number, name)
        }
    }

    fun callVoicemail() {
        if (!Voicemail.call(context)) {
            Toast.makeText(context, R.string.phone_voicemail_none, Toast.LENGTH_LONG).show()
        }
    }

    val isWideScreen = LocalIsWideScreen.current

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
            val tabTitles = listOf(
                stringResource(R.string.common_history),
                stringResource(R.string.phone_speed_dial)
            )
            Column(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    // On a tablet the pivot is laid out flat: both pages side by side, under the
                    // hub's own giant word, like every other wide hub.
                    ZuneWideHubTitle(text = stringResource(R.string.hub_phone))
                } else {
                    Text(
                        text = stringResource(R.string.hub_phone),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            letterSpacing = 1.sp
                        ),
                        color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(
                            top = 28.dp,
                            bottom = 4.dp,
                            start = ZuneDimens.ScreenPaddingHorizontal,
                            end = ZuneDimens.ScreenPaddingHorizontal
                        )
                    )

                    ZunePivotTabs(
                        tabs = tabTitles,
                        state = pager,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (!isDefaultDialer) {
                    DefaultDialerBanner(
                        onMakeDefault = {
                            PhoneCaller.defaultDialerRequest(context)?.let { dialerRoleRequest.launch(it) }
                        }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val renderPage: @Composable (Int) -> Unit = { index ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (index % 2) {
                                0 -> HistoryPage(
                                    hasPermission = hasPermissions,
                                    groups = callGroups,
                                    missedOnly = missedOnly,
                                    errorMessage = errorMessage,
                                    blockedKeys = blockedKeys,
                                    canBlock = canBlock,
                                    hasVoicemail = hasVoicemail,
                                    voicemailWaiting = voicemailWaiting,
                                    onRequestPermission = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALL_LOG,
                                                Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.READ_PHONE_STATE
                                            )
                                        )
                                    },
                                    onCall = { group -> call(group.number, group.name.orEmpty()) },
                                    onMessage = { group -> openMessaging(context, group.number) },
                                    onPin = { group ->
                                        viewModel.addToSpeedDial(group.displayName, group.number)
                                    },
                                    onToggleBlock = { group ->
                                        viewModel.toggleBlock(group.number) { blocked, worked ->
                                            Toast.makeText(
                                                context,
                                                when {
                                                    !worked -> context.getString(R.string.phone_block_failed)
                                                    blocked -> context.getString(
                                                        R.string.phone_blocked_toast,
                                                        group.displayName
                                                    )

                                                    else -> context.getString(
                                                        R.string.phone_unblocked_toast,
                                                        group.displayName
                                                    )
                                                },
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onVoicemail = { callVoicemail() }
                                )

                                else -> SpeedDialPage(
                                    entries = speedDial,
                                    onCall = { entry -> call(entry.number, entry.name) },
                                    onRemove = { entry -> viewModel.removeFromSpeedDial(entry.number) },
                                    onAdd = { isSpeedDialPickerOpen = true }
                                )
                            }
                        }
                    }

                    if (isWideScreen) {
                        ZuneWidePanorama(tabs = tabTitles, fillPageHeight = true) { index ->
                            renderPage(index)
                        }
                    } else {
                        ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                            Box(modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                                renderPage(index)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
            }

            val bottomBarActions = buildList {
                add(
                    WpBarAction(
                        icon = Icons.Default.Dialpad,
                        label = stringResource(R.string.phone_keypad),
                        onClick = {
                            viewModel.clearDialedNumber()
                            isDialerOpen = true
                        }
                    )
                )
                if (page == 0) {
                    add(
                        WpBarAction(
                            icon = Icons.AutoMirrored.Filled.CallMissed,
                            label = stringResource(
                                if (missedOnly) R.string.phone_filter_all else R.string.phone_filter_missed
                            ),
                            onClick = { viewModel.toggleMissedOnly() }
                        )
                    )
                } else {
                    add(
                        WpBarAction(
                            icon = Icons.Default.PersonAdd,
                            label = stringResource(R.string.phone_speed_dial_add),
                            onClick = { isSpeedDialPickerOpen = true }
                        )
                    )
                }
            }

            // The ellipsis holds what is wanted rarely: the blocked list, and which SIM to call
            // from. Both are left out entirely where the phone cannot do them.
            val bottomBarMenu = buildList {
                if (canBlock) {
                    add(
                        WpBarMenuItem(
                            text = stringResource(R.string.phone_blocked_list),
                            onClick = {
                                viewModel.loadBlocked()
                                isBlockedListOpen = true
                            }
                        )
                    )
                }
                if (simLines.size > 1) {
                    add(
                        WpBarMenuItem(
                            text = stringResource(R.string.phone_sim_menu),
                            onClick = { isSimPreferenceOpen = true }
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(bottomBarModifier),
                contentAlignment = Alignment.Center
            ) {
                WindowsPhoneBottomBar(actions = bottomBarActions, menuItems = bottomBarMenu)
            }

            // ── Keypad ──
            AnimatedVisibility(
                visible = isDialerOpen,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 30) {
                                    isDialerOpen = false
                                    viewModel.clearDialedNumber()
                                }
                            }
                        }
                ) {
                    if (matchingContacts.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                            contentPadding = PaddingValues(
                                horizontal = ZuneDimens.ScreenPaddingHorizontal,
                                vertical = 8.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(matchingContacts, key = { it.first.id + it.second }) { (contact, number) ->
                                MatchingContactItem(
                                    contact = contact,
                                    number = number,
                                    onClick = {
                                        call(number, contact.name)
                                        isDialerOpen = false
                                        viewModel.clearDialedNumber()
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    }

                    Text(
                        text = dialedNumber.ifEmpty { " " },
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 48.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )

                    Box(modifier = Modifier.height(280.dp).padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                        DialerKeypad(
                            onAppend = viewModel::appendDigit,
                            onVoicemail = {
                                isDialerOpen = false
                                viewModel.clearDialedNumber()
                                callVoicemail()
                            },
                            onBackspace = {
                                if (dialedNumber.isNotEmpty()) {
                                    viewModel.backspaceDigit()
                                } else {
                                    isDialerOpen = false
                                    viewModel.clearDialedNumber()
                                }
                            },
                            onCall = {
                                if (dialedNumber.isNotEmpty()) {
                                    call(dialedNumber)
                                    isDialerOpen = false
                                    viewModel.clearDialedNumber()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Which SIM this one call goes out on ──
            callAwaitingSim?.let { pending ->
                SimPicker(
                    lines = simLines,
                    title = stringResource(R.string.phone_sim_choose),
                    currentKey = preferredSim,
                    offerToRemember = true,
                    onPick = { line, remember ->
                        if (remember) viewModel.setPreferredSim(line.key)
                        callAwaitingSim = null
                        placeCall(pending.number, pending.name, line)
                    },
                    onAskEveryTime = null,
                    onDismiss = { callAwaitingSim = null }
                )
            }

            // ── Which SIM every call goes out on, from the menu ──
            if (isSimPreferenceOpen) {
                SimPicker(
                    lines = simLines,
                    title = stringResource(R.string.phone_sim_menu),
                    currentKey = preferredSim,
                    offerToRemember = false,
                    onPick = { line, _ ->
                        viewModel.setPreferredSim(line.key)
                        isSimPreferenceOpen = false
                    },
                    onAskEveryTime = {
                        viewModel.setPreferredSim(null)
                        isSimPreferenceOpen = false
                    },
                    onDismiss = { isSimPreferenceOpen = false }
                )
            }

            // ── The phone's blocked list ──
            if (isBlockedListOpen) {
                BlockedNumbersSheet(
                    numbers = blockedNumbers,
                    onUnblock = { blocked ->
                        viewModel.toggleBlock(blocked.number) { _, worked ->
                            if (!worked) {
                                Toast.makeText(
                                    context,
                                    R.string.phone_block_failed,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDismiss = { isBlockedListOpen = false }
                )
            }

            // ── Picking someone for the speed dial ──
            if (isSpeedDialPickerOpen) {
                SpeedDialPicker(
                    contacts = allContacts,
                    onPick = { contact, number ->
                        viewModel.addToSpeedDial(contact.name, number, contact.photoUri?.toString())
                        isSpeedDialPickerOpen = false
                    },
                    onDismiss = { isSpeedDialPickerOpen = false }
                )
            }
        }
    }
}

/** Opens the messaging hub's conversation with a number, through the phone's own SMS handler. */
private fun openMessaging(context: android.content.Context, number: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        com.serkantkn.zunelauncher.util.ZuneLog.w("PhoneHubScreen", "nothing here sends messages", e)
    }
}

/**
 * The launcher can only show its own call screen while it is the phone's dialler. Until it is,
 * this says so — and says what happens instead, rather than letting calls quietly go elsewhere.
 */
@Composable
private fun DefaultDialerBanner(onMakeDefault: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp)
            .background(zuneColors.accentColor.copy(alpha = 0.18f))
            .clickable(onClick = onMakeDefault)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.phone_default_dialer_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.phone_default_dialer_body),
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.textMuted
        )
    }
}

// ════════════════════════════════════════════════════════════
// HISTORY
// ════════════════════════════════════════════════════════════

@Composable
private fun HistoryPage(
    hasPermission: Boolean,
    groups: List<CallGroup>,
    missedOnly: Boolean,
    errorMessage: String?,
    blockedKeys: Set<String>,
    canBlock: Boolean,
    hasVoicemail: Boolean,
    voicemailWaiting: Int,
    onRequestPermission: () -> Unit,
    onCall: (CallGroup) -> Unit,
    onMessage: (CallGroup) -> Unit,
    onPin: (CallGroup) -> Unit,
    onToggleBlock: (CallGroup) -> Unit,
    onVoicemail: () -> Unit
) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    if (!hasPermission) {
        ZunePermissionRequest(
            title = stringResource(R.string.phone_permission_title),
            message = stringResource(R.string.phone_permission_message),
            buttonLabel = stringResource(R.string.grant_permission),
            onRequest = onRequestPermission
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Voicemail sits above the history, the way the phone has always put it there, and only
        // on a phone that has one.
        if (hasVoicemail && !missedOnly) {
            VoicemailRow(waiting = voicemailWaiting, onClick = onVoicemail)
        }

        if (groups.isEmpty()) {
            Text(
                text = when {
                    errorMessage != null -> stringResource(R.string.phone_history_failed, errorMessage)
                    missedOnly -> stringResource(R.string.phone_no_missed)
                    else -> stringResource(R.string.phone_history_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZuneColors.current.textDim,
                modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                val isBlocked = PhoneNumbers.matchKey(group.number) in blockedKeys
                Column {
                    CallGroupRow(
                        group = group,
                        isBlocked = isBlocked,
                        onClick = { expandedId = if (expandedId == group.id) null else group.id }
                    )
                    RowActions(
                        visible = expandedId == group.id,
                        canBlock = canBlock,
                        isBlocked = isBlocked,
                        onCall = { expandedId = null; onCall(group) },
                        onMessage = { expandedId = null; onMessage(group) },
                        onPin = { expandedId = null; onPin(group) },
                        onToggleBlock = { expandedId = null; onToggleBlock(group) }
                    )
                }
            }
        }
    }
}

/** The voicemail box, and how many messages the carrier says are sitting in it. */
@Composable
private fun VoicemailRow(waiting: Int, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Voicemail,
            contentDescription = null,
            tint = if (waiting > 0) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.phone_voicemail),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (waiting > 0) {
                    stringResource(R.string.phone_voicemail_waiting, waiting)
                } else {
                    stringResource(R.string.phone_voicemail_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (waiting > 0) zuneColors.accentColor else zuneColors.textMuted
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
}

@Composable
private fun CallGroupRow(group: CallGroup, isBlocked: Boolean, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val format = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    val dateString = format.format(Date(group.latestMillis)).lowercase()

    val icon = when {
        isMissedType(group.type) -> Icons.AutoMirrored.Filled.CallMissed
        group.type == CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
        group.type == CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived
        else -> Icons.Default.Call
    }
    val tint = if (group.hasMissed) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Three calls in a row is one thing that happened, so the row says how many.
                if (group.count > 1) {
                    Text(
                        text = " (${group.count})",
                        style = MaterialTheme.typography.titleMedium,
                        color = zuneColors.accentColor
                    )
                }
            }
            val subtitle = if (group.name != null) "$dateString • ${group.number}" else dateString
            Text(
                text = if (isBlocked) {
                    "$subtitle • ${stringResource(R.string.phone_blocked_badge)}"
                } else {
                    subtitle
                },
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * What a history row opens into: the things anyone does with a number. Blocking is only among
 * them while the launcher is the phone app, because Android lets nobody else change that list.
 */
@Composable
private fun RowActions(
    visible: Boolean,
    canBlock: Boolean,
    isBlocked: Boolean,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onPin: () -> Unit,
    onToggleBlock: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val quiet = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(220), expandFrom = Alignment.Top),
        exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, bottom = 6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowAction(stringResource(R.string.call), zuneColors.accentColor, Modifier.weight(1f), onCall)
                RowAction(stringResource(R.string.people_message), quiet, Modifier.weight(1f), onMessage)
                RowAction(stringResource(R.string.phone_speed_dial_add), quiet, Modifier.weight(1f), onPin)
            }
            if (canBlock) {
                RowAction(
                    stringResource(if (isBlocked) R.string.phone_unblock else R.string.phone_block),
                    quiet,
                    Modifier.fillMaxWidth(),
                    onToggleBlock
                )
            }
        }
    }
}

@Composable
private fun RowAction(label: String, background: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ════════════════════════════════════════════════════════════
// SPEED DIAL
// ════════════════════════════════════════════════════════════

@Composable
private fun SpeedDialPage(
    entries: List<SpeedDialEntry>,
    onCall: (SpeedDialEntry) -> Unit,
    onRemove: (SpeedDialEntry) -> Unit,
    onAdd: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    if (entries.isEmpty()) {
        Column(modifier = Modifier.padding(top = ZuneDimens.SpacingLg)) {
            Text(
                text = stringResource(R.string.phone_speed_dial_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textDim
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.phone_speed_dial_add),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(zuneColors.accentColor)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(entries, key = { it.number }) { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCall(entry) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = zuneColors.accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name.ifBlank { entry.number },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = entry.number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted,
                        maxLines = 1
                    )
                }
                Text(
                    text = stringResource(R.string.common_remove),
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.textMuted,
                    modifier = Modifier
                        .clickable { onRemove(entry) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeedDialPicker(
    contacts: List<Pair<ContactModel, String>>,
    onPick: (ContactModel, String) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
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
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.phone_speed_dial_pick),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp)
            )
            if (contacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.phone_speed_dial_no_contacts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(
                        horizontal = ZuneDimens.ScreenPaddingHorizontal,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(contacts, key = { it.first.id + it.second }) { (contact, number) ->
                        MatchingContactItem(
                            contact = contact,
                            number = number,
                            onClick = { onPick(contact, number) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchingContactItem(contact: ContactModel, number: String, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = number,
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// KEYPAD
// ════════════════════════════════════════════════════════════

@Composable
private fun DialerKeypad(
    onAppend: (String) -> Unit,
    onVoicemail: () -> Unit,
    onBackspace: () -> Unit,
    onCall: () -> Unit
) {
    val keys = listOf(
        listOf("1" to "", "2" to "abc", "3" to "def"),
        listOf("4" to "ghi", "5" to "jkl", "6" to "mno"),
        listOf("7" to "pqrs", "8" to "tuv", "9" to "wxyz"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { (digit, letters) ->
                    KeypadButton(
                        digit = digit,
                        letters = letters,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onAppend(digit) },
                        // Holding 1 has rung the voicemail box on every phone ever made.
                        onLongClick = if (digit == "1") onVoicemail else null
                    )
                }
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .background(LocalZuneColors.current.accentColor)
                    .clickable { onCall() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.call),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                    color = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    .clickable { onBackspace() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.common_delete),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadButton(
    digit: String,
    letters: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp
                    ),
                    color = LocalZuneColors.current.textMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// THE SIM A CALL GOES OUT ON
// ════════════════════════════════════════════════════════════

/** A call held back until the user has said which of the phone's two numbers to ring from. */
private data class PendingCall(val number: String, val name: String)

/**
 * Asks which SIM. It does two jobs: standing in front of one call, where it can also be told not
 * to ask again, and standing on its own from the menu, where the answer is the standing one.
 */
@Composable
private fun SimPicker(
    lines: List<SimLine>,
    title: String,
    currentKey: String?,
    offerToRemember: Boolean,
    onPick: (SimLine, Boolean) -> Unit,
    onAskEveryTime: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var rememberChoice by remember { mutableStateOf(false) }

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
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = ZuneDimens.ScreenPaddingHorizontal,
                    vertical = 8.dp
                )
            )

            lines.forEach { line ->
                val chosen = line.key == currentKey
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(line, rememberChoice) }
                        .padding(
                            horizontal = ZuneDimens.ScreenPaddingHorizontal,
                            vertical = 12.dp
                        )
                ) {
                    Text(
                        text = line.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (chosen) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    line.number?.let { number ->
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyMedium,
                            color = zuneColors.textMuted
                        )
                    }
                }
            }

            if (offerToRemember) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberChoice = !rememberChoice }
                        .padding(
                            horizontal = ZuneDimens.ScreenPaddingHorizontal,
                            vertical = 12.dp
                        )
                ) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.phone_sim_remember),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            onAskEveryTime?.let { onAsk ->
                Text(
                    text = stringResource(R.string.phone_sim_ask),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (currentKey == null) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAsk)
                        .padding(
                            horizontal = ZuneDimens.ScreenPaddingHorizontal,
                            vertical = 12.dp
                        )
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// BLOCKED NUMBERS
// ════════════════════════════════════════════════════════════

/**
 * The phone's own blocked list. It is worth saying whose list it is, because taking the launcher
 * off the phone will not unblock anybody.
 */
@Composable
private fun BlockedNumbersSheet(
    numbers: List<BlockedNumber>,
    onUnblock: (BlockedNumber) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
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
                .navigationBarsPadding()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.phone_blocked_list),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = ZuneDimens.ScreenPaddingHorizontal,
                    vertical = 8.dp
                )
            )
            Text(
                text = stringResource(R.string.phone_blocked_note),
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (numbers.isEmpty()) {
                Text(
                    text = stringResource(R.string.phone_blocked_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                contentPadding = PaddingValues(horizontal = ZuneDimens.ScreenPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(numbers, key = { it.id }) { blocked ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = zuneColors.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = blocked.number,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(R.string.phone_unblock),
                            style = MaterialTheme.typography.labelMedium,
                            color = zuneColors.textMuted,
                            modifier = Modifier
                                .clickable { onUnblock(blocked) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
