package com.serkantkn.zunelauncher.ui.screens.messaging

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.email.EmailConfirmDialog
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.PhoneCaller
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Messaging Hub.
 *
 * Canonical hub skeleton: ZuneHubEntranceLayout → 18sp header → ZunePivotTabs (tümü / okunmadı /
 * taslaklar) → ZuneLoopingPager → WindowsPhoneBottomBar, with the conversation and the new-message
 * picker arriving as door-hinge sub-screens from the left edge.
 */
@Composable
fun MessagingHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagingHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val hasPermission by viewModel.hasSmsPermission.collectAsState()
    val isDefaultApp by viewModel.isDefaultSmsApp.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val selectedConv by viewModel.selectedConversation.collectAsState()
    val threadMessages by viewModel.threadMessages.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val matchingMessages by viewModel.matchingMessages.collectAsState()
    val pinnedThreads by viewModel.pinnedThreads.collectAsState()
    val sims by viewModel.simLines.collectAsState()
    val selectedSim by viewModel.selectedSim.collectAsState()
    val isBlocked by viewModel.selectedBlocked.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var composeText by remember { mutableStateOf("") }
    var pendingForward by remember { mutableStateOf<String?>(null) }
    var menuConversation by remember { mutableStateOf<SmsConversationModel?>(null) }
    var menuMessage by remember { mutableStateOf<SmsMessageModel?>(null) }
    var deleteConversation by remember { mutableStateOf<SmsConversationModel?>(null) }
    var deleteMessage by remember { mutableStateOf<SmsMessageModel?>(null) }
    var detailsMessage by remember { mutableStateOf<SmsMessageModel?>(null) }

    // The list's stamps say "18:40", "yesterday", "Tuesday"; they have to age as the hub stays open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }

    val tabs = listOf(
        stringResource(R.string.msg_tab_all),
        stringResource(R.string.msg_tab_unread),
        stringResource(R.string.msg_tab_drafts)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    // ── Door hinges ──
    val hubHingeAnim = remember { Animatable(1f) }
    val detailHingeAnim = remember { Animatable(0f) }
    val pickerHingeAnim = remember { Animatable(0f) }
    var isTransitioning by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionAndLoad(context)
    }

    LaunchedEffect(Unit) { viewModel.checkPermissionAndLoad(context) }

    val openPicker: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                hubHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                pickerHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val closePicker: () -> Unit = {
        if (!isTransitioning) {
            isTransitioning = true
            pendingForward = null
            coroutineScope.launch {
                pickerHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                hubHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val openConversation: (SmsConversationModel) -> Unit = { conversation ->
        composeText = pendingForward ?: conversation.draft
        pendingForward = null
        viewModel.openConversation(context, conversation)
        if (!isWideScreen && !isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                hubHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                detailHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val closeConversation: () -> Unit = {
        // Whatever was typed and not sent stays under the conversation rather than disappearing.
        viewModel.saveDraft(context, composeText)
        if (isWideScreen) {
            viewModel.closeConversation()
            composeText = ""
        } else if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                detailHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                viewModel.closeConversation()
                composeText = ""
                hubHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val onRecipientChosen: (String, String) -> Unit = { name, number ->
        val forwarded = pendingForward
        viewModel.openConversationWithContact(context, name, number)
        composeText = forwarded.orEmpty()
        pendingForward = null
        coroutineScope.launch {
            if (isWideScreen) {
                pickerHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                hubHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
            } else {
                pickerHingeAnim.snapTo(0f)
                hubHingeAnim.snapTo(0f)
                detailHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
    }

    // ── Things arriving from outside the hub ──
    val pendingThread by MessagingBridge.pendingThreadId.collectAsState()
    LaunchedEffect(pendingThread, conversations.size) {
        val threadId = pendingThread ?: return@LaunchedEffect
        val conversation = conversations.firstOrNull { it.threadId == threadId }
        if (conversation != null) {
            MessagingBridge.consumeThread()
            openConversation(conversation)
        } else if (conversations.isNotEmpty()) {
            // The conversation is gone; do not keep asking for it.
            MessagingBridge.consumeThread()
        }
    }

    val pendingCompose by MessagingBridge.pendingCompose.collectAsState()
    LaunchedEffect(pendingCompose) {
        val request = pendingCompose ?: return@LaunchedEffect
        MessagingBridge.consumeCompose()
        if (request.address.isNotBlank()) {
            pendingForward = request.body.takeIf { it.isNotBlank() }
            viewModel.openConversationWithContact(context, "", request.address)
            composeText = request.body
            pendingForward = null
            if (!isWideScreen) {
                hubHingeAnim.snapTo(0f)
                detailHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
            }
        } else {
            pendingForward = request.body.takeIf { it.isNotBlank() }
            openPicker()
        }
    }

    BackHandler(
        enabled = menuConversation != null || menuMessage != null ||
            pickerHingeAnim.value > 0f ||
            (!isWideScreen && detailHingeAnim.value > 0f) ||
            (isWideScreen && selectedConv != null) ||
            isSearchActive
    ) {
        when {
            menuMessage != null -> menuMessage = null
            menuConversation != null -> menuConversation = null
            pickerHingeAnim.value > 0f -> closePicker()
            !isWideScreen && detailHingeAnim.value > 0f -> closeConversation()
            isWideScreen && selectedConv != null -> closeConversation()
            isSearchActive -> {
                isSearchActive = false
                viewModel.clearSearch()
            }
        }
    }

    // ── The bar ──
    val bottomBarActions = listOf(
        WpBarAction(Icons.Default.Add, stringResource(R.string.msg_new)) { openPicker() },
        WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
            isSearchActive = !isSearchActive
            if (!isSearchActive) viewModel.clearSearch()
        }
    )
    val bottomBarMenuItems = buildList {
        add(WpBarMenuItem(stringResource(R.string.common_refresh)) { viewModel.loadConversations(context) })
        if (!isDefaultApp) {
            add(WpBarMenuItem(stringResource(R.string.msg_make_default)) { askToBeDefault(context) })
        }
    }

    val bottomBarClearance = 56.dp + 16.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // ── The pages ──
    @Composable
    fun ConversationsPage(tab: MessageTab) {
        val list = remember(conversations, tab) { viewModel.conversationsFor(tab) }
        val emptyText = when {
            errorMessage != null -> stringResource(R.string.msg_load_failed, errorMessage.orEmpty())
            tab == MessageTab.UNREAD -> stringResource(R.string.msg_unread_empty)
            tab == MessageTab.DRAFTS -> stringResource(R.string.msg_drafts_empty)
            else -> stringResource(R.string.msg_history_empty)
        }
        ZunePageTransition {
            if (list.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(list, key = { it.threadId }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            now = now,
                            onClick = { openConversation(conversation) },
                            onLongClick = { menuConversation = conversation }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SearchResultsPage() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val matchingConversations = conversations.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.address.contains(searchQuery, ignoreCase = true)
            }
            items(matchingConversations, key = { "c-${it.threadId}" }) { conversation ->
                ConversationItem(
                    conversation = conversation,
                    now = now,
                    onClick = { openConversation(conversation) },
                    onLongClick = { menuConversation = conversation }
                )
            }
            if (matchingMessages.isNotEmpty()) {
                item(key = "in-messages") {
                    Text(
                        text = stringResource(R.string.msg_results_in_messages),
                        style = MaterialTheme.typography.labelLarge,
                        color = zuneColors.accentColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            items(matchingMessages, key = { "m-${it.id}" }) { message ->
                SearchResultRow(
                    message = message,
                    onClick = {
                        viewModel.conversationOf(message)?.let { openConversation(it) }
                    }
                )
            }
            if (matchingConversations.isEmpty() && matchingMessages.isEmpty()) {
                item(key = "none") {
                    Text(
                        text = stringResource(R.string.common_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textDim,
                        modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
                    )
                }
            }
        }
    }

    @Composable
    fun ListArea() {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isDefaultApp && hasPermission) {
                DefaultAppNotice(onMakeDefault = { askToBeDefault(context) })
                Spacer(modifier = Modifier.height(8.dp))
            }
            AnimatedVisibility(visible = isSearchActive) {
                MessageSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(context, it) },
                    isVisible = isSearchActive,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasPermission -> MessagePermissionCard(
                        onGrant = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_CONTACTS
                                )
                            )
                        }
                    )

                    isSearchActive && searchQuery.isNotBlank() -> SearchResultsPage()

                    else -> ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        ConversationsPage(MessageTab.entries[page])
                    }
                }
            }
        }
    }

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {

            // ── The hub board ──
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
                if (isWideScreen) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ZuneWideHubTitle(text = stringResource(R.string.hub_messaging))
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 72.dp, end = 48.dp)
                        ) {
                            Column(modifier = Modifier.width(380.dp).fillMaxHeight()) {
                                ZunePivotTabs(tabs = tabs, state = pager, fontSize = 30.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                ListArea()
                            }
                            Spacer(modifier = Modifier.width(32.dp))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                val conversation = selectedConv
                                if (conversation != null) {
                                    ConversationScreen(
                                        conversation = conversation,
                                        messages = threadMessages,
                                        input = composeText,
                                        onInputChange = { composeText = it },
                                        now = now,
                                        sims = sims,
                                        selectedSim = selectedSim,
                                        onSelectSim = viewModel::selectSim,
                                        isBlocked = isBlocked,
                                        onSend = {
                                            val text = composeText
                                            composeText = ""
                                            viewModel.sendSms(context, conversation.address, text)
                                        },
                                        onClose = closeConversation,
                                        onCall = { PhoneCaller.call(context, conversation.address, conversation.title) },
                                        onMessageLongPress = { menuMessage = it }
                                    )
                                } else {
                                    EmptyDetailPane()
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(bottomBarClearance))
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.hub_messaging),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                letterSpacing = 1.sp
                            ),
                            color = if (zuneColors.isDark) {
                                Color.White.copy(alpha = 0.9f)
                            } else {
                                Color.Black.copy(alpha = 0.85f)
                            },
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(
                                top = 28.dp,
                                bottom = 4.dp,
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = ZuneDimens.ScreenPaddingHorizontal
                            )
                        )
                        ZunePivotTabs(tabs = tabs, state = pager, modifier = Modifier.padding(top = 4.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                        ) {
                            ListArea()
                        }
                        Spacer(modifier = Modifier.height(bottomBarClearance))
                    }
                }

                if (hasPermission && detailHingeAnim.value == 0f && pickerHingeAnim.value == 0f) {
                    WindowsPhoneBottomBar(
                        actions = bottomBarActions,
                        menuItems = bottomBarMenuItems,
                        modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier)
                    )
                }
            }

            // ── New message ──
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
                    NewMessageScreen(
                        contacts = contacts,
                        onClose = closePicker,
                        onRecipientChosen = onRecipientChosen
                    )
                }
            }

            // ── The conversation, on a phone ──
            if (!isWideScreen && detailHingeAnim.value > 0f && selectedConv != null) {
                val conversation = selectedConv!!
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
                        ConversationScreen(
                            conversation = conversation,
                            messages = threadMessages,
                            input = composeText,
                            onInputChange = { composeText = it },
                            now = now,
                            sims = sims,
                            selectedSim = selectedSim,
                            onSelectSim = viewModel::selectSim,
                            isBlocked = isBlocked,
                            onSend = {
                                val text = composeText
                                composeText = ""
                                viewModel.sendSms(context, conversation.address, text)
                            },
                            onClose = closeConversation,
                            onCall = { PhoneCaller.call(context, conversation.address, conversation.title) },
                            onMessageLongPress = { menuMessage = it },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                }
            }

            // ── What just happened ──
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(zuneColors.accentColor)
                        .statusBarsPadding()
                        .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp)
                ) {
                    Text(
                        text = statusMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                }
            }
        }
    }

    // ── Long press on a conversation ──
    menuConversation?.let { conversation ->
        val pinned = conversation.threadId in pinnedThreads
        MessagingSheet(title = conversation.title, onDismiss = { menuConversation = null }) {
            if (conversation.unreadCount > 0 || !conversation.isRead) {
                SheetAction(stringResource(R.string.msg_mark_read)) {
                    viewModel.markThreadRead(context, conversation)
                    menuConversation = null
                }
            }
            if (conversation.address.isNotBlank()) {
                SheetAction(stringResource(R.string.msg_call)) {
                    PhoneCaller.call(context, conversation.address, conversation.title)
                    menuConversation = null
                }
            }
            SheetAction(
                stringResource(
                    if (pinned) R.string.msg_unpin_from_start else R.string.msg_pin_to_start
                )
            ) {
                viewModel.togglePinToStart(conversation)
                menuConversation = null
            }
            if (conversation.address.isNotBlank()) {
                SheetAction(stringResource(R.string.msg_block_number)) {
                    viewModel.toggleBlocked(context, conversation)
                    menuConversation = null
                }
            }
            SheetAction(
                stringResource(R.string.msg_delete_conversation),
                color = MaterialTheme.colorScheme.error
            ) {
                deleteConversation = conversation
                menuConversation = null
            }
        }
    }

    // ── Long press on a message ──
    menuMessage?.let { message ->
        MessagingSheet(
            title = selectedConv?.title.orEmpty().ifBlank { stringResource(R.string.hub_messaging) },
            onDismiss = { menuMessage = null }
        ) {
            if (message.body.isNotBlank()) {
                SheetAction(stringResource(R.string.common_copy)) {
                    copyToClipboard(context, message.body)
                    viewModel.showStatus(context.getString(R.string.msg_copied))
                    menuMessage = null
                }
                SheetAction(stringResource(R.string.msg_forward)) {
                    pendingForward = message.body
                    menuMessage = null
                    openPicker()
                }
            }
            if (message.isFailed) {
                SheetAction(stringResource(R.string.msg_resend)) {
                    viewModel.resend(context, message)
                    menuMessage = null
                }
            }
            SheetAction(stringResource(R.string.msg_details)) {
                detailsMessage = message
                menuMessage = null
            }
            SheetAction(
                stringResource(R.string.msg_delete_message),
                color = MaterialTheme.colorScheme.error
            ) {
                deleteMessage = message
                menuMessage = null
            }
        }
    }

    // ── Confirmations ──
    deleteConversation?.let { conversation ->
        EmailConfirmDialog(
            title = stringResource(R.string.msg_delete_conversation),
            message = stringResource(R.string.msg_delete_conversation_message, conversation.title),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.deleteThread(context, conversation)
                deleteConversation = null
            },
            onDismiss = { deleteConversation = null }
        )
    }

    deleteMessage?.let { message ->
        EmailConfirmDialog(
            title = stringResource(R.string.msg_delete_message),
            message = stringResource(R.string.msg_delete_message_message),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.deleteMessage(context, message)
                deleteMessage = null
            },
            onDismiss = { deleteMessage = null }
        )
    }

    detailsMessage?.let { message ->
        MessageDetailsDialog(
            title = stringResource(R.string.msg_details),
            lines = messageDetails(message),
            onDismiss = { detailsMessage = null }
        )
    }
}

// ── Small pieces of the hub ─────────────────────────────────────────────────

@Composable
private fun EmptyDetailPane() {
    val zuneColors = LocalZuneColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = null,
                tint = zuneColors.textDim,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.msg_pick_conversation),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.textMuted
            )
        }
    }
}

/** A message the search found, with the number it came from above it. */
@Composable
private fun SearchResultRow(message: SmsMessageModel, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = message.address.ifBlank { stringResource(R.string.hub_messaging) },
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textDim
        )
        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun messageDetails(message: SmsMessageModel): List<String> {
    val stamp = remember(message.timestamp) {
        SimpleDateFormat("dd MMMM yyyy HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }
    val delivery = when (message.delivery) {
        com.serkantkn.zunelauncher.data.model.MessageDelivery.SENDING -> stringResource(R.string.msg_sending)
        com.serkantkn.zunelauncher.data.model.MessageDelivery.SENT -> stringResource(R.string.msg_sent)
        com.serkantkn.zunelauncher.data.model.MessageDelivery.DELIVERED -> stringResource(R.string.msg_delivered)
        com.serkantkn.zunelauncher.data.model.MessageDelivery.FAILED -> stringResource(R.string.msg_failed)
        com.serkantkn.zunelauncher.data.model.MessageDelivery.NONE -> ""
    }
    val count = messageCount(message.body)
    return buildList {
        add(stamp)
        if (delivery.isNotBlank()) add(delivery)
        if (message.address.isNotBlank()) add(message.address)
        add(stringResource(R.string.msg_details_length, count.characters, count.segments))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
}

/** Asks Android to make the launcher the phone's messaging app. */
private fun askToBeDefault(context: Context) {
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                context.startActivity(
                    roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
        }
        context.startActivity(
            Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private const val HINGE_MILLIS = 320
