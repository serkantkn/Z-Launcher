package com.serkantkn.zunelauncher.ui.screens.messaging

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class MessagingTab(val title: String) {
    THREADS("konuşmalar"),
    NEW("yeni mesaj"),
    QUICK("hızlı mesaj")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagingHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagingHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val hasPermission by viewModel.hasSmsPermission.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val selectedConv by viewModel.selectedConversation.collectAsState()
    val threadMessages by viewModel.threadMessages.collectAsState()

    val tabs = MessagingTab.entries
    val actualPageCount = tabs.size
    val loopCount = 1000
    val initialPage = (loopCount / 2) * actualPageCount
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { loopCount * actualPageCount }
    )
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionAndLoad(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionAndLoad(context)
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val density = LocalDensity.current
        val screenWidthPx = with(density) { screenWidthDp.toPx() }
        val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
        val overflowYPx = with(density) { (-24).dp.toPx() }

        if (isWideScreen) {
            Text(
                text = "mesajlar",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 96.sp,
                    letterSpacing = (-4).sp,
                    lineHeight = 96.sp
                ),
                color = if (zuneColors.isDark) Color.White else Color.Black,
                modifier = Modifier
                    .padding(
                        start = 72.dp,
                        top = 4.dp,
                        bottom = 24.dp
                    )
                    .graphicsLayer { translationY = overflowYPx }
            )

            if (!hasPermission) {
                PermissionRequestCard(
                    onGrant = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS
                            )
                        )
                    }
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 72.dp, end = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    items(tabs.size) { index ->
                        Column(modifier = Modifier.width(360.dp)) {
                            Text(
                                text = tabs[index].title,
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                                color = zuneColors.accentColor,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            when (tabs[index]) {
                                MessagingTab.THREADS -> ThreadsPage(
                                    conversations = conversations,
                                    onConversationClick = { conv -> viewModel.openConversation(context, conv) }
                                )
                                MessagingTab.NEW -> NewMessagePage(
                                    onSend = { recipient, body ->
                                        viewModel.sendSms(context, recipient, body) {}
                                    }
                                )
                                MessagingTab.QUICK -> QuickMessagePage(
                                    onQuickSend = { recipient, body ->
                                        viewModel.sendSms(context, recipient, body) {}
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Mobile Parallax Title Box (Identical to SettingsScreen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 28.dp,
                        bottom = 4.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal
                    )
            ) {
                val cycle = (pagerState.currentPage + pagerState.currentPageOffsetFraction) % actualPageCount
                val actualCycle = if (cycle < 0) cycle + actualPageCount else cycle
                val threshold = (actualPageCount - 1).toFloat()

                val translationX1: Float
                val translationX2: Float

                if (actualCycle <= threshold) {
                    translationX1 = -actualCycle * parallaxMultiplierPx
                    translationX2 = screenWidthPx
                } else {
                    val fraction = actualCycle - threshold
                    translationX1 = -threshold * parallaxMultiplierPx - fraction * screenWidthPx
                    translationX2 = screenWidthPx - fraction * screenWidthPx
                }

                Text(
                    text = "mesajlar",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        lineHeight = 96.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        translationX = translationX1
                        translationY = overflowYPx
                    }
                )
                Text(
                    text = "mesajlar",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        lineHeight = 96.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        translationX = translationX2
                        translationY = overflowYPx
                    }
                )
            }

            // Pivot Tabs (Identical to SettingsScreen)
            ZunePivotTabs(
                tabs = tabs.map { it.title },
                pagerState = pagerState,
                onSelected = { index ->
                    val current = pagerState.currentPage
                    val size = actualPageCount
                    val currentActual = ((current % size) + size) % size
                    var diff = index - currentActual
                    if (diff > size / 2) {
                        diff -= size
                    } else if (diff < -size / 2) {
                        diff += size
                    }
                    val targetPage = current + diff
                    scope.launch { pagerState.animateScrollToPage(targetPage) }
                },
                modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
            )

            if (!hasPermission) {
                PermissionRequestCard(
                    onGrant = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS
                            )
                        )
                    }
                )
            } else {
                // Horizontal Pager (Identical to SettingsScreen)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = 48.dp
                    ),
                    pageSpacing = 24.dp
                ) { page ->
                    val actualPage = page % actualPageCount
                    when (tabs[actualPage]) {
                        MessagingTab.THREADS -> ThreadsPage(
                            conversations = conversations,
                            onConversationClick = { conv -> viewModel.openConversation(context, conv) }
                        )
                        MessagingTab.NEW -> NewMessagePage(
                            onSend = { recipient, body ->
                                viewModel.sendSms(context, recipient, body) { success ->
                                    if (success) {
                                        scope.launch {
                                            val target = page - (actualPage - 0)
                                            pagerState.animateScrollToPage(target)
                                        }
                                    }
                                }
                            }
                        )
                        MessagingTab.QUICK -> QuickMessagePage(
                            onQuickSend = { recipient, body ->
                                viewModel.sendSms(context, recipient, body) { success ->
                                    if (success) {
                                        scope.launch {
                                            val target = page - (actualPage - 0)
                                            pagerState.animateScrollToPage(target)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Conversation Detail Dialog Overlay
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
private fun MessagingGroup(
    title: String,
    content: @Composable () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { it / 4 },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(300)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                ),
                color = LocalZuneColors.current.textMuted,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun ThreadsPage(
    conversations: List<SmsConversationModel>,
    onConversationClick: (SmsConversationModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            MessagingGroup(title = "son konuşmalar") {
                if (conversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "mesaj bulunamadı",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                            color = LocalZuneColors.current.textMuted
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        conversations.forEach { conv ->
                            ConversationCardRow(
                                conversation = conv,
                                onClick = { onConversationClick(conv) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCardRow(
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
        color = if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2),
        shape = RoundedCornerShape(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.contactName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (zuneColors.isDark) Color.White else Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = zuneColors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = conversation.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NewMessagePage(
    onSend: (recipient: String, body: String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var recipient by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            MessagingGroup(title = "alıcı telefon numarası") {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    placeholder = { Text("05xx xxx xx xx", color = zuneColors.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = zuneColors.accentColor,
                        unfocusedBorderColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                        focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
                    )
                )
            }
        }

        item {
            MessagingGroup(title = "mesaj içeriği") {
                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    placeholder = { Text("mesajınızı buraya yazın...", color = zuneColors.textMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = zuneColors.accentColor,
                        unfocusedBorderColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                        focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
                    )
                )
            }
        }

        item {
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
                    .height(52.dp),
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
}

@Composable
private fun QuickMessagePage(
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            MessagingGroup(title = "hızlı mesaj şablonları") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickTemplates.forEach { template ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    quickText = template
                                    showDialog = true
                                },
                            color = if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2),
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
                                    color = if (zuneColors.isDark) Color.White else Color.Black,
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
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Hızlı Mesaj Gönder", color = if (zuneColors.isDark) Color.White else Color.Black) },
            text = {
                Column {
                    Text(text = "\"$quickText\"", color = zuneColors.textMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = quickRecipient,
                        onValueChange = { quickRecipient = it },
                        placeholder = { Text("Telefon numarası", color = zuneColors.textMuted) },
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
                    Text("İptal", color = zuneColors.textMuted)
                }
            },
            containerColor = if (zuneColors.isDark) Color(0xFF1E1E1E) else Color.White
        )
    }
}

@Composable
private fun PermissionRequestCard(
    onGrant: () -> Unit
) {
    val zuneColors = LocalZuneColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SMS mesajlarınızı Zune tarzında görüntüleyebilmek ve yanıtlayabilmek için erişim izni verin.",
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = zuneColors.accentColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text("SMS İzinlerini Ver")
            }
        }
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
        color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = conversation.contactName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (zuneColors.isDark) Color.White else Color.Black
                    )
                    Text(
                        text = conversation.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = zuneColors.textMuted
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = if (zuneColors.isDark) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    val isOut = msg.isOutgoing
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isOut) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            color = if (isOut) zuneColors.accentColor else (if (zuneColors.isDark) Color(0xFF262626) else Color(0xFFE5E5E5)),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOut || zuneColors.isDark) Color.White else Color.Black
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = (if (isOut || zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.6f),
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = { Text("mesaj yazın...", color = zuneColors.textMuted) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = zuneColors.accentColor,
                        unfocusedBorderColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                        focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                        unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
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
