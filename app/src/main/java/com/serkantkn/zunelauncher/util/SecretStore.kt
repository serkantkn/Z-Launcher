package com.serkantkn.zunelauncher.util

import android.content.Context

/**
 * Small secret vault for account passwords: AES-GCM with a key that lives in the Android
 * Keystore (never leaves the device, not backed up). Values are stored as base64 "iv:cipher"
 * in a private SharedPreferences file.
 *
 * The locking itself is [SecretCipher], which the notes hub uses as well under a key of its own.
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cipher = SecretCipher(ALIAS)

    fun put(id: String, secret: String) {
        val sealed = cipher.seal(secret) ?: return
        prefs.edit().putString(id, sealed).apply()
    }

    fun get(id: String): String? = prefs.getString(id, null)?.let { cipher.open(it) }

    fun remove(id: String) {
        prefs.edit().remove(id).apply()
    }

    private companion object {
        const val PREFS = "zune_secrets"
        const val ALIAS = "zune_secret_key"
    }
}
