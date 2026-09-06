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
import com.serkantkn.zunelauncher.data.model.FileItemModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}
