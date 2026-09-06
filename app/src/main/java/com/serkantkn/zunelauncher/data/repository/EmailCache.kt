package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.util.Base64
import com.serkantkn.zunelauncher.data.model.EmailFolder
import com.serkantkn.zunelauncher.data.model.EmailFolderType
import com.serkantkn.zunelauncher.data.model.EmailMessage
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Offline copy of what the Email hub has seen, kept as org.json files under
 * `filesDir/email/<accountId>/<folder>/`:
 *
 * - `folders.json`        folder list of the account
 * - `<folder>/index.json` message headers, newest first, plus `lastSeenUid`
 * - `<folder>/<uid>.json` full message (body + attachment list) once opened
 *
 * Everything is mirrored into StateFlows so screens and the Start tile observe changes.
 * Reads and writes are synchronous file operations; callers run them on Dispatchers.IO.
 */
class EmailCache(context: Context) {

    private val root = File(context.filesDir, "email")
    val attachmentsDir: File = File(context.cacheDir, "email_attachments").apply { mkdirs() }

    private val _folders = MutableStateFlow<Map<String, List<EmailFolder>>>(emptyMap())
    /** accountId -> folders */
    val folders: StateFlow<Map<String, List<EmailFolder>>> = _folders.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<EmailMessage>>>(emptyMap())
    /** "<accountId>|<folder>" -> headers, newest first */
    val messages: StateFlow<Map<String, List<EmailMessage>>> = _messages.asStateFlow()

    private val _inboxUnread = MutableStateFlow(0)
    /** Unread count across every account's inbox (Start tile badge). */
    val inboxUnread: StateFlow<Int> = _inboxUnread.asStateFlow()

    private val lastSeenUids = mutableMapOf<String, Long>()

    fun listKey(accountId: String, folder: String) = "$accountId|$folder"

    // ── Folders ───────────────────────────────────────────────────────────

    fun loadFolders(accountId: String): List<EmailFolder> {
        _folders.value[accountId]?.let { return it }
        val file = File(accountDir(accountId), "folders.json")
        val list = readArray(file).mapNotNull { runCatching { EmailFolder.fromJson(it) }.getOrNull() }
        _folders.value = _folders.value + (accountId to list)
        return list
    }

    fun saveFolders(accountId: String, folders: List<EmailFolder>) {
        writeArray(File(accountDir(accountId), "folders.json"), folders.map { it.toJson() })
        _folders.value = _folders.value + (accountId to folders)
    }

    fun folder(accountId: String, type: EmailFolderType): EmailFolder? =
        loadFolders(accountId).firstOrNull { it.type == type }

    // ── Message headers ───────────────────────────────────────────────────

    fun loadMessages(accountId: String, folder: String): List<EmailMessage> {
        val key = listKey(accountId, folder)
        _messages.value[key]?.let { return it }
        val file = File(folderDir(accountId, folder), "index.json")
        val list = mutableListOf<EmailMessage>()
        if (file.exists()) {
            try {
                val obj = JSONObject(file.readText())
                lastSeenUids[key] = obj.optLong("lastSeenUid", 0L)
                val arr = obj.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { runCatching { list += EmailMessage.fromJson(it) } }
                }
            } catch (e: Exception) {
                ZuneLog.w(TAG, "loadMessages: unreadable index for $key", e)
            }
        }
        publish(key, list)
        return list
    }

    fun lastSeenUid(accountId: String, folder: String): Long {
        val key = listKey(accountId, folder)
        loadMessages(accountId, folder)
        return lastSeenUids[key] ?: 0L
    }

    /** Replaces the header list of a folder (newest first) and remembers the highest UID seen. */
    fun saveMessages(accountId: String, folder: String, messages: List<EmailMessage>, lastSeenUid: Long? = null) {
        val key = listKey(accountId, folder)
        val sorted = messages.sortedByDescending { it.uid }
        val seen = maxOf(lastSeenUid ?: 0L, lastSeenUids[key] ?: 0L, sorted.firstOrNull()?.uid ?: 0L)
        lastSeenUids[key] = seen
        val obj = JSONObject().apply {
            put("lastSeenUid", seen)
            put("messages", JSONArray().apply { sorted.forEach { put(it.headerJson()) } })
        }
        writeText(File(folderDir(accountId, folder), "index.json"), obj.toString())
        publish(key, sorted)
    }

    /** Applies [transform] to every cached header with a matching key and persists the folder. */
    fun updateMessages(accountId: String, folder: String, uids: Set<Long>, transform: (EmailMessage) -> EmailMessage?) {
        val current = loadMessages(accountId, folder)
        val updated = current.mapNotNull { if (it.uid in uids) transform(it) else it }
        saveMessages(accountId, folder, updated)
    }

    fun removeMessages(accountId: String, folder: String, uids: Set<Long>) {
        updateMessages(accountId, folder, uids) { null }
        uids.forEach { File(folderDir(accountId, folder), "$it.json").delete() }
    }

    // ── Full messages ─────────────────────────────────────────────────────

    fun loadFullMessage(accountId: String, folder: String, uid: Long): EmailMessage? {
        val file = File(folderDir(accountId, folder), "$uid.json")
        if (!file.exists()) return null
        return try {
            EmailMessage.fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            ZuneLog.w(TAG, "loadFullMessage: unreadable $file", e)
            null
        }
    }

    fun saveFullMessage(message: EmailMessage) {
        writeText(File(folderDir(message.accountId, message.folder), "${message.uid}.json"), message.toJson().toString())
        // Keep the header row in sync (snippet, flags, attachment info).
        val key = listKey(message.accountId, message.folder)
        val current = loadMessages(message.accountId, message.folder)
        if (current.any { it.uid == message.uid }) {
            publish(key, current.map { if (it.uid == message.uid) message.copy(bodyText = null, bodyHtml = null) else it })
            writeText(File(folderDir(message.accountId, message.folder), "index.json"), JSONObject().apply {
                put("lastSeenUid", lastSeenUids[key] ?: 0L)
                put("messages", JSONArray().apply { _messages.value[key].orEmpty().forEach { put(it.headerJson()) } })
            }.toString())
        }
    }

    // ── Account removal ───────────────────────────────────────────────────

    fun clearAccount(accountId: String) {
        accountDir(accountId).deleteRecursively()
        _folders.value = _folders.value - accountId
        _messages.value = _messages.value.filterKeys { !it.startsWith("$accountId|") }
        lastSeenUids.keys.removeAll { it.startsWith("$accountId|") }
        recomputeUnread()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun publish(key: String, list: List<EmailMessage>) {
        _messages.value = _messages.value + (key to list)
        recomputeUnread()
    }

    private fun recomputeUnread() {
        _inboxUnread.value = _messages.value.entries
            .filter { it.key.endsWith("|" + EmailFolder.INBOX_NAME) }
            .sumOf { entry -> entry.value.count { !it.isRead } }
    }

    private fun EmailMessage.headerJson(): JSONObject = copy(bodyText = null, bodyHtml = null).toJson()

    private fun accountDir(accountId: String) = File(root, accountId).apply { mkdirs() }

    private fun folderDir(accountId: String, folder: String): File =
        File(accountDir(accountId), Base64.encodeToString(folder.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)).apply { mkdirs() }

    private fun readArray(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "readArray: unreadable $file", e)
            emptyList()
        }
    }

    private fun writeArray(file: File, items: List<JSONObject>) {
        writeText(file, JSONArray().apply { items.forEach { put(it) } }.toString())
    }

    private fun writeText(file: File, text: String) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) file.writeText(text)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "writeText failed for $file", e)
        }
    }

    private companion object {
        const val TAG = "EmailCache"
    }
}
