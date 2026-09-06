package com.serkantkn.zunelauncher.ui.screens.files

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequestStyle
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FilesHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilesHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

    val hasPermission by viewModel.hasPermission.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val fileItems by viewModel.fileItems.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            viewModel.refreshPermissionState()
        }
    )

    var isSearchActive by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<FileItemModel?>(null) }
    var itemToRename by remember { mutableStateOf<FileItemModel?>(null) }
    var selectedItemForMenu by remember { mutableStateOf<FileItemModel?>(null) }

    val tabs = listOf(stringResource(R.string.common_all), stringResource(R.string.files_tab_categories), stringResource(R.string.files_tab_quick))
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    // Handle back button for subfolder navigation
    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            viewModel.updateSearchQuery("")
        } else if (!viewModel.navigateUp()) {
            onClose()
        }
    }

    val bottomBarActions = listOf(
        WpBarAction(
            icon = Icons.Default.CreateNewFolder,
            label = stringResource(R.string.files_new_folder),
            onClick = { showNewFolderDialog = true }
        ),
        WpBarAction(
            icon = Icons.Default.Search,
            label = stringResource(R.string.common_search),
            onClick = { isSearchActive = !isSearchActive }
        ),
        WpBarAction(
            icon = Icons.Default.Refresh,
            label = stringResource(R.string.common_refresh),
            onClick = { viewModel.loadDirectory(currentDirectory) }
        )
    )

    val bottomBarMenuItems = listOf(
        WpBarMenuItem(
            text = stringResource(R.string.files_go_root),
            onClick = { viewModel.loadDirectory(Environment.getExternalStorageDirectory()) }
        )
    )

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                // Tablet Header
                ZuneWideHubTitle(text = stringResource(R.string.hub_files))
            } else {
                // Mobile Header
                Text(
                    text = stringResource(R.string.hub_files),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 28.dp, bottom = 4.dp)
                )

                ZunePivotTabs(
                    tabs = tabs,
                    state = pager,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                )
            }

            AnimatedVisibility(visible = isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    isVisible = isSearchActive,
                    modifier = Modifier.padding(horizontal = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal, vertical = 4.dp)
                )
            }

            if (!hasPermission) {
                ZunePermissionRequest(
                    title = stringResource(R.string.files_permission_title),
                    message = stringResource(R.string.files_permission_message),
                    buttonLabel = stringResource(R.string.files_permission_button),
                    style = ZunePermissionRequestStyle.Centered,
                    icon = Icons.Default.Folder,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                context.startActivity(intent)
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    }
                )
            } else {
                // Breadcrumb Path Bar
                PathBreadcrumbBar(
                    currentDir = currentDirectory,
                    onNavigateUp = { viewModel.navigateUp() },
                    modifier = Modifier.padding(horizontal = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp)
                )

                if (isWideScreen) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 48.dp)
                    ) {
                        FileList(
                            errorMessage = errorMessage,
                            items = fileItems,
                            searchQuery = searchQuery,
                            onItemClick = { item ->
                                if (item.isDirectory) viewModel.navigateTo(item.file)
                                else viewModel.openFile(context, item.file)
                            },
                            onItemLongClick = { item -> selectedItemForMenu = item }
                        )
                    }
                } else {
                    ZuneLoopingPager(
                        state = pager,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> FileList(
                                errorMessage = errorMessage,
                                items = fileItems,
                                searchQuery = searchQuery,
                                onItemClick = { item ->
                                    if (item.isDirectory) viewModel.navigateTo(item.file)
                                    else viewModel.openFile(context, item.file)
                                },
                                onItemLongClick = { item -> selectedItemForMenu = item }
                            )
                            1 -> CategoriesView(
                                onCategoryClick = { folderName ->
                                    val dir = File(Environment.getExternalStorageDirectory(), folderName)
                                    if (dir.exists()) viewModel.navigateTo(dir)
                                }
                            )
                            2 -> QuickAccessView(
                                onQuickClick = { dir -> viewModel.navigateTo(dir) }
                            )
                        }
                    }
                }
            }

            if (hasPermission) {
                WindowsPhoneBottomBar(
                    actions = bottomBarActions,
                    menuItems = bottomBarMenuItems,
                    modifier = bottomBarModifier
                )
            }
        }

        // New Folder Metro Dialog
        if (showNewFolderDialog) {
            InputDialog(
                title = stringResource(R.string.files_new_folder),
                hint = stringResource(R.string.files_folder_name),
                onDismiss = { showNewFolderDialog = false },
                onConfirm = { name ->
                    showNewFolderDialog = false
                    if (name.isNotBlank()) viewModel.createDirectory(name)
                }
            )
        }

        // Rename Item Metro Dialog
        itemToRename?.let { item ->
            InputDialog(
                title = stringResource(R.string.common_rename),
                initialText = item.name,
                hint = stringResource(R.string.files_new_name),
                onDismiss = { itemToRename = null },
                onConfirm = { newName ->
                    val target = itemToRename
                    itemToRename = null
                    if (target != null && newName.isNotBlank()) {
                        viewModel.renameItem(target, newName)
                    }
                }
            )
        }

        // Item Options Context Sheet / Dialog
        selectedItemForMenu?.let { item ->
            FileOptionsBottomSheet(
                item = item,
                onDismiss = { selectedItemForMenu = null },
                onOpen = {
                    selectedItemForMenu = null
                    if (item.isDirectory) viewModel.navigateTo(item.file)
                    else viewModel.openFile(context, item.file)
                },
                onShare = {
                    selectedItemForMenu = null
                    viewModel.shareFile(context, item.file)
                },
                onRename = {
                    val target = selectedItemForMenu
                    selectedItemForMenu = null
                    itemToRename = target
                },
                onDelete = {
                    val target = selectedItemForMenu
                    selectedItemForMenu = null
                    itemToDelete = target
                }
            )
        }

        // Standard ZuneFlipDialog for File Deletion
        itemToDelete?.let { item ->
            ZuneFlipDialog(
                onDismissRequest = { itemToDelete = null },
                title = stringResource(R.string.files_delete_title),
                confirmButton = {
                    ZuneDialogButton(
                        text = stringResource(R.string.common_yes),
                        borderColor = Color.Red,
                        onClick = {
                            dismissWithAnim {
                                val target = itemToDelete
                                itemToDelete = null
                                if (target != null) viewModel.deleteItem(target)
                            }
                        }
                    )
                },
                dismissButton = {
                    ZuneDialogButton(
                        text = stringResource(R.string.common_no),
                        onClick = { dismissWithAnim { itemToDelete = null } }
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.files_delete_confirm, item.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
}

// ── Path Breadcrumb Component ─────────────────────────────────────────────

@Composable
private fun PathBreadcrumbBar(
    currentDir: File,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val rootPath = Environment.getExternalStorageDirectory().absolutePath
    val internalStorage = stringResource(R.string.files_internal_storage)
    val relativePath = remember(currentDir, internalStorage) {
        val path = currentDir.absolutePath
        if (path == rootPath) internalStorage
        else "$internalStorage / " + path.removePrefix(rootPath).trim('/')
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentDir.absolutePath != rootPath) {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.files_parent_dir),
                    tint = zuneColors.accentColor
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = relativePath,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = zuneColors.accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── File List View ──────────────────────────────────────────────────────────

@Composable
private fun FileList(
    errorMessage: String? = null,
    items: List<FileItemModel>,
    searchQuery: String,
    onItemClick: (FileItemModel) -> Unit,
    onItemLongClick: (FileItemModel) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    if (filteredItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = errorMessage ?: if (searchQuery.isBlank()) stringResource(R.string.files_empty_folder) else stringResource(R.string.files_not_found),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                color = zuneColors.textMuted
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = ZuneDimens.ScreenPaddingHorizontal,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredItems, key = { it.file.absolutePath }) { item ->
                FileListItemRow(
                    item = item,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItemRow(
    item: FileItemModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val formattedDate = remember(item.lastModified) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
    }
    val folderLabel = stringResource(R.string.files_folder)
    val formattedSize = remember(item.size, item.isDirectory, folderLabel) {
        if (item.isDirectory) folderLabel else formatFileSize(item.size)
    }

    val icon = remember(item) { getFileIcon(item) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (item.isDirectory) zuneColors.accentColor else (if (zuneColors.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (item.isDirectory) Color.White else zuneColors.accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = zuneColors.textMuted
                    )
                    Text(text = " • ", style = MaterialTheme.typography.labelSmall, color = zuneColors.textMuted)
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = zuneColors.textMuted
                    )
                }
            }
        }
    }
}

// ── Categories Page ─────────────────────────────────────────────────────────

@Composable
private fun CategoriesView(onCategoryClick: (folderName: String) -> Unit) {
    val categories = listOf(
        Triple("Download", stringResource(R.string.files_cat_downloads), Icons.Default.Download),
        Triple("Pictures", stringResource(R.string.files_cat_pictures), Icons.Default.Image),
        Triple("Music", stringResource(R.string.files_cat_music), Icons.Default.MusicNote),
        Triple("Movies", stringResource(R.string.files_cat_movies), Icons.Default.Movie),
        Triple("Documents", stringResource(R.string.files_cat_documents), Icons.Default.Description)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories) { (folder, label, icon) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(folder) },
                color = if (LocalZuneColors.current.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = LocalZuneColors.current.accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

// ── Quick Access Page ───────────────────────────────────────────────────────

@Composable
private fun QuickAccessView(onQuickClick: (dir: File) -> Unit) {
    val root = Environment.getExternalStorageDirectory()
    val quickLocations = listOf(
        "DCIM/Camera" to stringResource(R.string.files_quick_camera),
        "Download" to stringResource(R.string.files_quick_downloads),
        "WhatsApp/Media" to stringResource(R.string.files_quick_whatsapp),
        "Music" to stringResource(R.string.files_quick_music)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(quickLocations) { (subPath, label) ->
            val dir = File(root, subPath)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (dir.exists()) onQuickClick(dir) },
                color = if (LocalZuneColors.current.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = LocalZuneColors.current.accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = subPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalZuneColors.current.textMuted
                        )
                    }
                }
            }
        }
    }
}

// ── File Options Bottom Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileOptionsBottomSheet(
    item: FileItemModel,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (zuneColors.isDark) Color(0xFF141414) else Color(0xFFF5F5F5),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (zuneColors.isDark) Color.White else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            ListItemOption(icon = Icons.Default.FolderOpen, label = stringResource(R.string.files_open_cap), onClick = onOpen)
            if (!item.isDirectory) {
                ListItemOption(icon = Icons.Default.Share, label = stringResource(R.string.files_share_cap), onClick = onShare)
            }
            ListItemOption(icon = Icons.Default.Edit, label = stringResource(R.string.files_rename_cap), onClick = onRename)
            ListItemOption(icon = Icons.Default.Delete, label = stringResource(R.string.files_delete_cap), textColor = Color.Red, onClick = onDelete)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ListItemOption(
    icon: ImageVector,
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = textColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}

// ── Search & Input Dialogs ──────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val bgColor = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = zuneColors.textDim, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(text = stringResource(R.string.files_search_hint), style = MaterialTheme.typography.bodyMedium, color = zuneColors.textDim)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    singleLine = true,
                    cursorBrush = SolidColor(zuneColors.accentColor),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    initialText: String = "",
    hint: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    val zuneColors = LocalZuneColors.current

    Surface(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFFAFAFA),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (zuneColors.isDark) Color.White else Color.Black)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = zuneColors.accentColor)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel_cap), color = zuneColors.textMuted) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(text) },
                    colors = ButtonDefaults.buttonColors(containerColor = zuneColors.accentColor)
                ) {
                    Text(stringResource(R.string.common_ok_cap))
                }
            }
        }
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

private fun getFileIcon(item: FileItemModel): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder
    return when (item.extension) {
        "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
        "mp3", "wav", "flac", "m4a", "aac" -> Icons.Default.MusicNote
        "mp4", "mkv", "avi", "mov" -> Icons.Default.Movie
        "pdf", "doc", "docx", "txt" -> Icons.Default.Description
        "zip", "rar", "7z" -> Icons.Default.FolderZip
        "apk" -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
