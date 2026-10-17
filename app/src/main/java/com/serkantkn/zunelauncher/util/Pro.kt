package com.serkantkn.zunelauncher.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.BuildConfig
import com.serkantkn.zunelauncher.R

/**
 * What the free build leaves to Pro, in one place.
 *
 * A Pro setting is never thrown away by the free build: it is kept as it was and simply not
 * applied, so whoever moves to Pro finds it the way they left it. Nor is anything of the user's
 * locked away - a locked note still opens, a saved page still reads; only making a new one is Pro.
 */
object Pro {
    val unlocked: Boolean get() = BuildConfig.IS_PREMIUM

    /** Says that this is a Pro feature. */
    fun tell(context: Context, @StringRes message: Int = R.string.pro_only_feature) {
        runCatching { Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show() }
    }

    /** Runs [action] in Pro; in the free build says what it would have needed. */
    inline fun orTell(context: Context, action: () -> Unit) {
        if (unlocked) action() else tell(context)
    }
}
