package com.serkantkn.zunelauncher.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Locks and unlocks a piece of text with a key that never leaves the phone.
 *
 * AES-GCM under a key held by the Android Keystore: the key material is kept by the hardware
 * (or by the platform's own store where there is no secure element), so a copy of the app's
 * files — pulled off a rooted phone, lifted out of a backup, read from a stolen device — is a
 * copy of ciphertext and nothing else. It is not protection against a phone somebody is holding
 * unlocked; that is what the fingerprint prompt in front of a locked note is for.
 *
 * [alias] names the key, so different things can be locked under different keys: the account
 * vault under one, notes under another. A fresh random nonce goes in front of every value, which
 * GCM requires — reusing one with the same key is what breaks it.
 *
 * Stored form is `base64(iv):base64(ciphertext)`, which is what [SecretStore] has always
 * written, so values saved by older builds still open.
 */
class SecretCipher(private val alias: String) {

    /** Returns the locked form of [plain], or null if this phone's keystore would not play. */
    fun seal(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(sealed, Base64.NO_WRAP)
    } catch (e: Exception) {
        ZuneLog.e(TAG, "could not lock a value under $alias", e)
        null
    }

    /**
     * Returns what [sealed] was made from, or null when it cannot be opened — a value from
     * another phone, or one whose key is gone. Callers must treat null as "leave this alone",
     * never as "this was empty".
     */
    fun open(sealed: String): String? = try {
        val (ivPart, dataPart) = sealed.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, Base64.decode(ivPart, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(dataPart, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        ZuneLog.e(TAG, "could not open a value locked under $alias", e)
        null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecretCipher"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
