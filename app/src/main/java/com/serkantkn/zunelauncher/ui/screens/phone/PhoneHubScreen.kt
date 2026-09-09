package com.serkantkn.zunelauncher.ui.screens.phone

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.CallLogModel
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.components.ZunePivotHeader
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PhoneHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhoneViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    
    val directCallEnabled by settingsViewModel.directCallEnabled.collectAsState()
    val recentCalls by viewModel.recentCalls.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val dialedNumber by viewModel.dialedNumber.collectAsState()
    val matchingContacts by viewModel.matchingContacts.collectAsState()

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isDialerOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.values.all { it }
            hasPermissions = allGranted
            if (allGranted) {
                viewModel.loadRecentCalls()
                viewModel.loadContacts()
            }
        }
    )

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.loadRecentCalls()
            viewModel.loadContacts()
        }
    }

    BackHandler {
        if (isDialerOpen) {
            isDialerOpen = false
            viewModel.clearDialedNumber()
        } else {
            onBack()
        }
    }

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content (Recent Calls & Header)
            Column(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val overflowYPx = with(density) { (-24).dp.toPx() }

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

            ZunePivotHeader(
                text = stringResource(R.string.common_history),
                modifier = Modifier.padding(
                    bottom = 12.dp,
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal
                )
            )

            Box(modifier = Modifier.weight(1f).padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                RecentCallsTab(
                    hasPermission = hasPermissions,
                    recentCalls = recentCalls,
                    errorMessage = errorMessage,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.READ_CONTACTS
                            )
                        )
                    },
                    onCallClick = { number ->
                        initiateCall(context, number)
                    }
                )
            }
            
            // Spacer to avoid list being hidden under the app bar
            Spacer(modifier = Modifier.height(80.dp))
        }

        // Application Bar (Bottom Menu Bar)
        val bottomBarActions = listOf(
            com.serkantkn.zunelauncher.ui.components.WpBarAction(
                icon = Icons.Default.Dialpad,
                label = stringResource(R.string.phone_keypad),
                onClick = {
                    viewModel.clearDialedNumber()
                    isDialerOpen = true
                }
            ),
            com.serkantkn.zunelauncher.ui.components.WpBarAction(
                icon = Icons.Default.Call,
                label = stringResource(R.string.call_outgoing),
                onClick = {
                    com.serkantkn.zunelauncher.data.service.CallManager.startOutgoingCall("Ahmet Yılmaz", "+90 (555) 123 45 67")
                }
            ),
            com.serkantkn.zunelauncher.ui.components.WpBarAction(
                icon = Icons.AutoMirrored.Filled.CallReceived,
                label = stringResource(R.string.call_incoming),
                onClick = {
                    com.serkantkn.zunelauncher.data.service.CallManager.startIncomingCall("Zeynep Kaya", "+90 (532) 987 65 43")
                }
            )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(bottomBarModifier),
            contentAlignment = Alignment.Center
        ) {
            com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar(actions = bottomBarActions)
        }

        // Dialer Overlay
        AnimatedVisibility(
            visible = isDialerOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ),
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
                // Matching contacts list (if any)
                if (matchingContacts.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        contentPadding = PaddingValues(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(matchingContacts, key = { it.first.id + it.second }) { (contact, number) ->
                            MatchingContactItem(
                                contact = contact, 
                                number = number,
                                onClick = {
                                    initiateCall(context, number, contact.name)
                                    isDialerOpen = false
                                    viewModel.clearDialedNumber()
                                }
                            )
                        }
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                }

                // Number Display
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )

                // Keypad Grid (Restricted Height)
                Box(modifier = Modifier.height(280.dp).padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                    DialerKeypad(
                        onAppend = viewModel::appendDigit,
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
                                initiateCall(context, dialedNumber)
                                isDialerOpen = false
                                viewModel.clearDialedNumber()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
}

@Composable
private fun MatchingContactItem(contact: ContactModel, number: String, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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

private fun initiateCall(context: android.content.Context, number: String, name: String = "") {
    com.serkantkn.zunelauncher.data.service.CallManager.startOutgoingCall(
        name = name,
        number = number
    )
}

@Composable
private fun DialerKeypad(
    onAppend: (String) -> Unit,
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
                        onClick = { onAppend(digit) }
                    )
                }
            }
        }

        // Bottom Actions (Call / Backspace)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Call Button
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
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = Color.White
                )
            }

            // Backspace Button
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
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    digit: String,
    letters: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
            .clickable { onClick() }
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

@Composable
private fun RecentCallsTab(
    hasPermission: Boolean,
    recentCalls: List<CallLogModel>,
    onRequestPermission: () -> Unit,
    onCallClick: (String) -> Unit,
    errorMessage: String? = null
) {
    if (!hasPermission) {
        ZunePermissionRequest(
            title = stringResource(R.string.phone_permission_title),
            message = stringResource(R.string.phone_permission_message),
            buttonLabel = stringResource(R.string.grant_permission),
            onRequest = onRequestPermission
        )
    } else if (recentCalls.isEmpty()) {
        Text(
            text = errorMessage?.let { stringResource(R.string.phone_history_failed, it) } ?: stringResource(R.string.phone_history_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZuneColors.current.textDim,
            modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(recentCalls, key = { it.id }) { call ->
                CallLogItem(call = call, onClick = { onCallClick(call.number) })
            }
        }
    }
}

@Composable
private fun CallLogItem(call: CallLogModel, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val format = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    val dateString = format.format(Date(call.dateMillis)).lowercase()
    
    val (icon, color) = when (call.type) {
        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.colorScheme.onBackground
        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.colorScheme.onBackground
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to zuneColors.accentColor
        else -> Icons.Default.Call to MaterialTheme.colorScheme.onBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = call.name ?: call.number,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row {
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted
                )
                if (call.name != null) {
                    Text(
                        text = " • ${call.number}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted
                    )
                }
            }
        }
    }
}
