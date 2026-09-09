package com.serkantkn.zunelauncher.ui.keyboard

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * An input method cannot ask for a runtime permission itself, so the dictation panel starts this
 * invisible activity, which requests the microphone and closes again straight away.
 */
class KeyboardMicPermissionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            ZuneLog.d(TAG, "microphone permission granted=$granted")
            finish()
        }
        try {
            request.launch(Manifest.permission.RECORD_AUDIO)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "could not ask for the microphone permission", e)
            finish()
        }
    }

    private companion object {
        const val TAG = "KeyboardMicPermission"
    }
}
