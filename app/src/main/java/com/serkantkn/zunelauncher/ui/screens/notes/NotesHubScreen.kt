package com.serkantkn.zunelauncher.ui.screens.notes

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.repository.NotesBridge
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneAlphabetIndex
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a pivot page shows. Tag pages are generated from the notes' tags. */
private sealed interface TabKind {
    data object Notes : TabKind
    data object Lists : TabKind
    data class Tag(val tag: String) : TabKind
    data object Archive : TabKind
    data object Trash : TabKind
}

/**
 * Notes Hub (stringResource(R.string.hub_notes)).
 *
 * Follows the canonical hub template: ZuneHubEntranceLayout → small header → ZunePivotTabs →
 * infinite ZuneLoopingPager → WindowsPhoneBottomBar. The note editor opens as a sub-screen with
 * the 2-stage 3D door hinge (Settings Hub pattern) on phones, and as a docked right pane on
 * wide screens (Messaging Hub pattern).
 */
@Composable
fun NotesHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val activeNotes by viewModel.activeNotes.collectAsState()
    val checklistNotes by viewModel.checklistNotes.collectAsState()
    val archivedNotes by viewModel.archivedNotes.collectAsState()
    val trashNotes by viewModel.trashNotes.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchOpen by viewModel.isSearchOpen.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val startPinnedNoteIds by viewModel.startPinnedNoteIds.collectAsState()
    val isQuickNoteTileOnStart by viewModel.isQuickNoteTileOnStart.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val pendingRequest by NotesBridge.pending.collectAsState()

    // ── Pivot tabs: fixed pages + one page per tag ────────────────────────
    val tabKinds: List<TabKind> = remember(allTags) {
        listOf(TabKind.Notes, TabKind.Lists) + allTags.map { TabKind.Tag(it) } + listOf(TabKind.Archive, TabKind.Trash)
    }
    val tabNotes = stringResource(R.string.hub_notes)
    val tabLists = stringResource(R.string.notes_tab_lists)
    val tabArchive = stringResource(R.string.notes_tab_archive)
    val tabTrash = stringResource(R.string.notes_tab_trash)
    val tabs: List<String> = remember(tabKinds, tabNotes, tabLists, tabArchive, tabTrash) {
        tabKinds.map {
            when (it) {
                TabKind.Notes -> tabNotes
                TabKind.Lists -> tabLists
                is TabKind.Tag -> "#${it.tag}"
                TabKind.Archive -> tabArchive
                TabKind.Trash -> tabTrash
            }
        }
    }
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val currentTab = pager.currentPage
    val currentKind = tabKinds.getOrElse(currentTab) { TabKind.Notes }

    // ── Editor draft (owned here, persisted on close) ─────────────────────
    var draft by remember { mutableStateOf<Note?>(null) }
    var isDraftNew by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteForeverDialog by remember { mutableStateOf(false) }
    var showClearArchiveDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var localStatus by remember { mutableStateOf<String?>(null) }
    var jumpLetter by remember { mutableStateOf<Pair<Char, Long>?>(null) }

    // 3D Door Hinge transition state for the editor (phone only)
    val editorHingeAnim = remember { Animatable(0f) }
    var isEditorOpen by remember { mutableStateOf(false) }

    fun showEditor(note: Note, isNew: Boolean) {
        // Persist whatever was open before switching notes (tablet pane case)
        draft?.let { previous -> if (previous.id != note.id) viewModel.saveNote(previous) }
        draft = note
        isDraftNew = isNew
        viewModel.openNote(note)
        if (!isWideScreen) {
            isEditorOpen = true
            coroutineScope.launch {
                editorHingeAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    /** Opens the editor, asking for biometric / device credential first when the note is locked. */
    fun openEditor(note: Note, isNew: Boolean) {
        if (note.isLocked && !isNew) {
            authenticateForNote(
                context = context,
                title = note.displayTitle,
                onSuccess = { showEditor(note, false) },
                onFailure = { reason -> localStatus = reason }
            )
        } else {
            showEditor(note, isNew)
        }
    }

    fun closeEditor(save: Boolean = true) {
        val current = draft
        if (save && current != null) viewModel.saveNote(current)
        if (isWideScreen) {
            draft = null
            viewModel.closeEditor()
        } else {
            coroutineScope.launch {
                editorHingeAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(HingeAnimation.DURATION_MS, easing = FastOutSlowInEasing)
                )
                isEditorOpen = false
                draft = null
                viewModel.closeEditor()
            }
        }
    }

    // Requests from other hubs / Start tiles / share sheet
    LaunchedEffect(pendingRequest) {
        if (pendingRequest != null) {
            viewModel.consumeBridgeRequest()?.let { (note, isNew) -> openEditor(note, isNew) }
        }
    }

    // Auto-save an open draft when the launcher goes to the background.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        draft?.let { viewModel.saveNote(it) }
    }

    // Status banner auto-dismiss
    val bannerText = statusMessage ?: localStatus
    LaunchedEffect(bannerText) {
        if (bannerText != null) {
            delay(2600)
            viewModel.clearStatus()
            localStatus = null
        }
    }

    // ── Back handling: editor → selection → search → hub ──────────────────
    BackHandler(enabled = true) {
        when {
            draft != null -> closeEditor()
            isSelectionMode -> viewModel.exitSelectionMode()
            isSearchOpen -> viewModel.closeSearch()
            else -> onBack()
        }
    }

    // ── Backup launchers (SAF) ────────────────────────────────────────────
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.writeBackup(uri)
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri)
    }

    fun notesFor(kind: TabKind): List<Note> = when (kind) {
        TabKind.Notes -> activeNotes
        TabKind.Lists -> checklistNotes
        is TabKind.Tag -> activeNotes.filter { kind.tag in it.tags }
        TabKind.Archive -> archivedNotes
        TabKind.Trash -> trashNotes
    }

    val visibleList = notesFor(currentKind)

    // ── Bottom bar actions per state ──────────────────────────────────────
    val bottomBarActions: List<WpBarAction> = if (isSelectionMode) {
        val selectedCount = selectedIds.size
        if (currentKind == TabKind.Trash) {
            listOf(
                WpBarAction(Icons.Default.Restore, stringResource(R.string.notes_restore)) { if (selectedCount > 0) viewModel.restoreFromTrash(selectedIds) },
                WpBarAction(Icons.Default.DeleteForever, stringResource(R.string.notes_delete_forever)) { if (selectedCount > 0) showDeleteForeverDialog = true },
                WpBarAction(Icons.Default.SelectAll, stringResource(R.string.common_all)) { viewModel.selectAll(visibleList.map { it.id }) },
                WpBarAction(Icons.Default.Close, stringResource(R.string.common_cancel)) { viewModel.exitSelectionMode() }
            )
        } else {
            listOf(
                if (currentKind == TabKind.Archive) {
                    WpBarAction(Icons.Default.Unarchive, stringResource(R.string.common_undo)) { if (selectedCount > 0) viewModel.setArchived(selectedIds, false) }
                } else {
                    WpBarAction(Icons.Default.Archive, stringResource(R.string.notes_archive)) { if (selectedCount > 0) viewModel.setArchived(selectedIds, true) }
                },
                WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) { if (selectedCount > 0) showBulkDeleteDialog = true },
                WpBarAction(Icons.Default.ContentCopy, stringResource(R.string.notes_duplicate)) { if (selectedCount > 0) viewModel.duplicateNotes(selectedIds) },
                WpBarAction(Icons.Default.SelectAll, stringResource(R.string.common_all)) { viewModel.selectAll(visibleList.map { it.id }) },
                WpBarAction(Icons.Default.Close, stringResource(R.string.common_cancel)) { viewModel.exitSelectionMode() }
            )
        }
    } else {
        when (val kind = currentKind) {
            TabKind.Notes -> listOf(
                WpBarAction(Icons.Default.Add, stringResource(R.string.common_new)) { openEditor(Note(), isNew = true) },
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) { viewModel.toggleSearch() },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
            TabKind.Lists -> listOf(
                WpBarAction(Icons.Default.Add, stringResource(R.string.notes_new_list)) { openEditor(Note(isList = true), isNew = true) },
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) { viewModel.toggleSearch() },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
            is TabKind.Tag -> listOf(
                WpBarAction(Icons.Default.Add, stringResource(R.string.common_new)) { openEditor(Note(tags = listOf(kind.tag)), isNew = true) },
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) { viewModel.toggleSearch() },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
            else -> listOf(
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) { viewModel.toggleSearch() },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
        }
    }

    val bottomBarMenuItems: List<WpBarMenuItem> = if (isSelectionMode) {
        buildList {
            if (selectedIds.size == 1) {
                add(WpBarMenuItem(stringResource(R.string.common_copy)) {
                    visibleList.firstOrNull { it.id == selectedIds.first() }?.let { viewModel.copyToClipboard(it) }
                    viewModel.exitSelectionMode()
                })
            }
        }
    } else {
        buildList {
            add(WpBarMenuItem(stringResource(R.string.notes_sort_menu, stringResource(sortMode.titleRes))) { showSortDialog = true })
            when (currentKind) {
                TabKind.Notes -> {
                    add(WpBarMenuItem(stringResource(R.string.notes_new_list)) { openEditor(Note(isList = true), isNew = true) })
                    add(WpBarMenuItem(stringResource(R.string.notes_backup)) {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                        createBackup.launch("zune-notlar-$stamp.json")
                    })
                    add(WpBarMenuItem(stringResource(R.string.notes_restore_backup)) { restoreBackup.launch(arrayOf("application/json", "text/plain", "*/*")) })
                    add(WpBarMenuItem(if (isQuickNoteTileOnStart) stringResource(R.string.notes_quick_tile_remove) else stringResource(R.string.notes_quick_tile_add)) {
                        viewModel.toggleQuickNoteTile()
                    })
                }
                TabKind.Archive -> add(WpBarMenuItem(stringResource(R.string.notes_clear_archive)) { if (archivedNotes.isNotEmpty()) showClearArchiveDialog = true })
                TabKind.Trash -> add(WpBarMenuItem(stringResource(R.string.notes_empty_trash)) { if (trashNotes.isNotEmpty()) showEmptyTrashDialog = true })
                else -> {}
            }
        }
    }

    // ── Shared list page renderer ─────────────────────────────────────────
    @Composable
    fun NotesListPage(
        kind: TabKind,
        notes: List<Note>,
        emptyTitle: String,
        emptySubtitle: String
    ) {
        val listState = rememberLazyListState()
        LaunchedEffect(jumpLetter) {
            val request = jumpLetter ?: return@LaunchedEffect
            if (kind != currentKind) return@LaunchedEffect
            val index = notes.indexOfFirst { note ->
                val first = note.displayTitle.trim().firstOrNull()?.uppercaseChar() ?: '#'
                if (request.first == '#') !first.isLetter() else first == request.first
            }
            if (index >= 0) listState.animateScrollToItem(index)
        }
        ZunePageTransition {
            if (notes.isEmpty()) {
                NotesEmptyState(
                    icon = Icons.Default.StickyNote2,
                    title = emptyTitle,
                    subtitle = emptySubtitle
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = if (sortMode == NoteSortMode.NAME && kind != TabKind.Trash) 44.dp else ZuneDimens.ScreenPaddingHorizontal,
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        val index = notes.indexOf(note)
                        NoteCard(
                            note = note,
                            index = index,
                            isSelectionMode = isSelectionMode,
                            isSelected = note.id in selectedIds,
                            isPinnedToStart = note.id in startPinnedNoteIds,
                            onClick = {
                                when {
                                    isSelectionMode -> viewModel.toggleSelected(note.id)
                                    kind == TabKind.Trash -> viewModel.enterSelectionMode(note.id)
                                    else -> openEditor(note, isNew = false)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) viewModel.enterSelectionMode(note.id)
                            },
                            onToggleItem = { itemId -> if (kind != TabKind.Trash) viewModel.toggleChecklistItem(note.id, itemId) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ColumnScope.PagerPages() {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ZuneLoopingPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isSelectionMode
            ) { page ->
                val kind = tabKinds.getOrElse(page) { TabKind.Notes }
                val searching = searchQuery.isNotBlank()
                when (kind) {
                    TabKind.Notes -> NotesListPage(
                        kind = kind,
                        notes = activeNotes,
                        emptyTitle = if (searching) stringResource(R.string.notes_no_results) else stringResource(R.string.notes_empty_notes),
                        emptySubtitle = if (searching) stringResource(R.string.notes_try_other_search) else stringResource(R.string.notes_empty_notes_hint)
                    )
                    TabKind.Lists -> NotesListPage(
                        kind = kind,
                        notes = checklistNotes,
                        emptyTitle = if (searching) stringResource(R.string.notes_no_results) else stringResource(R.string.notes_empty_lists),
                        emptySubtitle = if (searching) stringResource(R.string.notes_try_other_search) else stringResource(R.string.notes_empty_lists_hint)
                    )
                    is TabKind.Tag -> NotesListPage(
                        kind = kind,
                        notes = notesFor(kind),
                        emptyTitle = stringResource(R.string.notes_tag_empty, kind.tag),
                        emptySubtitle = stringResource(R.string.notes_tag_empty_hint)
                    )
                    TabKind.Archive -> NotesListPage(
                        kind = kind,
                        notes = archivedNotes,
                        emptyTitle = stringResource(R.string.notes_archive_empty),
                        emptySubtitle = stringResource(R.string.notes_archive_empty_hint)
                    )
                    TabKind.Trash -> NotesListPage(
                        kind = kind,
                        notes = trashNotes,
                        emptyTitle = stringResource(R.string.notes_trash_empty),
                        emptySubtitle = stringResource(R.string.notes_trash_empty_hint)
                    )
                }
            }

            // Alphabet index when sorting by name (People / Apps hub pattern)
            if (sortMode == NoteSortMode.NAME && !isSelectionMode && currentKind != TabKind.Trash && visibleList.isNotEmpty()) {
                val letters = remember(visibleList) {
                    visibleList.map { note ->
                        val first = note.displayTitle.trim().firstOrNull()?.uppercaseChar() ?: '#'
                        if (first.isLetter()) first else '#'
                    }.toSet()
                }
                ZuneAlphabetIndex(
                    availableLetters = letters,
                    onLetterSelected = { letter -> jumpLetter = letter to System.currentTimeMillis() },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }

    @Composable
    fun SearchRow() {
        AnimatedVisibility(
            visible = isSearchOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ZuneSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                placeholder = stringResource(R.string.notes_search_hint),
                modifier = Modifier.padding(
                    horizontal = ZuneDimens.ScreenPaddingHorizontal,
                    vertical = 8.dp
                )
            )
        }
    }

    @Composable
    fun EditorPane(currentDraft: Note, bottomBarModifier: Modifier) {
        NoteEditorScreen(
            note = currentDraft,
            onNoteChange = { draft = it },
            onClose = { closeEditor() },
            onDelete = {
                val id = currentDraft.id
                viewModel.deleteNote(id)
                closeEditor(save = false)
            },
            onShare = { viewModel.shareNote(currentDraft) },
            onToggleArchive = {
                draft = currentDraft.copy(isArchived = !currentDraft.isArchived, isPinned = false)
                closeEditor(save = true)
            },
            viewModel = viewModel,
            isNew = isDraftNew,
            isPinnedToStart = currentDraft.id in startPinnedNoteIds,
            onTogglePinToStart = { viewModel.togglePinToStart(currentDraft) },
            onStatus = { localStatus = it },
            bottomBarModifier = bottomBarModifier
        )
    }

    val headerColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
    val subScreenHingeProgress = editorHingeAnim.value

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Hub main screen (hinges out to -90° when the editor opens on phones)
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
                    // ═══════════════════════════════════════════════════
                    // TABLET: list pane (left) + docked editor pane (right)
                    // ═══════════════════════════════════════════════════
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                ZuneWideHubTitle(
                                    text = if (isSelectionMode) stringResource(R.string.notes_selected_count, selectedIds.size) else stringResource(R.string.hub_notes),
                                    bottomPadding = 8.dp
                                )
                                ZunePivotTabs(
                                    tabs = tabs,
                                    state = pager,
                                    fontSize = 40.sp,
                                    modifier = Modifier.padding(start = 48.dp)
                                )
                                SearchRow()
                                PagerPages()
                                Spacer(modifier = Modifier.height(72.dp))
                            }

                            WindowsPhoneBottomBar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .then(bottomBarModifier),
                                actions = bottomBarActions,
                                menuItems = bottomBarMenuItems
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                        ) {
                            val currentDraft = draft
                            if (currentDraft != null) {
                                EditorPane(currentDraft, bottomBarModifier)
                            } else {
                                NotesEmptyState(
                                    icon = Icons.Default.StickyNote2,
                                    title = stringResource(R.string.notes_pick_one),
                                    subtitle = stringResource(R.string.notes_pick_one_hint)
                                )
                            }
                        }
                    }
                } else {
                    // ═══════════════════════════════════════════════════
                    // PHONE: canonical hub skeleton
                    // ═══════════════════════════════════════════════════
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 28.dp, bottom = 8.dp)
                            ) {
                                Text(
                                    text = if (isSelectionMode) stringResource(R.string.notes_selected_count, selectedIds.size) else stringResource(R.string.hub_notes),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 18.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = headerColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(
                                        top = 28.dp,
                                        bottom = 4.dp,
                                        start = ZuneDimens.ScreenPaddingHorizontal
                                    )
                                )
                                ZunePivotTabs(
                                    tabs = tabs,
                                    state = pager,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            SearchRow()
                            PagerPages()

                            // Space for Bottom Bar
                            Spacer(modifier = Modifier.height(72.dp))
                        }

                        // --- WINDOWS PHONE STYLE BOTTOM MENU BAR ---
                        WindowsPhoneBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(bottomBarModifier),
                            actions = bottomBarActions,
                            menuItems = bottomBarMenuItems
                        )
                    }
                }
            }
        }

        // 2. Note editor sub-screen (hinges in from 90° to 0°) — phones only
        if (!isWideScreen && (isEditorOpen || editorHingeAnim.value > 0f)) {
            val currentDraft = draft
            if (currentDraft != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = editorHingeAnim.value
                            rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                            alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                        }
                ) {
                    EditorPane(currentDraft, Modifier)
                }
            }
        }

        // 3. Status banner (export / backup / auth results) — Metro accent strip at the top
        AnimatedVisibility(
            visible = bannerText != null,
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
                    text = bannerText ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White
                )
            }
        }
    }

    // ── DIALOGS ───────────────────────────────────────────────────────────
    if (showBulkDeleteDialog) {
        DeleteNotesDialog(
            count = selectedIds.size,
            onConfirm = { showBulkDeleteDialog = false; viewModel.moveToTrash(selectedIds) },
            onDismiss = { showBulkDeleteDialog = false }
        )
    }
    if (showDeleteForeverDialog) {
        DeleteForeverDialog(
            count = selectedIds.size,
            onConfirm = { showDeleteForeverDialog = false; viewModel.deleteForever(selectedIds) },
            onDismiss = { showDeleteForeverDialog = false }
        )
    }
    if (showClearArchiveDialog) {
        ClearArchiveDialog(
            count = archivedNotes.size,
            onConfirm = { showClearArchiveDialog = false; viewModel.clearArchive() },
            onDismiss = { showClearArchiveDialog = false }
        )
    }
    if (showEmptyTrashDialog) {
        EmptyTrashDialog(
            count = trashNotes.size,
            onConfirm = { showEmptyTrashDialog = false; viewModel.emptyTrash() },
            onDismiss = { showEmptyTrashDialog = false }
        )
    }
    if (showSortDialog) {
        SortDialog(
            current = sortMode,
            onSelect = { showSortDialog = false; viewModel.setSortMode(it) },
            onDismiss = { showSortDialog = false }
        )
    }
}
