package com.serkantkn.zeta.hubkit

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Telling Zeta from an app that only says it is Zeta. */
internal object ZetaIdentity {
    val PACKAGES = listOf("com.serkantkn.zeta", "com.serkantkn.zeta.dev")

    /** The SHA-256 of the certificate Zeta's releases are signed with. */
    private val CERTIFICATES = setOf("4554a00ac45cf021b3be9fd37d301c8b8193952d0239a9fc3b1316dcab41a986")

    /** Whether [packageName] is Zeta: signed with Zeta's key, or any Zeta build when this app is debuggable. */
    fun isZeta(context: Context, packageName: String): Boolean {
        if (packageName !in PACKAGES) return false
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return true
        return runCatching {
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return false
            val signers = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            signers.any { sha256(it.toByteArray()) in CERTIFICATES }
        }.getOrDefault(false)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
