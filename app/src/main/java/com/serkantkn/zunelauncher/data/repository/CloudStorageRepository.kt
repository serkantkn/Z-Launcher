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
 * The services are talked to over their own web APIs ([GoogleDriveApi], [OneDriveApi]), so a drive
 * is reachable whether or not the service's Android app is installed. Sign-in and tokens are
 * [CloudAuthRepository]'s job; this class turns them into folders, files and downloads.
 */
class CloudStorageRepository(
    private val context: Context,
    private val dataStore: CloudDataStore,
    private val auth: CloudAuthRepository
) {

    val accounts: Flow<List<CloudAccount>> = dataStore.accounts

    fun isMicrosoftConfigured(): Boolean = auth.isMicrosoftConfigured()

    fun microsoftAuthorizationUrl(): String = auth.microsoftAuthorizationUrl()

    fun googleAuthorizeTask(email: String? = null) = auth.googleAuthorizeTask(email)

    fun googleResultFromIntent(data: Intent): AuthorizationResult = auth.googleResultFromIntent(data)

    /** Stores the Google account that just granted the Drive scope. */
    suspend fun completeGoogleSignIn(result: AuthorizationResult): CloudAccount {
        val account = auth.googleAccountOf(result)
        dataStore.putAccount(account)
        ZuneLog.d(TAG, "stored cloud account ${account.id}")
        return account
    }

    /** Exchanges the code the browser handed back and stores the Microsoft account. */
    suspend fun completeMicrosoftSignIn(code: String): CloudAccount {
        val account = auth.completeMicrosoftSignIn(code)
        dataStore.putAccount(account)
        return account
    }

    suspend fun signOut(account: CloudAccount) {
        auth.forget(account.id)
        dataStore.removeAccount(account.id)
    }

    /** Top folder of the account's drive. */
    fun rootFolderId(account: CloudAccount): String = apiOf(account).rootId

    suspend fun list(account: CloudAccount, folderId: String): List<CloudItem> =
        withContext(Dispatchers.IO) { apiOf(account).list(tokenOf(account), folderId) }

    suspend fun createFolder(account: CloudAccount, parentId: String, name: String) =
        withContext(Dispatchers.IO) { apiOf(account).createFolder(tokenOf(account), parentId, name) }

    suspend fun rename(account: CloudAccount, item: CloudItem, newName: String) =
        withContext(Dispatchers.IO) { apiOf(account).rename(tokenOf(account), item, newName) }

    suspend fun delete(account: CloudAccount, item: CloudItem) =
        withContext(Dispatchers.IO) { apiOf(account).delete(tokenOf(account), item) }

    /**
     * Downloads the document and hands it to whichever app can show it. Cloud files have no local
     * bytes, so a copy goes into the app's cache first and travels as a FileProvider uri.
     */
    suspend fun open(account: CloudAccount, item: CloudItem) {
        val file = downloadToCache(account, item)
        startWithGrant(Intent(Intent.ACTION_VIEW), file, item)
    }

    suspend fun share(account: CloudAccount, item: CloudItem) {
        val file = downloadToCache(account, item)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = item.mimeType.ifBlank { "*/*" }
        }
        startChooser(intent)
    }

    /** Saves the document into the device's Downloads folder and returns the name it got. */
    suspend fun saveToDevice(account: CloudAccount, item: CloudItem): String =
        withContext(Dispatchers.IO) {
            val api = apiOf(account)
            val token = tokenOf(account)
            val name = api.downloadName(item)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, item.mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val target = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw CloudException(R.string.cloud_error_save_failed)
                context.contentResolver.openOutputStream(target)?.use { output ->
                    api.download(token, item, output)
                } ?: throw CloudException(R.string.cloud_error_save_failed)
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(target, values, null, null)
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloads.exists()) downloads.mkdirs()
                val target = uniqueFile(downloads, name)
                target.outputStream().use { output -> api.download(token, item, output) }
            }
            name
        }

    private suspend fun downloadToCache(account: CloudAccount, item: CloudItem): File =
        withContext(Dispatchers.IO) {
            val api = apiOf(account)
            val directory = File(context.cacheDir, "cloud").apply { mkdirs() }
            // One cache file per document, replaced on every open so edits elsewhere show up.
            val target = File(directory, api.downloadName(item).replace(File.separatorChar, '_'))
            target.outputStream().use { output -> api.download(tokenOf(account), item, output) }
            target
        }

    private fun startWithGrant(intent: Intent, file: File, item: CloudItem) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        intent.setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
        startChooser(intent)
    }

    private fun startChooser(intent: Intent) {
        try {
            val chooser = Intent.createChooser(intent, context.localizedString(R.string.files_share_chooser))
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
        CloudService.ONEDRIVE -> auth.microsoftAccessToken(account)
    }

    private fun apiOf(account: CloudAccount): CloudApi = when (account.service) {
        CloudService.GOOGLE_DRIVE -> GoogleDriveApi
        CloudService.ONEDRIVE -> OneDriveApi
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
    }
}
