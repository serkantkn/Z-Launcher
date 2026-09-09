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
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.BorderStroke
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudCrumb
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.data.repository.CloudAuthBridge
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

    val cloudAccounts by viewModel.cloudAccounts.collectAsState()
    val activeCloudAccount by viewModel.activeCloudAccount.collectAsState()
    val cloudPath by viewModel.cloudPath.collectAsState()
    val cloudItems by viewModel.cloudItems.collectAsState()
    val cloudBusy by viewModel.cloudBusy.collectAsState()
    val cloudMessage by viewModel.cloudMessage.collectAsState()

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
        onResult = { viewModel.refreshPermissionState() }
    )

    // "Google ile oturum aç": Play services asks which account and grants the Drive scope, so the
    // Drive app does not have to be installed and no password is ever seen by the launcher.
    val googleSignIn = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            runCatching { viewModel.googleResultFromIntent(data) }
                .onSuccess { viewModel.completeGoogleSignIn(it) }
                .onFailure { viewModel.reportCloudError(it) }
        } else {
            viewModel.reportCloudError(
                com.serkantkn.zunelauncher.data.model.CloudException(R.string.cloud_error_cancelled)
            )
        }
    }

    fun startGoogleSignIn() {
        viewModel.googleAuthorizeTask()
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    googleSignIn.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    viewModel.completeGoogleSignIn(result)
                }
            }
            .addOnFailureListener { viewModel.reportCloudError(it) }
    }

    // "Microsoft ile oturum aç": the sign-in page opens in the browser and comes back through the
    // launcher's redirect scheme (OAuth 2.0 with PKCE, no client secret).
    fun startMicrosoftSignIn() {
        val url = viewModel.microsoftAuthorizationUrl() ?: return
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            viewModel.reportCloudError(
                com.serkantkn.zunelauncher.data.model.CloudException(R.string.cloud_error_no_browser, e.message, e)
            )
        }
    }

    val pendingAuthCode by CloudAuthBridge.pendingCode.collectAsState()
    LaunchedEffect(pendingAuthCode) {
        val code = CloudAuthBridge.consumeCode() ?: return@LaunchedEffect
        viewModel.completeMicrosoftSignIn(code)
    }
    val pendingAuthError by CloudAuthBridge.pendingError.collectAsState()
    LaunchedEffect(pendingAuthError) {
        val error = CloudAuthBridge.consumeError() ?: return@LaunchedEffect
        viewModel.reportCloudError(
            com.serkantkn.zunelauncher.data.model.CloudException(R.string.cloud_error_cancelled, error)
        )
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<FileItemModel?>(null) }
    var itemToRename by remember { mutableStateOf<FileItemModel?>(null) }
    var selectedItemForMenu by remember { mutableStateOf<FileItemModel?>(null) }
    var showCloudOnWide by remember { mutableStateOf(false) }
    var showCloudFolderDialog by remember { mutableStateOf(false) }
    var cloudItemForMenu by remember { mutableStateOf<CloudItem?>(null) }
    var cloudItemToRename by remember { mutableStateOf<CloudItem?>(null) }
    var cloudItemToDelete by remember { mutableStateOf<CloudItem?>(null) }

    val tabs = listOf(
        stringResource(R.string.common_all),
        stringResource(R.string.files_tab_categories),
        stringResource(R.string.files_tab_quick),
        stringResource(R.string.files_tab_cloud)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    // Handle back button for subfolder navigation
    BackHandler {
        val browsingCloud = activeCloudAccount != null && (showCloudOnWide || pager.currentPage == CLOUD_TAB_INDEX)
        when {
            isSearchActive -> {
                isSearchActive = false
                viewModel.updateSearchQuery("")
            }
            browsingCloud && viewModel.cloudNavigateUp() -> Unit
            browsingCloud -> viewModel.closeCloudAccount()
            showCloudOnWide -> showCloudOnWide = false
            !viewModel.navigateUp() -> onClose()
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

    val bottomBarMenuItems = buildList {
        add(
            WpBarMenuItem(
                text = stringResource(R.string.files_go_root),
                onClick = { viewModel.loadDirectory(Environment.getExternalStorageDirectory()) }
            )
        )
        if (isWideScreen) {
            add(
                WpBarMenuItem(
                    text = if (showCloudOnWide) {
                        stringResource(R.string.hub_files)
                    } else {
                        stringResource(R.string.files_tab_cloud)
                    },
                    onClick = { showCloudOnWide = !showCloudOnWide }
                )
            )
        }
    }

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

            // Cloud storage needs none of the local file permissions, so the gate only covers the
            // pages that read the device itself.
            val storagePermissionGate: @Composable () -> Unit = {
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
            }

            // Breadcrumb Path Bar (local storage; the cloud page draws its own)
            val showLocalPath = hasPermission &&
                if (isWideScreen) !showCloudOnWide else pager.currentPage != CLOUD_TAB_INDEX
            if (showLocalPath) PathBreadcrumbBar(
                currentDir = currentDirectory,
                onNavigateUp = { viewModel.navigateUp() },
                modifier = Modifier.padding(horizontal = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp)
            )

            if (isWideScreen && showCloudOnWide) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 72.dp, end = 48.dp)
                ) {
                    CloudView(
                        accounts = cloudAccounts,
                        activeAccount = activeCloudAccount,
                        path = cloudPath,
                        items = cloudItems,
                        isBusy = cloudBusy,
                        message = cloudMessage,
                        microsoftConfigured = viewModel.isMicrosoftConfigured,
                        onSignInGoogle = { startGoogleSignIn() },
                        onSignInMicrosoft = { startMicrosoftSignIn() },
                        onOpenAccount = { account -> viewModel.openCloudAccount(account) },
                        onSignOut = { account -> viewModel.signOutCloudAccount(account) },
                        onItemClick = { item -> viewModel.openCloudItem(item) },
                        onItemLongClick = { item -> cloudItemForMenu = item },
                        onNavigateUp = { if (!viewModel.cloudNavigateUp()) viewModel.closeCloudAccount() },
                        onNewFolder = { showCloudFolderDialog = true }
                    )
                }
            } else if (isWideScreen && !hasPermission) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { storagePermissionGate() }
            } else if (isWideScreen) {
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
                    when {
                        page == CLOUD_TAB_INDEX -> CloudView(
                            accounts = cloudAccounts,
                            activeAccount = activeCloudAccount,
                            path = cloudPath,
                            items = cloudItems,
                            isBusy = cloudBusy,
                            message = cloudMessage,
                            microsoftConfigured = viewModel.isMicrosoftConfigured,
                            onSignInGoogle = { startGoogleSignIn() },
                            onSignInMicrosoft = { startMicrosoftSignIn() },
                            onOpenAccount = { account -> viewModel.openCloudAccount(account) },
                            onSignOut = { account -> viewModel.signOutCloudAccount(account) },
                            onItemClick = { item -> viewModel.openCloudItem(item) },
                            onItemLongClick = { item -> cloudItemForMenu = item },
                            onNavigateUp = { if (!viewModel.cloudNavigateUp()) viewModel.closeCloudAccount() },
                            onNewFolder = { showCloudFolderDialog = true }
                        )
                        !hasPermission -> storagePermissionGate()
                        page == 0 -> FileList(
                            errorMessage = errorMessage,
                            items = fileItems,
                            searchQuery = searchQuery,
                            onItemClick = { item ->
                                if (item.isDirectory) viewModel.navigateTo(item.file)
                                else viewModel.openFile(context, item.file)
                            },
                            onItemLongClick = { item -> selectedItemForMenu = item }
                        )
                        page == 1 -> CategoriesView(
                            onCategoryClick = { folderName ->
                                val dir = File(Environment.getExternalStorageDirectory(), folderName)
                                if (dir.exists()) viewModel.navigateTo(dir)
                            }
                        )
                        page == 2 -> QuickAccessView(
                            onQuickClick = { dir -> viewModel.navigateTo(dir) }
                        )
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

        // New folder inside a cloud service
        if (showCloudFolderDialog) {
            InputDialog(
                title = stringResource(R.string.files_new_folder),
                hint = stringResource(R.string.files_folder_name),
                onDismiss = { showCloudFolderDialog = false },
                onConfirm = { name ->
                    showCloudFolderDialog = false
                    if (name.isNotBlank()) viewModel.createCloudFolder(name)
                }
            )
        }

        cloudItemToRename?.let { item ->
            InputDialog(
                title = stringResource(R.string.common_rename),
                initialText = item.name,
                hint = stringResource(R.string.files_new_name),
                onDismiss = { cloudItemToRename = null },
                onConfirm = { newName ->
                    cloudItemToRename = null
                    if (newName.isNotBlank()) viewModel.renameCloudItem(item, newName)
                }
            )
        }

        cloudItemForMenu?.let { item ->
            CloudOptionsSheet(
                item = item,
                onDismiss = { cloudItemForMenu = null },
                onOpen = {
                    cloudItemForMenu = null
                    viewModel.openCloudItem(item)
                },
                onShare = {
                    cloudItemForMenu = null
                    viewModel.shareCloudItem(item)
                },
                onSaveToDevice = {
                    cloudItemForMenu = null
                    viewModel.saveCloudItemToDevice(item)
                },
                onRename = {
                    cloudItemForMenu = null
                    cloudItemToRename = item
                },
                onDelete = {
                    cloudItemForMenu = null
                    cloudItemToDelete = item
                }
            )
        }

        cloudItemToDelete?.let { item ->
            ZuneFlipDialog(
                onDismissRequest = { cloudItemToDelete = null },
                title = stringResource(R.string.files_delete_title),
                confirmButton = {
                    ZuneDialogButton(
                        text = stringResource(R.string.common_yes),
                        borderColor = Color.Red,
                        onClick = {
                            dismissWithAnim {
                                cloudItemToDelete = null
                                viewModel.deleteCloudItem(item)
                            }
                        }
                    )
                },
                dismissButton = {
                    ZuneDialogButton(
                        text = stringResource(R.string.common_no),
                        onClick = { dismissWithAnim { cloudItemToDelete = null } }
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

// ── Cloud Storage Page ──────────────────────────────────────────────────────

/** Index of the cloud pivot; used by the back gesture to know what it should close. */
private const val CLOUD_TAB_INDEX = 3

/**
 * Cloud storage of the Files hub. Without an account open it lists the drives that are signed in
 * and offers the two sign-in buttons; with one open it browses that drive like a local folder.
 *
 * The services are reached over their own web APIs, so nothing here depends on Drive's or
 * OneDrive's Android app being installed.
 */
@Composable
private fun CloudView(
    accounts: List<CloudAccount>,
    activeAccount: CloudAccount?,
    path: List<CloudCrumb>,
    items: List<CloudItem>,
    isBusy: Boolean,
    message: String?,
    microsoftConfigured: Boolean,
    onSignInGoogle: () -> Unit,
    onSignInMicrosoft: () -> Unit,
    onOpenAccount: (CloudAccount) -> Unit,
    onSignOut: (CloudAccount) -> Unit,
    onItemClick: (CloudItem) -> Unit,
    onItemLongClick: (CloudItem) -> Unit,
    onNavigateUp: () -> Unit,
    onNewFolder: () -> Unit
) {
    val zuneColors = LocalZuneColors.current

    if (activeAccount != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = zuneColors.accentColor,
                    modifier = Modifier.size(20.dp).clickable { onNavigateUp() }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = path.joinToString(" › ") { it.title },
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.files_new_folder),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor,
                    maxLines = 1,
                    modifier = Modifier.clickable { onNewFolder() }
                )
            }

            message?.let { CloudStatusLine(it) }

            when {
                isBusy && items.isEmpty() -> CloudMessage(stringResource(R.string.files_cloud_loading))
                items.isEmpty() -> CloudMessage(stringResource(R.string.files_empty_folder))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal,
                        bottom = 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        CloudItemRow(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ZuneDimens.ScreenPaddingHorizontal,
            end = ZuneDimens.ScreenPaddingHorizontal,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        message?.let { text ->
            item(key = "cloud_status") {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.accentColor
                )
            }
        }

        item(key = "cloud_hint") {
            Text(
                text = stringResource(
                    if (isBusy) R.string.files_cloud_signing_in else R.string.files_cloud_hint
                ),
                style = MaterialTheme.typography.labelSmall,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item(key = "cloud_accounts_header") {
            CloudSectionHeader(stringResource(R.string.files_cloud_accounts))
        }

        if (accounts.isEmpty()) {
            item(key = "cloud_no_accounts") {
                Text(
                    text = stringResource(R.string.files_cloud_none_connected),
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textDim
                )
            }
        } else {
            items(accounts, key = { it.id }) { account ->
                CloudAccountRow(
                    account = account,
                    onOpen = { onOpenAccount(account) },
                    onSignOut = { onSignOut(account) }
                )
            }
        }

        item(key = "cloud_add_header") {
            CloudSectionHeader(stringResource(R.string.files_cloud_add_account))
        }

        item(key = "cloud_sign_in_google") {
            CloudActionButton(
                icon = Icons.Default.CloudQueue,
                label = stringResource(R.string.files_cloud_sign_in_google),
                filled = true,
                onClick = onSignInGoogle
            )
        }

        item(key = "cloud_sign_in_microsoft") {
            CloudActionButton(
                icon = Icons.Default.CloudQueue,
                label = stringResource(R.string.files_cloud_sign_in_microsoft),
                filled = false,
                onClick = onSignInMicrosoft
            )
            if (!microsoftConfigured) {
                Text(
                    text = stringResource(R.string.cloud_error_microsoft_config),
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CloudStatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalZuneColors.current.accentColor,
        modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 2.dp)
    )
}

@Composable
private fun CloudSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, letterSpacing = 1.sp),
        color = LocalZuneColors.current.textMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun CloudMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
            color = LocalZuneColors.current.textMuted
        )
    }
}

/** Flat Metro button used by the sign-in actions. */
@Composable
private fun CloudActionButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Surface(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        color = if (filled) zuneColors.accentColor else Color.Transparent,
        border = if (filled) null else BorderStroke(1.dp, zuneColors.accentColor),
        shape = RoundedCornerShape(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) Color.White else zuneColors.accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (filled) Color.White else zuneColors.accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CloudAccountRow(account: CloudAccount, onOpen: () -> Unit, onSignOut: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        color = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
        shape = RoundedCornerShape(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(zuneColors.accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(account.service.titleRes) +
                        if (account.email.isBlank()) "" else " • ${account.email}",
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(R.string.files_cloud_sign_out),
                style = MaterialTheme.typography.labelSmall,
                color = zuneColors.textMuted,
                modifier = Modifier.clickable { onSignOut() }.padding(start = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CloudItemRow(item: CloudItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val folderLabel = stringResource(R.string.files_folder)
    val subtitle = remember(item, folderLabel) {
        val size = if (item.isFolder) folderLabel else formatFileSize(item.size)
        if (item.lastModified > 0L) {
            val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
            "$size • $date"
        } else {
            size
        }
    }
    val icon = remember(item.mimeType, item.isFolder) { cloudIcon(item) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
        shape = RoundedCornerShape(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isFolder) zuneColors.accentColor
                        else if (zuneColors.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (item.isFolder) Color.White else zuneColors.accentColor,
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
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CloudOptionsSheet(
    item: CloudItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveToDevice: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ZuneFlipDialog(onDismissRequest = onDismiss, title = item.name) {
        Column {
            ListItemOption(
                icon = if (item.isFolder) Icons.Default.FolderOpen else Icons.Default.OpenInNew,
                label = stringResource(R.string.common_open),
                onClick = { dismissWithAnim { onOpen() } }
            )
            if (!item.isFolder) {
                ListItemOption(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.common_share),
                    onClick = { dismissWithAnim { onShare() } }
                )
                ListItemOption(
                    icon = Icons.Default.Download,
                    label = stringResource(R.string.files_cloud_save_to_device),
                    onClick = { dismissWithAnim { onSaveToDevice() } }
                )
            }
            ListItemOption(
                icon = Icons.Default.DriveFileRenameOutline,
                label = stringResource(R.string.common_rename),
                onClick = { dismissWithAnim { onRename() } }
            )
            ListItemOption(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.common_delete),
                textColor = Color.Red,
                onClick = { dismissWithAnim { onDelete() } }
            )
        }
    }
}

private fun cloudIcon(item: CloudItem): ImageVector {
    if (item.isFolder) return Icons.Default.Folder
    val mime = item.mimeType.lowercase()
    return when {
        mime.startsWith("image/") -> Icons.Default.Image
        mime.startsWith("audio/") -> Icons.Default.MusicNote
        mime.startsWith("video/") -> Icons.Default.Movie
        mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") ||
            mime.contains("presentation") || mime.contains("sheet") -> Icons.Default.Description
        mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") -> Icons.Default.FolderZip
        mime.contains("android.package-archive") -> Icons.Default.Android
        else -> Icons.Default.InsertDriveFile
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
