package com.serkantkn.zeta.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A document the user opened through the phone's own picker, from whatever app keeps it. */
data class CloudDocument(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    /** In bytes; -1 when the app that keeps the document did not say. */
    val size: Long,
    val openedAt: Long
)

/**
 * The cloud as the Files hub reaches it now: through Android's own document picker.
 *
 * The hub used to be a Google Drive client of its own, signed in with Google and holding the full
 * Drive scope, and Google asks a yearly security assessment for that scope. The picker needs none
 * of it: it lists whatever cloud apps the phone has - Drive, OneDrive, Dropbox - and hands back
 * the one document the user chose, with no sign-in here at all.
 *
 * What is kept is only the list of documents opened that way, newest first, so they can be opened
 * again without going back through the picker. Each one holds a lasting read grant from the app
 * that keeps it; a document that leaves the list gives its grant back, because a phone allows an
 * app only so many of them.
 */
class CloudDocuments(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _documents = MutableStateFlow(load())
    /** The documents opened from the cloud, the most recent first. */
    val documents: StateFlow<List<CloudDocument>> = _documents.asStateFlow()

    /**
     * Adds what the picker handed back to the top of the list and keeps the right to read it.
     * It asks the app that keeps the document for its name and size, so it is called off the main
     * thread. Null when the document could not be read at all.
     */
    fun remember(uri: Uri): CloudDocument? {
        // Only a grant taken now outlives the picker; without it the document opens this time only.
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { ZuneLog.w(TAG, "no lasting grant for $uri", it) }
        val document = describe(uri) ?: return null
        val dropped = mutableListOf<CloudDocument>()
        _documents.update { current ->
            val next = listOf(document) + current.filterNot { it.uri == uri }
            dropped += next.drop(MAX_DOCUMENTS)
            next.take(MAX_DOCUMENTS)
        }
        dropped.forEach(::release)
        persist()
        return document
    }

    /** Moves a document back to the top when it is opened again from the list. */
    fun markOpened(document: CloudDocument) {
        _documents.update { current ->
            val found = current.firstOrNull { it.uri == document.uri } ?: return@update current
            listOf(found.copy(openedAt = System.currentTimeMillis())) + current.filterNot { it.uri == document.uri }
        }
        persist()
    }

    /** Takes a document off the list and gives back the right to read it. */
    fun forget(document: CloudDocument) {
        _documents.update { current -> current.filterNot { it.uri == document.uri } }
        release(document)
        persist()
    }

    /**
     * Whether the document can still be read: the app that keeps it may have been removed, the
     * file deleted, or the grant lost. Asks that app, so it is called off the main thread.
     */
    fun isReadable(document: CloudDocument): Boolean = runCatching {
        app.contentResolver.query(document.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    /**
     * The apps on the phone that offer their documents to the picker - Drive, OneDrive, Dropbox -
     * by their names, without the phone's own storage, downloads and media, which the hub already
     * shows as folders.
     */
    fun cloudApps(): List<String> {
        val pm = app.packageManager
        return runCatching {
            pm.queryIntentContentProviders(Intent(DocumentsContract.PROVIDER_INTERFACE), 0)
                .mapNotNull { info ->
                    val provider = info.providerInfo ?: return@mapNotNull null
                    if (provider.packageName == app.packageName) return@mapNotNull null
                    if (provider.packageName in LOCAL_PACKAGES) return@mapNotNull null
                    if (provider.authority.orEmpty().split(';').any { it in LOCAL_AUTHORITIES }) return@mapNotNull null
                    provider.applicationInfo?.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                }
                .distinct()
                .sortedBy { it.lowercase(Locale.getDefault()) }
        }.onFailure { ZuneLog.w(TAG, "the document providers could not be listed", it) }
            .getOrDefault(emptyList())
    }

    private fun describe(uri: Uri): CloudDocument? {
        val resolver = app.contentResolver
        var name: String? = null
        var size = -1L
        val read = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
                true
            } ?: false
        }.onFailure { ZuneLog.w(TAG, "$uri could not be described", it) }.getOrDefault(false)
        if (!read) return null
        return CloudDocument(
            uri = uri,
            name = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/'),
            mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
            size = size,
            openedAt = System.currentTimeMillis()
        )
    }

    private fun release(document: CloudDocument) {
        runCatching { app.contentResolver.releasePersistableUriPermission(document.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun load(): List<CloudDocument> = runCatching {
        val array = JSONArray(prefs.getString(KEY_DOCUMENTS, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val json = array.getJSONObject(index)
                CloudDocument(
                    uri = Uri.parse(json.getString("uri")),
                    name = json.optString("name"),
                    mimeType = json.optString("mime").ifEmpty { null },
                    size = json.optLong("size", -1L),
                    openedAt = json.optLong("openedAt")
                )
            }.getOrNull()
        }
    }.onFailure { ZuneLog.w(TAG, "the cloud list could not be read", it) }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        _documents.value.forEach { document ->
            array.put(
                JSONObject()
                    .put("uri", document.uri.toString())
                    .put("name", document.name)
                    .put("mime", document.mimeType.orEmpty())
                    .put("size", document.size)
                    .put("openedAt", document.openedAt)
            )
        }
        prefs.edit().putString(KEY_DOCUMENTS, array.toString()).apply()
    }

    private companion object {
        const val TAG = "CloudDocuments"
        const val PREFS = "cloud_documents"
        const val KEY_DOCUMENTS = "documents"

        /** Enough to find last week's document again; the picker is there for the rest. */
        const val MAX_DOCUMENTS = 30

        /** The phone's own storage, which the picker offers alongside the cloud apps. */
        val LOCAL_PACKAGES = setOf(
            "com.android.externalstorage",
            "com.android.providers.downloads",
            "com.android.providers.downloads.ui",
            "com.android.providers.media",
            "com.android.providers.media.module",
            "com.google.android.providers.media.module",
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.android.mtp",
            "com.android.shell"
        )

        val LOCAL_AUTHORITIES = setOf(
            "com.android.externalstorage.documents",
            "com.android.providers.downloads.documents",
            "com.android.providers.media.documents",
            "com.android.mtp.documents",
            "com.android.shell.documents"
        )
    }
}
