package com.serkantkn.zunelauncher.ui.screens.messaging

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.TransformOrigin
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
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
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

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // 3D Door Hinge Animation for Both Mobile and Tablet Views (All rotating from LEFT screen edge: TransformOrigin(0f, 0.5f))
    val hubHingeAnim = remember { Animatable(1f) }
    val detailHingeAnim = remember { Animatable(0f) }
    val pickerHingeAnim = remember { Animatable(0f) }
    var isTransitioning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionAndLoad(context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissionAndLoad(context)
    }

    val openContactPickerWithAnimation: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                hubHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                pickerHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val closeContactPickerWithAnimation: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                pickerHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                hubHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val openConversationWithAnimation: (SmsConversationModel) -> Unit = { conv ->
        if (isWideScreen) {
            viewModel.openConversation(context, conv)
        } else {
            if (!isTransitioning) {
                isTransitioning = true
                viewModel.openConversation(context, conv)
                coroutineScope.launch {
                    hubHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    detailHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    isTransitioning = false
                }
            }
        }
    }

    val closeConversationWithAnimation: () -> Unit = {
        if (isWideScreen) {
            viewModel.closeConversation()
        } else {
            if (!isTransitioning) {
                isTransitioning = true
                coroutineScope.launch {
                    detailHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    viewModel.closeConversation()
                    hubHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                    isTransitioning = false
                }
            }
        }
    }

    val onContactSelectedInPicker: (name: String, number: String) -> Unit = { name, number ->
        if (isWideScreen) {
            viewModel.openConversationWithContact(context, name, number)
            coroutineScope.launch {
                pickerHingeAnim.animateTo(0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
                hubHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
            }
        } else {
            viewModel.openConversationWithContact(context, name, number)
            coroutineScope.launch {
                pickerHingeAnim.snapTo(0f)
                hubHingeAnim.snapTo(0f)
                detailHingeAnim.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
            }
        }
    }

    BackHandler(
        enabled = pickerHingeAnim.value > 0f || (!isWideScreen && detailHingeAnim.value > 0f) || (isWideScreen && selectedConv != null)
    ) {
        if (pickerHingeAnim.value > 0f) {
            closeContactPickerWithAnimation()
        } else if (!isWideScreen && detailHingeAnim.value > 0f) {
            closeConversationWithAnimation()
        } else if (isWideScreen && selectedConv != null) {
            closeConversationWithAnimation()
        }
    }

    val bottomBarActions = remember {
        listOf(
            WpBarAction(
                icon = Icons.Default.Add,
                label = "yeni mesaj",
                onClick = { openContactPickerWithAnimation() }
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
            val density = LocalDensity.current
            val overflowYPx = with(density) { (-24).dp.toPx() }

            if (isWideScreen) {
                // ─── TABLET DUAL-PANE LAYOUT WITH 3D LEFT-EDGE HINGE ANIMATION ───
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = hubHingeAnim.value
                            rotationY = -90f * (1f - p)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = 12f * density.density
                            alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                                        onConversationClick = { conv -> openConversationWithAnimation(conv) }
                                    )
                                }

                                Spacer(modifier = Modifier.width(32.dp))

                                // Right Pane: Active Chat Conversation
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    if (selectedConv != null) {
                                        ConversationDetailContent(
                                            conversation = selectedConv!!,
                                            messages = threadMessages,
                                            onClose = { closeConversationWithAnimation() },
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
                                                    imageVector = Icons.AutoMirrored.Filled.Message,
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

                        if (hasPermission && pickerHingeAnim.value == 0f) {
                            WindowsPhoneBottomBar(
                                actions = bottomBarActions,
                                menuItems = bottomBarMenuItems,
                                modifier = bottomBarModifier
                            )
                        }
                    }
                }

                // ─── TABLET 3D HINGE ANIMATED CONTACT PICKER (LEFT-EDGE HINGE) ───
                if (pickerHingeAnim.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = pickerHingeAnim.value
                                rotationY = 90f * (1f - p)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = 12f * density.density
                                alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                            }
                    ) {
                        ContactPickerScreen(
                            contacts = contacts,
                            onClose = { closeContactPickerWithAnimation() },
                            onContactSelected = onContactSelectedInPicker
                        )
                    }
                }
            } else {
                // ─── PHONE MODE: MATCHING PHONE HUB SCREEN DESIGN ───
                // Hub Screen (Rotates out from the LEFT edge: TransformOrigin(0f, 0.5f))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = hubHingeAnim.value
                            rotationY = -90f * (1f - p)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = 12f * density.density
                            alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Label ("mesajlar")
                        Text(
                            text = "mesajlar",
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

                        // Main Header ("sohbetler")
                        Text(
                            text = "sohbetler",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 72.sp,
                                letterSpacing = (-3).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(
                                bottom = 12.dp,
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = ZuneDimens.ScreenPaddingHorizontal
                            )
                        )

                        // Collapsible Search Bar
                        AnimatedVisibility(visible = isSearchActive) {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                isVisible = isSearchActive,
                                modifier = Modifier.padding(
                                    horizontal = ZuneDimens.ScreenPaddingHorizontal,
                                    vertical = 6.dp
                                )
                            )
                        }

                        // Content List Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                        ) {
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
                                ThreadsPage(
                                    conversations = conversations,
                                    searchQuery = searchQuery,
                                    onConversationClick = { conv -> openConversationWithAnimation(conv) }
                                )
                            }
                        }

                        // Bottom Spacer for Bar
                        Spacer(modifier = Modifier.height(80.dp))
                    }

                    // Phone Bottom Application Bar
                    if (hasPermission && detailHingeAnim.value == 0f && pickerHingeAnim.value == 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .then(bottomBarModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            WindowsPhoneBottomBar(
                                actions = bottomBarActions,
                                menuItems = bottomBarMenuItems
                            )
                        }
                    }
                }

                // ─── PHONE 3D HINGE ANIMATED CONTACT PICKER (LEFT-EDGE HINGE) ───
                if (pickerHingeAnim.value > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = pickerHingeAnim.value
                                rotationY = 90f * (1f - p)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = 12f * density.density
                                alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                            }
                    ) {
                        ContactPickerScreen(
                            contacts = contacts,
                            onClose = { closeContactPickerWithAnimation() },
                            onContactSelected = onContactSelectedInPicker
                        )
                    }
                }

                // ─── PHONE 3D HINGE ANIMATED FULLSCREEN CHAT (LEFT-EDGE HINGE) ───
                if (detailHingeAnim.value > 0f && selectedConv != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = detailHingeAnim.value
                                rotationY = 90f * (1f - p)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = 12f * density.density
                                alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                            }
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (zuneColors.isDark) Color(0xFF0C0C0C) else Color(0xFFFAFAFA)
                        ) {
                            ConversationDetailContent(
                                conversation = selectedConv!!,
                                messages = threadMessages,
                                onClose = { closeConversationWithAnimation() },
                                onSend = { text ->
                                    viewModel.sendSms(context, selectedConv!!.address, text) {}
                                },
                                modifier = Modifier.statusBarsPadding()
                            )
                        }
                    }
                }
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
    val bgColor = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
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
            .height(44.dp)
            .background(bgColor, RoundedCornerShape(2.dp))
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
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
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

// ── Message Threads List (Styled Matching Phone Hub Screen) ──────────────────

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
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (filteredConversations.isEmpty()) {
            item {
                Text(
                    text = if (searchQuery.isNotBlank()) "sonuç bulunamadı" else "mesaj geçmişi boş",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalZuneColors.current.textDim,
                    modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
                )
            }
        } else {
            items(filteredConversations, key = { it.threadId }) { conv ->
                ConversationItem(
                    conversation = conv,
                    onClick = { onConversationClick(conv) }
                )
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: SmsConversationModel,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val format = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    val dateString = format.format(Date(conversation.timestamp)).lowercase()
    val isUnread = !conversation.isRead

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = if (isUnread) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.contactName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUnread) zuneColors.accentColor else zuneColors.textMuted
                )
            }
            Text(
                text = conversation.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUnread) MaterialTheme.colorScheme.onBackground else zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Contact Picker Screen ───────────────────────────────────────────────────

@Composable
private fun ContactPickerScreen(
    contacts: List<Pair<ContactModel, String>>,
    onClose: () -> Unit,
    onContactSelected: (name: String, number: String) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxSize(),
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredContacts) { (contact, number) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onContactSelected(contact.name, number) },
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

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
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

// ── Conversation Detail View ────────────────────────────────────────────────

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
                    text = conversation.contactName.lowercase(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 32.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black
                )
                Text(
                    text = conversation.address,
                    style = MaterialTheme.typography.labelMedium,
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
                            color = if (isOut) zuneColors.accentColor else (if (zuneColors.isDark) Color(0xFF222222) else Color(0xFFE5E5E5)),
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
                shape = RoundedCornerShape(2.dp),
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
        color = if (zuneColors.isDark) Color(0xFF181818) else Color(0xFFF2F2F2),
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
                text = "SMS mesajlarınızı ve rehberinizi Zune tarzında görüntüleyebilmek için erişim izni verin.",
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
