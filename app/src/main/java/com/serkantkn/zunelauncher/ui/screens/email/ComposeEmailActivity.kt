package com.serkantkn.zunelauncher.ui.screens.email

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.data.repository.EmailBridge
import com.serkantkn.zunelauncher.util.AppLocale
import com.serkantkn.zunelauncher.util.ZuneLog
import java.io.File

/**
 * Target of mailto: links and the share sheet ("paylaş → E-posta"). Parses the intent into an
 * [EmailBridge.Request.Compose], then brings the launcher to the front with the Email hub open.
 * Has no UI of its own (same pattern as ReceiveNoteActivity).
 */
class ComposeEmailActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        var to = ""; var cc = ""; var bcc = ""; var subject = ""; var body = ""
        val attachments = mutableListOf<String>()

        val data = intent?.data
        if (data != null && data.scheme.equals("mailto", ignoreCase = true)) {
            val raw = data.schemeSpecificPart ?: ""
            to = Uri.decode(raw.substringBefore('?'))
            val query = raw.substringAfter('?', "")
            query.split('&').filter { it.contains('=') }.forEach { pair ->
                val key = pair.substringBefore('=').lowercase()
                val value = Uri.decode(pair.substringAfter('=').replace('+', ' '))
                when (key) {
                    "to" -> to = if (to.isBlank()) value else "$to, $value"
                    "cc" -> cc = value
                    "bcc" -> bcc = value
                    "subject" -> subject = value
                    "body" -> body = value
                }
            }
        }
        intent?.getStringArrayExtra(Intent.EXTRA_EMAIL)?.let { if (to.isBlank()) to = it.joinToString(", ") }
        intent?.getStringArrayExtra(Intent.EXTRA_CC)?.let { if (cc.isBlank()) cc = it.joinToString(", ") }
        intent?.getStringArrayExtra(Intent.EXTRA_BCC)?.let { if (bcc.isBlank()) bcc = it.joinToString(", ") }
        intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.let { if (subject.isBlank()) subject = it }
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { if (body.isBlank()) body = it }
        val shared = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(streamExtra(intent))
            Intent.ACTION_SEND_MULTIPLE -> streamExtras(intent)
            else -> emptyList()
        }
        // The grant that came with the share lasts only as long as this activity, and the mail
        // is sent later from a screen that has none; so each file is copied into the cache now.
        // Only content:// from another app is taken: a file:// path, or a URI that names this
        // app's own provider, is a way of asking the launcher to mail its own private files.
        shared.forEach { uri ->
            if (!isAcceptable(uri)) {
                ZuneLog.w(TAG, "a shared attachment was refused: ${uri.scheme}")
                return@forEach
            }
            copyToCache(uri)?.let { attachments += Uri.fromFile(it).toString() }
        }

        EmailBridge.compose(to = to, cc = cc, bcc = bcc, subject = subject, body = body, attachmentUris = attachments)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_EMAIL, true)
        })
        finish()
    }

    private fun isAcceptable(uri: Uri): Boolean {
        if (!uri.scheme.equals("content", ignoreCase = true)) return false
        val authority = uri.authority.orEmpty()
        return authority.isNotBlank() && !authority.startsWith(packageName)
    }

    /** The shared file, as a file of our own under cache/shared_attachments; null if unreadable. */
    private fun copyToCache(uri: Uri): File? = try {
        val dir = File(cacheDir, SHARED_DIR).apply { mkdirs() }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
        runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        }
        val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "attachment" }
        val target = File(dir, "${System.currentTimeMillis()}_$safeName")
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                var copied = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > MAX_BYTES) {
                        // The repository would refuse it at send time anyway; better not to fill
                        // the cache with something that will never leave.
                        target.delete()
                        return null
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: return null
        target
    } catch (e: Exception) {
        ZuneLog.w(TAG, "a shared attachment could not be copied", e)
        null
    }

    private companion object {
        const val TAG = "ComposeEmailActivity"
        const val SHARED_DIR = "shared_attachments"
        const val MAX_BYTES = 25L * 1024 * 1024
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamExtras(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()
}
