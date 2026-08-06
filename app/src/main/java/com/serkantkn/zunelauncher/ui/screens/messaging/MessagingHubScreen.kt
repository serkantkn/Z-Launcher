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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class MessagingTab(val title: String) {
    THREADS("konuşmalar"),
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
    val contacts by viewModel.contacts.collectAsState()
    val selectedConv by viewModel.selectedConversation.collectAsState()
    val threadMessages by viewModel.threadMessages.collectAsState()

    var showContactPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    LazyRow(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
                                        searchQuery = searchQuery,
                                        onConversationClick = { conv -> viewModel.openConversation(context, conv) }
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
                // Mobile Header Parallax Title Box
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

                // Pivot Tabs
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
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                )

                // Optional Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    val searchFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(isSearchActive) {
                        if (isSearchActive) {
                            kotlinx.coroutines.delay(100)
                            searchFocusRequester.requestFocus()
                        }
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("sohbetlerde ara...", color = zuneColors.textMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 4.dp)
                            .focusRequester(searchFocusRequester),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = zuneColors.accentColor,
                            unfocusedBorderColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                            focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                            unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
                        )
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
                    // Horizontal Pager Content
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
                                searchQuery = searchQuery,
                                onConversationClick = { conv -> viewModel.openConversation(context, conv) }
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

            // Windows Phone Metro Bottom Application Bar
            if (hasPermission) {
                WindowsPhoneBottomBar(
                    actions = bottomBarActions,
                    menuItems = bottomBarMenuItems
                )
            }
        }

        // WhatsApp Style Contact Picker Sheet
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

        // WhatsApp Style Conversation Detail Dialog
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
                    .size(46.dp)
                    .background(zuneColors.accentColor, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = conversation.contactName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
        contentPadding = PaddingValues(bottom = 64.dp),
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

            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("rehberde ara...", color = zuneColors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = zuneColors.accentColor,
                    unfocusedBorderColor = (if (zuneColors.isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                    focusedTextColor = if (zuneColors.isDark) Color.White else Color.Black,
                    unfocusedTextColor = if (zuneColors.isDark) Color.White else Color.Black
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "rehberde kişi bulunamadı",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        .background(zuneColors.accentColor, RoundedCornerShape(2.dp)),
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
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (zuneColors.isDark) Color.White else Color.Black
                                    )
                                    Text(
                                        text = number,
                                        style = MaterialTheme.typography.bodySmall,
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

            if (messages.isEmpty()) {
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
