package com.serkantkn.zunelauncher.ui.screens.email

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.data.model.EmailDraft
import com.serkantkn.zunelauncher.data.model.EmailFolder
import com.serkantkn.zunelauncher.data.model.EmailFolderType
import com.serkantkn.zunelauncher.data.model.EmailMessage
import com.serkantkn.zunelauncher.data.repository.EmailBridge
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.notes.NotesEmptyState
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Screens that hinge in over the hub (phones) or dock on the right (tablets). */
private sealed interface EmailSubScreen {
    data object Read : EmailSubScreen
    data object Compose : EmailSubScreen
    data class Setup(val account: EmailAccount?) : EmailSubScreen
    data object Settings : EmailSubScreen
    data object Drafts : EmailSubScreen
}

private const val PAGE_ALL = 0
private const val PAGE_UNREAD = 1
private const val PAGE_FLAGGED = 2
private const val PAGE_FOLDERS = 3

/**
 * Email Hub. Canonical hub skeleton: ZuneHubEntranceLayout -> 18sp header (account name) ->
 * ZunePivotTabs (tümü / okunmamış / işaretli / klasörler) -> ZuneLoopingPager ->
 * WindowsPhoneBottomBar. Reader, composer, account setup, drafts and settings are hinge
 * sub-screens on phones and a docked right pane on tablets.
 */
@Composable
fun EmailHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmailHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val accounts by viewModel.accounts.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val currentAccount by viewModel.currentAccount.collectAsState()
    val isUnified by viewModel.isUnified.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val currentFolderModel by viewModel.currentFolderModel.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchOpen by viewModel.isSearchOpen.collectAsState()
    val serverResults by viewModel.serverResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selectedKeys by viewModel.selectedKeys.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val openMessage by viewModel.openMessage.collectAsState()
    val isLoadingBody by viewModel.isLoadingBody.collectAsState()
    val drafts by viewModel.drafts.collectAsState()
    val composeDraft by viewModel.composeDraft.collectAsState()
    val pendingRequest by EmailBridge.pending.collectAsState()
    val isReady by viewModel.isReady.collectAsState()

    val hasAccounts = accounts.isNotEmpty()
    val tabs: List<String> = buildList {
        add(stringResource(R.string.common_all))
        add(stringResource(R.string.email_tab_unread))
        add(stringResource(R.string.email_tab_flagged))
        if (!isUnified) add(stringResource(R.string.email_tab_folders))
    }
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val currentPage = pager.currentPage

    // ── Sub-screen (hinge) state ─────────────────────────────────────────
    var subScreen by remember { mutableStateOf<EmailSubScreen?>(null) }
    val hingeAnim = remember { Animatable(0f) }
    var showMoveDialogFor by remember { mutableStateOf<Set<String>?>(null) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun showSub(screen: EmailSubScreen) {
        subScreen = screen
        if (!isWideScreen) coroutineScope.launch {
            hingeAnim.animateTo(1f, tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing))
        }
    }

    fun closeSub() {
        val current = subScreen ?: return
        when (current) {
            EmailSubScreen.Compose -> viewModel.saveDraftAndClose()
            EmailSubScreen.Read -> viewModel.closeMessage()
            else -> {}
        }
        if (isWideScreen) {
            subScreen = null
        } else {
            coroutineScope.launch {
                hingeAnim.animateTo(0f, tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing))
                subScreen = null
            }
        }
    }

    // Reader / composer follow the ViewModel state (opened directly or through the bridge).
    LaunchedEffect(openMessage) {
        if (openMessage != null && subScreen != EmailSubScreen.Read) showSub(EmailSubScreen.Read)
        if (openMessage == null && subScreen == EmailSubScreen.Read) closeSub()
    }
    LaunchedEffect(composeDraft) {
        if (composeDraft != null && subScreen != EmailSubScreen.Compose) showSub(EmailSubScreen.Compose)
        if (composeDraft == null && subScreen == EmailSubScreen.Compose) closeSub()
    }
    LaunchedEffect(pendingRequest, isReady) {
        if (pendingRequest != null && isReady) {
            val request = viewModel.consumeBridgeRequest()
            if (request is EmailBridge.Request.Compose && !hasAccounts) showSub(EmailSubScreen.Setup(null))
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (hasAccounts) viewModel.sync() }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) { delay(2600); viewModel.clearStatus() }
    }

    BackHandler(enabled = true) {
        when {
            subScreen != null -> closeSub()
            isSelectionMode -> viewModel.exitSelectionMode()
            isSearchOpen -> viewModel.closeSearch()
            currentFolder != EmailFolder.INBOX_NAME && !isUnified -> viewModel.selectFolder(EmailFolder.INBOX_NAME)
            else -> onBack()
        }
    }

    // ── Visible list ─────────────────────────────────────────────────────
    val listBase: List<EmailMessage> = remember(messages, searchQuery, serverResults) {
        if (searchQuery.isBlank()) messages else {
            val q = searchQuery.trim()
            val local = messages.filter { it.subject.contains(q, true) || it.from.display.contains(q, true) || it.from.address.contains(q, true) || it.snippet.contains(q, true) }
            val known = local.map { it.key }.toSet()
            (local + serverResults.orEmpty().filter { it.key !in known }).sortedByDescending { it.date }
        }
    }
    fun listFor(page: Int): List<EmailMessage> = when (page) {
        PAGE_UNREAD -> listBase.filter { !it.isRead }
        PAGE_FLAGGED -> listBase.filter { it.isFlagged }
        else -> listBase
    }
    val visibleKeys = listFor(currentPage).map { it.key }

    // ── Bottom bar ───────────────────────────────────────────────────────
    val bottomBarActions: List<WpBarAction> = when {
        !hasAccounts -> listOf(WpBarAction(Icons.Default.Add, stringResource(R.string.email_add_account)) { showSub(EmailSubScreen.Setup(null)) })
        isSelectionMode -> {
            val selectedMessages = listBase.filter { it.key in selectedKeys }
            val anyUnread = selectedMessages.any { !it.isRead }
            val anyUnflagged = selectedMessages.any { !it.isFlagged }
            listOf(
                WpBarAction(Icons.Default.MarkEmailRead, stringResource(if (anyUnread) R.string.email_mark_read else R.string.email_mark_unread)) { if (selectedKeys.isNotEmpty()) viewModel.setRead(selectedKeys, anyUnread) },
                WpBarAction(Icons.Default.Flag, stringResource(if (anyUnflagged) R.string.email_flag else R.string.email_unflag)) { if (selectedKeys.isNotEmpty()) viewModel.setFlagged(selectedKeys, anyUnflagged) },
                WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) { if (selectedKeys.isNotEmpty()) showDeleteDialog = true },
                WpBarAction(Icons.Default.Close, stringResource(R.string.common_cancel)) { viewModel.exitSelectionMode() }
            )
        }
        else -> listOf(
            WpBarAction(Icons.Default.Add, stringResource(R.string.common_new)) { viewModel.newMessage() },
            WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) { viewModel.toggleSearch() },
            WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() },
            WpBarAction(Icons.Default.Sync, stringResource(R.string.email_sync)) { viewModel.sync(force = true) }
        )
    }
    val bottomBarMenuItems: List<WpBarMenuItem> = when {
        !hasAccounts -> emptyList()
        isSelectionMode -> buildList {
            add(WpBarMenuItem(stringResource(R.string.common_select_all)) { viewModel.selectAll(visibleKeys) })
            if (!isUnified) add(WpBarMenuItem(stringResource(R.string.email_move)) { if (selectedKeys.isNotEmpty()) showMoveDialogFor = selectedKeys })
            add(WpBarMenuItem(stringResource(R.string.email_archive)) { if (selectedKeys.isNotEmpty()) viewModel.archive(selectedKeys) })
            add(WpBarMenuItem(stringResource(R.string.email_mark_spam)) { if (selectedKeys.isNotEmpty()) viewModel.markSpam(selectedKeys) })
        }
        else -> buildList {
            add(WpBarMenuItem(stringResource(R.string.email_accounts)) { showAccountPicker = true })
            add(WpBarMenuItem(stringResource(R.string.email_drafts_count, drafts.size)) { showSub(EmailSubScreen.Drafts) })
            if (!isUnified && currentFolder != EmailFolder.INBOX_NAME) add(WpBarMenuItem(stringResource(R.string.email_folder_inbox)) { viewModel.selectFolder(EmailFolder.INBOX_NAME) })
            add(WpBarMenuItem(stringResource(R.string.common_settings)) { showSub(EmailSubScreen.Settings) })
        }
    }

    val hubTitle = when {
        isSelectionMode -> stringResource(R.string.notes_selected_count, selectedKeys.size)
        !hasAccounts -> stringResource(R.string.hub_email)
        isUnified -> stringResource(R.string.email_all_inboxes)
        currentFolder != EmailFolder.INBOX_NAME -> "${currentAccount?.shortName ?: ""} · ${folderTitle(currentFolderModel)}"
        else -> currentAccount?.shortName ?: stringResource(R.string.hub_email)
    }
    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    // ── Pages ────────────────────────────────────────────────────────────
    @Composable
    fun MessageListPage(page: Int) {
        val list = listFor(page)
        ZunePageTransition {
            if (list.isEmpty()) {
                NotesEmptyState(
                    icon = Icons.Default.Mail,
                    title = when {
                        searchQuery.isNotBlank() -> stringResource(R.string.notes_no_results)
                        isSyncing -> stringResource(R.string.email_syncing)
                        page == PAGE_UNREAD -> stringResource(R.string.email_empty_unread)
                        page == PAGE_FLAGGED -> stringResource(R.string.email_empty_flagged)
                        else -> stringResource(R.string.email_empty_folder)
                    },
                    subtitle = when {
                        searchQuery.isNotBlank() && serverResults == null -> stringResource(R.string.email_search_server_hint)
                        else -> stringResource(R.string.email_empty_hint)
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal, top = 4.dp, bottom = 24.dp)
                ) {
                    items(list, key = { it.key }) { message ->
                        EmailMessageRow(
                            message = message,
                            isSelectionMode = isSelectionMode,
                            isSelected = message.key in selectedKeys,
                            showAccountBadge = isUnified,
                            accountName = if (isUnified) accounts.firstOrNull { it.id == message.accountId }?.shortName else null,
                            onClick = { if (isSelectionMode) viewModel.toggleSelected(message.key) else viewModel.open(message) },
                            onLongClick = { if (!isSelectionMode) viewModel.enterSelectionMode(message.key) }
                        )
                    }
                    if (!isUnified && searchQuery.isBlank() && page == PAGE_ALL && list.size >= 20) {
                        item(key = "load_more") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoadingMore) { viewModel.loadMore() }.padding(vertical = 16.dp)
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(color = zuneColors.accentColor, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = stringResource(if (isLoadingMore) R.string.common_loading else R.string.email_load_more),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = zuneColors.accentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FoldersPage() {
        ZunePageTransition {
            if (folders.isEmpty()) {
                NotesEmptyState(icon = Icons.Default.Mail, title = stringResource(R.string.email_syncing), subtitle = stringResource(R.string.email_empty_hint))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal, top = 4.dp, bottom = 24.dp)
                ) {
                    items(folders, key = { it.fullName }) { folder ->
                        val isCurrent = folder.fullName == currentFolder
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectFolder(folder.fullName)
                                    coroutineScope.launch { pager.animateScrollToPage(PAGE_ALL) }
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderTitle(folder),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
                                    color = if (isCurrent) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground
                                )
                                if (folder.type != EmailFolderType.OTHER && !folder.isInbox && folders.count { it.type == folder.type } > 1) {
                                    Text(text = folder.fullName, style = MaterialTheme.typography.bodySmall, color = zuneColors.textMuted)
                                }
                            }
                            if (folder.unreadCount > 0 && folder.type != EmailFolderType.TRASH && folder.type != EmailFolderType.SENT) {
                                Text(text = folder.unreadCount.toString(), style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun WelcomePage() {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 24.dp)) {
            Text(text = stringResource(R.string.email_welcome_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp), color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.email_welcome_message), style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.email_add_account),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = zuneColors.accentColor,
                modifier = Modifier.clickable { showSub(EmailSubScreen.Setup(null)) }.padding(vertical = 6.dp)
            )
        }
    }

    @Composable
    fun ColumnScopePages(modifier: Modifier) {
        Box(modifier = modifier.fillMaxWidth()) {
            if (!hasAccounts) {
                WelcomePage()
            } else {
                ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !isSelectionMode) { page ->
                    if (page == PAGE_FOLDERS) FoldersPage() else MessageListPage(page)
                }
            }
        }
    }

    @Composable
    fun SearchRow() {
        AnimatedVisibility(visible = isSearchOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                ZuneSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholder = stringResource(R.string.email_search_hint),
                    modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp)
                )
                if (searchQuery.length >= 2) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
                        if (isSearching) {
                            CircularProgressIndicator(color = zuneColors.accentColor, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(if (serverResults == null) R.string.email_search_server else R.string.email_search_server_again),
                            style = MaterialTheme.typography.labelLarge,
                            color = zuneColors.accentColor,
                            modifier = Modifier.clickable(enabled = !isSearching) { viewModel.searchOnServer() }.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun SubScreenContent(bottomBarModifier: Modifier) {
        when (val screen = subScreen) {
            EmailSubScreen.Read -> openMessage?.let { message ->
                EmailReadScreen(
                    message = message,
                    isLoading = isLoadingBody,
                    viewModel = viewModel,
                    onClose = { closeSub() },
                    onReply = { all -> viewModel.reply(message, all) },
                    onForward = { viewModel.forward(message) },
                    onMove = { showMoveDialogFor = setOf(message.key) },
                    bottomBarModifier = bottomBarModifier
                )
            }
            EmailSubScreen.Compose -> composeDraft?.let { draft ->
                EmailComposeScreen(draft = draft, viewModel = viewModel, onClose = { if (subScreen == EmailSubScreen.Compose) closeSub() }, bottomBarModifier = bottomBarModifier)
            }
            is EmailSubScreen.Setup -> EmailAccountSetupScreen(existing = screen.account, viewModel = viewModel, onClose = { closeSub() }, bottomBarModifier = bottomBarModifier)
            EmailSubScreen.Settings -> EmailSettingsScreen(
                accounts = accounts,
                viewModel = viewModel,
                onClose = { closeSub() },
                onAddAccount = { subScreen = EmailSubScreen.Setup(null) },
                onEditAccount = { subScreen = EmailSubScreen.Setup(it) },
                bottomBarModifier = bottomBarModifier
            )
            EmailSubScreen.Drafts -> EmailDraftsScreen(
                drafts = drafts,
                accounts = accounts,
                onOpen = { viewModel.openDraft(it) },
                onDelete = { viewModel.deleteDraft(it.id) },
                onClose = { closeSub() },
                bottomBarModifier = bottomBarModifier
            )
            null -> {}
        }
    }

    val subScreenHingeProgress = hingeAnim.value

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Hub main screen (hinges out when a sub-screen opens on phones)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = subScreenHingeProgress
                    rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * p
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                    alpha = (1f - p * 1.5f).coerceIn(0f, 1f)
                }
        ) {
            ZuneHubEntranceLayout { bottomBarModifier ->
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                ZuneWideHubTitle(text = hubTitle, bottomPadding = 8.dp)
                                if (hasAccounts) ZunePivotTabs(tabs = tabs, state = pager, fontSize = 40.sp, modifier = Modifier.padding(start = 48.dp))
                                SearchRow()
                                ColumnScopePages(Modifier.weight(1f))
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                            WindowsPhoneBottomBar(
                                modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
                                actions = bottomBarActions,
                                menuItems = bottomBarMenuItems
                            )
                        }
                        Box(modifier = Modifier.weight(0.55f).fillMaxHeight()) {
                            if (subScreen != null) SubScreenContent(bottomBarModifier)
                            else NotesEmptyState(icon = Icons.Default.Mail, title = stringResource(R.string.email_pick_one), subtitle = stringResource(R.string.email_pick_one_hint))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp, start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal)) {
                                    Text(
                                        text = hubTitle,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 1.sp),
                                        color = headerColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.weight(1f).clickable(enabled = hasAccounts) { showAccountPicker = true }
                                    )
                                    if (isSyncing) CircularProgressIndicator(color = zuneColors.accentColor, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                }
                                if (hasAccounts) ZunePivotTabs(tabs = tabs, state = pager, modifier = Modifier.padding(top = 4.dp))
                            }
                            SearchRow()
                            ColumnScopePages(Modifier.weight(1f))
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                        WindowsPhoneBottomBar(
                            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
                            actions = bottomBarActions,
                            menuItems = bottomBarMenuItems
                        )
                    }
                }
            }
        }

        // 2. Sub-screen hinging in (phones only)
        if (!isWideScreen && (subScreen != null || hingeAnim.value > 0f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = hingeAnim.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                SubScreenContent(Modifier)
            }
        }

        // 3. Status banner
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
                Text(text = statusMessage ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Color.White)
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────
    if (showAccountPicker) {
        EmailAccountPickerDialog(
            accounts = accounts,
            selectedId = selectedAccountId,
            onSelect = { viewModel.selectAccount(it); showAccountPicker = false },
            onAddAccount = { showAccountPicker = false; showSub(EmailSubScreen.Setup(null)) },
            onDismiss = { showAccountPicker = false }
        )
    }
    showMoveDialogFor?.let { keys ->
        EmailFolderPickerDialog(
            folders = folders,
            currentFolder = currentFolder,
            onSelect = { folder -> viewModel.move(keys, folder.fullName); showMoveDialogFor = null },
            onDismiss = { showMoveDialogFor = null }
        )
    }
    if (showDeleteDialog) {
        val isTrash = currentFolderModel?.type == EmailFolderType.TRASH
        EmailConfirmDialog(
            title = stringResource(R.string.email_delete_cap),
            message = if (isTrash) stringResource(R.string.email_delete_forever_message, selectedKeys.size) else stringResource(R.string.email_delete_message, selectedKeys.size),
            confirmText = stringResource(R.string.files_delete_cap),
            onConfirm = { showDeleteDialog = false; viewModel.delete(selectedKeys) },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/** Local drafts list. Tap opens the composer, the trash icon deletes. */
@Composable
private fun EmailDraftsScreen(
    drafts: List<EmailDraft>,
    accounts: List<EmailAccount>,
    onOpen: (EmailDraft) -> Unit,
    onDelete: (EmailDraft) -> Unit,
    onClose: () -> Unit,
    bottomBarModifier: Modifier
) {
    val zuneColors = LocalZuneColors.current
    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 56.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.hub_email),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 1.sp),
                    color = headerColor,
                    modifier = Modifier.clickable { onClose() }
                )
                Text(
                    text = "  >  " + stringResource(R.string.email_folder_drafts),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp, letterSpacing = 1.sp),
                    color = zuneColors.textMuted
                )
            }
            if (drafts.isEmpty()) {
                NotesEmptyState(icon = Icons.Default.Drafts, title = stringResource(R.string.email_no_drafts), subtitle = stringResource(R.string.email_no_drafts_hint), modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(drafts, key = { it.id }) { draft ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpen(draft) }.padding(vertical = 10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = draft.to.ifBlank { stringResource(R.string.email_no_recipient) },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1
                                )
                                Text(
                                    text = draft.subject.ifBlank { stringResource(R.string.email_no_subject) },
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    color = zuneColors.accentColor,
                                    maxLines = 1
                                )
                                Text(
                                    text = (accounts.firstOrNull { it.id == draft.accountId }?.shortName ?: "") + " · " + formatListDate(draft.updatedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = zuneColors.textMuted
                                )
                            }
                            Text(
                                text = stringResource(R.string.common_delete),
                                style = MaterialTheme.typography.labelLarge,
                                color = zuneColors.textMuted,
                                modifier = Modifier.clickable { onDelete(draft) }.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
        WindowsPhoneBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
            actions = listOf(WpBarAction(Icons.Default.Close, stringResource(R.string.common_close)) { onClose() })
        )
    }
}
