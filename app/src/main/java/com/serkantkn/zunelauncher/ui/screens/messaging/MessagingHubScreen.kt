package com.serkantkn.zunelauncher.ui.screens.messaging

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
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
    val isWideScreen = LocalIsWideScreen.current
    val hasPermission by viewModel.hasSmsPermission.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val selectedConv by viewModel.selectedConversation.collectAsState()
    val threadMessages by viewModel.threadMessages.collectAsState()

    var showContactPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionAndLoad(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionAndLoad(context)
    }

    BackHandler(enabled = selectedConv != null) {
        viewModel.closeConversation()
    }

    val bottomBarActions = remember {
        listOf(
            WpBarAction(
                icon = Icons.Default.Add,
                label = "yeni mesaj",
                onClick = { showContactPicker = true }
            ),
            WpBarAction(
                icon = Icons.Default.Search,
                label = "ara",
                onClick = { isSearchActive = !isSearchActive }
            )
        )
    }

    val bottomBarMenuItems = remember {
        listOf(
            WpBarMenuItem(
                text = "yenile",
                onClick = { viewModel.loadConversations(context) }
            )
        )
    }

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
            val overflowYPx = with(LocalDensity.current) { (-24).dp.toPx() }

            if (isWideScreen) {
                // Tablet Header
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
                        .padding(start = 72.dp, top = 4.dp, bottom = 24.dp)
                        .graphicsLayer { translationY = overflowYPx }
                )

                if (!hasPermission) {
                    PermissionRequestCard(
                        onGrant = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_CONTACTS
                                )
                            )
                        }
                    )
                } else {
                    // Tablet Mode Dual-Pane Layout (Left: Message List, Right: Transparent Active Conversation)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 48.dp)
                    ) {
                        // Left Pane: Conversation List
                        Column(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                        ) {
                            AnimatedVisibility(visible = isSearchActive) {
                                SearchBar(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    isVisible = isSearchActive,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                            }

                            ThreadsPage(
                                conversations = conversations,
                                searchQuery = searchQuery,
                                onConversationClick = { conv -> viewModel.openConversation(context, conv) }
                            )
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        // Right Pane: Active Chat Conversation (Transparent Background)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (selectedConv != null) {
                                ConversationDetailContent(
                                    conversation = selectedConv!!,
                                    messages = threadMessages,
                                    onClose = { viewModel.closeConversation() },
                                    onSend = { text ->
                                        viewModel.sendSms(context, selectedConv!!.address, text) {}
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Message,
                                            contentDescription = null,
                                            tint = zuneColors.textDim,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "sohbet seçin",
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                                            color = zuneColors.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Mobile Header
                Text(
                    text = "mesajlar",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 28.dp, bottom = 4.dp)
                )

                // Mobile Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        isVisible = isSearchActive,
                        modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 4.dp)
                    )
                }

                if (!hasPermission) {
                    PermissionRequestCard(
                        onGrant = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_CONTACTS
                                )
                            )
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                    ) {
                        ThreadsPage(
                            conversations = conversations,
                            searchQuery = searchQuery,
                            onConversationClick = { conv -> viewModel.openConversation(context, conv) }
                        )
                    }
                }
            }

            // Windows Phone Metro Bottom Application Bar
            if (hasPermission) {
                WindowsPhoneBottomBar(
                    actions = bottomBarActions,
                    menuItems = bottomBarMenuItems,
                    modifier = bottomBarModifier
                )
            }
        }

        // Contact Picker Sheet
        if (showContactPicker) {
            ContactPickerSheet(
                contacts = contacts,
                onClose = { showContactPicker = false },
                onContactSelected = { name, number ->
                    showContactPicker = false
                    viewModel.openConversationWithContact(context, name, number)
                }
            )
        }

        // Mobile Fullscreen Conversation Dialog
        if (!isWideScreen && selectedConv != null) {
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
}

// ── Search Bar Component ────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val bgColor = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
    val textColor = MaterialTheme.colorScheme.onBackground
    val hintColor = zuneColors.textDim
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(bgColor, RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "sohbetlerde ara...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = hintColor
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                    singleLine = true,
                    cursorBrush = SolidColor(zuneColors.accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}

// ── Message Threads List ─────────────────────────────────────────────────────

@Composable
private fun ThreadsPage(
    conversations: List<SmsConversationModel>,
    searchQuery: String,
    onConversationClick: (SmsConversationModel) -> Unit
) {
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter {
            it.contactName.contains(searchQuery, ignoreCase = true) ||
                    it.snippet.contains(searchQuery, ignoreCase = true) ||
                    it.address.contains(searchQuery)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MessagingGroup(title = "son sohbetler") {
                if (filteredConversations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "sohbet bulunamadı",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                            color = LocalZuneColors.current.textMuted
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredConversations.forEach { conv ->
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
                    .clip(CircleShape)
                    .background(zuneColors.accentColor, CircleShape),
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
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
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

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = conversation.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessagingGroup(
    title: String,
    content: @Composable () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 22.sp
            ),
            color = zuneColors.accentColor,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

// ── Contact Picker Sheet ─────────────────────────────────────────────────────

@Composable
private fun ContactPickerSheet(
    contacts: List<Pair<ContactModel, String>>,
    onClose: () -> Unit,
    onContactSelected: (name: String, number: String) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    var filterQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, filterQuery) {
        if (filterQuery.isBlank()) contacts
        else contacts.filter { (contact, number) ->
            contact.name.contains(filterQuery, ignoreCase = true) || number.contains(filterQuery)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "kişi seçin",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 36.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = if (zuneColors.isDark) Color.White else Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SearchBar(
                query = filterQuery,
                onQueryChange = { filterQuery = it },
                isVisible = true,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredContacts) { (contact, number) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onContactSelected(contact.name, number) },
                        color = if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(zuneColors.accentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (zuneColors.isDark) Color.White else Color.Black
                                )
                                Text(
                                    text = number,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = zuneColors.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Conversation Detail View & Dialog ────────────────────────────────────────

@Composable
private fun ConversationDetailDialog(
    conversation: SmsConversationModel,
    messages: List<SmsMessageModel>,
    onClose: () -> Unit,
    onSend: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LocalZuneColors.current.let { if (it.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA) }
    ) {
        ConversationDetailContent(
            conversation = conversation,
            messages = messages,
            onClose = onClose,
            onSend = onSend,
            modifier = Modifier.statusBarsPadding()
        )
    }
}

@Composable
private fun ConversationDetailContent(
    conversation: SmsConversationModel,
    messages: List<SmsMessageModel>,
    onClose: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    var inputMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val sortedMessages = remember(messages) {
        messages.sortedBy { it.timestamp }
    }

    LaunchedEffect(sortedMessages.size, conversation.threadId) {
        if (sortedMessages.isNotEmpty()) {
            listState.scrollToItem(sortedMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
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

        if (sortedMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "henüz mesaj yok, sohbet başlatın",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.textMuted
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedMessages) { msg ->
                    val isOut = msg.isOutgoing
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isOut) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            color = if (isOut) zuneColors.accentColor else (if (zuneColors.isDark) Color(0xFF262626) else Color(0xFFE5E5E5)),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.widthIn(max = 320.dp)
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
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Gönder",
                    tint = Color.White
                )
            }
        }
    }
}

// ── Permission Request Card ─────────────────────────────────────────────────

@Composable
private fun PermissionRequestCard(onGrant: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF2F2F2),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "SMS ve Kişiler İzni Gerekli",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (zuneColors.isDark) Color.White else Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SMS mesajlarınızı ve rehberinizi Zune tarzında görüntüleyebilmek ve WhatsApp stili doğrudan sohbet başlatabilmek için erişim izni verin.",
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
                Text("İzinleri Ver")
            }
        }
    }
}
