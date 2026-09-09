package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.util.ZuneLog
import org.json.JSONObject
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

    fun list(token: String, folderId: String): List<CloudItem>

    fun createFolder(token: String, parentId: String, name: String)

    fun rename(token: String, item: CloudItem, newName: String)

    fun delete(token: String, item: CloudItem)

    /** Writes the document's bytes into [output]; Google's own formats are exported as PDF. */
    fun download(token: String, item: CloudItem, output: OutputStream)

    /** File name a download should get, which may differ for exported documents. */
    fun downloadName(item: CloudItem): String = item.name
}

/** Google Drive, REST API v3. */
internal object GoogleDriveApi : CloudApi {

    private const val TAG = "GoogleDriveApi"
    private const val BASE = "https://www.googleapis.com/drive/v3"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val EXPORT_MIME = "application/pdf"

    override val rootId: String = "root"

    override fun list(token: String, folderId: String): List<CloudItem> {
        val query = CloudHttp.encode("'$folderId' in parents and trashed = false")
        val fields = CloudHttp.encode("files(id,name,mimeType,size,modifiedTime)")
        val url = "$BASE/files?q=$query&fields=$fields&pageSize=200&orderBy=folder,name" +
            "&supportsAllDrives=true&includeItemsFromAllDrives=true"
        return parse(CloudHttp.getJson(url, token))
    }

    /** Turns a files.list answer into hub items; kept separate so it can be unit tested. */
    internal fun parse(response: JSONObject): List<CloudItem> {
        val files = response.optJSONArray("files") ?: return emptyList()
        return (0 until files.length()).mapNotNull { index ->
            val json = files.optJSONObject(index) ?: return@mapNotNull null
            val mimeType = json.optString("mimeType")
            CloudItem(
                id = json.optString("id").ifBlank { return@mapNotNull null },
                name = json.optString("name"),
                isFolder = mimeType == FOLDER_MIME,
                size = json.optString("size").toLongOrNull() ?: 0L,
                lastModified = parseTimestamp(json.optString("modifiedTime")),
                mimeType = mimeType,
                isGoogleDocument = mimeType.startsWith("application/vnd.google-apps.") && mimeType != FOLDER_MIME
            )
        }
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

    override fun delete(token: String, item: CloudItem) {
        CloudHttp.delete("$BASE/files/${item.id}?supportsAllDrives=true", token)
    }

    override fun download(token: String, item: CloudItem, output: OutputStream) {
        val url = if (item.isGoogleDocument) {
            "$BASE/files/${item.id}/export?mimeType=${CloudHttp.encode(EXPORT_MIME)}"
        } else {
            "$BASE/files/${item.id}?alt=media&supportsAllDrives=true"
        }
        CloudHttp.download(url, token, output)
    }

    override fun downloadName(item: CloudItem): String =
        if (item.isGoogleDocument && !item.name.endsWith(".pdf", ignoreCase = true)) "${item.name}.pdf" else item.name

    private fun parseTimestamp(value: String): Long = try {
        if (value.isBlank()) 0L else Instant.parse(value).toEpochMilli()
    } catch (e: DateTimeParseException) {
        ZuneLog.w(TAG, "unreadable timestamp: $value", e)
        0L
    }
}

/** OneDrive, through Microsoft Graph. */
internal object OneDriveApi : CloudApi {

    private const val TAG = "OneDriveApi"
    private val base get() = "${CloudAuthRepository.GRAPH}/me/drive"
    private const val DOWNLOAD_URL_FIELD = "@microsoft.graph.downloadUrl"

    override val rootId: String = "root"

    override fun list(token: String, folderId: String): List<CloudItem> {
        val path = if (folderId == rootId) "$base/root/children" else "$base/items/$folderId/children"
        val url = "$path?\$top=200&\$select=id,name,size,lastModifiedDateTime,folder,file"
        return parse(CloudHttp.getJson(url, token))
    }

    /** Turns a children answer into hub items; kept separate so it can be unit tested. */
    internal fun parse(response: JSONObject): List<CloudItem> {
        val values = response.optJSONArray("value") ?: return emptyList()
        return (0 until values.length()).mapNotNull { index ->
            val json = values.optJSONObject(index) ?: return@mapNotNull null
            CloudItem(
                id = json.optString("id").ifBlank { return@mapNotNull null },
                name = json.optString("name"),
                isFolder = json.has("folder"),
                size = json.optLong("size", 0L),
                lastModified = parseTimestamp(json.optString("lastModifiedDateTime")),
                mimeType = json.optJSONObject("file")?.optString("mimeType").orEmpty()
            )
        }.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))
    }

    override fun createFolder(token: String, parentId: String, name: String) {
        val path = if (parentId == rootId) "$base/root/children" else "$base/items/$parentId/children"
        CloudHttp.postJson(
            path,
            token,
            JSONObject()
                .put("name", name)
                .put("folder", JSONObject())
                .put("@microsoft.graph.conflictBehavior", "rename")
        )
    }

    override fun rename(token: String, item: CloudItem, newName: String) {
        CloudHttp.patchJson("$base/items/${item.id}", token, JSONObject().put("name", newName))
    }

    override fun delete(token: String, item: CloudItem) {
        CloudHttp.delete("$base/items/${item.id}", token)
    }

    override fun download(token: String, item: CloudItem, output: OutputStream) {
        // Graph hands out a short-lived pre-signed link; it must be fetched without the token.
        val details = CloudHttp.getJson("$base/items/${item.id}?\$select=id,$DOWNLOAD_URL_FIELD", token)
        val url = details.optString(DOWNLOAD_URL_FIELD)
        if (url.isBlank()) throw CloudException(R.string.cloud_error_service)
        CloudHttp.download(url, null, output)
    }

    private fun parseTimestamp(value: String): Long = try {
        if (value.isBlank()) 0L else Instant.parse(value).toEpochMilli()
    } catch (e: DateTimeParseException) {
        ZuneLog.w(TAG, "unreadable timestamp: $value", e)
        0L
    }
}
