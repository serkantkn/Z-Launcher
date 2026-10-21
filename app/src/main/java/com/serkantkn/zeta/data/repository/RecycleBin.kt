package com.serkantkn.zeta.data.repository

import com.serkantkn.zeta.util.uniqueFileName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * One thing in the recycle bin: where it came from, when it was put there, and what it was.
 *
 * [binPath] is the bin it sits in — every volume has its own, so a file deleted from the SD card
 * waits on the SD card rather than being copied across to the phone's memory.
 */
data class RecycleBinEntry(
    val id: String,
    val originalPath: String,
    val name: String,
    val deletedAt: Long,
    val isDirectory: Boolean,
    val size: Long,
    val binPath: String
) {
    /** The thing itself, under its id inside the bin. */
    val payload: File get() = File(File(binPath, RecycleBin.FILES_DIR), id)

    /** The folder it was deleted from. */
    val originalParent: String get() = originalPath.substringBeforeLast('/', "/").ifEmpty { "/" }

    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}

/**
 * The Files hub's recycle bin.
 *
 * A delete used to be final: one tap past "are you sure" and the holiday photos were gone. Now a
 * delete is a move. Each volume keeps a hidden folder at its top, `.ZuneRecycleBin`, which holds
 * what was deleted under ids that cannot clash, and an index that remembers where each thing came
 * from. Because the bin is on the same volume as the file, the move is a rename — instant however
 * big the folder — and it costs no space that the file was not already taking.
 *
 * Nothing here needs Android, so all of it is tested on a temporary folder. The work is done on
 * [io], and one lock covers every read-change-write of an index: two deletes landing together
 * would otherwise each read the old index and the second write would forget the first.
 */
class RecycleBin(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Mutex()

    /** What a trip to the bin did: what went in, and what would not go. */
    data class TrashResult(val trashed: List<RecycleBinEntry>, val failed: List<File>)

    /** What came back out: where each restored thing now is, and what could not be put back. */
    data class RestoreResult(val restored: List<File>, val failed: List<RecycleBinEntry>)

    /**
     * Moves [files] into the bin of the volume each one is on. A file on no known volume, the bin
     * itself and anything inside it are refused rather than guessed at.
     */
    suspend fun trash(files: List<File>, volumeRoots: List<File>): TrashResult = withContext(io) {
        lock.withLock {
            val added = mutableMapOf<File, MutableList<RecycleBinEntry>>()
            val failed = mutableListOf<File>()
            for (file in files) {
                val root = volumeRootOf(file, volumeRoots)
                if (root == null || !file.exists() || isBinOrInside(file, root) ||
                    file.absolutePath == root.absolutePath
                ) {
                    failed += file
                    continue
                }
                val bin = prepareBin(root)
                if (bin == null) {
                    failed += file
                    continue
                }
                val id = UUID.randomUUID().toString()
                val isDirectory = file.isDirectory
                // Measured before the move: afterwards the walk would be through the bin.
                val size = sizeOf(file)
                if (!move(file, File(File(bin, FILES_DIR), id))) {
                    failed += file
                    continue
                }
                added.getOrPut(bin) { mutableListOf() } += RecycleBinEntry(
                    id = id,
                    originalPath = file.absolutePath,
                    name = file.name,
                    deletedAt = clock(),
                    isDirectory = isDirectory,
                    size = size,
                    binPath = bin.absolutePath
                )
            }
            added.forEach { (bin, entries) -> writeIndex(bin, readIndex(bin) + entries) }
            TrashResult(added.values.flatten(), failed)
        }
    }

    /** Everything in the bins of [volumeRoots], the most recently deleted first. */
    suspend fun list(volumeRoots: List<File>): List<RecycleBinEntry> = withContext(io) {
        lock.withLock { listUnlocked(volumeRoots) }
    }

    /**
     * Puts entries back where they were. A folder that has gone since is made again; a name that
     * has been taken meanwhile gets a number ("rapor (2).pdf") rather than replacing what is there.
     */
    suspend fun restore(entries: List<RecycleBinEntry>): RestoreResult = withContext(io) {
        lock.withLock {
            val restored = mutableListOf<File>()
            val failed = mutableListOf<RecycleBinEntry>()
            val gone = mutableSetOf<String>()
            for (entry in entries) {
                val payload = entry.payload
                if (!payload.exists()) {
                    // Nothing left to restore; the record is only in the way.
                    gone += entry.id
                    failed += entry
                    continue
                }
                val original = File(entry.originalPath)
                val parent = original.parentFile
                if (parent == null || (!parent.isDirectory && !parent.mkdirs())) {
                    failed += entry
                    continue
                }
                val name = uniqueFileName(original.name, parent.list()?.toSet() ?: emptySet())
                val target = File(parent, name)
                if (!target.exists() && move(payload, target)) {
                    gone += entry.id
                    restored += target
                } else {
                    failed += entry
                }
            }
            forget(entries.filter { it.id in gone })
            RestoreResult(restored, failed)
        }
    }

    /** Deletes entries for good; returns those that would not go. */
    suspend fun deleteForever(entries: List<RecycleBinEntry>): List<RecycleBinEntry> = withContext(io) {
        lock.withLock { deleteUnlocked(entries) }
    }

    /** Empties every bin; returns what would not go. */
    suspend fun empty(volumeRoots: List<File>): List<RecycleBinEntry> = withContext(io) {
        lock.withLock { deleteUnlocked(listUnlocked(volumeRoots)) }
    }

    /**
     * Deletes for good whatever has been in the bin longer than [days] — Windows' own bin keeps
     * things until the disk fills, but a phone has less disk and nobody to tell it so. Returns how
     * many went.
     */
    suspend fun purgeOlderThan(volumeRoots: List<File>, days: Int = DEFAULT_KEEP_DAYS): Int = withContext(io) {
        lock.withLock {
            val cutoff = clock() - days * DAY_MILLIS
            val old = listUnlocked(volumeRoots).filter { it.deletedAt < cutoff }
            old.size - deleteUnlocked(old).size
        }
    }

    // ── Inside the lock ─────────────────────────────────────────────────────

    private fun listUnlocked(volumeRoots: List<File>): List<RecycleBinEntry> =
        volumeRoots
            .distinctBy { it.absolutePath }
            .mapNotNull { root -> File(root, FOLDER_NAME).takeIf { it.isDirectory } }
            .flatMap { bin -> reconcile(bin) }
            .sortedByDescending { it.deletedAt }

    /**
     * The index of [bin] squared with what is really in it. A record whose file has gone is
     * dropped; a file with no record — the app stopped between the move and the index write —
     * is taken in under its id, at the top of its volume, rather than left there unseen and
     * taking space for ever.
     */
    private fun reconcile(bin: File): List<RecycleBinEntry> {
        val recorded = readIndex(bin)
        val present = File(bin, FILES_DIR).listFiles().orEmpty().associateBy { it.name }
        val kept = recorded.filter { it.id in present }
        val known = kept.mapTo(HashSet()) { it.id }
        val volumeRoot = bin.parentFile ?: return kept
        val adopted = present.values.filter { it.name !in known }.map { payload ->
            RecycleBinEntry(
                id = payload.name,
                originalPath = File(volumeRoot, payload.name).absolutePath,
                name = payload.name,
                deletedAt = payload.lastModified(),
                isDirectory = payload.isDirectory,
                size = sizeOf(payload),
                binPath = bin.absolutePath
            )
        }
        val all = kept + adopted
        if (all.size != recorded.size || adopted.isNotEmpty()) writeIndex(bin, all)
        return all
    }

    private fun deleteUnlocked(entries: List<RecycleBinEntry>): List<RecycleBinEntry> {
        val failed = mutableListOf<RecycleBinEntry>()
        val gone = mutableListOf<RecycleBinEntry>()
        entries.forEach { entry ->
            val payload = entry.payload
            runCatching { payload.deleteRecursively() }
            if (payload.exists()) failed += entry else gone += entry
        }
        forget(gone)
        return failed
    }

    /** Drops records from their bins' indexes. */
    private fun forget(entries: List<RecycleBinEntry>) {
        entries.groupBy { it.binPath }.forEach { (binPath, leaving) ->
            val bin = File(binPath)
            val ids = leaving.mapTo(HashSet()) { it.id }
            writeIndex(bin, readIndex(bin).filter { it.id !in ids })
        }
    }

    // ── The bin on disk ─────────────────────────────────────────────────────

    /** The bin of the volume at [root], made if need be, with the media scanner told to keep out. */
    private fun prepareBin(root: File): File? {
        val bin = File(root, FOLDER_NAME)
        val files = File(bin, FILES_DIR)
        if (!files.isDirectory && !files.mkdirs()) return null
        // Without this, every photo in the bin would still be in the gallery.
        val noMedia = File(bin, NO_MEDIA)
        if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
        return bin
    }

    private fun readIndex(bin: File): List<RecycleBinEntry> {
        val index = File(bin, INDEX)
        if (!index.isFile) return emptyList()
        return runCatching {
            val array = JSONObject(index.readText()).optJSONArray("entries") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RecycleBinEntry(
                    id = id,
                    originalPath = item.optString("originalPath"),
                    name = item.optString("name"),
                    deletedAt = item.optLong("deletedAt"),
                    isDirectory = item.optBoolean("isDirectory"),
                    size = item.optLong("size"),
                    binPath = bin.absolutePath
                )
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Writes the index whole, to a side file first and then over the old one, so a write cut
     * short leaves the last good index rather than half of a new one.
     */
    private fun writeIndex(bin: File, entries: List<RecycleBinEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("originalPath", entry.originalPath)
                    .put("name", entry.name)
                    .put("deletedAt", entry.deletedAt)
                    .put("isDirectory", entry.isDirectory)
                    .put("size", entry.size)
            )
        }
        val json = JSONObject().put("version", INDEX_VERSION).put("entries", array).toString()
        val index = File(bin, INDEX)
        val temp = File(bin, "$INDEX.tmp")
        runCatching {
            temp.writeText(json)
            if (!temp.renameTo(index)) {
                index.delete()
                if (!temp.renameTo(index)) index.writeText(json)
            }
        }
    }

    companion object {
        /** The bin's folder at the top of each volume; hidden, and kept out of every listing. */
        const val FOLDER_NAME = ".ZuneRecycleBin"
        const val DEFAULT_KEEP_DAYS = 30

        internal const val FILES_DIR = "files"
        private const val INDEX = "index.json"
        private const val NO_MEDIA = ".nomedia"
        private const val INDEX_VERSION = 1
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** A guard against a tree that loops through a symbolic link. */
        private const val MAX_WALK = 20_000

        /** The volume [file] is on: the longest root its path starts with. */
        fun volumeRootOf(file: File, volumeRoots: List<File>): File? {
            val path = file.absolutePath
            return volumeRoots
                .filter { path == it.absolutePath || path.startsWith(it.absolutePath.trimEnd('/') + "/") }
                .maxByOrNull { it.absolutePath.length }
        }

        /** Whether [file] is a volume's bin or something inside one. */
        fun isBinOrInside(file: File, volumeRoot: File): Boolean {
            val bin = File(volumeRoot, FOLDER_NAME).absolutePath
            val path = file.absolutePath
            return path == bin || path.startsWith("$bin/")
        }

        /**
         * Moves [source] to [target]: a rename where the storage allows it, otherwise a copy and
         * then a delete. A file whose original will not go is taken back out of the copy, so it
         * is not in two places. A folder is different: its delete can stop half way, and then the
         * copy is the only whole one left — that one stays, and the move counts as done.
         */
        private fun move(source: File, target: File): Boolean {
            if (target.exists()) return false
            if (runCatching { source.renameTo(target) }.getOrDefault(false)) return true
            val wasDirectory = source.isDirectory
            val copied = runCatching { source.copyRecursively(target, overwrite = false) }.getOrDefault(false)
            if (!copied) {
                runCatching { target.deleteRecursively() }
                return false
            }
            runCatching { source.deleteRecursively() }
            if (source.exists() && !wasDirectory) {
                runCatching { target.delete() }
                return false
            }
            return true
        }

        private fun sizeOf(file: File): Long {
            if (!file.isDirectory) return runCatching { file.length() }.getOrDefault(0L)
            var total = 0L
            var visited = 0
            val queue = ArrayDeque(listOf(file))
            while (queue.isNotEmpty() && visited < MAX_WALK) {
                val directory = queue.removeFirst()
                visited++
                runCatching { directory.listFiles() }.getOrNull().orEmpty().forEach { child ->
                    if (child.isDirectory) queue.addLast(child) else total += child.length()
                }
            }
            return total
        }
    }
}
