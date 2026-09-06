package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.data.model.EmailAddress
import com.serkantkn.zunelauncher.data.model.EmailAttachment
import com.serkantkn.zunelauncher.data.model.EmailDraft
import com.serkantkn.zunelauncher.data.model.EmailDraftKind
import com.serkantkn.zunelauncher.data.model.EmailFolder
import com.serkantkn.zunelauncher.data.model.EmailFolderType
import com.serkantkn.zunelauncher.data.model.EmailMessage
import com.serkantkn.zunelauncher.data.model.EmailSecurity
import com.serkantkn.zunelauncher.data.service.GoogleMailAuth
import com.serkantkn.zunelauncher.util.SecretStore
import com.serkantkn.zunelauncher.util.ZuneLog
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.UnknownHostException
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.mail.search.BodyTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.SubjectTerm
import javax.mail.util.ByteArrayDataSource

/** A mail operation failed; [messageRes] is the user-facing reason, [detail] the server text. */
class EmailException(@StringRes val messageRes: Int, val detail: String? = null, cause: Throwable? = null) :
    Exception(detail ?: cause?.message, cause)

/** Result of one folder sync: the merged header list plus the UIDs that are new since last time. */
data class EmailSyncResult(val messages: List<EmailMessage>, val newMessages: List<EmailMessage>)

/**
 * IMAP/SMTP access for the Email hub (JavaMail). One [IMAPStore] is kept per account and
 * reconnected on demand; every operation runs on Dispatchers.IO under a per-account mutex.
 * Results are written to [EmailCache] so screens observe the cache, never this class.
 */
class EmailRepository(
    private val context: Context,
    private val secretStore: SecretStore,
    val cache: EmailCache
) {
    private val stores = mutableMapOf<String, IMAPStore>()
    private val locks = mutableMapOf<String, Mutex>()

    private fun lockFor(accountId: String) = synchronized(locks) { locks.getOrPut(accountId) { Mutex() } }

    // ── Connection ────────────────────────────────────────────────────────

    /** Logs in to IMAP and SMTP without saving anything. [password] is ignored for Google accounts. */
    suspend fun testConnection(account: EmailAccount, password: String?) = withContext(Dispatchers.IO) {
        wrap {
            val secret = if (account.isGoogleOAuth) credential(account) else password ?: throw EmailException(R.string.email_error_no_password)
            val session = Session.getInstance(imapProps(account))
            val store = session.getStore("imap")
            store.connect(account.imapHost, account.imapPort, account.username, secret)
            store.close()
            val smtpSession = Session.getInstance(smtpProps(account))
            val transport = smtpSession.getTransport("smtp")
            transport.connect(account.smtpHost, account.smtpPort, account.username, secret)
            transport.close()
        }
    }

    /** The "password" for a connection: the stored password, or a fresh Google access token. */
    private fun credential(account: EmailAccount): String =
        if (account.isGoogleOAuth) GoogleMailAuth.accessToken(context, account.email)
        else secretStore.get(account.id) ?: throw EmailException(R.string.email_error_no_password)

    fun savePassword(accountId: String, password: String) = secretStore.put(accountId, password)

    fun forget(accountId: String) {
        secretStore.remove(accountId)
        synchronized(stores) { stores.remove(accountId)?.let { runCatching { it.close() } } }
        cache.clearAccount(accountId)
    }

    private fun store(account: EmailAccount): IMAPStore {
        synchronized(stores) {
            stores[account.id]?.let { if (it.isConnected) return it }
            val password = credential(account)
            val session = Session.getInstance(imapProps(account))
            val store = session.getStore("imap") as IMAPStore
            store.connect(account.imapHost, account.imapPort, account.username, password)
            stores[account.id] = store
            return store
        }
    }

    private fun imapProps(account: EmailAccount) = Properties().apply {
        put("mail.imap.host", account.imapHost)
        put("mail.imap.port", account.imapPort.toString())
        put("mail.imap.connectiontimeout", "20000")
        put("mail.imap.timeout", "40000")
        put("mail.imap.writetimeout", "40000")
        put("mail.imap.partialfetch", "false")
        put("mail.mime.decodefilename", "true")
        put("mail.mime.decodetext.strict", "false")
        if (account.isGoogleOAuth) put("mail.imap.auth.mechanisms", "XOAUTH2")
        when (account.imapSecurity) {
            EmailSecurity.SSL -> put("mail.imap.ssl.enable", "true")
            EmailSecurity.STARTTLS -> { put("mail.imap.starttls.enable", "true"); put("mail.imap.starttls.required", "true") }
            EmailSecurity.NONE -> {}
        }
    }

    private fun smtpProps(account: EmailAccount) = Properties().apply {
        put("mail.smtp.host", account.smtpHost)
        put("mail.smtp.port", account.smtpPort.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.connectiontimeout", "20000")
        put("mail.smtp.timeout", "40000")
        put("mail.smtp.writetimeout", "60000")
        if (account.isGoogleOAuth) put("mail.smtp.auth.mechanisms", "XOAUTH2")
        when (account.smtpSecurity) {
            EmailSecurity.SSL -> put("mail.smtp.ssl.enable", "true")
            EmailSecurity.STARTTLS -> { put("mail.smtp.starttls.enable", "true"); put("mail.smtp.starttls.required", "true") }
            EmailSecurity.NONE -> {}
        }
    }

    // ── Folders ───────────────────────────────────────────────────────────

    suspend fun refreshFolders(account: EmailAccount): List<EmailFolder> = io(account) {
        val store = store(account)
        val result = mutableListOf<EmailFolder>()
        val all = store.defaultFolder.list("*")
        for (f in all) {
            val imap = f as? IMAPFolder ?: continue
            val attrs = runCatching { imap.attributes.toList() }.getOrDefault(emptyList())
            if (attrs.any { it.equals("\\Noselect", true) || it.equals("\\NonExistent", true) }) continue
            val type = classify(imap.fullName, imap.name, attrs)
            val counts = runCatching { imap.open(Folder.READ_ONLY); Pair(imap.unreadMessageCount, imap.messageCount) }
                .getOrDefault(Pair(0, 0))
            runCatching { if (imap.isOpen) imap.close(false) }
            result += EmailFolder(account.id, imap.fullName, imap.name, type, counts.first, counts.second)
        }
        if (result.none { it.isInbox }) {
            result += EmailFolder(account.id, EmailFolder.INBOX_NAME, EmailFolder.INBOX_NAME, EmailFolderType.INBOX)
        }
        val sorted = result.sortedWith(compareBy({ it.type.sortOrder }, { it.fullName.lowercase() }))
        cache.saveFolders(account.id, sorted)
        sorted
    }

    private fun classify(fullName: String, name: String, attrs: List<String>): EmailFolderType {
        val a = attrs.map { it.lowercase() }
        val n = name.lowercase()
        val full = fullName.lowercase()
        return when {
            fullName.equals(EmailFolder.INBOX_NAME, ignoreCase = true) -> EmailFolderType.INBOX
            "\\sent" in a || n in setOf("sent", "sent items", "sent mail", "sent messages", "gönderilmiş öğeler", "gönderilenler", "giden") || full.endsWith("/sent mail") -> EmailFolderType.SENT
            "\\drafts" in a || n in setOf("drafts", "draft", "taslaklar") -> EmailFolderType.DRAFTS
            "\\trash" in a || n in setOf("trash", "deleted", "deleted items", "deleted messages", "çöp kutusu", "silinmiş öğeler", "bin") -> EmailFolderType.TRASH
            "\\junk" in a || n in setOf("junk", "spam", "junk e-mail", "önemsiz", "istenmeyen") -> EmailFolderType.SPAM
            "\\archive" in a || "\\all" in a || n in setOf("archive", "arşiv", "all mail") -> EmailFolderType.ARCHIVE
            else -> EmailFolderType.OTHER
        }
    }

    // ── Headers ───────────────────────────────────────────────────────────

    /**
     * Fetches the newest [pageSize] headers of [folderName], refreshes the flags of everything
     * already cached below that window, removes headers that disappeared from the server and
     * returns the merged list. [EmailSyncResult.newMessages] holds messages whose UID is above
     * the last UID seen by a previous sync (empty on the very first sync).
     */
    suspend fun syncFolder(account: EmailAccount, folderName: String, pageSize: Int = 40, prefetchBodies: Int = 8): EmailSyncResult = io(account) {
        val store = store(account)
        val folder = store.getFolder(folderName) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val cached = cache.loadMessages(account.id, folderName)
            val previousLastSeen = cache.lastSeenUid(account.id, folderName)
            val count = folder.messageCount
            val start = maxOf(1, count - pageSize + 1)
            val fresh = if (count > 0) folder.getMessages(start, count).also { folder.fetch(it, headerProfile()) }
                .mapNotNull { toHeader(account.id, folderName, folder, it) } else emptyList()
            val windowLowUid = fresh.minOfOrNull { it.uid } ?: Long.MAX_VALUE

            // Older cached rows: refresh flags, drop deleted ones.
            val olderCached = cached.filter { it.uid < windowLowUid }
            val olderRefreshed = if (olderCached.isNotEmpty() && olderCached.size <= 400) {
                val serverMsgs = folder.getMessagesByUID(olderCached.map { it.uid }.toLongArray()).filterNotNull()
                folder.fetch(serverMsgs.toTypedArray(), FetchProfile().apply { add(FetchProfile.Item.FLAGS); add(UIDFolder.FetchProfileItem.UID) })
                val flagsByUid = serverMsgs.associate { folder.getUID(it) to it.flags }
                olderCached.mapNotNull { old ->
                    val flags = flagsByUid[old.uid] ?: return@mapNotNull null
                    old.copy(isRead = flags.contains(Flags.Flag.SEEN), isFlagged = flags.contains(Flags.Flag.FLAGGED), isAnswered = flags.contains(Flags.Flag.ANSWERED))
                }
            } else olderCached

            val cachedByUid = cached.associateBy { it.uid }
            val merged = (fresh.map { h -> cachedByUid[h.uid]?.let { c -> h.copy(snippet = c.snippet.ifBlank { h.snippet }) } ?: h } + olderRefreshed)
                .sortedByDescending { it.uid }
            val newOnes = if (previousLastSeen > 0L) fresh.filter { it.uid > previousLastSeen && !it.isRead } else emptyList()
            cache.saveMessages(account.id, folderName, merged)

            // Preview lines for the newest rows that have no cached body yet.
            var fetched = 0
            for (header in merged) {
                if (fetched >= prefetchBodies) break
                if (header.snippet.isNotBlank()) continue
                val full = cache.loadFullMessage(account.id, folderName, header.uid)
                    ?: runCatching { fetchBodyIn(folder, account.id, folderName, header) }.getOrNull()
                    ?: continue
                fetched++
                cache.saveFullMessage(full)
            }
            EmailSyncResult(cache.loadMessages(account.id, folderName), newOnes.map { n -> cache.loadMessages(account.id, folderName).firstOrNull { it.uid == n.uid } ?: n })
        } finally {
            runCatching { folder.close(false) }
        }
    }

    /** Appends the [pageSize] headers older than [beforeUid] to the cache. Returns how many were added. */
    suspend fun loadOlder(account: EmailAccount, folderName: String, beforeUid: Long, pageSize: Int = 40): Int = io(account) {
        val store = store(account)
        val folder = store.getFolder(folderName) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val anchor = folder.getMessageByUID(beforeUid) ?: return@io 0
            val end = anchor.messageNumber - 1
            if (end < 1) return@io 0
            val start = maxOf(1, end - pageSize + 1)
            val msgs = folder.getMessages(start, end)
            folder.fetch(msgs, headerProfile())
            val older = msgs.mapNotNull { toHeader(account.id, folderName, folder, it) }
            val cached = cache.loadMessages(account.id, folderName)
            val known = cached.map { it.uid }.toSet()
            val added = older.filter { it.uid !in known }
            cache.saveMessages(account.id, folderName, cached + added)
            added.size
        } finally {
            runCatching { folder.close(false) }
        }
    }

    private fun headerProfile() = FetchProfile().apply {
        add(FetchProfile.Item.ENVELOPE)
        add(FetchProfile.Item.FLAGS)
        add(FetchProfile.Item.SIZE)
        add(FetchProfile.Item.CONTENT_INFO)
        add(UIDFolder.FetchProfileItem.UID)
    }

    private fun toHeader(accountId: String, folderName: String, folder: IMAPFolder, msg: Message): EmailMessage? {
        return try {
            val mime = msg as MimeMessage
            val uid = folder.getUID(msg)
            val flags = msg.flags
            val from = (mime.from?.firstOrNull() as? InternetAddress)?.toModel() ?: EmailAddress("", "")
            val attachments = runCatching { hasAttachments(mime) }.getOrDefault(false)
            EmailMessage(
                accountId = accountId,
                folder = folderName,
                uid = uid,
                messageId = runCatching { mime.messageID }.getOrNull(),
                subject = decode(mime.subject).ifBlank { "" },
                from = from,
                to = mime.getRecipients(Message.RecipientType.TO).toModels(),
                cc = mime.getRecipients(Message.RecipientType.CC).toModels(),
                replyTo = (runCatching { mime.replyTo }.getOrNull()?.firstOrNull() as? InternetAddress)?.toModel(),
                date = (mime.sentDate ?: mime.receivedDate)?.time ?: 0L,
                isRead = flags.contains(Flags.Flag.SEEN),
                isFlagged = flags.contains(Flags.Flag.FLAGGED),
                isAnswered = flags.contains(Flags.Flag.ANSWERED),
                hasAttachments = attachments,
                size = mime.size.toLong(),
                inReplyTo = runCatching { mime.getHeader("In-Reply-To")?.firstOrNull() }.getOrNull(),
                references = runCatching { mime.getHeader("References")?.firstOrNull() }.getOrNull()
            )
        } catch (e: Exception) {
            ZuneLog.w(TAG, "toHeader failed", e)
            null
        }
    }

    /** Attachment detection from the BODYSTRUCTURE only (no body download). */
    private fun hasAttachments(part: Part): Boolean {
        if (part.isMimeType("multipart/*")) {
            val mp = part.content as? Multipart ?: return false
            for (i in 0 until mp.count) if (hasAttachments(mp.getBodyPart(i))) return true
            return false
        }
        val disposition = runCatching { part.disposition }.getOrNull()
        val fileName = runCatching { part.fileName }.getOrNull()
        return disposition.equals(Part.ATTACHMENT, true) || (!fileName.isNullOrBlank() && !part.isMimeType("text/*"))
    }

    // ── Bodies and attachments ────────────────────────────────────────────

    /** Returns the full message (body, attachment list), from cache when available. */
    suspend fun fetchBody(account: EmailAccount, header: EmailMessage): EmailMessage {
        cache.loadFullMessage(account.id, header.folder, header.uid)?.let { cached ->
            if (cached.hasBody) return cached.copy(isRead = header.isRead, isFlagged = header.isFlagged, isAnswered = header.isAnswered)
        }
        return io(account) {
            val folder = store(account).getFolder(header.folder) as IMAPFolder
            folder.open(Folder.READ_ONLY)
            try {
                val full = fetchBodyIn(folder, account.id, header.folder, header)
                cache.saveFullMessage(full)
                full
            } finally {
                runCatching { folder.close(false) }
            }
        }
    }

    private fun fetchBodyIn(folder: IMAPFolder, accountId: String, folderName: String, header: EmailMessage): EmailMessage {
        val msg = folder.getMessageByUID(header.uid) as? MimeMessage
            ?: throw EmailException(R.string.email_error_message_gone)
        val parts = BodyParts()
        collectParts(msg, "", parts)
        val text = parts.text?.trim()
        val html = parts.html?.trim()
        val snippet = (text ?: html?.let(::htmlToText) ?: "").replace(Regex("\\s+"), " ").trim().take(140)
        return header.copy(
            bodyText = text,
            bodyHtml = html,
            attachments = parts.attachments,
            hasAttachments = parts.attachments.any { it.contentId == null } || header.hasAttachments,
            snippet = snippet
        )
    }

    private class BodyParts {
        var text: String? = null
        var html: String? = null
        val attachments = mutableListOf<EmailAttachment>()
    }

    private fun collectParts(part: Part, path: String, out: BodyParts) {
        val disposition = runCatching { part.disposition }.getOrNull()
        val fileName = runCatching { part.fileName }.getOrNull()?.let { decode(it) }
        when {
            part.isMimeType("multipart/*") -> {
                val mp = part.content as? Multipart ?: return
                for (i in 0 until mp.count) {
                    val childPath = if (path.isEmpty()) "${i + 1}" else "$path.${i + 1}"
                    collectParts(mp.getBodyPart(i), childPath, out)
                }
            }
            part.isMimeType("message/rfc822") -> {
                val nested = part.content as? Part
                if (nested != null) collectParts(nested, path, out)
            }
            part.isMimeType("text/plain") && fileName.isNullOrBlank() && !disposition.equals(Part.ATTACHMENT, true) -> {
                if (out.text == null) out.text = part.content.toString() else out.text += "\n" + part.content.toString()
            }
            part.isMimeType("text/html") && fileName.isNullOrBlank() && !disposition.equals(Part.ATTACHMENT, true) -> {
                if (out.html == null) out.html = part.content.toString()
            }
            else -> {
                val contentId = runCatching { (part as? MimeBodyPart)?.contentID }.getOrNull()?.trim('<', '>')
                val mime = part.contentType.substringBefore(';').trim().lowercase()
                out.attachments += EmailAttachment(
                    partPath = path.ifEmpty { "1" },
                    name = fileName?.ifBlank { null } ?: "attachment-${out.attachments.size + 1}",
                    mimeType = mime,
                    size = part.size.toLong().coerceAtLeast(0L),
                    contentId = contentId
                )
            }
        }
    }

    /** Downloads one attachment into the cache dir and returns the file. */
    suspend fun downloadAttachment(account: EmailAccount, message: EmailMessage, attachment: EmailAttachment): File = io(account) {
        attachment.localPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }?.let { return@io it }
        val folder = store(account).getFolder(message.folder) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val msg = folder.getMessageByUID(message.uid) ?: throw EmailException(R.string.email_error_message_gone)
            val part = resolvePart(msg, attachment.partPath) ?: throw EmailException(R.string.email_error_attachment)
            val safeName = attachment.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "attachment" }
            val target = File(cache.attachmentsDir, "${message.uid}_$safeName")
            part.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            val updated = message.copy(attachments = message.attachments.map { if (it.partPath == attachment.partPath) it.copy(localPath = target.absolutePath) else it })
            cache.saveFullMessage(updated)
            target
        } finally {
            runCatching { folder.close(false) }
        }
    }

    private fun resolvePart(root: Part, path: String): Part? {
        if (!root.isMimeType("multipart/*")) return root
        var current: Part = root
        for (segment in path.split('.')) {
            val idx = segment.toIntOrNull()?.minus(1) ?: return null
            val mp = current.content as? Multipart ?: return null
            if (idx !in 0 until mp.count) return null
            current = mp.getBodyPart(idx)
            if (current.isMimeType("message/rfc822")) current = current.content as? Part ?: return null
        }
        return current
    }

    // ── Flags, moves, deletes ─────────────────────────────────────────────

    suspend fun setSeen(account: EmailAccount, folderName: String, uids: Set<Long>, seen: Boolean) =
        setFlag(account, folderName, uids, Flags.Flag.SEEN, seen) { it.copy(isRead = seen) }

    suspend fun setFlagged(account: EmailAccount, folderName: String, uids: Set<Long>, flagged: Boolean) =
        setFlag(account, folderName, uids, Flags.Flag.FLAGGED, flagged) { it.copy(isFlagged = flagged) }

    private suspend fun setFlag(account: EmailAccount, folderName: String, uids: Set<Long>, flag: Flags.Flag, value: Boolean, local: (EmailMessage) -> EmailMessage) {
        cache.updateMessages(account.id, folderName, uids, local)  // optimistic
        io(account) {
            val folder = store(account).getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_WRITE)
            try {
                val msgs = folder.getMessagesByUID(uids.toLongArray()).filterNotNull().toTypedArray()
                if (msgs.isNotEmpty()) folder.setFlags(msgs, Flags(flag), value)
            } finally {
                runCatching { folder.close(false) }
            }
        }
    }

    /** Moves messages to [targetFolder] (IMAP MOVE when supported, otherwise copy + delete). */
    suspend fun move(account: EmailAccount, folderName: String, uids: Set<Long>, targetFolder: String) {
        if (folderName == targetFolder) return
        io(account) {
            val store = store(account)
            val source = store.getFolder(folderName) as IMAPFolder
            val target = store.getFolder(targetFolder)
            source.open(Folder.READ_WRITE)
            try {
                val msgs = source.getMessagesByUID(uids.toLongArray()).filterNotNull().toTypedArray()
                if (msgs.isEmpty()) return@io
                val moved = store.hasCapability("MOVE") && runCatching { source.moveMessages(msgs, target) }
                    .onFailure { ZuneLog.w(TAG, "move: MOVE refused, falling back to COPY+DELETE", it) }
                    .isSuccess
                if (!moved) {
                    source.copyMessages(msgs, target)
                    source.setFlags(msgs, Flags(Flags.Flag.DELETED), true)
                    source.expunge()
                }
            } finally {
                runCatching { source.close(false) }
            }
        }
        cache.removeMessages(account.id, folderName, uids)
    }

    /** Trash-aware delete: moves to the trash folder, or expunges when already in trash / no trash exists. */
    suspend fun delete(account: EmailAccount, folderName: String, uids: Set<Long>) {
        val trash = cache.folder(account.id, EmailFolderType.TRASH)
        if (trash != null && trash.fullName != folderName) {
            move(account, folderName, uids, trash.fullName)
            return
        }
        io(account) {
            val folder = store(account).getFolder(folderName) as IMAPFolder
            folder.open(Folder.READ_WRITE)
            try {
                val msgs = folder.getMessagesByUID(uids.toLongArray()).filterNotNull().toTypedArray()
                if (msgs.isNotEmpty()) {
                    folder.setFlags(msgs, Flags(Flags.Flag.DELETED), true)
                    folder.expunge()
                }
            } finally {
                runCatching { folder.close(false) }
            }
        }
        cache.removeMessages(account.id, folderName, uids)
    }

    // ── Search ────────────────────────────────────────────────────────────

    /** Server-side search (subject, sender, body) in one folder; newest [limit] hits. */
    suspend fun search(account: EmailAccount, folderName: String, query: String, limit: Int = 60): List<EmailMessage> = io(account) {
        val folder = store(account).getFolder(folderName) as IMAPFolder
        folder.open(Folder.READ_ONLY)
        try {
            val hits = folder.search(OrTerm(arrayOf(SubjectTerm(query), FromStringTerm(query), BodyTerm(query))))
            val newest = hits.takeLast(limit).toTypedArray()
            folder.fetch(newest, headerProfile())
            newest.mapNotNull { toHeader(account.id, folderName, folder, it) }.sortedByDescending { it.uid }
        } finally {
            runCatching { folder.close(false) }
        }
    }

    // ── Sending ───────────────────────────────────────────────────────────

    /** Sends [draft] over SMTP and appends a copy to the Sent folder (unless the server does it itself). */
    suspend fun send(account: EmailAccount, draft: EmailDraft) = withContext(Dispatchers.IO) {
        wrap {
            val password = credential(account)
            val session = Session.getInstance(smtpProps(account))
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.email, account.displayName.ifBlank { null }, "UTF-8"))
                setRecipients(Message.RecipientType.TO, parseAddresses(draft.to))
                if (draft.cc.isNotBlank()) setRecipients(Message.RecipientType.CC, parseAddresses(draft.cc))
                if (draft.bcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, parseAddresses(draft.bcc))
                setSubject(draft.subject, "UTF-8")
                sentDate = Date()
                draft.inReplyTo?.let { setHeader("In-Reply-To", it) }
                draft.references?.let { setHeader("References", it) }
                setHeader("X-Mailer", "Z Launcher")
            }
            val bodyText = buildString {
                append(draft.body)
                if (account.signature.isNotBlank()) append("\n\n").append(account.signature)
                if (draft.quotedText.isNotBlank()) append("\n\n").append(draft.quotedText)
            }
            val attachmentParts = mutableListOf<MimeBodyPart>()
            for (uriString in draft.attachmentUris) {
                val uri = Uri.parse(uriString)
                val (name, bytes, mime) = readUri(uri) ?: continue
                attachmentParts += MimeBodyPart().apply {
                    dataHandler = DataHandler(ByteArrayDataSource(bytes, mime))
                    fileName = MimeUtility.encodeText(name, "UTF-8", null)
                }
            }
            if (draft.forwardedAttachments.isNotEmpty() && draft.forwardedMessageKey != null) {
                val (fAccountId, fFolder, fUid) = draft.forwardedMessageKey.split('|')
                if (fAccountId == account.id) {
                    val folder = store(account).getFolder(fFolder) as IMAPFolder
                    folder.open(Folder.READ_ONLY)
                    try {
                        val original = folder.getMessageByUID(fUid.toLong())
                        for (att in draft.forwardedAttachments) {
                            val part = original?.let { resolvePart(it, att.partPath) } ?: continue
                            val bytes = part.inputStream.use { it.readBytes() }
                            attachmentParts += MimeBodyPart().apply {
                                dataHandler = DataHandler(ByteArrayDataSource(bytes, att.mimeType))
                                fileName = MimeUtility.encodeText(att.name, "UTF-8", null)
                            }
                        }
                    } finally {
                        runCatching { folder.close(false) }
                    }
                }
            }
            if (attachmentParts.isEmpty()) {
                message.setText(bodyText, "UTF-8")
            } else {
                val multipart = MimeMultipart("mixed")
                multipart.addBodyPart(MimeBodyPart().apply { setText(bodyText, "UTF-8") })
                attachmentParts.forEach { multipart.addBodyPart(it) }
                message.setContent(multipart)
            }
            message.saveChanges()

            val transport = session.getTransport("smtp")
            transport.connect(account.smtpHost, account.smtpPort, account.username, password)
            try {
                transport.sendMessage(message, message.allRecipients)
            } finally {
                runCatching { transport.close() }
            }

            // Gmail files sent mail on its own; other servers need an explicit APPEND.
            if (!account.isGmail) {
                runCatching {
                    val sent = cache.folder(account.id, EmailFolderType.SENT) ?: return@runCatching
                    val folder = store(account).getFolder(sent.fullName)
                    folder.open(Folder.READ_WRITE)
                    try {
                        message.setFlag(Flags.Flag.SEEN, true)
                        folder.appendMessages(arrayOf(message))
                    } finally {
                        runCatching { folder.close(false) }
                    }
                }.onFailure { ZuneLog.w(TAG, "send: append to Sent failed", it) }
            }
            // Mark the original as answered.
            if (draft.kind == EmailDraftKind.REPLY || draft.kind == EmailDraftKind.REPLY_ALL) {
                draft.repliedMessageKey?.let { key ->
                    runCatching {
                        val (aId, f, uid) = key.split('|')
                        if (aId == account.id) {
                            val folder = store(account).getFolder(f) as IMAPFolder
                            folder.open(Folder.READ_WRITE)
                            try {
                                folder.getMessageByUID(uid.toLong())?.setFlag(Flags.Flag.ANSWERED, true)
                            } finally { runCatching { folder.close(false) } }
                            cache.updateMessages(account.id, f, setOf(uid.toLong())) { it.copy(isAnswered = true) }
                        }
                    }
                }
            }
        }
    }

    private fun parseAddresses(raw: String): Array<InternetAddress> =
        EmailAddress.parseList(raw).map { InternetAddress(it.address, it.name.ifBlank { null }, "UTF-8") }.toTypedArray()

    private fun readUri(uri: Uri): Triple<String, ByteArray, String>? {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment ?: "attachment"
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return null
        if (bytes.size > MAX_ATTACHMENT_BYTES) throw EmailException(R.string.email_error_attachment_too_large, name)
        return Triple(name, bytes, mime)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private suspend fun <T> io(account: EmailAccount, block: () -> T): T = withContext(Dispatchers.IO) {
        lockFor(account.id).withLock { wrap(block) }
    }

    private fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: EmailException) {
        throw e
    } catch (e: AuthenticationFailedException) {
        throw EmailException(R.string.email_error_auth, e.message, e)
    } catch (e: MessagingException) {
        val cause = e.nextException ?: e.cause
        when {
            cause is UnknownHostException -> throw EmailException(R.string.email_error_host, e.message, e)
            cause is java.net.SocketTimeoutException || cause is java.net.ConnectException -> throw EmailException(R.string.email_error_connection, e.message, e)
            cause is javax.net.ssl.SSLException -> throw EmailException(R.string.email_error_ssl, e.message, e)
            else -> throw EmailException(R.string.email_error_generic, e.message, e)
        }
    } catch (e: java.io.IOException) {
        throw EmailException(R.string.email_error_connection, e.message, e)
    }

    private fun InternetAddress.toModel() = EmailAddress(decode(personal ?: ""), address ?: "")

    private fun Array<javax.mail.Address>?.toModels(): List<EmailAddress> =
        this?.mapNotNull { (it as? InternetAddress)?.toModel() } ?: emptyList()

    private fun decode(raw: String?): String = raw?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) } ?: ""

    companion object {
        private const val TAG = "EmailRepository"
        const val MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024

        /** Rough HTML → text for previews and quoted replies. */
        fun htmlToText(html: String): String = html
            .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n+"), "\n\n")
            .trim()
    }
}
