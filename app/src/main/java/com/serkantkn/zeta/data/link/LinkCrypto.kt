package com.serkantkn.zeta.data.link

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The phone link's cryptography, kept small and standard.
 *
 * Pairing: the two sides swap P-256 public keys over the local network and agree a secret
 * (ECDH). The six digits the TV shows are mixed into the key derived from it, and each side
 * proves it holds that key before anything is stored - a device in the middle that swapped its
 * own public keys in would have to guess the digits at the first try, since the TV drops the
 * code after one wrong answer. After pairing every message is sealed with AES-GCM under a key
 * only the two devices have, which is kept wrapped by the Android Keystore.
 */
internal object LinkCrypto {
    private val random = SecureRandom()

    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    fun publicKeyOf(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    fun agree(own: KeyPair, theirs: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(own.private)
            doPhase(theirs, true)
            generateSecret()
        }

    fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            parts.forEach { part ->
                update(intToBytes(part.size))
                update(part)
            }
            digest()
        }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            parts.forEach { update(it) }
            doFinal()
        }

    /** HKDF with SHA-256 (RFC 5869), for a 32-byte key. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        return hmac(prk, info, byteArrayOf(1))
    }

    fun sameBytes(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    /** Six digits, as the TV shows them. */
    fun pairingCode(): String = (random.nextInt(900_000) + 100_000).toString()

    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return iv to cipher.doFinal(plain)
    }

    fun open(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed)
    }

    // ── Keys kept at rest ─────────────────────────────────────────────────────────────────

    /** A pairing's key as stored: wrapped by a Keystore key that never leaves the device. */
    fun wrap(key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return b64(cipher.iv) + ":" + b64(cipher.doFinal(key))
    }

    fun unwrap(stored: String): ByteArray? = runCatching {
        val (iv, data) = stored.split(':').let { unb64(it[0]) to unb64(it[1]) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(data)
    }.getOrNull()

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)

    private fun intToBytes(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private const val KEYSTORE_ALIAS = "z_link_keys"
}
