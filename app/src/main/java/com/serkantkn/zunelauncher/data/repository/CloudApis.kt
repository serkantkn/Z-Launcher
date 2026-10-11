package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.CloudPage
import com.serkantkn.zunelauncher.data.model.CloudQuota
import com.serkantkn.zunelauncher.util.ZuneLog
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * What the Files hub needs from a cloud service, and the two implementations behind it.
 * Everything blocks and belongs on Dispatchers.IO; [CloudStorageRepository] handles that.
 */
internal interface CloudApi {

    /** Identifier of the drive's top folder for this service. */
    val rootId: String

    /** One page of a folder; [pageToken] continues where the last answer left off. */
    fun list(token: String, folderId: String, pageToken: String? = null): CloudPage

    /** Files anywhere on the drive whose name matches, which the hub cannot work out by itself. */
    fun search(token: String, query: String): List<CloudItem>

    /** How full the drive is, or null when the service will not say. */
    fun quota(token: String): CloudQuota?

    fun createFolder(token: String, parentId: String, name: String)

    fun rename(token: String, item: CloudItem, newName: String)

    fun delete(token: String, item: CloudItem)

    /** Writes the document's bytes into [output]; Google's own formats are exported as PDF. */
    fun download(token: String, item: CloudItem, output: OutputStream, onProgress: (Long, Long) -> Unit = { _, _ -> })

    /** Sends a file from the device up into a folder. */
    fun upload(token: String, parentId: String, file: File, mimeType: String, onProgress: (Long, Long) -> Unit = { _, _ -> })

    /** File name a download should get, which may differ for exported documents. */
    fun downloadName(item: CloudItem): String = item.name

    /**
     * What kind of file a download is, for the app that is to open it: an exported document is
     * a PDF whatever Google calls it, and a file a drive only knows as "bytes" goes by its name.
     */
    fun downloadMime(item: CloudItem): String {
        val declared = item.mimeType
        if (declared.isNotBlank() && declared != "application/octet-stream") return declared
        val extension = downloadName(item).substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    /** The item's page on the service's own website. */
    fun webUrlOf(item: CloudItem): String
}

/** Google Drive, REST API v3. */
internal object GoogleDriveApi : CloudApi {

    private const val TAG = "GoogleDriveApi"
    private const val BASE = "https://www.googleapis.com/drive/v3"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val EXPORT_MIME = "application/pdf"
    private const val SHORTCUT_MIME = "application/vnd.google-apps.shortcut"
    private const val GOOGLE_PREFIX = "application/vnd.google-apps."

    /** The Google formats Drive can export as a PDF; every other one only opens on the web. */
    private val EXPORTABLE = setOf(
        "application/vnd.google-apps.document",
        "application/vnd.google-apps.spreadsheet",
        "application/vnd.google-apps.presentation",
        "application/vnd.google-apps.drawing"
    )
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private const val PAGE_SIZE = 200
    private val FIELDS = CloudHttp.encode(
        "nextPageToken,files(id,name,mimeType,size,modifiedTime,thumbnailLink,webViewLink,shortcutDetails)"
    )

    override val rootId: String = "root"

    override fun list(token: String, folderId: String, pageToken: String?): CloudPage {
        val query = CloudHttp.encode("'$folderId' in parents and trashed = false")
        val url = buildString {
            append("$BASE/files?q=$query&fields=$FIELDS&pageSize=$PAGE_SIZE&orderBy=folder,name")
            append("&supportsAllDrives=true&includeItemsFromAllDrives=true")
            if (!pageToken.isNullOrEmpty()) append("&pageToken=${CloudHttp.encode(pageToken)}")
        }
        return parse(CloudHttp.getJson(url, token))
    }

    override fun search(token: String, query: String): List<CloudItem> {
        val escaped = query.replace("\\", "\\\\").replace("'", "\\'")
        val q = CloudHttp.encode("name contains '$escaped' and trashed = false")
        val url = "$BASE/files?q=$q&fields=$FIELDS&pageSize=$PAGE_SIZE&orderBy=folder,name" +
            "&supportsAllDrives=true&includeItemsFromAllDrives=true"
        return parse(CloudHttp.getJson(url, token)).items
    }

    override fun quota(token: String): CloudQuota? {
        val response = CloudHttp.getJson(
            "$BASE/about?fields=${CloudHttp.encode("storageQuota")}",
            token
        )
        val quota = response.optJSONObject("storageQuota") ?: return null
        val used = quota.optString("usage").toLongOrNull() ?: return null
        // A workspace account with no cap reports no limit at all rather than a huge one.
        val total = quota.optString("limit").toLongOrNull() ?: 0L
        return CloudQuota(usedBytes = used, totalBytes = total)
    }

    /** Turns a files.list answer into hub items; kept separate so it can be unit tested. */
    internal fun parse(response: JSONObject): CloudPage {
        val files = response.optJSONArray("files") ?: return CloudPage(emptyList())
        val items = (0 until files.length()).mapNotNull { index ->
            val json = files.optJSONObject(index) ?: return@mapNotNull null
            val ownMime = json.optString("mimeType")
            // A shortcut is shown as what it points at: a folder opens, a document opens.
            val shortcut = json.optJSONObject("shortcutDetails")?.takeIf { ownMime == SHORTCUT_MIME }
            val mimeType = shortcut?.optString("targetMimeType")?.ifBlank { null } ?: ownMime
            val google = mimeType.startsWith(GOOGLE_PREFIX) && mimeType != FOLDER_MIME
            CloudItem(
                id = json.optString("id").ifBlank { return@mapNotNull null },
                name = json.optString("name"),
                isFolder = mimeType == FOLDER_MIME,
                size = json.optString("size").toLongOrNull() ?: 0L,
                lastModified = parseTimestamp(json.optString("modifiedTime")),
                mimeType = mimeType,
                isGoogleDocument = google && mimeType in EXPORTABLE,
                thumbnailUrl = json.optString("thumbnailLink").ifBlank { null },
                webUrl = json.optString("webViewLink").ifBlank { null },
                targetId = shortcut?.optString("targetId")?.ifBlank { null },
                isWebOnly = google && mimeType !in EXPORTABLE
            )
        }
        return CloudPage(items, response.optString("nextPageToken").ifBlank { null })
    }

    override fun createFolder(token: String, parentId: String, name: String) {
        CloudHttp.postJson(
            "$BASE/files?supportsAllDrives=true",
            token,
            JSONObject()
                .put("name", name)
                .put("mimeType", FOLDER_MIME)
                .put("parents", org.json.JSONArray().put(parentId))
        )
    }

    override fun rename(token: String, item: CloudItem, newName: String) {
        CloudHttp.patchJson("$BASE/files/${item.id}?supportsAllDrives=true", token, JSONObject().put("name", newName))
    }

    /**
     * Moves the item to Drive's own bin rather than deleting it: files.delete skips the bin and
     * is gone for good, and a tap in a list is no way to lose a file forever. Drive empties its
     * bin after thirty days, as the hub's own bin does.
     */
    override fun delete(token: String, item: CloudItem) {
        CloudHttp.patchJson("$BASE/files/${item.id}?supportsAllDrives=true", token, JSONObject().put("trashed", true))
    }

    override fun download(
        token: String,
        item: CloudItem,
        output: OutputStream,
        onProgress: (Long, Long) -> Unit
    ) {
        if (item.isWebOnly) throw CloudException(R.string.cloud_error_web_only)
        val url = if (item.isGoogleDocument) {
            "$BASE/files/${item.contentId}/export?mimeType=${CloudHttp.encode(EXPORT_MIME)}"
        } else {
            "$BASE/files/${item.contentId}?alt=media&supportsAllDrives=true"
        }
        // An exported document has no size of its own until it arrives, so the known total is the
        // file's own size and zero for a Google format — the bar simply counts up instead.
        CloudHttp.download(url, token, output, if (item.isGoogleDocument) 0L else item.size, onProgress)
    }

    /**
     * Sends a file up in one request with its metadata beside it.
     *
     * Drive's multipart upload is a single POST carrying a JSON part and the bytes, which is all
     * the hub needs: the resumable protocol only earns its complexity on unreliable connections
     * and very large files, and a failed upload here can simply be tried again.
     */
    override fun upload(
        token: String,
        parentId: String,
        file: File,
        mimeType: String,
        onProgress: (Long, Long) -> Unit
    ) {
        val metadata = JSONObject()
            .put("name", file.name)
            .put("parents", org.json.JSONArray().put(parentId))
        CloudHttp.uploadMultipart(
            url = "$UPLOAD/files?uploadType=multipart&supportsAllDrives=true",
            token = token,
            metadata = metadata,
            file = file,
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            onProgress = onProgress
        )
    }

    override fun downloadMime(item: CloudItem): String =
        if (item.isGoogleDocument) EXPORT_MIME else super.downloadMime(item)

    override fun webUrlOf(item: CloudItem): String =
        item.webUrl ?: "https://drive.google.com/open?id=${CloudHttp.encode(item.contentId)}"

    override fun downloadName(item: CloudItem): String =
        if (item.isGoogleDocument && !item.name.endsWith(".pdf", ignoreCase = true)) "${item.name}.pdf" else item.name

    private fun parseTimestamp(value: String): Long = try {
        if (value.isBlank()) 0L else Instant.parse(value).toEpochMilli()
    } catch (e: DateTimeParseException) {
        ZuneLog.w(TAG, "unreadable timestamp: $value", e)
        0L
    }
}
