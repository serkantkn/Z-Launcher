package com.serkantkn.zunelauncher.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.datastore.CloudDataStore
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.CloudPage
import com.serkantkn.zunelauncher.data.model.CloudQuota
import com.serkantkn.zunelauncher.data.model.CloudService
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Files hub's cloud storage: signed-in accounts and everything done with them.
 *
 * The service is talked to over its own web API ([GoogleDriveApi]), so a drive is reachable
 * whether or not the service's Android app is installed. Sign-in and tokens are
 * [CloudAuthRepository]'s job; this class turns them into folders, files and downloads.
 */
class CloudStorageRepository(
    private val context: Context,
    private val dataStore: CloudDataStore,
    private val auth: CloudAuthRepository
) {

    val accounts: Flow<List<CloudAccount>> = dataStore.accounts

    fun googleAuthorizeTask(email: String? = null) = auth.googleAuthorizeTask(email)

    fun googleResultFromIntent(data: Intent): AuthorizationResult = auth.googleResultFromIntent(data)

    /** Stores the Google account that just granted the Drive scope. */
    suspend fun completeGoogleSignIn(result: AuthorizationResult): CloudAccount {
        val account = auth.googleAccountOf(result)
        dataStore.putAccount(account)
        ZuneLog.d(TAG, "stored cloud account ${account.id}")
        return account
    }

    suspend fun signOut(account: CloudAccount) {
        auth.forget(account.id)
        dataStore.removeAccount(account.id)
    }

    /** Top folder of the account's drive. */
    fun rootFolderId(account: CloudAccount): String = apiOf(account).rootId

    /**
     * Everything in a folder, however many pages the service hands it over in.
     *
     * The loop is bounded: a folder with tens of thousands of files would otherwise be a great
     * many requests before anything appeared on screen, and nobody scrolls that far.
     */
    suspend fun list(account: CloudAccount, folderId: String): List<CloudItem> =
        withContext(Dispatchers.IO) {
            val api = apiOf(account)
            val token = tokenOf(account)
            val collected = mutableListOf<CloudItem>()
            var pageToken: String? = null
            var pages = 0
            do {
                val page: CloudPage = api.list(token, folderId, pageToken)
                collected += page.items
                pageToken = page.nextPageToken
                pages++
            } while (!pageToken.isNullOrEmpty() && pages < MAX_PAGES)
            if (!pageToken.isNullOrEmpty()) {
                ZuneLog.w(TAG, "folder $folderId has more than ${collected.size} items; stopped there")
            }
            collected
        }

    /** Files anywhere on the drive whose name matches — the service does the looking. */
    suspend fun search(account: CloudAccount, query: String): List<CloudItem> =
        withContext(Dispatchers.IO) { apiOf(account).search(tokenOf(account), query.trim()) }

    /** How full the drive is, or null when the service will not say. */
    suspend fun quota(account: CloudAccount): CloudQuota? =
        withContext(Dispatchers.IO) { runCatching { apiOf(account).quota(tokenOf(account)) }.getOrNull() }

    /** Sends a file from the device up into the open folder. */
    suspend fun upload(
        account: CloudAccount,
        parentId: String,
        file: File,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        apiOf(account).upload(tokenOf(account), parentId, file, mimeType, onProgress)
    }

    suspend fun createFolder(account: CloudAccount, parentId: String, name: String) =
        withContext(Dispatchers.IO) { apiOf(account).createFolder(tokenOf(account), parentId, name) }

    suspend fun rename(account: CloudAccount, item: CloudItem, newName: String) =
        withContext(Dispatchers.IO) { apiOf(account).rename(tokenOf(account), item, newName) }

    suspend fun delete(account: CloudAccount, item: CloudItem) =
        withContext(Dispatchers.IO) { apiOf(account).delete(tokenOf(account), item) }

    /**
     * Opens the document. Google's own formats — Docs, Sheets, Slides, and the forms and maps that
     * cannot be exported at all — open on their page, where Google's apps or the browser can read
     * and edit them; a PDF copy of a spreadsheet was the most a download could give. Everything
     * else is downloaded into the app's cache and handed, as a FileProvider uri, to whichever app
     * can show it, with the type it really is.
     */
    suspend fun open(account: CloudAccount, item: CloudItem, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        if (item.isGoogleDocument || item.isWebOnly) {
            openWeb(account, item)
            return
        }
        val file = downloadToCache(account, item, onProgress)
        startWithGrant(Intent(Intent.ACTION_VIEW), file, apiOf(account).downloadMime(item))
    }

    /**
     * Where a file can be streamed from, and a fresh token to read it with: a video plays from
     * here as it arrives rather than after a download of the whole thing.
     */
    suspend fun streamOf(account: CloudAccount, contentId: String): CloudStream =
        withContext(Dispatchers.IO) { CloudStream(apiOf(account).mediaUrlOf(contentId), tokenOf(account)) }

    /** Opens the item's page on the service's website, in Google's app for it or the browser. */
    fun openWeb(account: CloudAccount, item: CloudItem) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(apiOf(account).webUrlOf(item)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "nothing could open the page", e)
            throw CloudException(R.string.cloud_error_no_app, e.message, e)
        }
    }

    suspend fun share(account: CloudAccount, item: CloudItem, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val file = downloadToCache(account, item, onProgress)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = apiOf(account).downloadMime(item)
        }
        startChooser(grantReading(intent, uri))
    }

    /** Saves the document into the device's Downloads folder and returns the name it got. */
    suspend fun saveToDevice(
        account: CloudAccount,
        item: CloudItem,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): String =
        withContext(Dispatchers.IO) {
            val api = apiOf(account)
            val token = tokenOf(account)
            val name = api.downloadName(item)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, api.downloadMime(item).takeIf { it != "*/*" } ?: "application/octet-stream")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val target = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw CloudException(R.string.cloud_error_save_failed)
                context.contentResolver.openOutputStream(target)?.use { output ->
                    api.download(token, item, output, onProgress)
                } ?: throw CloudException(R.string.cloud_error_save_failed)
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(target, values, null, null)
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val target = uniqueFile(downloads, name)
                target.outputStream().use { output -> api.download(token, item, output, onProgress) }
            }
            name
        }

    private suspend fun downloadToCache(
        account: CloudAccount,
        item: CloudItem,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File =
        withContext(Dispatchers.IO) {
            val api = apiOf(account)
            // One folder per document: two files of the same name in different folders of the
            // drive used to overwrite each other's copy while the first was still open.
            val directory = File(File(context.cacheDir, "cloud"), item.contentId.replace(File.separatorChar, '_'))
                .apply { mkdirs() }
            // Replaced on every open so edits made elsewhere show up.
            val target = File(directory, api.downloadName(item).replace(File.separatorChar, '_'))
            try {
                target.outputStream().use { output -> api.download(tokenOf(account), item, output, onProgress) }
            } catch (e: Exception) {
                // A half-written copy must not be opened next time as if it were whole.
                target.delete()
                throw e
            }
            target
        }

    private fun startWithGrant(intent: Intent, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        intent.setDataAndType(uri, mimeType)
        startChooser(grantReading(intent, uri), R.string.files_open_with)
    }

    /**
     * Lets whichever app is picked read the copy in the cache.
     *
     * The grant has to be on the intent the chooser hands on, not on the chooser: the chooser
     * carries only the grants its target intent asks for, and with the flag on the chooser alone
     * the app that was picked got a uri it was not allowed to open - a video player said the file
     * could not be found while the hub showed its thumbnail perfectly well, having made that
     * itself. The uri also goes in as the intent's clip, which is what the grant travels with.
     */
    private fun grantReading(intent: Intent, uri: android.net.Uri): Intent = intent.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri(null, uri)
    }

    /** [title] says what the chooser is for: opening a file, or sending it somewhere. */
    private fun startChooser(intent: Intent, title: Int = R.string.files_share_chooser) {
        try {
            val chooser = Intent.createChooser(intent, context.localizedString(title))
            context.startActivity(
                chooser.apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            ZuneLog.e(TAG, "no app could handle the document", e)
            throw CloudException(R.string.cloud_error_no_app, e.message, e)
        }
    }

    private suspend fun tokenOf(account: CloudAccount): String = when (account.service) {
        CloudService.GOOGLE_DRIVE -> auth.googleAccessToken(account.email)
    }

    private fun apiOf(account: CloudAccount): CloudApi = when (account.service) {
        CloudService.GOOGLE_DRIVE -> GoogleDriveApi
    }

    private fun uniqueFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isEmpty()) "" else ".$extension"
            candidate = File(directory, "$base ($index)$suffix")
            index++
        }
        return candidate
    }

    private companion object {
        const val TAG = "CloudStorage"

        /** Enough pages for any folder a person actually browses. */
        const val MAX_PAGES = 25
    }
}

/** A file's bytes as a stream: the address, and the token the request has to carry. */
data class CloudStream(val url: String, val token: String)
