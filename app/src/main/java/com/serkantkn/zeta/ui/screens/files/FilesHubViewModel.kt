package com.serkantkn.zeta.ui.screens.files

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.FileItemModel
import com.serkantkn.zeta.data.model.FileTransfer
import com.serkantkn.zeta.data.model.StorageVolumeInfo
import com.serkantkn.zeta.data.repository.CloudDocument
import com.serkantkn.zeta.data.repository.CloudDocuments
import com.serkantkn.zeta.data.repository.RecycleBin
import com.serkantkn.zeta.data.repository.RecycleBinEntry
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.util.DropAction
import com.serkantkn.zeta.util.FileSort
import com.serkantkn.zeta.util.PackageInstall
import com.serkantkn.zeta.util.SafeReplace
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.dropActionFor
import com.serkantkn.zeta.util.isAlreadyThere
import com.serkantkn.zeta.util.isInsideItself
import com.serkantkn.zeta.util.localizedString
import com.serkantkn.zeta.util.matchesFileQuery
import com.serkantkn.zeta.util.sortFiles
import com.serkantkn.zeta.util.uniqueFileName
import com.serkantkn.zeta.util.ConflictChoice
import com.serkantkn.zeta.util.ConflictPlan
import com.serkantkn.zeta.util.FileConflict
import com.serkantkn.zeta.util.FileTransferProgress
import com.serkantkn.zeta.util.TransferMeter
import com.serkantkn.zeta.util.findTakenName
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
    val view: StateFlow<com.serkantkn.zeta.util.FilesView?> = settings.filesView
        .map { name -> name?.let { runCatching { com.serkantkn.zeta.util.FilesView.valueOf(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setView(next: com.serkantkn.zeta.util.FilesView) {
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
                        if (child.name == RecycleBin.FOLDER_NAME) return@forEach
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

    /** Chooses exactly [paths]: a Shift range, a right-click on something not yet chosen. */
    fun setSelection(paths: Collection<String>) {
        _selection.value = paths.toSet()
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
                    // A replace cut short by the launcher being killed is put right before anything
                    // else lands here.
                    touched += SafeReplace.recover(destination)
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
                            // What this one is to stand in place of, when the answer is "replace".
                            var replacing: File? = null
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
                                        // The old one stays until the new one is whole.
                                        replacing = clash
                                    }
                                }
                            }
                            val target = File(destination, name)
                            // A replacement is written beside the old one under a hidden name, and
                            // the two change places only once it is all there (see SafeReplace).
                            val landing = if (replacing != null) File(destination, SafeReplace.incomingName(name)) else target
                            var renamed = false
                            val ok = try {
                                val arrived = when {
                                    // renameTo replaces an existing target without a word; by now
                                    // nothing should be there, and the check makes sure of it.
                                    landing.exists() -> false
                                    board.isMove && source.renameTo(landing) -> {
                                        renamed = true
                                        meter.advance(sizes[index], SystemClock.elapsedRealtime())
                                        true
                                    }
                                    else -> copyChunked(source, landing, meter)
                                }
                                val placed = when {
                                    !arrived -> {
                                        // Whatever part of a replacement was written goes; the old
                                        // one was never touched.
                                        if (replacing != null && !renamed) landing.deleteRecursively()
                                        false
                                    }
                                    replacing == null -> true
                                    SafeReplace.swapIn(landing, replacing, target) -> true
                                    else -> {
                                        ZuneLog.w(TAG, "could not replace ${replacing.absolutePath}")
                                        // The old one is back where it was; the new one goes back
                                        // where it came from.
                                        if (renamed) landing.renameTo(source) else landing.deleteRecursively()
                                        false
                                    }
                                }
                                // A move lets go of the original only once its copy is in place.
                                if (placed && board.isMove && !renamed) source.deleteRecursively()
                                placed
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                if (replacing != null && !renamed) landing.deleteRecursively()
                                throw e
                            } catch (e: Exception) {
                                ZuneLog.w(TAG, "could not paste ${source.absolutePath}", e)
                                if (replacing != null && !renamed) landing.deleteRecursively()
                                false
                            }
                            if (ok) {
                                replacing?.let { old ->
                                    taken -= old.name
                                    touched += old.absolutePath
                                }
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
            refreshRecycleBin()
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
        // A search is of the folder it was typed in: carried into the next one it hid that
        // folder's whole contents behind "nothing found". Reloading the same folder keeps it.
        if (directory.absolutePath != _currentDirectory.value.absolutePath) _searchQuery.value = ""
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
            // The recycle bin is the hub's own, and is reached from its own entry; it stays out
            // of the folder it sits in even with hidden things showing.
            val visible = files
                .filter { (hidden || !it.isHidden) && it.name != RecycleBin.FOLDER_NAME }
                .map { it.toItem() }
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
        _inRecycleBin.value = false
        val current = _currentDirectory.value
        if (!directory.isDirectory || directory.absolutePath == current.absolutePath) return
        pushStop(current)
        loadDirectory(directory)
    }

    /** Opens a volume's top as a step that Back can return from. */
    fun visitVolume(volume: StorageVolumeInfo) {
        _inRecycleBin.value = false
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

    /** Moves one thing to the recycle bin; it is deleted for good only from there. */
    fun deleteItem(item: FileItemModel) = moveToRecycleBin(listOf(item))

    /** Moves everything chosen to the recycle bin, reporting how many would not go. */
    fun deleteSelection() {
        val chosen = selectedItems().ifEmpty { return }
        clearSelection()
        moveToRecycleBin(chosen)
    }

    private fun moveToRecycleBin(items: List<FileItemModel>) {
        viewModelScope.launch {
            if (items.size > 1) _busyMessage.value = application().localizedString(R.string.files_working)
            val result = recycleBinStore.trash(items.map { it.file }, _volumes.value.map { it.root })
            _busyMessage.value = null
            // The media store still has the old addresses; a scan of a path that has gone
            // takes it out of the gallery.
            scanPaths(result.trashed.map { it.originalPath })
            _errorMessage.value = when {
                result.failed.isEmpty() -> null
                items.size == 1 -> application().localizedString(R.string.files_trash_failed, items.single().name)
                else -> application().localizedString(R.string.files_delete_partial, result.failed.size)
            }
            reload()
            refreshRecycleBin()
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
        extension = extension.lowercase(),
        childCount = if (isDirectory) childCountOf(this) else -1
    )

    /** What a folder holds, as the folder's own listing would show it; -1 when it cannot be read. */
    private fun childCountOf(folder: File): Int {
        val names = folder.list() ?: return -1
        return if (showHidden.value) names.size else names.count { !it.startsWith(".") }
    }

    private fun application(): Application = getApplication()

    // ── The recycle bin ─────────────────────────────────────────────────────

    // A delete is a move into the bin of the volume the thing is on (RecycleBin). The bin is a
    // place of its own on both layouts — a row on the phone's quick access page, an entry at the
    // foot of the tablet's places — with its own selection, so choosing things in it never mixes
    // with choosing things in a folder.

    private val recycleBinStore = RecycleBin()

    private val _recycleBin = MutableStateFlow<List<RecycleBinEntry>>(emptyList())
    /** What is in the bins of every volume, the most recently deleted first. */
    val recycleBin: StateFlow<List<RecycleBinEntry>> = _recycleBin.asStateFlow()

    private val _inRecycleBin = MutableStateFlow(false)
    /** Whether the bin is the place open now rather than a folder. */
    val inRecycleBin: StateFlow<Boolean> = _inRecycleBin.asStateFlow()

    private val _binSelection = MutableStateFlow<Set<String>>(emptySet())
    /** Ids of the entries chosen in the bin; empty means selection mode is off there. */
    val binSelection: StateFlow<Set<String>> = _binSelection.asStateFlow()

    private val _binMessage = MutableStateFlow<String?>(null)
    /**
     * What went wrong in the bin. Its own, not [errorMessage]: a restore reloads the folder
     * behind the bin, and the reload clears that one before anybody could read it.
     */
    val binMessage: StateFlow<String?> = _binMessage.asStateFlow()

    init {
        // What has waited longer than a month goes for good, once each time the hub opens.
        if (_hasPermission.value) {
            viewModelScope.launch {
                runCatching { recycleBinStore.purgeOlderThan(_volumes.value.map { it.root }) }
                    .onFailure { ZuneLog.w(TAG, "the recycle bin could not be purged", it) }
                refreshRecycleBin()
            }
        }
    }

    fun refreshRecycleBin() {
        if (!_hasPermission.value) return
        viewModelScope.launch {
            val entries = runCatching { recycleBinStore.list(_volumes.value.map { it.root }) }
                .onFailure { ZuneLog.w(TAG, "the recycle bin could not be read", it) }
                .getOrDefault(emptyList())
            _recycleBin.value = entries
            // Whatever left the bin meanwhile cannot stay chosen.
            val ids = entries.mapTo(HashSet()) { it.id }
            _binSelection.value = _binSelection.value.filterTo(HashSet()) { it in ids }
        }
    }

    fun openRecycleBin() {
        clearDeepSearch()
        clearSelection()
        _binSelection.value = emptySet()
        _binMessage.value = null
        _inRecycleBin.value = true
        refreshRecycleBin()
    }

    fun closeRecycleBin() {
        _inRecycleBin.value = false
        _binSelection.value = emptySet()
    }

    fun toggleBinSelection(entry: RecycleBinEntry) {
        val id = entry.id
        _binSelection.value = if (id in _binSelection.value) _binSelection.value - id else _binSelection.value + id
    }

    fun selectAllInBin() {
        _binSelection.value = _recycleBin.value.mapTo(HashSet()) { it.id }
    }

    fun clearBinSelection() {
        _binSelection.value = emptySet()
    }

    /** The entries chosen in the bin. */
    fun binSelectedEntries(): List<RecycleBinEntry> =
        _recycleBin.value.filter { it.id in _binSelection.value }

    /** Puts entries back where they were deleted from (a name taken meanwhile gets a number). */
    fun restore(entries: List<RecycleBinEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val result = recycleBinStore.restore(entries)
            scanPaths(result.restored.map { it.absolutePath })
            _binMessage.value = if (result.failed.isEmpty()) null
            else application().localizedString(R.string.files_bin_restore_partial, result.failed.size)
            clearBinSelection()
            refreshRecycleBin()
            reload()
        }
    }

    /** Deletes entries for good: the only way anything in the hub is deleted permanently. */
    fun deleteForever(entries: List<RecycleBinEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val failed = recycleBinStore.deleteForever(entries)
            _binMessage.value = if (failed.isEmpty()) null
            else application().localizedString(R.string.files_delete_partial, failed.size)
            clearBinSelection()
            refreshRecycleBin()
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            _busyMessage.value = application().localizedString(R.string.files_working)
            val failed = recycleBinStore.empty(_volumes.value.map { it.root })
            _busyMessage.value = null
            _binMessage.value = if (failed.isEmpty()) null
            else application().localizedString(R.string.files_delete_partial, failed.size)
            clearBinSelection()
            refreshRecycleBin()
        }
    }

    // ── Dragging things ─────────────────────────────────────────────────────

    /** What letting go of [paths] over [destination] would do; the ghost says it before the drop. */
    fun dropActionOnto(paths: List<String>, destination: File): DropAction {
        // A library the phone never made (no Documents folder yet, say) has nowhere to put things.
        if (!destination.isDirectory) return DropAction.REFUSED
        return dropActionFor(paths, destination.absolutePath, _volumes.value.map { it.root.absolutePath })
    }

    /**
     * Files dragged onto a folder. They go through the clipboard and [paste] like any other paste,
     * so a drop gets the same safety, names and progress as cut-and-paste — there is one engine
     * for putting files somewhere, not two. Whatever was on the clipboard before is replaced, as
     * it would be on Windows by a drag that ends in a paste.
     *
     * [copy] comes from [dropActionOnto] — a copy across volumes, a move within one — and a drop
     * that rule refuses (a folder into itself, things onto the folder they are in) does nothing.
     * While another paste is still going the drop is refused too: the running one would empty
     * the clipboard under it when it finished.
     */
    fun dropOnto(paths: List<String>, destination: File, copy: Boolean) {
        if (dropActionOnto(paths, destination) == DropAction.REFUSED) return
        if (pasteJob?.isActive == true) return
        _clipboard.value = Clipboard(paths, isMove = !copy)
        clearSelection()
        paste(destination)
    }

    // ── The cloud, through the phone's own picker ───────────────────────────

    // The hub no longer signs in to anything. Android's document picker shows whatever cloud apps
    // the phone has, and the hub keeps a short list of what was opened through it (CloudDocuments).
    // Saving the other way is the picker too: it makes an empty document wherever the user says,
    // and the hub writes the file into it.

    private val cloudStore = CloudDocuments(application)

    /** The documents opened from the cloud, the most recent first. */
    val cloudDocuments: StateFlow<List<CloudDocument>> = cloudStore.documents

    private val _cloudApps = MutableStateFlow<List<String>>(emptyList())
    /** The names of the apps on the phone that offer their documents to the picker. */
    val cloudApps: StateFlow<List<String>> = _cloudApps.asStateFlow()

    private val _cloudMessage = MutableStateFlow<String?>(null)
    val cloudMessage: StateFlow<String?> = _cloudMessage.asStateFlow()

    private val _transfer = MutableStateFlow<FileTransfer?>(null)
    /** The file being written into the cloud right now, or null. */
    val transfer: StateFlow<FileTransfer?> = _transfer.asStateFlow()

    private var transferJob: Job? = null

    /** A file waiting for the picker to say where in the cloud it should go. */
    data class CloudSaveRequest(val file: File, val mimeType: String)

    private val _cloudSaveRequest = MutableStateFlow<CloudSaveRequest?>(null)
    /** Set when the screen should open the picker's "save" side; taken with [takeCloudSaveRequest]. */
    val cloudSaveRequest: StateFlow<CloudSaveRequest?> = _cloudSaveRequest.asStateFlow()

    // Held between the picker opening and its answer. The request itself is cleared the moment the
    // screen takes it, so a screen built again meanwhile (the phone turned) does not open a second
    // picker over the first.
    private var pendingCloudSave: File? = null

    init {
        refreshCloudApps()
    }

    /** Looks again for cloud apps; one may have been installed while the hub was away. */
    fun refreshCloudApps() {
        viewModelScope.launch {
            _cloudApps.value = withContext(Dispatchers.IO) { cloudStore.cloudApps() }
        }
    }

    fun clearCloudMessage() {
        _cloudMessage.value = null
    }

    /** The picker could not be opened at all, which a phone without one does. */
    fun reportNoPicker() {
        _cloudMessage.value = application().localizedString(R.string.files_cloud_no_picker)
    }

    /** What the picker handed back: each goes on the list, and a single one opens at once. */
    fun onCloudPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _cloudMessage.value = null
        viewModelScope.launch {
            val remembered = withContext(Dispatchers.IO) { uris.mapNotNull { cloudStore.remember(it) } }
            when {
                remembered.isEmpty() -> _cloudMessage.value = application().localizedString(R.string.files_upload_failed)
                uris.size == 1 -> openCloudDocument(application(), remembered.single())
            }
        }
    }

    /**
     * Opens a document from the list. A film plays in the launcher's own player, as a film from
     * the phone does; anything else goes to whichever app takes its kind. A document that can no
     * longer be read, or that nothing on the phone can open, comes off the list, since a tap on it
     * would only ever fail again.
     */
    fun openCloudDocument(context: Context, document: CloudDocument) {
        viewModelScope.launch {
            val readable = withContext(Dispatchers.IO) { cloudStore.isReadable(document) }
            if (!readable) {
                cloudStore.forget(document)
                _cloudMessage.value = application().localizedString(R.string.files_cloud_gone, document.name)
                return@launch
            }
            val mimeType = document.mimeType ?: mimeTypeOfName(document.name)
            val opened = runCatching {
                if (com.serkantkn.zeta.ui.screens.video.VideoPlayer.handles(mimeType)) {
                    com.serkantkn.zeta.ui.screens.video.VideoPlayer.open(context, document.uri, document.name, mimeType)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(document.uri, mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }.onFailure { ZuneLog.w(TAG, "nothing opened ${document.name} ($mimeType)", it) }.isSuccess
            if (opened) {
                _cloudMessage.value = null
                cloudStore.markOpened(document)
            } else {
                cloudStore.forget(document)
                _cloudMessage.value = application().localizedString(R.string.cloud_error_no_app)
            }
        }
    }

    /** Takes a document off the list; the document itself stays where it is. */
    fun forgetCloudDocument(document: CloudDocument) {
        cloudStore.forget(document)
    }

    /**
     * Sends the chosen files to the cloud. One file goes through the picker's "save" side, which
     * asks where; several go to the share sheet, where Drive, OneDrive and the rest each offer
     * their own "upload", since the picker saves only one document at a time. Folders are left
     * out: neither takes a folder.
     */
    fun saveSelectionToCloud(context: Context) {
        val files = selectedItems().filter { !it.isDirectory }.map { it.file }
        clearSelection()
        saveFilesToCloud(context, files)
    }

    fun saveFilesToCloud(context: Context, files: List<File>) {
        when {
            files.isEmpty() -> return
            files.size == 1 -> {
                val file = files.single()
                _cloudSaveRequest.value = CloudSaveRequest(file, mimeTypeOf(file) ?: "application/octet-stream")
            }
            else -> shareToCloud(context, files)
        }
    }

    /** Hands the waiting save to the screen, which opens the picker with it. */
    fun takeCloudSaveRequest(): CloudSaveRequest? {
        val request = _cloudSaveRequest.value ?: return null
        _cloudSaveRequest.value = null
        pendingCloudSave = request.file
        return request
    }

    /** The picker's answer: where the waiting file goes, or null when the user turned back. */
    fun onCloudSaveTarget(target: Uri?) {
        val file = pendingCloudSave
        pendingCloudSave = null
        if (target != null && file != null) saveFileToCloud(target, file)
    }

    /**
     * Writes [file] into the document the picker made for it. It goes a piece at a time under the
     * same bar as before, so it can be stopped; a document stopped or failed halfway is deleted
     * again rather than left in the cloud looking whole.
     */
    fun saveFileToCloud(target: Uri, file: File) {
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            val resolver = application().contentResolver
            val total = file.length()
            _transfer.value = FileTransfer(file.name, isUpload = true, transferredBytes = 0L, totalBytes = total)
            var written = false
            try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(target) ?: throw java.io.IOException("no stream for $target")
                    output.use { out ->
                        file.inputStream().use { input ->
                            val buffer = ByteArray(COPY_CHUNK)
                            var moved = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                moved += read
                                _transfer.value = FileTransfer(file.name, true, moved, total)
                            }
                        }
                    }
                }
                written = true
                _cloudMessage.value = application().localizedString(R.string.files_cloud_saved_to, file.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ZuneLog.w(TAG, "could not save ${file.name} to the cloud", e)
                _cloudMessage.value = application().localizedString(R.string.files_cloud_save_failed, file.name)
            } finally {
                _transfer.value = null
                if (!written) {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        runCatching { android.provider.DocumentsContract.deleteDocument(resolver, target) }
                    }
                }
            }
        }
    }

    /** Stops whatever is being written, and takes the half-written document away again. */
    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _transfer.value = null
    }

    private fun shareToCloud(context: Context, files: List<File>) {
        runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
            // One kind for all of them when they share it, so the share sheet offers the apps
            // that take that kind; otherwise anything.
            val kinds = files.map { mimeTypeOf(it) ?: "*/*" }.distinct()
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = kinds.singleOrNull() ?: "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                clipData = android.content.ClipData.newRawUri(null, uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.files_cloud_save_chooser))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }.onFailure {
            ZuneLog.e(TAG, "the files could not be shared to the cloud", it)
            _cloudMessage.value = application().localizedString(R.string.cloud_error_no_app)
        }
    }

    private fun mimeTypeOfName(name: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

    private companion object {
        private const val MAX_BACK_STOPS = 64

        const val TAG = "FilesHubViewModel"

        /** Enough matches to scroll through; past this, the query is the thing to fix. */
        const val MAX_RESULTS = 500

        /** A guard against a tree that loops through a symbolic link. */
        const val MAX_FOLDERS = 4000

        /** How much of a file is copied between looks at the cancel flag. */
        const val COPY_CHUNK = 256 * 1024
    }
}
