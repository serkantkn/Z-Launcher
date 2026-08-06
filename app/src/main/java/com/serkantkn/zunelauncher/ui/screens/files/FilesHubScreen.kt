package com.serkantkn.zunelauncher.ui.screens.files

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
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
    val searchQuery by viewModel.searchQuery.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<FileItemModel?>(null) }
    var itemToRename by remember { mutableStateOf<FileItemModel?>(null) }
    var selectedItemForMenu by remember { mutableStateOf<FileItemModel?>(null) }

    val tabs = listOf("tümü", "kategoriler", "hızlı erişim")
    val actualPageCount = tabs.size
    val loopCount = 1000
    val initialPage = (loopCount / 2) * actualPageCount
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { loopCount * actualPageCount }
    )
    val coroutineScope = rememberCoroutineScope()

    // Handle back button for subfolder navigation
    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            viewModel.updateSearchQuery("")
        } else if (!viewModel.navigateUp()) {
            onClose()
        }
    }

    val bottomBarActions = remember {
        listOf(
            WpBarAction(
                icon = Icons.Default.CreateNewFolder,
                label = "yeni klasör",
                onClick = { showNewFolderDialog = true }
            ),
            WpBarAction(
                icon = Icons.Default.Search,
                label = "ara",
                onClick = { isSearchActive = !isSearchActive }
            ),
            WpBarAction(
                icon = Icons.Default.Refresh,
                label = "yenile",
                onClick = { viewModel.loadDirectory(currentDirectory) }
            )
        )
    }

    val bottomBarMenuItems = remember {
        listOf(
            WpBarMenuItem(
                text = "ana dizine git",
                onClick = { viewModel.loadDirectory(Environment.getExternalStorageDirectory()) }
            )
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val overflowYPx = with(LocalDensity.current) { (-24).dp.toPx() }

            if (isWideScreen) {
                // Tablet Header
                Text(
                    text = "dosyalar",
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
            } else {
                // Mobile Header
                Text(
                    text = "dosyalar",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        lineHeight = 96.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    modifier = Modifier
                        .padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 28.dp, bottom = 4.dp)
                        .graphicsLayer { translationY = overflowYPx }
                )

                ZunePivotTabs(
                    tabs = tabs,
                    pagerState = pagerState,
                    onSelected = { index ->
                        val current = pagerState.currentPage
                        val currentActual = ((current % actualPageCount) + actualPageCount) % actualPageCount
                        var diff = index - currentActual
                        if (diff > actualPageCount / 2) diff -= actualPageCount
                        else if (diff < -actualPageCount / 2) diff += actualPageCount
                        coroutineScope.launch { pagerState.animateScrollToPage(current + diff) }
                    },
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
                PermissionRequestView(
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
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
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { page ->
                        val actualPage = page % actualPageCount
                        when (tabs[actualPage]) {
                            "tümü" -> FileList(
                                items = fileItems,
                                searchQuery = searchQuery,
                                onItemClick = { item ->
                                    if (item.isDirectory) viewModel.navigateTo(item.file)
                                    else viewModel.openFile(context, item.file)
                                },
                                onItemLongClick = { item -> selectedItemForMenu = item }
                            )
                            "kategoriler" -> CategoriesView(
                                onCategoryClick = { folderName ->
                                    val dir = File(Environment.getExternalStorageDirectory(), folderName)
                                    if (dir.exists()) viewModel.navigateTo(dir)
                                }
                            )
                            "hızlı erişim" -> QuickAccessView(
                                onQuickClick = { dir -> viewModel.navigateTo(dir) }
                            )
                        }
                    }
                }
            }

            if (hasPermission) {
                WindowsPhoneBottomBar(
                    actions = bottomBarActions,
                    menuItems = bottomBarMenuItems
                )
            }
        }

        // New Folder Metro Dialog
        if (showNewFolderDialog) {
            InputDialog(
                title = "yeni klasör",
                hint = "klasör adı",
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
                title = "yeniden adlandır",
                initialText = item.name,
                hint = "yeni ad",
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
                title = "dosyayı sil",
                confirmButton = {
                    ZuneDialogButton(
                        text = "evet",
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
                        text = "hayır",
                        onClick = { dismissWithAnim { itemToDelete = null } }
                    )
                }
            ) {
                Text(
                    text = "\"${item.name}\" kalıcı olarak silinecek. Onaylıyor musunuz?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
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
    val relativePath = remember(currentDir) {
        val path = currentDir.absolutePath
        if (path == rootPath) "Dahili Depolama"
        else "Dahili Depolama / " + path.removePrefix(rootPath).trim('/')
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
                    contentDescription = "Yukarı Dizin",
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
                text = if (searchQuery.isBlank()) "klasör boş" else "dosya bulunamadı",
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
    val formattedSize = remember(item.size, item.isDirectory) {
        if (item.isDirectory) "klasör" else formatFileSize(item.size)
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
        Triple("Download", "İndirilenler", Icons.Default.Download),
        Triple("Pictures", "Resimler", Icons.Default.Image),
        Triple("Music", "Müzikler", Icons.Default.MusicNote),
        Triple("Movies", "Videolar", Icons.Default.Movie),
        Triple("Documents", "Belgeler", Icons.Default.Description)
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
        "DCIM/Camera" to "Kamera Fotoğrafları",
        "Download" to "İndirmeler",
        "WhatsApp/Media" to "WhatsApp Medya",
        "Music" to "Müzik Arşivi"
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

            ListItemOption(icon = Icons.Default.FolderOpen, label = "Aç", onClick = onOpen)
            if (!item.isDirectory) {
                ListItemOption(icon = Icons.Default.Share, label = "Paylaş", onClick = onShare)
            }
            ListItemOption(icon = Icons.Default.Edit, label = "Yeniden Adlandır", onClick = onRename)
            ListItemOption(icon = Icons.Default.Delete, label = "Sil", textColor = Color.Red, onClick = onDelete)

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
                    Text(text = "dosyalarda ara...", style = MaterialTheme.typography.bodyMedium, color = zuneColors.textDim)
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
                TextButton(onClick = onDismiss) { Text("İptal", color = zuneColors.textMuted) }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(text) },
                    colors = ButtonDefaults.buttonColors(containerColor = zuneColors.accentColor)
                ) {
                    Text("Tamam")
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestView(onGrant: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = zuneColors.accentColor, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Tüm Dosyalara Erişim İzni Gerekli", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Cihazınızdaki klasör ve dosyaları Zune tarzında gezebilmek için dosya erişim izni verin.", style = MaterialTheme.typography.bodyMedium, color = zuneColors.textMuted)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = zuneColors.accentColor)) {
                Text("İzin Ver")
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
