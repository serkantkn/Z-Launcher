package com.serkantkn.zunelauncher.ui.screens.messaging

import com.serkantkn.zunelauncher.ui.animation.pageHingeIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.SmsConversationModel
import com.serkantkn.zunelauncher.data.model.SmsMessageModel
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHub
import com.serkantkn.zunelauncher.ui.components.ZuneHubBar
import com.serkantkn.zunelauncher.ui.components.ZuneHubPane
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.email.EmailConfirmDialog
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.PhoneCaller
import com.serkantkn.zunelauncher.util.PhoneNumbers
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
    val zuneMotion = com.serkantkn.zunelauncher.ui.animation.LocalZuneMotion.current
    val zunePageSlidePx = with(density) { com.serkantkn.zunelauncher.ui.animation.ZUNE_PAGE_SLIDE.toPx() }
    val hubFlight = com.serkantkn.zunelauncher.ui.animation.LocalZuneHubFlight.current
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
    val conversationFind by viewModel.conversationFind.collectAsState()
    val downloadingMms by com.serkantkn.zunelauncher.data.repository.MmsTransport.downloading.collectAsState()
    // Pictures and clips chosen for the next message; with any, it goes as a picture message.
    var composeAttachments by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val pickMedia = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)
    ) { picked -> composeAttachments = (composeAttachments + picked).distinct().take(MAX_ATTACHMENTS) }
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
    /** A group conversation's people, offered one at a time when "call" is pressed. */
    var callChooser by remember { mutableStateOf<SmsConversationModel?>(null) }
    /** On a tablet the new-message picker takes the pane beside the list, not the whole hub. */
    var widePicker by remember { mutableStateOf(false) }
    /** Choosing several conversations at once, and which are chosen. */
    var selecting by remember { mutableStateOf(false) }
    var chosenThreads by remember { mutableStateOf(emptySet<Long>()) }
    var deleteChosen by remember { mutableStateOf(false) }
    val stopSelecting: () -> Unit = {
        selecting = false
        chosenThreads = emptySet()
    }

    // The list's stamps say "18:40", "yesterday", "Tuesday"; they have to age as the hub stays open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }

    // A tablet's headings carry search as a page of its own, as the calendar's do; a phone keeps
    // it as a box that drops in over the list.
    val tabs = buildList {
        add(stringResource(R.string.msg_tab_all))
        add(stringResource(R.string.msg_tab_unread))
        add(stringResource(R.string.msg_tab_drafts))
        if (isWideScreen) add(stringResource(R.string.common_search))
    }
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

    // The hub stays composed while the launcher is away, and the messaging role, the permissions
    // and the store itself can all change out there; they are read again on the way back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkPermissionAndLoad(context) }

    // The system's own "make this the messaging app" sheet. Its answer arrives here, where the
    // banner can be taken down the moment the role is held — not on the next cold start.
    // SMS is Google Play's to hand out only to the phone's messaging app: the role comes first, and
    // Android grants SMS with it. Contacts and the SIMs, which the role leaves out, are asked after.
    var permissionsAfterRole by remember { mutableStateOf(false) }
    val requestMessagePermissions: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_CONTACTS,
                // Without this the phone will not name its SIMs, and the
                // chooser for which one to send from never appears.
                Manifest.permission.READ_PHONE_STATE
            )
        )
    }
    val roleRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermissionAndLoad(context)
        if (permissionsAfterRole && com.serkantkn.zunelauncher.data.repository.SmsRepository.isDefaultSmsApp(context)) requestMessagePermissions()
        permissionsAfterRole = false
    }
    val askToBeDefault: () -> Unit = {
        defaultSmsRequest(context)?.let { intent ->
            runCatching { roleRequest.launch(intent) }
        }
    }

    // Ringing one of several people: the chooser, or the only one there is.
    val callConversation: (SmsConversationModel) -> Unit = { conversation ->
        val people = conversation.recipients.filter { it.isNotBlank() }
        when {
            people.size > 1 -> callChooser = conversation
            else -> PhoneCaller.tap(context, conversation.address, conversation.title)
        }
    }

    val openPicker: () -> Unit = {
        if (isWideScreen) {
            widePicker = true
        } else if (!isTransitioning) {
            isTransitioning = true
            coroutineScope.launch {
                hubHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                pickerHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val closePicker: () -> Unit = {
        if (isWideScreen) {
            widePicker = false
            pendingForward = null
        } else if (!isTransitioning) {
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
        // On a tablet another conversation can be opened straight from the list while one is
        // open; what was typed in that one is kept as its draft, as closing it would have.
        val previous = selectedConv
        if (previous != null && previous.threadId != conversation.threadId) {
            viewModel.saveDraft(context, composeText)
        }
        widePicker = false
        if (previous?.threadId != conversation.threadId) composeAttachments = emptyList()
        composeText = pendingForward ?: conversation.draft
        pendingForward = null
        viewModel.openConversation(context, conversation)
        if (!isWideScreen && !isTransitioning) {
            isTransitioning = true
            // The Zune layout: the name touched flies up to become the conversation's title, and
            // the board and the conversation cross under it rather than one after the other.
            val flies = zuneMotion && hubFlight?.openPage(
                conversationTitleKey(conversation.threadId),
                conversation.title.lowercase(java.util.Locale.getDefault()),
                conversationItemId(conversation.threadId)
            ) == true
            coroutineScope.launch {
                if (flies) {
                    launch { hubHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing)) }
                } else {
                    hubHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                }
                detailHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    // The hinges are set to what is open whenever the hub comes onto the screen, and whenever the
    // window changes between a phone's and a tablet's width - a foldable opened or closed, a split
    // screen dragged wider. A phone turns the hub away on a hinge to show a conversation or the
    // picker; a tablet shows them beside it and has no hinge. A turn cut short - the hub moving
    // into place on the screen as it was being opened from a notification, a pinned tile or a
    // message link - or a layout with no hinge left the hub turned away over nothing: a blank
    // screen.
    LaunchedEffect(isWideScreen) {
        if (isWideScreen) {
            if (pickerHingeAnim.value > 0f) widePicker = true
            pickerHingeAnim.snapTo(0f)
            detailHingeAnim.snapTo(0f)
            hubHingeAnim.snapTo(1f)
        } else {
            when {
                widePicker || pickerHingeAnim.value > 0f -> {
                    widePicker = false
                    hubHingeAnim.snapTo(0f)
                    pickerHingeAnim.snapTo(1f)
                }
                selectedConv != null -> {
                    hubHingeAnim.snapTo(0f)
                    detailHingeAnim.snapTo(1f)
                }
                else -> {
                    detailHingeAnim.snapTo(0f)
                    hubHingeAnim.snapTo(1f)
                }
            }
        }
        isTransitioning = false
    }

    val closeConversation: () -> Unit = {
        // Whatever was typed and not sent stays under the conversation rather than disappearing.
        viewModel.saveDraft(context, composeText)
        if (isWideScreen) {
            viewModel.closeConversation()
            composeText = ""
            composeAttachments = emptyList()
        } else if (!isTransitioning) {
            isTransitioning = true
            // The Zune layout: the title flies back down to the name it came from, and the
            // conversation and the board cross under it.
            val conversation = selectedConv
            val flies = zuneMotion && conversation != null && hubFlight?.closePage(
                conversationTitleKey(conversation.threadId),
                conversation.title.lowercase(java.util.Locale.getDefault())
            ) == true
            coroutineScope.launch {
                val boardBack = if (flies) launch { hubHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing)) } else null
                detailHingeAnim.animateTo(0f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                viewModel.closeConversation()
                composeText = ""
                composeAttachments = emptyList()
                if (boardBack != null) boardBack.join()
                else hubHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
                isTransitioning = false
            }
        }
    }

    val onRecipientsChosen: (List<Pair<String, String>>) -> Unit = { people ->
        val forwarded = pendingForward
        val previous = selectedConv
        if (previous != null) viewModel.saveDraft(context, composeText)
        viewModel.openConversationWithContacts(context, people)
        composeText = forwarded.orEmpty()
        pendingForward = null
        if (isWideScreen) {
            widePicker = false
        } else {
            coroutineScope.launch {
                pickerHingeAnim.snapTo(0f)
                hubHingeAnim.snapTo(0f)
                detailHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
            }
        }
    }

    // A picture, a clip or a card from a picture message, handed to whatever opens that sort.
    val onOpenAttachment: (com.serkantkn.zunelauncher.data.model.MmsAttachment) -> Unit = { attachment ->
        coroutineScope.launch {
            if (!openAttachment(context, attachment)) {
                viewModel.showStatus(context.getString(R.string.msg_attachment_open_failed))
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
        if (request.address.isNotBlank()) {
            // The hub is often still settling into the screen when a request arrives, and this
            // can be cancelled half way; the request is let go of only once it has been seen
            // through, so a second pass finishes what the first began.
            if (viewModel.selectedConversation.value?.let { PhoneNumbers.sameNumber(it.address, request.address) } != true) {
                viewModel.openConversationWithContact(context, "", request.address)
            }
            composeText = request.body
            pendingForward = null
            if (!isWideScreen) {
                hubHingeAnim.snapTo(0f)
                detailHingeAnim.animateTo(1f, tween(HINGE_MILLIS, easing = FastOutSlowInEasing))
            }
            MessagingBridge.consumeCompose()
        } else {
            MessagingBridge.consumeCompose()
            pendingForward = request.body.takeIf { it.isNotBlank() }
            openPicker()
        }
    }

    BackHandler(
        enabled = menuConversation != null || menuMessage != null ||
            pickerHingeAnim.value > 0f || widePicker || selecting ||
            (!isWideScreen && detailHingeAnim.value > 0f) ||
            (isWideScreen && selectedConv != null) ||
            isSearchActive
    ) {
        when {
            menuMessage != null -> menuMessage = null
            menuConversation != null -> menuConversation = null
            selecting -> stopSelecting()
            pickerHingeAnim.value > 0f || widePicker -> closePicker()
            !isWideScreen && detailHingeAnim.value > 0f -> closeConversation()
            isWideScreen && selectedConv != null -> closeConversation()
            isSearchActive -> {
                isSearchActive = false
                viewModel.clearSearch()
            }
        }
    }

    // ── The bar ──
    // Choosing several turns the bar over to what can be done to them, as Windows Phone did.
    val bottomBarActions = if (selecting) {
        listOf(
            WpBarAction(Icons.Default.MarkChatRead, stringResource(R.string.msg_mark_read)) {
                viewModel.markThreadsRead(context, chosenThreads)
                stopSelecting()
            },
            WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) {
                if (chosenThreads.isNotEmpty()) deleteChosen = true
            }
        )
    } else {
        listOf(
            WpBarAction(Icons.Default.Add, stringResource(R.string.msg_new)) { openPicker() },
            WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
                if (isWideScreen) {
                    // The tablet's search is a heading of its own.
                    coroutineScope.launch { pager.scrollToPage(tabs.lastIndex) }
                } else {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) viewModel.clearSearch()
                }
            },
            WpBarAction(Icons.Default.Checklist, stringResource(R.string.msg_select)) {
                selecting = true
                chosenThreads = emptySet()
            }
        )
    }
    val bottomBarMenuItems = buildList {
        if (selecting) {
            add(WpBarMenuItem(stringResource(R.string.common_select_all)) {
                val page = pager.currentPage.coerceAtMost(MessageTab.entries.lastIndex)
                chosenThreads = viewModel.conversationsFor(MessageTab.entries[page]).map { it.threadId }.toSet()
            })
        } else {
            add(WpBarMenuItem(stringResource(R.string.msg_mark_all_read)) { viewModel.markAllRead(context) })
            add(WpBarMenuItem(stringResource(R.string.common_refresh)) { viewModel.loadConversations(context) })
            if (!isDefaultApp) {
                add(WpBarMenuItem(stringResource(R.string.msg_make_default)) { askToBeDefault() })
            }
        }
    }

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
        val toggle: (SmsConversationModel) -> Unit = { conversation ->
            chosenThreads = if (conversation.threadId in chosenThreads) {
                chosenThreads - conversation.threadId
            } else {
                chosenThreads + conversation.threadId
            }
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
                            onClick = { if (selecting) toggle(conversation) else openConversation(conversation) },
                            onLongClick = { if (selecting) toggle(conversation) else menuConversation = conversation },
                            selecting = selecting,
                            checked = conversation.threadId in chosenThreads,
                            highlighted = isWideScreen && !selecting && !widePicker &&
                                selectedConv?.threadId == conversation.threadId
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
                    onLongClick = { menuConversation = conversation },
                    highlighted = isWideScreen && selectedConv?.threadId == conversation.threadId
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
                val owner = viewModel.conversationOf(message)
                SearchResultRow(
                    message = message,
                    from = owner?.title ?: message.address,
                    query = searchQuery,
                    now = now,
                    onClick = {
                        owner?.let {
                            viewModel.openAtMessage(message, searchQuery)
                            openConversation(it)
                        }
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

    /** The notices that stand over the list: not the messaging app, or not allowed to read. */
    @Composable
    fun ListNotices() {
        if (!isDefaultApp && hasPermission) {
            DefaultAppNotice(onMakeDefault = { askToBeDefault() })
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (selecting) {
            Text(
                text = pluralStringResource(R.plurals.msg_selected_count, chosenThreads.size, chosenThreads.size),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.accentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }

    @Composable
    fun PermissionCard() {
        MessagePermissionCard(
            needsDefault = !isDefaultApp,
            onGrant = {
                if (isDefaultApp) {
                    requestMessagePermissions()
                } else {
                    permissionsAfterRole = true
                    askToBeDefault()
                }
            }
        )
    }
    /** One of a tablet's headings: a list of conversations, or the search. */
    @Composable
    fun WidePage(index: Int) {
        Column(modifier = Modifier.fillMaxSize()) {
            ListNotices()
            when {
                !hasPermission -> PermissionCard()
                index >= MessageTab.entries.size -> {
                    MessageSearchBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(context, it) },
                        isVisible = pager.currentPage == index,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (searchQuery.isNotBlank()) SearchResultsPage()
                }
                else -> ConversationsPage(MessageTab.entries[index])
            }
        }
    }

    // ── Long presses, and which of a group to ring ──
    // On a phone these come up from the bottom of the screen; on a tablet from the bottom of the
    // pane beside the list, leaving the list itself in view.
    @Composable
    fun MenuSheets() {
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
                        callConversation(conversation)
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

        callChooser?.let { conversation ->
            MessagingSheet(title = stringResource(R.string.msg_pick_recipient_to_call), onDismiss = { callChooser = null }) {
                conversation.recipients.filter { it.isNotBlank() }.forEach { number ->
                    val name = remember(number) { com.serkantkn.zunelauncher.data.repository.SmsRepository.contactNameFor(context, number) }
                    SheetAction(if (name == number) number else "$name · $number") {
                        callChooser = null
                        PhoneCaller.tap(context, number, name)
                    }
                }
            }
        }
    }

    @Composable
    fun OpenConversation(conversation: SmsConversationModel, modifier: Modifier = Modifier) {
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
                val attached = composeAttachments
                composeText = ""
                composeAttachments = emptyList()
                if (attached.isNotEmpty()) {
                    viewModel.sendMms(context, conversation.address, text, attached)
                } else {
                    viewModel.sendSms(context, conversation.address, text)
                }
            },
            onClose = closeConversation,
            onCall = { callConversation(conversation) },
            onVisibilityChange = viewModel::setConversationVisible,
            onMessageLongPress = { menuMessage = it },
            onOpenAttachment = onOpenAttachment,
            find = conversationFind?.takeIf { it.threadId == conversation.threadId },
            onFindShown = viewModel::consumeConversationFind,
            attachments = composeAttachments,
            onAttach = {
                pickMedia.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
            },
            onRemoveAttachment = { uri -> composeAttachments = composeAttachments - uri },
            onDownloadMms = { viewModel.downloadMms(context, it) },
            downloadingMms = downloadingMms,
            modifier = modifier
        )
    }

    /**
     * The pane beside a tablet's list: the open conversation, the new-message picker, or a word
     * about what to do. A change slides the new one in from the right as it fades up, the way
     * Windows 8 brought in a page.
     */
    @Composable
    fun WidePane() {
        Box(modifier = Modifier.fillMaxSize()) {
            val paneKey = when {
                widePicker -> "picker"
                selectedConv != null -> "c:" + selectedConv!!.recipients.joinToString(",").ifBlank { selectedConv!!.address }
                else -> "empty"
            }
            AnimatedContent(
                targetState = paneKey,
                transitionSpec = {
                    (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 12 }) togetherWith fadeOut(tween(90))
                },
                label = "messaging_pane",
                modifier = Modifier.fillMaxSize()
            ) { key ->
                val conversation = selectedConv
                when {
                    key == "picker" -> NewMessageScreen(
                        contacts = contacts,
                        onClose = closePicker,
                        onRecipientsChosen = onRecipientsChosen
                    )
                    key.startsWith("c:") && conversation != null -> OpenConversation(conversation)
                    else -> EmptyDetailPane(onNew = openPicker)
                }
            }
            MenuSheets()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // ── The hub board ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = hubHingeAnim.value
                    if (zuneMotion) {
                        // The Zune layout: the board slides off to the left as a page comes in.
                        translationX = -zunePageSlidePx * 1.5f * (1f - p)
                        alpha = p.coerceIn(0f, 1f)
                    } else {
                        rotationY = -90f * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 12f * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
                }
        ) {
            ZuneHub(
                title = stringResource(R.string.hub_messaging),
                zuneTitleKey = "MESSAGING",
                tabs = tabs,
                pager = pager,
                bar = if (hasPermission && detailHingeAnim.value == 0f && pickerHingeAnim.value == 0f) {
                    ZuneHubBar(bottomBarActions, bottomBarMenuItems)
                } else {
                    null
                },
                // A tablet's pages say for themselves when there is no permission, or a search.
                takeover = when {
                    isWideScreen -> null
                    !hasPermission -> { { PhonePadded { PermissionCard() } } }
                    isSearchActive && searchQuery.isNotBlank() -> { { PhonePadded { SearchResultsPage() } } }
                    else -> null
                },
                phoneAboveContent = {
                    Column(modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                        ListNotices()
                        AnimatedVisibility(visible = isSearchActive) {
                            MessageSearchBar(
                                query = searchQuery,
                                onQueryChange = { viewModel.setSearchQuery(context, it) },
                                isVisible = isSearchActive,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                },
                wideListWidth = WIDE_LIST_WIDTH + 32.dp,
                // The open conversation, the picker or a word about what to do; the pane draws
                // its own changes between them.
                widePane = ZuneHubPane(key = Unit, ownsBar = false) { _, _ ->
                    Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp, end = 32.dp, bottom = 12.dp)) { WidePane() }
                }
            ) { index ->
                if (isWideScreen) WidePage(index) else ConversationsPage(MessageTab.entries[index])
            }
        }

        // ── New message, on a phone ──
        if (!isWideScreen && pickerHingeAnim.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageHingeIn(sequential = true) { pickerHingeAnim.value }
            ) {
                NewMessageScreen(
                    contacts = contacts,
                    onClose = closePicker,
                    onRecipientsChosen = onRecipientsChosen
                )
            }
        }

        // ── The conversation, on a phone ──
        if (!isWideScreen && detailHingeAnim.value > 0f && selectedConv != null) {
            val conversation = selectedConv!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageHingeIn(sequential = true) { detailHingeAnim.value }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (zuneColors.isDark) Color(0xFF0C0C0C) else Color(0xFFFAFAFA)
                ) {
                    OpenConversation(conversation, modifier = Modifier.statusBarsPadding())
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

    if (!isWideScreen) MenuSheets()

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

    if (deleteChosen) {
        EmailConfirmDialog(
            title = stringResource(R.string.msg_delete_selected),
            message = pluralStringResource(R.plurals.msg_delete_selected_message, chosenThreads.size, chosenThreads.size),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.deleteThreads(context, chosenThreads)
                deleteChosen = false
                stopSelecting()
            },
            onDismiss = { deleteChosen = false }
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

/**
 * A tablet's pane with nothing open in it: what to do, said in the hub's own type at the top left,
 * and the way to start something new as a word in the accent.
 */
@Composable
private fun EmptyDetailPane(onNew: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.msg_pick_conversation),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 34.sp),
            color = zuneColors.textMuted
        )
        Text(
            text = stringResource(R.string.msg_or_start_new),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            color = zuneColors.accentColor,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(onClick = onNew)
                .padding(vertical = 4.dp)
        )
    }
}

/**
 * A message the search found: who it is with, when, and the line itself with what was searched
 * for picked out in the accent.
 */
@Composable
private fun SearchResultRow(message: SmsMessageModel, from: String, query: String, now: Long, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val stamp = remember(now, message.timestamp) { conversationStamp(now, message.timestamp, Locale.getDefault()) }
    val accent = zuneColors.accentColor
    val body = remember(message.body, query, accent) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(message.body)
            if (query.isNotBlank()) {
                var at = message.body.indexOf(query, ignoreCase = true)
                while (at >= 0) {
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                        at,
                        at + query.length
                    )
                    at = message.body.indexOf(query, at + query.length, ignoreCase = true)
                }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = from.ifBlank { stringResource(R.string.hub_messaging) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stampText(stamp),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = zuneColors.textMuted,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
        com.serkantkn.zunelauncher.data.model.MessageDelivery.READ -> stringResource(R.string.msg_read_receipt)
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

/** The system's "make this the messaging app" prompt, for an activity-result launcher to start. */
private fun defaultSmsRequest(context: Context): Intent? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
            return roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
        }
    }
    return Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        .putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
}

private const val HINGE_MILLIS = 320

/** How wide a tablet's list of conversations is; the open one takes the rest. */
private val WIDE_LIST_WIDTH = 400.dp


/** Something a phone shows in place of the pages, kept off the screen's edges as a page is. */
@Composable
private fun PhonePadded(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) { content() }
}

/** As many pictures and clips as a picture message is sensibly sent with. */
private const val MAX_ATTACHMENTS = 5
