package com.serkantkn.zunelauncher.ui.screens.messaging

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Activity for SMS compose intent handling required by Android OS for default SMS app role eligibility.
 */
class ComposeSmsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
