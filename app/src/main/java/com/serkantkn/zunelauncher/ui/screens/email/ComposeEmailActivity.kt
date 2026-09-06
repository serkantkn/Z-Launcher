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
        when (intent?.action) {
            Intent.ACTION_SEND -> streamExtra(intent)?.let { attachments += it.toString() }
            Intent.ACTION_SEND_MULTIPLE -> streamExtras(intent).forEach { attachments += it.toString() }
        }
        attachments.forEach { uri ->
            runCatching { grantUriPermission(packageName, Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }

        EmailBridge.compose(to = to, cc = cc, bcc = bcc, subject = subject, body = body, attachmentUris = attachments)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_EMAIL, true)
        })
        finish()
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
