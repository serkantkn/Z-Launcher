package com.serkantkn.zunelauncher.ui.screens.files

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.serkantkn.zunelauncher.data.repository.CloudAuthBridge
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * Invisible landing pad for the Microsoft sign-in: the browser sends the authorization code back
 * to zunelauncher://oauth/microsoft, this activity picks it up, hands it to [CloudAuthBridge] and
 * closes again. The Files hub finishes the exchange and shows the result.
 */
class CloudAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
        finish()
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        val code = data.getQueryParameter("code")
        val error = data.getQueryParameter("error_description") ?: data.getQueryParameter("error")
        when {
            !code.isNullOrBlank() -> CloudAuthBridge.onCode(code)
            !error.isNullOrBlank() -> {
                ZuneLog.w(TAG, "sign-in returned an error: $error")
                CloudAuthBridge.onError(error)
            }
            else -> ZuneLog.w(TAG, "redirect without a code: $data")
        }
    }

    private companion object {
        const val TAG = "CloudAuthActivity"
    }
}
