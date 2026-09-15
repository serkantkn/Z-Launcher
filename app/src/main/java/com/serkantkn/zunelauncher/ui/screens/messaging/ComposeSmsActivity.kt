package com.serkantkn.zunelauncher.ui.screens.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.util.AppLocale
import com.serkantkn.zunelauncher.util.parseSmsLink

/**
 * Another app asking us to write a message.
 *
 * This is what an `sms:` link, a number tapped in a browser, and "share to messages" all arrive
 * as. It has no screen of its own: it works out who the message is for and what it should say,
 * hands both to the Messaging hub, and steps out of the way.
 */
class ComposeSmsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = targetOf(intent?.data)
        val body = target.body.ifBlank { sharedText(intent) }
        MessagingBridge.compose(target.address, body)

        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun targetOf(data: Uri?) =
        if (data == null || data.scheme !in SCHEMES) {
            parseSmsLink("")
        } else {
            parseSmsLink(data.schemeSpecificPart.orEmpty())
        }

    /** Text can also arrive beside the address: as an extra, or as something shared to us. */
    private fun sharedText(intent: Intent?): String =
        intent?.getStringExtra("sms_body")?.takeIf { it.isNotBlank() }
            ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: ""

    private companion object {
        val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")
    }
}
