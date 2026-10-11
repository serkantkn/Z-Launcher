package com.serkantkn.zunelauncher.ui.screens.files

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudCrumb
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.CloudQuota
import com.serkantkn.zunelauncher.data.model.CloudTransfer
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.data.model.StorageVolumeInfo
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.FileSort
import com.serkantkn.zunelauncher.util.PackageInstall
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.isAlreadyThere
import com.serkantkn.zunelauncher.util.isInsideItself
import com.serkantkn.zunelauncher.util.localizedString
import com.serkantkn.zunelauncher.util.matchesFileQuery
import com.serkantkn.zunelauncher.util.sanitizeFileName
import com.serkantkn.zunelauncher.util.sortFiles
import com.serkantkn.zunelauncher.util.toUserMessage
import com.serkantkn.zunelauncher.util.uniqueFileName
import com.serkantkn.zunelauncher.util.ConflictChoice
import com.serkantkn.zunelauncher.util.ConflictPlan
import com.serkantkn.zunelauncher.util.FileConflict
import com.serkantkn.zunelauncher.util.FileTransferProgress
import com.serkantkn.zunelauncher.util.TransferMeter
import com.serkantkn.zunelauncher.util.findTakenName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

/**
 * The Files hub's state.
 *
 * What it gained: every volume the phone has rather than only the built-in one, an order that can
 * be changed and is remembered, a clipboard so files can be copied and moved rather than only
 * renamed and deleted, a selection so more than one thing can be acted on at a time, and a search
 * that goes down into the folders rather than only filtering what is already on screen.
 */
class FilesHubViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = application.appContainer.settingsDataStore
    private val cloudRepository = application.appContainer.cloudStorageRepository

    // ── Where the files are ─────────────────────────────────────────────────

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes.asStateFlow()

    private val _currentVolume = MutableStateFlow<StorageVolumeInfo?>(null)
    val currentVolume: StateFlow<StorageVolumeInfo?> = _currentVolume.asStateFlow()

    private val _currentDirectory = MutableStateFlow(Environment.getExternalStorageDirectory())
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    /** True while the open folder is the top of its volume, which is where "back" leaves the hub. */
    val isAtVolumeRoot: Boolean
        get() = _currentDirectory.value.absolutePath == (_currentVolume.value?.root?.absolutePath)

    private val _fileItems = MutableStateFlow<List<FileItemModel>>(emptyList())
    val fileItems: StateFlow<List<FileItemModel>> = _fileItems.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    // ── How it is shown ─────────────────────────────────────────────────────

    val sort: StateFlow<FileSort> = settings.filesSort
        .stateIn(viewModelScope, SharingStarted.Eagerly, FileSort.NAME)

    val sortAscending: StateFlow<Boolean> = settings.filesSortAscending
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showHidden: StateFlow<Boolean> = settings.filesShowHidden
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The chosen view, or null when none has been chosen yet (each layout has its default). */
    val view: StateFlow<com.serkantkn.zunelauncher.util.FilesView?> = settings.filesView
        .map { name -> name?.let { runCatching { com.serkantkn.zunelauncher.util.FilesView.valueOf(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setView(next: com.serkantkn.zunelauncher.util.FilesView) {
        viewModelScope.launch { settings.setFilesView(next.name) }
    }

    fun setSort(next: FileSort) {
        viewModelScope.launch {
            settings.setFilesSort(next)
            reload()
        }
    }

    fun toggleSortDirection() {
        viewModelScope.launch {
            settings.setFilesSortAscending(!sortAscending.value)
            reload()
        }
    }

    fun setShowHidden(show: Boolean) {
        viewModelScope.launch {
            settings.setFilesShowHidden(show)
            reload()
        }
    }

    // ── Searching ───────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _deepResults = MutableStateFlow<List<FileItemModel>?>(null)
    /** Results of a search through the folders below, or null when none has been run. */
    val deepResults: StateFlow<List<FileItemModel>?> = _deepResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) clearDeepSearch()
    }

    fun clearDeepSearch() {
        searchJob?.cancel()
        _isSearching.value = false
        _deepResults.value = null
    }

    /**
     * Looks through everything under the open folder, not only what is in it.
     *
     * It walks rather than recursing so a deep tree cannot overflow the stack, gives way between
     * folders so the list stays responsive, and stops at a sensible number of hits — nobody reads
     * the two thousandth match.
     */
    fun searchDeep() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        val root = _currentDirectory.value
        val hidden = showHidden.value
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _deepResults.value = emptyList()
            val found = mutableListOf<FileItemModel>()
            withContext(Dispatchers.IO) {
                val queue = ArrayDeque(listOf(root))
                var visited = 0
                while (queue.isNotEmpty() && found.size < MAX_RESULTS && visited < MAX_FOLDERS) {
                    yield()
                    val directory = queue.removeFirst()
                    visited++
                    val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
                    children.forEach { child ->
                        if (!hidden && child.isHidden) return@forEach
                        if (child.isDirectory) queue.addLast(child)
                        if (matchesFileQuery(child.name, query) && found.size < MAX_RESULTS) {
                            found += child.toItem()
                        }
                    }
                }
            }
            _deepResults.value = found.toList()
            _isSearching.value = false
        }
    }

    // ── Choosing things ─────────────────────────────────────────────────────

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    /** Absolute paths of the chosen items; empty means selection mode is off. */
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    fun toggleSelection(item: FileItemModel) {
        val path = item.file.absolutePath
        _selection.value = if (path in _selection.value) _selection.value - path else _selection.value + path
    }

    fun selectAll() {
        _selection.value = visibleItems().map { it.file.absolutePath }.toSet()
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** What is on screen: the search's hits while a search is showing, the folder otherwise. */
    private fun visibleItems(): List<FileItemModel> = _deepResults.value ?: _fileItems.value

    private fun selectedItems(): List<FileItemModel> =
        visibleItems().filter { it.file.absolutePath in _selection.value }

    // ── The clipboard ───────────────────────────────────────────────────────

    /** Files waiting to be pasted, and whether they are being moved rather than copied. */
    data class Clipboard(val paths: List<String>, val isMove: Boolean)

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    private val _busyMessage = MutableStateFlow<String?>(null)
    /** What a long job is doing right now, shown under the header. */
    val busyMessage: StateFlow<String?> = _busyMessage.asStateFlow()

    fun copySelection() = putOnClipboard(isMove = false)

    fun cutSelection() = putOnClipboard(isMove = true)

    /** One item straight onto the clipboard, from its own menu, without a selection first. */
    fun copyItem(item: FileItemModel) {
        _clipboard.value = Clipboard(listOf(item.file.absolutePath), isMove = false)
        clearSelection()
    }

    fun cutItem(item: FileItemModel) {
        _clipboard.value = Clipboard(listOf(item.file.absolutePath), isMove = true)
        clearSelection()
    }

    private fun putOnClipboard(isMove: Boolean) {
        val chosen = selectedItems().ifEmpty { return }
        _clipboard.value = Clipboard(chosen.map { it.file.absolutePath }, isMove)
        clearSelection()
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    private var pasteJob: Job? = null

    // ── Pasting: how far it has gone, and names already taken ──────────────

    private val _pasteProgress = MutableStateFlow<FileTransferProgress?>(null)
    /** The paste going on now, or null when there is none. */
    val pasteProgress: StateFlow<FileTransferProgress?> = _pasteProgress.asStateFlow()

    private val _pasteConflict = MutableStateFlow<FileConflict?>(null)
    /** A name the paste is waiting to be told what to do about, or null when it is not waiting. */
    val pasteConflict: StateFlow<FileConflict?> = _pasteConflict.asStateFlow()

    /** Checked between chunks; a paused copy holds its files open and simply waits. */
    private val pastePaused = MutableStateFlow(false)

    private class ConflictAnswer(val choice: ConflictChoice, val applyToAll: Boolean)

    // Written on the disk thread by the paste, completed on the main thread by the dialog.
    @Volatile private var pendingConflict: CompletableDeferred<ConflictAnswer>? = null
    private var conflictCount = 0

    /**
     * Answers the question the paste is waiting on. A second answer to the same question — a
     * dialog that closes after its button was pressed — finds it already answered and does nothing.
     */
    fun resolveConflict(choice: ConflictChoice, applyToAll: Boolean) {
        pendingConflict?.complete(ConflictAnswer(choice, applyToAll))
    }

    /** Holds a paste where it is, or lets it go on. */
    fun togglePausePaste() {
        if (pasteJob?.isActive != true) return
        val paused = !pastePaused.value
        pastePaused.value = paused
        // Drawn at once: the copy itself says nothing more until it moves again.
        _pasteProgress.value = _pasteProgress.value?.copy(isPaused = paused, bytesPerSecond = 0L)
    }

    /**
     * Puts what is on the clipboard into the open folder.
     *
     * A move is tried as a rename first, which on the same volume is instant and costs nothing;
     * only when that fails — moving between the phone and a card, say — is it copied and then
     * deleted. A folder cannot be pasted inside itself, and a thing pasted back where it already
     * sits is left as it is rather than renamed to "(2)".
     *
     * A name that is already used in the folder is no longer quietly numbered: the paste stops and
     * asks — replace what is there, skip this one, or keep both under a numbered name — and an
     * answer given "for all" stands for the rest of the paste.
     *
     * The copy goes a piece at a time so it can be paused and stopped, and so it can say how far it
     * has gone: a half-copied file is removed, what was finished before stays.
     */
    fun paste(destination: File = _currentDirectory.value) {
        val board = _clipboard.value ?: return
        if (pasteJob?.isActive == true) return
        pastePaused.value = false
        pasteJob = viewModelScope.launch {
            val meter = TransferMeter(board.isMove, board.paths.size)
            _pasteProgress.value = meter.snapshot(isPaused = false)
            var failed = 0
            var alreadyThere = 0
            val touched = mutableListOf<String>()
            try {
                withContext(Dispatchers.IO) {
                    val taken = (destination.list() ?: emptyArray()).toMutableSet()
                    val sources = board.paths.map(::File)
                    val pastable = sources.map { source ->
                        source.exists() &&
                            !isAlreadyThere(source.absolutePath, destination.absolutePath) &&
                            !(source.isDirectory && isInsideItself(source.absolutePath, destination.absolutePath))
                    }
                    // Counted before the first byte moves, so the line under the header means
                    // something from the start rather than racing to the end of each folder.
                    val sizes = sources.mapIndexed { index, source -> if (pastable[index]) sizeOf(source) else 0L }
                    meter.setTotalBytes(sizes.sum())
                    val plan = ConflictPlan(sources.mapIndexed { index, source -> source.name.takeIf { pastable[index] } }, taken)
                    sources.forEachIndexed { index, source ->
                        ensureActive()
                        meter.begin(source.name)
                        publishPaste(meter, force = true)
                        try {
                            if (!source.exists()) { failed++; return@forEachIndexed }
                            if (isAlreadyThere(source.absolutePath, destination.absolutePath)) {
                                alreadyThere++
                                return@forEachIndexed
                            }
                            if (source.isDirectory && isInsideItself(source.absolutePath, destination.absolutePath)) {
                                failed++
                                return@forEachIndexed
                            }
                            var name = source.name
                            val clash = findTakenName(name, taken)?.let { File(destination, it) }
                            if (clash != null && clash.exists()) {
                                // The same file under another address — /sdcard and
                                // /storage/emulated/0 are one place — is already there, not in the way.
                                if (sameFile(clash, source)) {
                                    alreadyThere++
                                    meter.drop(sizes[index])
                                    return@forEachIndexed
                                }
                                val choice = plan.standing ?: run {
                                    val answer = askAboutConflict(source, clash, sizes[index], plan.othersAfter(index))
                                    plan.record(answer.choice, answer.applyToAll)
                                    // The time spent deciding is not time spent copying.
                                    meter.restartSpeed()
                                    answer.choice
                                }
                                when (choice) {
                                    ConflictChoice.SKIP -> {
                                        meter.drop(sizes[index])
                                        return@forEachIndexed
                                    }
                                    ConflictChoice.KEEP_BOTH -> name = uniqueFileName(source.name, taken)
                                    ConflictChoice.REPLACE -> {
                                        // Replacing a folder with something from inside it would
                                        // delete the very thing being pasted.
                                        if (isInsideItself(clash.absolutePath, source.absolutePath)) {
                                            failed++
                                            return@forEachIndexed
                                        }
                                        val removed = runCatching { clash.deleteRecursively() }.getOrDefault(false)
                                        if (!removed) {
                                            ZuneLog.w(TAG, "could not replace ${clash.absolutePath}")
                                            failed++
                                            return@forEachIndexed
                                        }
                                        taken -= clash.name
                                        touched += clash.absolutePath
                                    }
                                }
                            }
                            val target = File(destination, name)
                            val ok = try {
                                when {
                                    // renameTo replaces an existing target without a word; by now
                                    // nothing should be there, and the check makes sure of it.
                                    target.exists() -> false
                                    board.isMove && source.renameTo(target) -> {
                                        meter.advance(sizes[index], SystemClock.elapsedRealtime())
                                        true
                                    }
                                    else -> {
                                        val copied = copyChunked(source, target, meter)
                                        if (copied && board.isMove) source.deleteRecursively()
                                        copied
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ZuneLog.w(TAG, "could not paste ${source.absolutePath}", e)
                                false
                            }
                            if (ok) {
                                taken += name
                                touched += target.absolutePath
                                if (board.isMove) touched += source.absolutePath
                            } else {
                                failed++
                            }
                        } finally {
                            meter.finishItem()
                        }
                    }
                    publishPaste(meter, force = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _errorMessage.value = application().localizedString(R.string.files_paste_cancelled)
                scanPaths(touched)
                throw e
            } finally {
                _clipboard.value = null
                _pasteProgress.value = null
                _pasteConflict.value = null
                pendingConflict = null
                pastePaused.value = false
            }
            scanPaths(touched)
            _errorMessage.value = when {
                failed > 0 -> application().localizedString(R.string.files_paste_partial, failed)
                alreadyThere > 0 && touched.isEmpty() -> application().localizedString(R.string.files_already_here)
                else -> null
            }
            reload()
        }
    }

    /** Stops a paste that is still going, or waiting on a question; the file being copied is removed. */
    fun cancelPaste() {
        pasteJob?.cancel()
        pasteJob = null
        _busyMessage.value = null
        _pasteProgress.value = null
        _pasteConflict.value = null
        pastePaused.value = false
        reload()
    }

    /** Puts the question up and waits for it; a paste stopped meanwhile takes the question down. */
    private suspend fun askAboutConflict(source: File, existing: File, sourceSize: Long, othersRemaining: Int): ConflictAnswer {
        val answer = CompletableDeferred<ConflictAnswer>()
        pendingConflict = answer
        _pasteConflict.value = FileConflict(
            id = ++conflictCount,
            name = existing.name,
            isDirectory = source.isDirectory,
            sourceSize = sourceSize,
            sourceModified = source.lastModified(),
            existingIsDirectory = existing.isDirectory,
            existingSize = sizeOf(existing),
            existingModified = existing.lastModified(),
            othersRemaining = othersRemaining
        )
        try {
            return answer.await()
        } finally {
            pendingConflict = null
            _pasteConflict.value = null
        }
    }

    private fun publishPaste(meter: TransferMeter, force: Boolean = false) {
        if (meter.due(SystemClock.elapsedRealtime(), force)) {
            _pasteProgress.value = meter.snapshot(pastePaused.value)
        }
    }

    /** Everything a thing holds, counted file by file; a folder's own entry weighs nothing. */
    private fun sizeOf(file: File): Long = runCatching {
        if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
    }.getOrDefault(0L)

    private fun sameFile(a: File, b: File): Boolean =
        runCatching { a.canonicalPath == b.canonicalPath }.getOrDefault(false)

    /**
     * Copies a file (or a tree) in pieces, checking between pieces whether anybody has asked it to
     * stop or to wait, and telling [meter] how far it has got. [File.copyTo] cannot be interrupted
     * and would leave a half-written file behind as though it were whole.
     */
    private suspend fun copyChunked(source: File, target: File, meter: TransferMeter): Boolean {
        if (source.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) return false
            val children = source.listFiles() ?: return false
            for (child in children) {
                if (!copyChunked(child, File(target, child.name), meter)) return false
            }
            return true
        }
        // Inside a folder the line names the file actually being copied, so a long folder is
        // seen to be moving.
        meter.begin(source.name)
        val buffer = ByteArray(COPY_CHUNK)
        try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (pastePaused.value) {
                            pastePaused.first { !it }
                            meter.restartSpeed()
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        meter.advance(read.toLong(), SystemClock.elapsedRealtime())
                        publishPaste(meter)
                    }
                }
            }
        } catch (e: Throwable) {
            runCatching { target.delete() }
            throw e
        }
        return target.exists()
    }

    /**
     * Tells the media store about files that changed on disk. Without this a moved photo still
     * sits in the gallery at its old address, and the new one takes until the next reboot to show.
     */
    private fun scanPaths(paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching {
            android.media.MediaScannerConnection.scanFile(application(), paths.toTypedArray(), null, null)
        }.onFailure { ZuneLog.w(TAG, "media scan failed", it) }
    }

    // ── Reading a folder ────────────────────────────────────────────────────

    init {
        refreshVolumes()
        if (_hasPermission.value) loadDirectory(_currentDirectory.value)
        viewModelScope.launch {
            // Order and hidden files are remembered, so the first listing has to wait for them.
            settings.filesSort.first()
            reload()
        }
    }

    /**
     * Whether the hub may read and write the storage. From Android 11 only "all files access"
     * will do: READ_EXTERNAL_STORAGE there opens the media folders and nothing else, and a hub
     * that counted it as enough listed Download as empty and could not write anywhere.
     */
    fun checkPermission(): Boolean {
        val context: Context = getApplication()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager()
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshPermissionState() {
        val granted = checkPermission()
        _hasPermission.value = granted
        if (granted) {
            refreshVolumes()
            loadDirectory(_currentDirectory.value)
        }
    }

    /** Asks the phone what it can store things on: its own memory, and any card in it. */
    fun refreshVolumes() {
        val context: Context = getApplication()
        val found = mutableListOf<StorageVolumeInfo>()
        val primaryRoot = Environment.getExternalStorageDirectory()
        found += StorageVolumeInfo(
            root = primaryRoot,
            label = context.localizedString(R.string.files_internal_storage),
            isPrimary = true,
            isRemovable = false
        )
        runCatching {
            val manager = context.getSystemService(StorageManager::class.java)
            manager?.storageVolumes.orEmpty().forEach { volume ->
                if (volume.isPrimary) return@forEach
                val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory
                } else {
                    null
                } ?: return@forEach
                found += StorageVolumeInfo(
                    root = directory,
                    label = volume.getDescription(context)
                        ?: context.localizedString(R.string.files_sd_card),
                    isPrimary = false,
                    isRemovable = volume.isRemovable
                )
            }
        }.onFailure { ZuneLog.w(TAG, "the phone would not list its volumes", it) }

        _volumes.value = found
        if (_currentVolume.value == null) _currentVolume.value = found.firstOrNull()
    }

    fun openVolume(volume: StorageVolumeInfo) {
        _currentVolume.value = volume
        loadDirectory(volume.root)
    }

    fun reload() = loadDirectory(_currentDirectory.value)

    private var loadJob: Job? = null

    fun loadDirectory(directory: File) {
        clearDeepSearch()
        clearSelection()
        // Two folders opened in quick succession used to race: the slower listing landed last
        // and was shown under the faster one's name.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _currentDirectory.value = directory
            _currentVolume.value = volumeOf(directory)
            if (!directory.exists() || !directory.canRead()) {
                ZuneLog.w(TAG, "loadDirectory: unreadable ${directory.absolutePath}")
                _errorMessage.value = application().localizedString(
                    if (!directory.exists()) R.string.files_folder_missing else R.string.files_folder_unreadable
                )
                _fileItems.value = emptyList()
                return@launch
            }
            _errorMessage.value = null
            _isLoading.value = true
            _fileItems.value = readDirectory(directory)
            _isLoading.value = false
        }
    }

    /**
     * Reads a folder without showing it. The screen uses this to have the folder above ready
     * before it leaves the one it is in, so the jump back has a row to land on the moment the
     * page changes rather than a blank while the storage is asked.
     */
    suspend fun readDirectory(directory: File): List<FileItemModel> {
        val hidden = showHidden.value
        val order = sort.value
        val ascending = sortAscending.value
        return withContext(Dispatchers.IO) {
            val files = runCatching { directory.listFiles() }.getOrNull() ?: emptyArray()
            val visible = files.filter { hidden || !it.isHidden }.map { it.toItem() }
            sortFiles(visible, order, ascending)
        }
    }

    /** Shows a folder that [readDirectory] has already read, in one step. */
    fun showDirectory(directory: File, items: List<FileItemModel>) {
        clearDeepSearch()
        clearSelection()
        _errorMessage.value = null
        _currentDirectory.value = directory
        _currentVolume.value = volumeOf(directory)
        _fileItems.value = items
    }

    /** The folder above, or null when the top of the volume is already open. */
    fun parentOfCurrent(): File? {
        val current = _currentDirectory.value
        val volumeRoot = _currentVolume.value?.root ?: Environment.getExternalStorageDirectory()
        val parent = current.parentFile ?: return null
        return if (current.absolutePath != volumeRoot.absolutePath && parent.exists()) parent else null
    }

    private fun volumeOf(directory: File): StorageVolumeInfo? =
        _volumes.value
            .filter { directory.absolutePath.startsWith(it.root.absolutePath) }
            .maxByOrNull { it.root.absolutePath.length }
            ?: _currentVolume.value

    fun navigateTo(directory: File) {
        if (directory.isDirectory) loadDirectory(directory)
    }

    // ── Where the user has been ─────────────────────────────────────────────

    // Back returns to the place you came from — a folder opened from the libraries goes back to
    // the folder you were in, a trip to the SD card comes back to the phone's storage — rather
    // than only ever up the tree. Phone and tablet keep the same trail.
    private val backStack = ArrayDeque<File>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private fun pushStop(directory: File) {
        if (backStack.lastOrNull()?.absolutePath != directory.absolutePath) backStack.addLast(directory)
        while (backStack.size > MAX_BACK_STOPS) backStack.removeFirst()
        _canGoBack.value = true
    }

    /** Opens [directory] as a step that Back can return from. */
    fun visit(directory: File) {
        val current = _currentDirectory.value
        if (!directory.isDirectory || directory.absolutePath == current.absolutePath) return
        pushStop(current)
        loadDirectory(directory)
    }

    /** Opens a volume's top as a step that Back can return from. */
    fun visitVolume(volume: StorageVolumeInfo) {
        val current = _currentDirectory.value
        if (volume.root.absolutePath == current.absolutePath) return
        pushStop(current)
        openVolume(volume)
    }

    /** Where Back would go next: the last place visited, or the folder above. */
    fun backTarget(): File? = backStack.lastOrNull() ?: parentOfCurrent()

    /** Goes back a step; false when there is nowhere left to go but out of the hub. */
    fun goBack(): Boolean {
        val stop = backStack.removeLastOrNull()
        _canGoBack.value = backStack.isNotEmpty()
        if (stop == null) return navigateUp()
        if (stop.exists()) loadDirectory(stop) else return goBack()
        return true
    }

    /**
     * The step back has been taken by the screen itself (the phone's word flying back down onto
     * its row, which shows the folder above through [showDirectory]); forget it here.
     */
    fun consumeBackStop(directory: File) {
        if (backStack.lastOrNull()?.absolutePath == directory.absolutePath) backStack.removeLast()
        _canGoBack.value = backStack.isNotEmpty()
    }

    /** Goes up one folder; false when the top of the volume is already open. */
    fun navigateUp(): Boolean {
        val current = _currentDirectory.value
        val volumeRoot = _currentVolume.value?.root ?: Environment.getExternalStorageDirectory()
        val parent = current.parentFile
        return if (parent != null && current.absolutePath != volumeRoot.absolutePath && parent.exists()) {
            loadDirectory(parent)
            true
        } else {
            false
        }
    }

    // ── Changing things ─────────────────────────────────────────────────────

    // Every change to the disk runs off the main thread and says so when it did not happen; a
    // tap that quietly did nothing is worse than one that failed out loud.

    fun createDirectory(folderName: String) {
        val name = folderName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val newDir = File(_currentDirectory.value, name)
            val created = withContext(Dispatchers.IO) {
                !newDir.exists() && runCatching { newDir.mkdirs() }.getOrDefault(false)
            }
            if (created) reload() else _errorMessage.value = application().localizedString(R.string.files_folder_failed)
        }
    }

    fun deleteItem(item: FileItemModel) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                runCatching { if (item.isDirectory) item.file.deleteRecursively() else item.file.delete() }
                    .getOrDefault(false)
            }
            if (deleted) scanPaths(listOf(item.file.absolutePath))
            else _errorMessage.value = application().localizedString(R.string.files_delete_failed, item.name)
            reload()
        }
    }

    /** Deletes everything chosen, reporting how many would not go. */
    fun deleteSelection() {
        val chosen = selectedItems().ifEmpty { return }
        viewModelScope.launch {
            _busyMessage.value = application().localizedString(R.string.files_working)
            val gone = mutableListOf<String>()
            val failed = withContext(Dispatchers.IO) {
                chosen.count { item ->
                    yield()
                    val ok = runCatching { if (item.isDirectory) item.file.deleteRecursively() else item.file.delete() }
                        .getOrDefault(false)
                    if (ok) gone += item.file.absolutePath
                    !ok
                }
            }
            _busyMessage.value = null
            clearSelection()
            scanPaths(gone)
            if (failed > 0) {
                _errorMessage.value = application().localizedString(R.string.files_delete_partial, failed)
            }
            reload()
        }
    }

    /**
     * Gives a thing a new name. A change of case only — "Rapor" to "rapor" — is a rename to a
     * name shared storage considers the same, which it refuses; so it goes by way of a temporary
     * name, the way a desktop file manager does it.
     */
    fun renameItem(item: FileItemModel, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == item.name) return
        viewModelScope.launch {
            val source = item.file
            val target = File(source.parentFile, trimmed)
            val caseOnly = trimmed.equals(item.name, ignoreCase = true)
            val renamed = withContext(Dispatchers.IO) {
                runCatching {
                    if (!caseOnly && target.exists()) return@runCatching false
                    if (caseOnly) {
                        val temp = File(source.parentFile, uniqueFileName("$trimmed.renaming", source.parentFile?.list()?.toSet() ?: emptySet()))
                        source.renameTo(temp) && temp.renameTo(target)
                    } else {
                        source.renameTo(target)
                    }
                }.getOrDefault(false)
            }
            if (renamed) {
                scanPaths(listOf(source.absolutePath, target.absolutePath))
                reload()
            } else {
                _errorMessage.value = application().localizedString(R.string.files_rename_failed, item.name)
            }
        }
    }

    fun openFile(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            // An .apk goes to the installer, which has to be asked for by name.
            if (PackageInstall.isApk(file.name)) {
                PackageInstall.open(context, uri)
                return
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeOf(file) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            ZuneLog.e(TAG, "openFile failed", it)
            _errorMessage.value = application().localizedString(R.string.cloud_error_no_app)
        }
    }

    /** The system's own chooser, for a file the hub cannot show or the person wants elsewhere. */
    fun openWith(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeOf(file) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.files_open_with))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }.onFailure {
            ZuneLog.e(TAG, "openWith failed", it)
            _errorMessage.value = application().localizedString(R.string.cloud_error_no_app)
        }
    }

    fun shareFile(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mimeTypeOf(file) ?: "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.files_share_chooser))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }.onFailure { ZuneLog.e(TAG, "shareFile failed", it) }
    }

    /** How much a folder holds, counted only when somebody asks for its details. */
    suspend fun measure(item: FileItemModel): Pair<Long, Int> = withContext(Dispatchers.IO) {
        if (!item.isDirectory) return@withContext item.size to 1
        var bytes = 0L
        var count = 0
        val queue = ArrayDeque(listOf(item.file))
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_FOLDERS) {
            yield()
            val directory = queue.removeFirst()
            visited++
            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
            children.forEach { child ->
                if (child.isDirectory) queue.addLast(child) else { bytes += child.length(); count++ }
            }
        }
        bytes to count
    }

    private fun mimeTypeOf(file: File): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())

    private fun File.toItem() = FileItemModel(
        file = this,
        name = name,
        isDirectory = isDirectory,
        size = if (isDirectory) 0L else length(),
        lastModified = lastModified(),
        extension = extension.lowercase()
    )

    private fun application(): Application = getApplication()

    // ── Cloud storage ───────────────────────────────────────────────────────

    val cloudAccounts: StateFlow<List<CloudAccount>> = cloudRepository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeCloudAccount = MutableStateFlow<CloudAccount?>(null)
    val activeCloudAccount: StateFlow<CloudAccount?> = _activeCloudAccount.asStateFlow()

    private val _cloudPath = MutableStateFlow<List<CloudCrumb>>(emptyList())
    val cloudPath: StateFlow<List<CloudCrumb>> = _cloudPath.asStateFlow()

    private val _cloudItems = MutableStateFlow<List<CloudItem>>(emptyList())
    val cloudItems: StateFlow<List<CloudItem>> = _cloudItems.asStateFlow()

    private val _cloudBusy = MutableStateFlow(false)
    val cloudBusy: StateFlow<Boolean> = _cloudBusy.asStateFlow()

    private val _cloudMessage = MutableStateFlow<String?>(null)
    val cloudMessage: StateFlow<String?> = _cloudMessage.asStateFlow()

    private val _cloudQuota = MutableStateFlow<CloudQuota?>(null)
    val cloudQuota: StateFlow<CloudQuota?> = _cloudQuota.asStateFlow()

    private val _transfer = MutableStateFlow<CloudTransfer?>(null)
    /** The download or upload happening right now, or null. */
    val transfer: StateFlow<CloudTransfer?> = _transfer.asStateFlow()

    private var transferJob: Job? = null

    private val _cloudSearchResults = MutableStateFlow<List<CloudItem>?>(null)
    val cloudSearchResults: StateFlow<List<CloudItem>?> = _cloudSearchResults.asStateFlow()

    /**
     * Folders already read, so stepping back out of one is instant.
     *
     * Every listing used to be a fresh network call, which meant going up a level sat on a
     * spinner for as long as the first visit did. The cache is dropped whenever the folder is
     * changed by us, and can always be thrown away by hand.
     */
    private val folderCache = mutableMapOf<String, List<CloudItem>>()

    fun googleAuthorizeTask() = cloudRepository.googleAuthorizeTask()

    fun googleResultFromIntent(data: Intent) = cloudRepository.googleResultFromIntent(data)

    fun reportCloudError(throwable: Throwable) {
        _cloudBusy.value = false
        _cloudMessage.value = cloudMessageOf(throwable)
    }

    fun clearCloudMessage() {
        _cloudMessage.value = null
    }

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
        folderCache.clear()
        _cloudPath.value = listOf(CloudCrumb(cloudRepository.rootFolderId(account), account.displayName))
        loadCloudFolder()
        viewModelScope.launch { _cloudQuota.value = cloudRepository.quota(account) }
    }

    fun openCloudFolder(item: CloudItem) {
        if (!item.isFolder) return
        _cloudPath.value = _cloudPath.value + CloudCrumb(item.id, item.name)
        loadCloudFolder()
    }

    fun cloudNavigateUp(): Boolean {
        if (_cloudSearchResults.value != null) {
            _cloudSearchResults.value = null
            return true
        }
        val path = _cloudPath.value
        if (path.size <= 1) return false
        _cloudPath.value = path.dropLast(1)
        loadCloudFolder()
        return true
    }

    fun closeCloudAccount() {
        _activeCloudAccount.value = null
        _cloudPath.value = emptyList()
        _cloudItems.value = emptyList()
        _cloudMessage.value = null
        _cloudQuota.value = null
        _cloudSearchResults.value = null
        folderCache.clear()
    }

    fun reloadCloudFolder() = loadCloudFolder(force = true)

    private var cloudSearchJob: Job? = null

    /**
     * Asks the service to look through the whole drive, not only the open folder. It waits for
     * the typing to pause first: every keystroke used to be a request of its own, and the
     * answers came back in whatever order the network felt like.
     */
    fun searchCloud(query: String) {
        val account = _activeCloudAccount.value ?: return
        cloudSearchJob?.cancel()
        if (query.isBlank()) {
            _cloudSearchResults.value = null
            _cloudBusy.value = false
            return
        }
        cloudSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(CLOUD_SEARCH_DEBOUNCE_MS)
            _cloudBusy.value = true
            try {
                _cloudSearchResults.value = cloudRepository.search(account, query)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _cloudBusy.value = false
            }
        }
    }

    fun clearCloudSearch() {
        _cloudSearchResults.value = null
    }

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

    /** Downloads into the device's Downloads folder, with a bar and a way to stop it. */
    fun saveCloudItemToDevice(item: CloudItem) {
        val account = _activeCloudAccount.value ?: return
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            _transfer.value = CloudTransfer(item.name, isUpload = false, transferredBytes = 0L, totalBytes = item.size)
            try {
                val name = cloudRepository.saveToDevice(account, item) { moved, total ->
                    _transfer.value = CloudTransfer(item.name, false, moved, total)
                }
                _cloudMessage.value = application().localizedString(R.string.files_cloud_saved, name)
            } catch (e: Exception) {
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _transfer.value = null
            }
        }
    }

    /** Sends a file from the phone up into the open cloud folder. */
    /**
     * Sends the chosen files up to a drive: the open one's current folder, or, with none open,
     * the top of the first account. Folders are skipped — a drive takes files one at a time.
     */
    fun uploadSelectionToCloud() {
        val files = selectedItems().filter { !it.isDirectory }.map { it.file }
        clearSelection()
        uploadFilesToCloud(files)
    }

    fun uploadFilesToCloud(files: List<File>) {
        if (files.isEmpty()) return
        if (_activeCloudAccount.value == null) {
            val first = cloudAccounts.value.firstOrNull() ?: return
            openCloudAccount(first)
        }
        val account = _activeCloudAccount.value ?: return
        val parent = _cloudPath.value.lastOrNull()?.id ?: return
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            for (file in files) {
                _transfer.value = CloudTransfer(file.name, isUpload = true, transferredBytes = 0L, totalBytes = file.length())
                try {
                    cloudRepository.upload(account, parent, file) { moved, total ->
                        _transfer.value = CloudTransfer(file.name, true, moved, total)
                    }
                    _cloudMessage.value = application().localizedString(R.string.files_cloud_uploaded, file.name)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _cloudMessage.value = cloudMessageOf(e)
                }
            }
            _transfer.value = null
            folderCache.remove(parent)
            loadCloudFolder(force = true)
        }
    }

    fun uploadToCloud(file: File) {
        val account = _activeCloudAccount.value ?: return
        val parent = _cloudPath.value.lastOrNull()?.id ?: return
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            _transfer.value = CloudTransfer(file.name, isUpload = true, transferredBytes = 0L, totalBytes = file.length())
            try {
                cloudRepository.upload(account, parent, file) { moved, total ->
                    _transfer.value = CloudTransfer(file.name, true, moved, total)
                }
                _cloudMessage.value = application().localizedString(R.string.files_cloud_uploaded, file.name)
                folderCache.remove(parent)
                loadCloudFolder(force = true)
            } catch (e: Exception) {
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _transfer.value = null
            }
        }
    }

    /**
     * Sends whatever the document picker handed back.
     *
     * A picked document is a content URI, not a file, so it is copied into the cache first — the
     * upload needs a length and a stream it can re-read, and a provider gives neither reliably.
     */
    fun uploadPickedUri(uri: android.net.Uri) {
        val account = _activeCloudAccount.value ?: return
        val parent = _cloudPath.value.lastOrNull()?.id ?: return
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            val context: Context = getApplication()
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val name = sanitizeFileName(queryDisplayName(context, uri), fallback = "upload")
                    val target = File(context.cacheDir, "upload").apply { mkdirs() }.let { File(it, name) }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@runCatching null
                    target
                }.getOrNull()
            }
            if (staged == null) {
                _cloudMessage.value = application().localizedString(R.string.files_upload_failed)
                return@launch
            }
            _transfer.value = CloudTransfer(staged.name, isUpload = true, transferredBytes = 0L, totalBytes = staged.length())
            try {
                cloudRepository.upload(account, parent, staged) { moved, total ->
                    _transfer.value = CloudTransfer(staged.name, true, moved, total)
                }
                _cloudMessage.value = application().localizedString(R.string.files_cloud_uploaded, staged.name)
                folderCache.remove(parent)
                loadCloudFolder(force = true)
            } catch (e: Exception) {
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _transfer.value = null
                runCatching { staged.delete() }
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: android.net.Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    /** Stops whatever is being moved, leaving what arrived behind. */
    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _transfer.value = null
    }

    private fun loadCloudFolder(force: Boolean = false) {
        val account = _activeCloudAccount.value ?: return
        val current = _cloudPath.value.lastOrNull() ?: return
        _cloudSearchResults.value = null

        val cached = folderCache[current.id]
        if (cached != null && !force) {
            // Show what we already have at once, then quietly check whether it is still right.
            _cloudItems.value = cached
        }
        viewModelScope.launch {
            if (cached == null || force) _cloudBusy.value = true
            _cloudMessage.value = null
            try {
                val items = cloudRepository.list(account, current.id)
                folderCache[current.id] = items
                _cloudItems.value = items
            } catch (e: Exception) {
                ZuneLog.w(TAG, "cloud folder could not be read", e)
                if (cached == null) _cloudItems.value = emptyList()
                _cloudMessage.value = cloudMessageOf(e)
            } finally {
                _cloudBusy.value = false
            }
        }
    }

    private fun runCloudAction(block: suspend (CloudAccount, String) -> Unit) {
        val account = _activeCloudAccount.value ?: return
        val parent = _cloudPath.value.lastOrNull()?.id ?: return
        viewModelScope.launch {
            _cloudBusy.value = true
            try {
                block(account, parent)
                _cloudMessage.value = null
                folderCache.remove(parent)
                loadCloudFolder(force = true)
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
        val app = application()
        return if (throwable is CloudException) app.localizedString(throwable.messageRes)
        else throwable.toUserMessage(app)
    }

    private companion object {
        private const val MAX_BACK_STOPS = 64

        const val TAG = "FilesHubViewModel"

        /** Enough matches to scroll through; past this, the query is the thing to fix. */
        const val MAX_RESULTS = 500

        /** A guard against a tree that loops through a symbolic link. */
        const val MAX_FOLDERS = 4000

        /** How much of a file is copied between looks at the cancel flag. */
        const val COPY_CHUNK = 256 * 1024

        /** How long the typing has to pause before the cloud is asked. */
        const val CLOUD_SEARCH_DEBOUNCE_MS = 300L
    }
}
