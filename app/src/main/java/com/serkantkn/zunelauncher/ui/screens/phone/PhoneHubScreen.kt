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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.serkantkn.zunelauncher.ui.screens.settings.SettingsViewModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
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

    Box(modifier = modifier.fillMaxSize()) {
        // Main Content (Recent Calls & Header)
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = "telefon",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                ),
                color = zuneColors.accentColor,
                modifier = Modifier.padding(
                    top = 60.dp,
                    bottom = 4.dp,
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal
                )
            )

            Text(
                text = "geçmiş",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    bottom = 18.dp,
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal
                )
            )

            Box(modifier = Modifier.weight(1f).padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                RecentCallsTab(
                    hasPermission = hasPermissions,
                    recentCalls = recentCalls,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.READ_CONTACTS
                            )
                        )
                    },
                    onCallClick = { number ->
                        initiateCall(context, number, directCallEnabled)
                    }
                )
            }
            
            // Spacer to avoid list being hidden under the app bar
            Spacer(modifier = Modifier.height(80.dp))
        }

        // Application Bar (Bottom Menu Bar)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp, top = 8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, Color.White), CircleShape)
                    .clickable { 
                        viewModel.clearDialedNumber()
                        isDialerOpen = true 
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "çeviriciyi aç",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
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
                                    initiateCall(context, number, directCallEnabled)
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
                                initiateCall(context, dialedNumber, directCallEnabled)
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

private fun initiateCall(context: android.content.Context, number: String, directCallEnabled: Boolean) {
    val intent = if (directCallEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
    } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    }
    context.startActivity(intent)
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
                    text = "ara",
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
                    text = "sil",
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
    onCallClick: (String) -> Unit
) {
    if (!hasPermission) {
        PermissionRequestView(onRequestPermission)
    } else if (recentCalls.isEmpty()) {
        Text(
            text = "çağrı geçmişi boş",
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

@Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.padding(top = ZuneDimens.SpacingHuge)) {
        Text(
            text = "çağrı geçmişi izni",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "son arananlar listesini görebilmeniz için çağrı geçmişinize ve rehberinize erişim izni gereklidir.",
            style = MaterialTheme.typography.bodyMedium,
            color = zuneColors.textMuted
        )
        Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = zuneColors.accentColor,
                contentColor = Color.White
            )
        ) {
            Text(text = "izin ver".lowercase())
        }
    }
}
