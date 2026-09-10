package com.serkantkn.zunelauncher.ui.screens.files

import com.serkantkn.zunelauncher.util.localizedString
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ZuneLog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudCrumb
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilesHubViewModel(application: Application) : AndroidViewModel(application) {

    private val rootDirectory = Environment.getExternalStorageDirectory()

    private val _currentDirectory = MutableStateFlow<File>(rootDirectory)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /** Last load failure as user text, or null. Shown by the hub's empty state. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _fileItems = MutableStateFlow<List<FileItemModel>>(emptyList())
    val fileItems: StateFlow<List<FileItemModel>> = _fileItems.asStateFlow()

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        if (_hasPermission.value) {
            loadDirectory(_currentDirectory.value)
        }
    }

    fun checkPermission(): Boolean {
        val context: Context = getApplication()
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        return hasAllFilesAccess || hasReadPermission
    }

    fun refreshPermissionState() {
        val granted = checkPermission()
        _hasPermission.value = granted
        if (granted) {
            loadDirectory(_currentDirectory.value)
        }
    }

    fun loadDirectory(directory: File) {
        viewModelScope.launch {
            _currentDirectory.value = directory
            if (!directory.exists() || !directory.canRead()) {
                ZuneLog.w("FilesHubViewModel", "loadDirectory: unreadable ${directory.absolutePath}")
                _errorMessage.value = if (!directory.exists()) getApplication<Application>().localizedString(R.string.files_folder_missing) else getApplication<Application>().localizedString(R.string.files_folder_unreadable)
                _fileItems.value = emptyList()
                return@launch
            }
            _errorMessage.value = null
            val items = withContext(Dispatchers.IO) {
                val files = directory.listFiles() ?: emptyArray()
                files.map { file ->
                    FileItemModel(
                        file = file,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        size = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        extension = file.extension.lowercase()
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
            _fileItems.value = items
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun navigateTo(directory: File) {
        // Unreadable folders are entered too: loadDirectory shows "klasör okunamıyor" instead of
        // silently ignoring the tap.
        if (directory.isDirectory) {
            loadDirectory(directory)
        }
    }

    fun navigateUp(): Boolean {
        val current = _currentDirectory.value
        val parent = current.parentFile
        return if (parent != null && current != rootDirectory && parent.exists()) {
            loadDirectory(parent)
            true
        } else {
            false
        }
    }

    fun createDirectory(folderName: String): Boolean {
        val newDir = File(_currentDirectory.value, folderName)
        return if (!newDir.exists()) {
            val created = newDir.mkdirs()
            if (created) {
                loadDirectory(_currentDirectory.value)
            }
            created
        } else false
    }

    fun deleteItem(item: FileItemModel): Boolean {
        val deleted = if (item.isDirectory) {
            item.file.deleteRecursively()
        } else {
            item.file.delete()
        }
        if (deleted) {
            loadDirectory(_currentDirectory.value)
        }
        return deleted
    }

    fun renameItem(item: FileItemModel, newName: String): Boolean {
        val target = File(item.file.parentFile, newName)
        val renamed = item.file.renameTo(target)
        if (renamed) {
            loadDirectory(_currentDirectory.value)
        }
        return renamed
    }

    fun openFile(context: Context, file: File) {
        try {
            val mimeType = getMimeType(file)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            ZuneLog.e("FilesHubViewModel", "openFile failed", e)
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            val mimeType = getMimeType(file)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mimeType ?: "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.files_share_chooser)))
        } catch (e: Exception) {
            ZuneLog.e("FilesHubViewModel", "shareFile failed", e)
        }
    }

    private fun getMimeType(file: File): String? {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    // --- Cloud storage (services over their own web APIs) ----------------------------------------

    private val cloudRepository = application.appContainer.cloudStorageRepository

    /** Accounts the user signed in to; the drive of each one opens without its app installed. */
    val cloudAccounts: StateFlow<List<CloudAccount>> = cloudRepository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeCloudAccount = MutableStateFlow<CloudAccount?>(null)
    val activeCloudAccount: StateFlow<CloudAccount?> = _activeCloudAccount.asStateFlow()

    private val _cloudPath = MutableStateFlow<List<CloudCrumb>>(emptyList())
    /** Folders entered inside the open account; the last one is what is on screen. */
    val cloudPath: StateFlow<List<CloudCrumb>> = _cloudPath.asStateFlow()

    private val _cloudItems = MutableStateFlow<List<CloudItem>>(emptyList())
    val cloudItems: StateFlow<List<CloudItem>> = _cloudItems.asStateFlow()

    private val _cloudBusy = MutableStateFlow(false)
    /** True while a drive is being read or an account is signing in. */
    val cloudBusy: StateFlow<Boolean> = _cloudBusy.asStateFlow()

    private val _cloudMessage = MutableStateFlow<String?>(null)
    /** Last failure or confirmation, shown under the header. */
    val cloudMessage: StateFlow<String?> = _cloudMessage.asStateFlow()


    fun googleAuthorizeTask() = cloudRepository.googleAuthorizeTask()

    fun googleResultFromIntent(data: Intent) = cloudRepository.googleResultFromIntent(data)

    fun reportCloudError(throwable: Throwable) {
        _cloudBusy.value = false
        _cloudMessage.value = cloudMessageOf(throwable)
    }

    fun clearCloudMessage() {
        _cloudMessage.value = null
    }

    /** Stores the Google account that just granted the Drive scope and opens its drive. */
    fun completeGoogleSignIn(result: AuthorizationResult) {
        viewModelScope.launch {
            _cloudBusy.value = true
            try {
                openCloudAccount(cloudRepository.completeGoogleSignIn(result))
                _cloudMessage.value = null
            } catch (e: Exception) {
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _cloudBusy.value = false
            }
        }
    }

    fun signOutCloudAccount(account: CloudAccount) {
        viewModelScope.launch {
            if (_activeCloudAccount.value?.id == account.id) closeCloudAccount()
            cloudRepository.signOut(account)
        }
    }

    fun openCloudAccount(account: CloudAccount) {
        _activeCloudAccount.value = account
        _cloudPath.value = listOf(CloudCrumb(cloudRepository.rootFolderId(account), account.displayName))
        loadCloudFolder()
    }

    fun openCloudFolder(item: CloudItem) {
        if (!item.isFolder) return
        _cloudPath.value = _cloudPath.value + CloudCrumb(item.id, item.name)
        loadCloudFolder()
    }

    /** Goes one folder up; returns false when the drive's top folder is on screen. */
    fun cloudNavigateUp(): Boolean {
        val path = _cloudPath.value
        if (path.size <= 1) return false
        _cloudPath.value = path.dropLast(1)
        loadCloudFolder()
        return true
    }

    /** Leaves the drive and goes back to the list of accounts. */
    fun closeCloudAccount() {
        _activeCloudAccount.value = null
        _cloudPath.value = emptyList()
        _cloudItems.value = emptyList()
        _cloudMessage.value = null
    }

    fun reloadCloudFolder() = loadCloudFolder()

    fun createCloudFolder(name: String) = runCloudAction { account, parent ->
        cloudRepository.createFolder(account, parent, name.trim())
    }

    fun renameCloudItem(item: CloudItem, newName: String) = runCloudAction { account, _ ->
        cloudRepository.rename(account, item, newName.trim())
    }

    fun deleteCloudItem(item: CloudItem) = runCloudAction { account, _ ->
        cloudRepository.delete(account, item)
    }

    fun openCloudItem(item: CloudItem) {
        if (item.isFolder) {
            openCloudFolder(item)
            return
        }
        withCloudAccount { account -> cloudRepository.open(account, item) }
    }

    fun shareCloudItem(item: CloudItem) =
        withCloudAccount { account -> cloudRepository.share(account, item) }

    /** Downloads the document into the device's Downloads folder. */
    fun saveCloudItemToDevice(item: CloudItem) = withCloudAccount { account ->
        val name = cloudRepository.saveToDevice(account, item)
        _cloudMessage.value = getApplication<Application>().localizedString(R.string.files_cloud_saved, name)
    }

    private fun loadCloudFolder() {
        val account = _activeCloudAccount.value ?: return
        val current = _cloudPath.value.lastOrNull() ?: return
        viewModelScope.launch {
            _cloudBusy.value = true
            _cloudMessage.value = null
            try {
                _cloudItems.value = cloudRepository.list(account, current.id)
            } catch (e: Exception) {
                ZuneLog.w("FilesHubViewModel", "cloud folder could not be read", e)
                _cloudItems.value = emptyList()
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _cloudBusy.value = false
            }
        }
    }

    /** Runs a change on the open folder and reloads it, reporting whatever the service says. */
    private fun runCloudAction(block: suspend (CloudAccount, String) -> Unit) {
        val account = _activeCloudAccount.value ?: return
        val parent = _cloudPath.value.lastOrNull()?.id ?: return
        viewModelScope.launch {
            _cloudBusy.value = true
            try {
                block(account, parent)
                _cloudMessage.value = null
                loadCloudFolder()
            } catch (e: Exception) {
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _cloudBusy.value = false
            }
        }
    }

    private fun withCloudAccount(block: suspend (CloudAccount) -> Unit) {
        val account = _activeCloudAccount.value ?: return
        viewModelScope.launch {
            _cloudBusy.value = true
            try {
                block(account)
            } catch (e: Exception) {
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _cloudBusy.value = false
            }
        }
    }

    private fun cloudMessageOf(throwable: Throwable): String {
        val application = getApplication<Application>()
        return if (throwable is CloudException) {
            application.localizedString(throwable.messageRes)
        } else {
            throwable.toUserMessage(application)
        }
    }
}
