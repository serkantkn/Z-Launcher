package com.serkantkn.zunelauncher.ui.screens.messaging

import android.content.Context
import com.serkantkn.zunelauncher.util.AppLocale
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Activity for SMS compose intent handling required by Android OS for default SMS app role eligibility.
 */
class ComposeSmsActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
