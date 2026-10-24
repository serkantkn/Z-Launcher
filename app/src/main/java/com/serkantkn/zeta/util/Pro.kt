package com.serkantkn.zeta.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.serkantkn.zeta.BuildConfig
import com.serkantkn.zeta.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the free build leaves to Pro, in one place.
 *
 * Pro is bought once inside the app, through Google Play (see ProStore); the premium flavour is
 * always Pro, and an install from Play is Pro for its first ten days (ProTrial). Whether it is Pro can change while the launcher runs - a purchase, a refund - so it
 * is held twice over: as Compose state, so anything drawn reading [unlocked] is drawn again, and
 * as [flow], for the settings and repositories that combine with it.
 *
 * A Pro setting is never thrown away by the free build: it is kept as it was and simply not
 * applied, so whoever buys Pro finds it the way they left it. Nor is anything of the user's
 * locked away - a locked note still opens, a saved page still reads; only making a new one is Pro.
 */
object Pro {
    private val _flow = MutableStateFlow(BuildConfig.IS_PREMIUM)

    /** Pro or not, for whatever combines with it. */
    val flow: StateFlow<Boolean> = _flow.asStateFlow()

    private var state by mutableStateOf(BuildConfig.IS_PREMIUM)

    /** Pro or not, right now. Read while drawing, it draws again when that changes. */
    val unlocked: Boolean get() = state

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var bought = BuildConfig.IS_PREMIUM
    @Volatile private var trying = false

    /** Bought (or the premium flavour): Pro for good, not for a trial's ten days. */
    val purchased: Boolean get() = bought

    /** Whether Pro has been bought; the premium flavour is Pro whatever this says. */
    fun setPurchased(purchased: Boolean) {
        bought = BuildConfig.IS_PREMIUM || purchased
        publish()
    }

    /** Whether the trial's ten days are running (ProTrial). */
    fun setTrial(running: Boolean) {
        trying = running
        publish()
    }

    private fun publish() {
        val value = bought || trying
        _flow.value = value
        if (Looper.myLooper() == Looper.getMainLooper()) state = value else main.post { state = value }
    }

    /**
     * Says that this is a Pro feature, and where to get it: the Pro page, with [message] - what
     * was asked for - at its top.
     */
    fun tell(context: Context, @StringRes message: Int = R.string.pro_only_feature) {
        runCatching {
            val intent = Intent(context, com.serkantkn.zeta.ui.pro.ProActivity::class.java)
                .putExtra(com.serkantkn.zeta.ui.pro.ProActivity.EXTRA_REASON, message)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** Runs [action] in Pro; otherwise says what it would have needed. */
    inline fun orTell(context: Context, action: () -> Unit) {
        if (unlocked) action() else tell(context)
    }
}
