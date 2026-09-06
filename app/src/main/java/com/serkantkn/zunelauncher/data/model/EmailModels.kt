package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Transport security of an IMAP/SMTP connection. */
enum class EmailSecurity(@StringRes val titleRes: Int) {
    SSL(R.string.email_security_ssl),
    STARTTLS(R.string.email_security_starttls),
    NONE(R.string.email_security_none)
}

/** How the account authenticates: a stored password, or a Google OAuth token (XOAUTH2). */
enum class EmailAuthType { PASSWORD, GOOGLE_OAUTH }

/**
 * One configured mailbox. The password is never part of the JSON; it lives in
 * [com.serkantkn.zunelauncher.util.SecretStore] under [id]. Google accounts have no password at
 * all: an access token is fetched from Google Play services for every connection.
 */
data class EmailAccount(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val email: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: EmailSecurity,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: EmailSecurity,
    val username: String,
    val authType: EmailAuthType = EmailAuthType.PASSWORD,
    val signature: String = "",
    val syncMinutes: Int = 15,
    val notificationsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Short lowercase name shown as the hub title, e.g. "gmail". */
    val shortName: String
        get() = displayName.ifBlank { email.substringBefore("@") }.lowercase()

    val isGmail: Boolean get() = imapHost.contains("gmail", ignoreCase = true)

    val isGoogleOAuth: Boolean get() = authType == EmailAuthType.GOOGLE_OAUTH

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("displayName", displayName); put("email", email)
        put("imapHost", imapHost); put("imapPort", imapPort); put("imapSecurity", imapSecurity.name)
        put("smtpHost", smtpHost); put("smtpPort", smtpPort); put("smtpSecurity", smtpSecurity.name)
        put("username", username); put("authType", authType.name); put("signature", signature); put("syncMinutes", syncMinutes)
        put("notificationsEnabled", notificationsEnabled); put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): EmailAccount = EmailAccount(
            id = o.getString("id"),
            displayName = o.optString("displayName", ""),
            email = o.getString("email"),
            imapHost = o.getString("imapHost"),
            imapPort = o.optInt("imapPort", 993),
            imapSecurity = securityOf(o.optString("imapSecurity"), EmailSecurity.SSL),
            smtpHost = o.getString("smtpHost"),
            smtpPort = o.optInt("smtpPort", 465),
            smtpSecurity = securityOf(o.optString("smtpSecurity"), EmailSecurity.SSL),
            username = o.optString("username", o.getString("email")),
            authType = EmailAuthType.entries.firstOrNull { it.name == o.optString("authType") } ?: EmailAuthType.PASSWORD,
            signature = o.optString("signature", ""),
            syncMinutes = o.optInt("syncMinutes", 15),
            notificationsEnabled = o.optBoolean("notificationsEnabled", true),
            createdAt = o.optLong("createdAt", 0L)
        )

        private fun securityOf(name: String, default: EmailSecurity) =
            EmailSecurity.entries.firstOrNull { it.name == name } ?: default
    }
}

/** Well-known providers offered in the account setup screen. */
data class EmailProviderPreset(
    val id: String,
    val name: String,
    val domains: List<String>,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: EmailSecurity,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: EmailSecurity,
    @StringRes val hintRes: Int? = null
)

object EmailProviders {
    val GMAIL = EmailProviderPreset("gmail", "Gmail", listOf("gmail.com", "googlemail.com"), "imap.gmail.com", 993, EmailSecurity.SSL, "smtp.gmail.com", 465, EmailSecurity.SSL, R.string.email_hint_app_password_gmail)
    val OUTLOOK = EmailProviderPreset("outlook", "Outlook / Hotmail", listOf("outlook.com", "hotmail.com", "live.com", "msn.com"), "outlook.office365.com", 993, EmailSecurity.SSL, "smtp.office365.com", 587, EmailSecurity.STARTTLS, R.string.email_hint_app_password_outlook)
    val YAHOO = EmailProviderPreset("yahoo", "Yahoo", listOf("yahoo.com", "yahoo.com.tr", "ymail.com"), "imap.mail.yahoo.com", 993, EmailSecurity.SSL, "smtp.mail.yahoo.com", 465, EmailSecurity.SSL, R.string.email_hint_app_password_generic)
    val ICLOUD = EmailProviderPreset("icloud", "iCloud", listOf("icloud.com", "me.com", "mac.com"), "imap.mail.me.com", 993, EmailSecurity.SSL, "smtp.mail.me.com", 587, EmailSecurity.STARTTLS, R.string.email_hint_app_password_generic)
    val YANDEX = EmailProviderPreset("yandex", "Yandex", listOf("yandex.com", "yandex.ru", "yandex.com.tr"), "imap.yandex.com", 993, EmailSecurity.SSL, "smtp.yandex.com", 465, EmailSecurity.SSL, R.string.email_hint_app_password_generic)
    val CUSTOM = EmailProviderPreset("custom", "IMAP / SMTP", emptyList(), "", 993, EmailSecurity.SSL, "", 587, EmailSecurity.STARTTLS)

    val all: List<EmailProviderPreset> = listOf(GMAIL, OUTLOOK, YAHOO, ICLOUD, YANDEX, CUSTOM)

    /** Preset matching the address domain, or [CUSTOM]. */
    fun forAddress(address: String): EmailProviderPreset {
        val domain = address.substringAfter('@', "").lowercase()
        return all.firstOrNull { domain in it.domains } ?: CUSTOM
    }
}

/** Special-use classification of an IMAP folder (RFC 6154 attributes or well-known names). */
enum class EmailFolderType(@StringRes val titleRes: Int, val sortOrder: Int) {
    INBOX(R.string.email_folder_inbox, 0),
    DRAFTS(R.string.email_folder_drafts, 1),
    SENT(R.string.email_folder_sent, 2),
    ARCHIVE(R.string.email_folder_archive, 3),
    SPAM(R.string.email_folder_spam, 4),
    TRASH(R.string.email_folder_trash, 5),
    OTHER(R.string.email_folder_other, 6)
}

data class EmailFolder(
    val accountId: String,
    /** Full IMAP name used on the wire, e.g. "[Gmail]/Sent Mail". */
    val fullName: String,
    /** Last path segment, shown for [EmailFolderType.OTHER] folders. */
    val name: String,
    val type: EmailFolderType,
    val unreadCount: Int = 0,
    val totalCount: Int = 0
) {
    val isInbox: Boolean get() = type == EmailFolderType.INBOX

    fun toJson(): JSONObject = JSONObject().apply {
        put("accountId", accountId); put("fullName", fullName); put("name", name); put("type", type.name)
        put("unreadCount", unreadCount); put("totalCount", totalCount)
    }

    companion object {
        const val INBOX_NAME = "INBOX"

        fun fromJson(o: JSONObject): EmailFolder = EmailFolder(
            accountId = o.getString("accountId"),
            fullName = o.getString("fullName"),
            name = o.optString("name", o.getString("fullName")),
            type = EmailFolderType.entries.firstOrNull { it.name == o.optString("type") } ?: EmailFolderType.OTHER,
            unreadCount = o.optInt("unreadCount", 0),
            totalCount = o.optInt("totalCount", 0)
        )
    }
}

data class EmailAddress(val name: String, val address: String) {
    /** "Ad Soyad" when a name exists, otherwise the bare address. */
    val display: String get() = name.ifBlank { address }

    fun toJson(): JSONObject = JSONObject().apply { put("name", name); put("address", address) }

    override fun toString(): String = if (name.isBlank()) address else "$name <$address>"

    companion object {
        fun fromJson(o: JSONObject) = EmailAddress(o.optString("name", ""), o.optString("address", ""))

        /** Parses "Name <a@b>", "a@b" or a comma separated list of them. */
        fun parseList(raw: String): List<EmailAddress> = raw.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { token ->
                val lt = token.indexOf('<')
                val gt = token.lastIndexOf('>')
                if (lt >= 0 && gt > lt) EmailAddress(token.substring(0, lt).trim().trim('"'), token.substring(lt + 1, gt).trim())
                else EmailAddress("", token)
            }

        fun isValid(address: String): Boolean =
            Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(address.trim())
    }
}

data class EmailAttachment(
    /** IMAP body-part path inside the message, e.g. "2" or "1.3". */
    val partPath: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val localPath: String? = null,
    val contentId: String? = null
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")

    fun toJson(): JSONObject = JSONObject().apply {
        put("partPath", partPath); put("name", name); put("mimeType", mimeType); put("size", size)
        put("localPath", localPath); put("contentId", contentId)
    }

    companion object {
        fun fromJson(o: JSONObject) = EmailAttachment(
            partPath = o.optString("partPath", ""),
            name = o.optString("name", "attachment"),
            mimeType = o.optString("mimeType", "application/octet-stream"),
            size = o.optLong("size", 0L),
            localPath = o.optStringOrNull("localPath"),
            contentId = o.optStringOrNull("contentId")
        )
    }
}

/**
 * A cached message. Header fields come from the folder sync; [bodyText]/[bodyHtml] and the
 * attachment list are filled in when the message is opened.
 */
data class EmailMessage(
    val accountId: String,
    val folder: String,
    val uid: Long,
    val messageId: String?,
    val subject: String,
    val from: EmailAddress,
    val to: List<EmailAddress>,
    val cc: List<EmailAddress>,
    val replyTo: EmailAddress?,
    val date: Long,
    val snippet: String = "",
    val isRead: Boolean,
    val isFlagged: Boolean,
    val isAnswered: Boolean = false,
    val hasAttachments: Boolean,
    val size: Long = 0L,
    val bodyText: String? = null,
    val bodyHtml: String? = null,
    val attachments: List<EmailAttachment> = emptyList(),
    val inReplyTo: String? = null,
    val references: String? = null
) {
    /** Stable identity across accounts and folders. */
    val key: String get() = key(accountId, folder, uid)

    val hasBody: Boolean get() = bodyText != null || bodyHtml != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("accountId", accountId); put("folder", folder); put("uid", uid); put("messageId", messageId)
        put("subject", subject); put("from", from.toJson())
        put("to", JSONArray().apply { to.forEach { put(it.toJson()) } })
        put("cc", JSONArray().apply { cc.forEach { put(it.toJson()) } })
        put("replyTo", replyTo?.toJson()); put("date", date); put("snippet", snippet)
        put("isRead", isRead); put("isFlagged", isFlagged); put("isAnswered", isAnswered)
        put("hasAttachments", hasAttachments); put("size", size)
        put("bodyText", bodyText); put("bodyHtml", bodyHtml)
        put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
        put("inReplyTo", inReplyTo); put("references", references)
    }

    companion object {
        fun key(accountId: String, folder: String, uid: Long) = "$accountId|$folder|$uid"

        private fun addresses(a: JSONArray?): List<EmailAddress> =
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(EmailAddress::fromJson) }

        fun fromJson(o: JSONObject): EmailMessage = EmailMessage(
            accountId = o.getString("accountId"),
            folder = o.getString("folder"),
            uid = o.getLong("uid"),
            messageId = o.optStringOrNull("messageId"),
            subject = o.optString("subject", ""),
            from = o.optJSONObject("from")?.let(EmailAddress::fromJson) ?: EmailAddress("", ""),
            to = addresses(o.optJSONArray("to")),
            cc = addresses(o.optJSONArray("cc")),
            replyTo = o.optJSONObject("replyTo")?.let(EmailAddress::fromJson),
            date = o.optLong("date", 0L),
            snippet = o.optString("snippet", ""),
            isRead = o.optBoolean("isRead", false),
            isFlagged = o.optBoolean("isFlagged", false),
            isAnswered = o.optBoolean("isAnswered", false),
            hasAttachments = o.optBoolean("hasAttachments", false),
            size = o.optLong("size", 0L),
            bodyText = o.optStringOrNull("bodyText"),
            bodyHtml = o.optStringOrNull("bodyHtml"),
            attachments = o.optJSONArray("attachments")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(EmailAttachment::fromJson) } } ?: emptyList(),
            inReplyTo = o.optStringOrNull("inReplyTo"),
            references = o.optStringOrNull("references")
        )
    }
}

enum class EmailDraftKind { NEW, REPLY, REPLY_ALL, FORWARD }

/** A message being written. Drafts are stored locally until sent. */
data class EmailDraft(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
    /** Quoted original for replies/forwards; sent below [body]. */
    val quotedText: String = "",
    /** content:// URIs picked by the user. */
    val attachmentUris: List<String> = emptyList(),
    /** Attachments of a forwarded message, re-fetched from the server when sending. */
    val forwardedAttachments: List<EmailAttachment> = emptyList(),
    val forwardedMessageKey: String? = null,
    val kind: EmailDraftKind = EmailDraftKind.NEW,
    val inReplyTo: String? = null,
    val references: String? = null,
    val repliedMessageKey: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isBlank: Boolean
        get() = to.isBlank() && cc.isBlank() && bcc.isBlank() && subject.isBlank() && body.isBlank() && attachmentUris.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("accountId", accountId); put("to", to); put("cc", cc); put("bcc", bcc)
        put("subject", subject); put("body", body); put("quotedText", quotedText)
        put("attachmentUris", JSONArray(attachmentUris))
        put("forwardedAttachments", JSONArray().apply { forwardedAttachments.forEach { put(it.toJson()) } })
        put("forwardedMessageKey", forwardedMessageKey); put("kind", kind.name)
        put("inReplyTo", inReplyTo); put("references", references); put("repliedMessageKey", repliedMessageKey)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): EmailDraft = EmailDraft(
            id = o.getString("id"),
            accountId = o.getString("accountId"),
            to = o.optString("to", ""), cc = o.optString("cc", ""), bcc = o.optString("bcc", ""),
            subject = o.optString("subject", ""), body = o.optString("body", ""), quotedText = o.optString("quotedText", ""),
            attachmentUris = o.optJSONArray("attachmentUris")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            forwardedAttachments = o.optJSONArray("forwardedAttachments")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(EmailAttachment::fromJson) } } ?: emptyList(),
            forwardedMessageKey = o.optStringOrNull("forwardedMessageKey"),
            kind = EmailDraftKind.entries.firstOrNull { it.name == o.optString("kind") } ?: EmailDraftKind.NEW,
            inReplyTo = o.optStringOrNull("inReplyTo"), references = o.optStringOrNull("references"),
            repliedMessageKey = o.optStringOrNull("repliedMessageKey"),
            updatedAt = o.optLong("updatedAt", 0L)
        )
    }
}
