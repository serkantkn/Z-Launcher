package com.serkantkn.zunelauncher.ui.screens.messaging

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessagingHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagingHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val hasPermission by viewModel.hasSmsPermission.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val selectedConv by viewModel.selectedConversation.collectAsState()
    val threadMessages by viewModel.threadMessages.collectAsState()

    val pivotTabs = remember { listOf("konuşmalar", "yeni mesaj", "hızlı mesaj") }
    val pagerState = rememberPagerState(pageCount = { pivotTabs.size })
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionAndLoad(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionAndLoad(context)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F0F0F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header & Back Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "messaging",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Thin,
                        fontSize = 48.sp,
                        letterSpacing = (-1.5).sp
                    ),
                    color = Color.White
                )

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pivot Tabs
            ZunePivotTabs(
                tabs = pivotTabs,
                pagerState = pagerState,
                onSelected = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                // Permission Request Card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = null,
                            tint = zuneColors.accentColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SMS İzinleri Gerekli",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SMS mesajlarını Zune tarzında listeleyebilmek ve yanıtlayabilmek için izin verin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.SEND_SMS
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = zuneColors.accentColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text("SMS İzinlerini Ver")
                        }
                    }
                }
            } else {
                // Horizontal Pager Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> ThreadsPivot(
                            conversations = conversations,
                            onConversationClick = { conv -> viewModel.openConversation(context, conv) }
                        )
                        1 -> NewMessagePivot(
                            onSend = { recipient, body ->
                                viewModel.sendSms(context, recipient, body) { success ->
                                    if (success) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(0)
                                        }
                                    }
                                }
                            }
                        )
                        2 -> QuickMessagePivot(
                            onQuickSend = { recipient, body ->
                                viewModel.sendSms(context, recipient, body) { success ->
                                    if (success) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(0)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Thread Conversation Detail View Overlay
        if (selectedConv != null) {
            ConversationDetailDialog(
                conversation = selectedConv!!,
                messages = threadMessages,
                onClose = { viewModel.closeConversation() },
                onSend = { text ->
                    viewModel.sendSms(context, selectedConv!!.address, text) {}
                }
            )
        }
    }
}

@Composable
private fun ThreadsPivot(
    conversations: List<SmsConversationModel>,
    onConversationClick: (SmsConversationModel) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "mesaj bulunamadı",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Thin),
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversations) { conv ->
                ConversationTile(
                    conversation = conv,
                    onClick = { onConversationClick(conv) }
                )
            }
        }
    }
}

@Composable
private fun ConversationTile(
    conversation: SmsConversationModel,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val timeStr = remember(conversation.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conversation.timestamp))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Tile
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(zuneColors.accentColor, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.contactName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.contactName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = conversation.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NewMessagePivot(
    onSend: (recipient: String, body: String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var recipient by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "kime (telefon numarası)",
            style = MaterialTheme.typography.labelSmall,
            color = zuneColors.accentColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = recipient,
            onValueChange = { recipient = it },
            placeholder = { Text("05xx xxx xx xx", color = Color.White.copy(alpha = 0.4f)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = zuneColors.accentColor,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "mesajınız",
            style = MaterialTheme.typography.labelSmall,
            color = zuneColors.accentColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = messageBody,
            onValueChange = { messageBody = it },
            placeholder = { Text("mesajınızı yazın...", color = Color.White.copy(alpha = 0.4f)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = zuneColors.accentColor,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (recipient.isNotBlank() && messageBody.isNotBlank()) {
                    isSending = true
                    onSend(recipient, messageBody)
                }
            },
            enabled = !isSending && recipient.isNotBlank() && messageBody.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = zuneColors.accentColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(2.dp)
        ) {
            Text(
                text = if (isSending) "gönderiliyor..." else "mesaj gönder",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun QuickMessagePivot(
    onQuickSend: (recipient: String, body: String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var showDialog by remember { mutableStateOf(false) }
    var quickRecipient by remember { mutableStateOf("") }
    var quickText by remember { mutableStateOf("") }

    val quickTemplates = remember {
        listOf(
            "Tamamdır, birazdan oradayım.",
            "Şu an meşgulüm, sonra arayacağım.",
            "Eve vardığımda haber vereceğim.",
            "Teşekkürler!",
            "Toplantıdayım, mesaj atabilir misin?"
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "hızlı mesaj şablonları",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickTemplates) { template ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            quickText = template
                            showDialog = true
                        },
                    color = Color(0xFF1E1E1E),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = template,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Gönder",
                            tint = zuneColors.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Hızlı Mesaj Gönder", color = Color.White) },
            text = {
                Column {
                    Text(text = "\"$quickText\"", color = Color.White.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = quickRecipient,
                        onValueChange = { quickRecipient = it },
                        placeholder = { Text("Telefon numarası", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (quickRecipient.isNotBlank()) {
                            onQuickSend(quickRecipient, quickText)
                            showDialog = false
                        }
                    }
                ) {
                    Text("Gönder", color = zuneColors.accentColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("İptal", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}

@Composable
private fun ConversationDetailDialog(
    conversation: SmsConversationModel,
    messages: List<SmsMessageModel>,
    onClose: () -> Unit,
    onSend: (String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var inputMessage by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0A0A0A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = conversation.contactName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = conversation.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Messages List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isOut = msg.isOutgoing
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isOut) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            color = if (isOut) zuneColors.accentColor else Color(0xFF262626),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = msg.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("mesaj yazın...", color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = zuneColors.accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputMessage.isNotBlank()) {
                            onSend(inputMessage)
                            inputMessage = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(zuneColors.accentColor, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Gönder",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
