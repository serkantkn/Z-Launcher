package com.serkantkn.zunelauncher.ui.screens.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import com.serkantkn.zunelauncher.ui.components.ZuneWideNavHub
import com.serkantkn.zunelauncher.ui.components.ZuneWideNavLabel
import com.serkantkn.zunelauncher.ui.components.ZuneWideNavWidth
import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec

/** What a pivot page shows. Tag pages are generated from the notes' tags. */
private sealed interface TabKind {
    data object Notes : TabKind
    data object Lists : TabKind
    data class Tag(val tag: String) : TabKind
    data object Archive : TabKind
    data object Trash : TabKind
    /** A tablet's search heading; a phone searches with a box over whichever page is open. */
    data object Search : TabKind
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
    val tabKinds: List<TabKind> = remember(allTags, isWideScreen) {
        listOf(TabKind.Notes, TabKind.Lists) + allTags.map { TabKind.Tag(it) } + listOf(TabKind.Archive, TabKind.Trash) +
            if (isWideScreen) listOf(TabKind.Search) else emptyList()
    }
    // On a tablet the tags are listed under the headings, as the email hub lists its folders, and
    // their pages are reached from there rather than as headings of their own.
    val hiddenWideTabs: Set<Int> = remember(tabKinds) {
        tabKinds.indices.filter { tabKinds[it] is TabKind.Tag }.toSet()
    }
    val searchPage = tabKinds.indexOf(TabKind.Search)
    val tabNotes = stringResource(R.string.hub_notes)
    val tabLists = stringResource(R.string.notes_tab_lists)
    val tabArchive = stringResource(R.string.notes_tab_archive)
    val tabTrash = stringResource(R.string.notes_tab_trash)
    val tabSearch = stringResource(R.string.common_search)
    val tabs: List<String> = remember(tabKinds, tabNotes, tabLists, tabArchive, tabTrash, tabSearch) {
        tabKinds.map {
            when (it) {
                TabKind.Notes -> tabNotes
                TabKind.Lists -> tabLists
                is TabKind.Tag -> "#${it.tag}"
                TabKind.Archive -> tabArchive
                TabKind.Trash -> tabTrash
                TabKind.Search -> tabSearch
            }
        }
    }
    val pager = rememberLoopingPagerState(pageCount = tabs.size)
    val currentTab = pager.currentPage
    val currentKind = tabKinds.getOrElse(currentTab) { TabKind.Notes }

    // A tag added or taken away puts a page in or out among the others, and the pager would stay
    // at the same position and so show a different page - giving a note its first tag used to
    // land on the bin. The page being looked at is kept instead.
    var kindsSeen by remember { mutableStateOf(tabKinds) }
    var keptKind by remember { mutableStateOf<TabKind>(TabKind.Notes) }
    if (kindsSeen == tabKinds) {
        SideEffect { keptKind = currentKind }
    }
    LaunchedEffect(tabKinds) {
        if (kindsSeen == tabKinds) return@LaunchedEffect
        val index = tabKinds.indexOf(keptKind).takeIf { it >= 0 } ?: 0
        if (pager.currentPage != index) pager.scrollToPage(index)
        kindsSeen = tabKinds
    }

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
    val hingeSpec = rememberHingeSpec()
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
                    animationSpec = hingeSpec
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
                    animationSpec = hingeSpec
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

    // On a tablet the search heading is the search: arriving there opens it and leaving closes it,
    // so no other heading is left filtered by a query nobody can see.
    LaunchedEffect(currentKind, isWideScreen) {
        if (!isWideScreen) return@LaunchedEffect
        if (currentKind == TabKind.Search && !isSearchOpen) viewModel.toggleSearch()
        if (currentKind != TabKind.Search && isSearchOpen) viewModel.closeSearch()
    }

    // ── Back handling: editor → selection → search → hub ──────────────────
    BackHandler(enabled = true) {
        when {
            draft != null -> closeEditor()
            isSelectionMode -> viewModel.exitSelectionMode()
            isWideScreen && currentKind == TabKind.Search -> coroutineScope.launch { pager.scrollToPage(0) }
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
        TabKind.Search -> activeNotes
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
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
                    if (isWideScreen && searchPage >= 0) coroutineScope.launch { pager.scrollToPage(searchPage) } else viewModel.toggleSearch()
                },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
            TabKind.Lists -> listOf(
                WpBarAction(Icons.Default.Add, stringResource(R.string.notes_new_list)) { openEditor(Note(isList = true), isNew = true) },
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
                    if (isWideScreen && searchPage >= 0) coroutineScope.launch { pager.scrollToPage(searchPage) } else viewModel.toggleSearch()
                },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
            is TabKind.Tag -> listOf(
                WpBarAction(Icons.Default.Add, stringResource(R.string.common_new)) { openEditor(Note(tags = listOf(kind.tag)), isNew = true) },
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
                    if (isWideScreen && searchPage >= 0) coroutineScope.launch { pager.scrollToPage(searchPage) } else viewModel.toggleSearch()
                },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
            else -> listOf(
                WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
                    if (isWideScreen && searchPage >= 0) coroutineScope.launch { pager.scrollToPage(searchPage) } else viewModel.toggleSearch()
                },
                WpBarAction(Icons.Default.Checklist, stringResource(R.string.common_select)) { viewModel.enterSelectionMode() }
            )
        }
    }

    val bottomBarMenuItems: List<WpBarMenuItem> = if (isSelectionMode) {
        buildList {
            if (selectedIds.size == 1) {
                add(WpBarMenuItem(stringResource(R.string.common_copy)) {
                    visibleList.firstOrNull { it.id == selectedIds.first() }?.let { note ->
                        // The clipboard is open to every app; a locked note is not handed to
                        // it without the same unlock that opening the note asks for.
                        if (note.isLocked) {
                            authenticateForNote(
                                context = context,
                                title = note.displayTitle,
                                onSuccess = { viewModel.copyToClipboard(note) },
                                onFailure = { reason -> localStatus = reason }
                            )
                        } else {
                            viewModel.copyToClipboard(note)
                        }
                    }
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
                        createBackup.launch("z-launcher-notes-$stamp.json")
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
                        // Beside a tablet's headings the list already stands clear of the edge.
                        start = if (isWideScreen) 0.dp else ZuneDimens.ScreenPaddingHorizontal,
                        end = if (sortMode == NoteSortMode.NAME && kind != TabKind.Trash) 44.dp else if (isWideScreen) 0.dp else ZuneDimens.ScreenPaddingHorizontal,
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
                            highlighted = isWideScreen && !isSelectionMode && draft?.id == note.id,
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

    /** One page: the notes of a heading, a tag's notes, the archive, the bin, or the search. */
    @Composable
    fun PageContent(page: Int) {
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
            TabKind.Search -> Column(modifier = Modifier.fillMaxSize()) {
                // The box the messaging and email hubs' search headings have, which takes
                // the keyboard as soon as the heading is chosen.
                com.serkantkn.zunelauncher.ui.screens.messaging.MessageSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    isVisible = pager.currentPage == page,
                    hint = stringResource(R.string.notes_search_hint),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    NotesListPage(
                        kind = kind,
                        notes = activeNotes,
                        emptyTitle = stringResource(R.string.notes_no_results),
                        emptySubtitle = stringResource(R.string.notes_try_other_search)
                    )
                }
            }
        }
    }

    /** The alphabet down the right edge, while the notes are in name order. */
    @Composable
    fun BoxScope.AlphabetIndex() {
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
            ) { page -> PageContent(page) }
            AlphabetIndex()
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

    /** Under a tablet's headings: the tags, each opening its own notes, as the email hub lists folders. */
    @Composable
    fun WideTagList() {
        if (allTags.isEmpty()) return
        Spacer(modifier = Modifier.height(28.dp))
        ZuneWideNavLabel(stringResource(R.string.notes_tags))
        Spacer(modifier = Modifier.height(6.dp))
        allTags.forEach { tag ->
            val page = tabKinds.indexOf(TabKind.Tag(tag))
            val isCurrent = page == currentTab
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (page >= 0) coroutineScope.launch { pager.scrollToPage(page) } }
                    .padding(vertical = 5.dp)
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
                    color = if (isCurrent) zuneColors.accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val count = activeNotes.count { tag in it.tags }
                if (count > 0) {
                    Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
                }
            }
        }
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
                    // TABLET: as the other tablet hubs stand - the hub's name small at the top
                    // left, the headings down the left with the tags under them, the notes
                    // beside them, and the open note in the rest of the width with its own bar.
                    // ═══════════════════════════════════════════════════
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .width(ZuneWideNavWidth + WIDE_LIST_WIDTH)
                                .fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                ZuneWideNavHub(
                                    title = if (isSelectionMode) stringResource(R.string.notes_selected_count, selectedIds.size) else stringResource(R.string.hub_notes),
                                    tabs = tabs,
                                    state = pager,
                                    hiddenTabs = hiddenWideTabs,
                                    modifier = Modifier.weight(1f),
                                    navExtra = { WideTagList() }
                                ) { page ->
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        PageContent(page)
                                        AlphabetIndex()
                                    }
                                }
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
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            AnimatedContent(
                                targetState = draft?.id,
                                transitionSpec = {
                                    (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 12 }) togetherWith fadeOut(tween(90))
                                },
                                label = "notes_pane",
                                modifier = Modifier.fillMaxSize()
                            ) { openId ->
                                val currentDraft = draft
                                if (openId != null && currentDraft != null) {
                                    EditorPane(currentDraft, bottomBarModifier)
                                } else {
                                    WideEmptyPane(onNew = {
                                        openEditor(
                                            when (val kind = currentKind) {
                                                TabKind.Lists -> Note(isList = true)
                                                is TabKind.Tag -> Note(tags = listOf(kind.tag))
                                                else -> Note()
                                            },
                                            isNew = true
                                        )
                                    })
                                }
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

/**
 * A tablet's pane with nothing open in it: what to do, in the hub's own light type at the top
 * left, and starting a new note as a word in the accent; and an empty bar under it, so the bar
 * along the bottom runs the whole width.
 */
@Composable
private fun WideEmptyPane(onNew: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 40.dp)) {
            Text(
                text = stringResource(R.string.notes_pick_one),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 34.sp),
                color = zuneColors.textMuted
            )
            Text(
                text = stringResource(R.string.notes_or_write_new),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(onClick = onNew)
                    .padding(vertical = 4.dp)
            )
        }
        com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBarStrip(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** How wide a tablet's list of notes is; the open note takes the rest. */
private val WIDE_LIST_WIDTH = 440.dp
