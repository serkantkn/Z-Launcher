package com.serkantkn.zunelauncher.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.serkantkn.zunelauncher.R
import java.util.Locale

/**
 * Handing an .apk to the system's installer.
 *
 * Since Android 8 the installer will not open for an app that has not been allowed to install
 * unknown apps — and it does not say so; the tap simply does nothing, which is what the Files
 * and Internet hubs used to do with an .apk. So this asks first: if the launcher is not yet
 * allowed, it opens the one setting that allows it, says why, and reports that the install has
 * not started. Tapping the file again after that opens the installer.
 */
object PackageInstall {

    const val MIME = "application/vnd.android.package-archive"

    /** Whether a file is something the installer takes. Split bundles (.apks, .xapk) are not. */
    fun isApk(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) == "apk"

    /**
     * Opens the installer for the package at [uri], a content URI the installer may read.
     * Returns false when the launcher first had to ask to be allowed; the file is not installed
     * in that case and the person is looking at the setting.
     */
    fun open(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(context, R.string.files_install_permission, Toast.LENGTH_LONG).show()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { ZuneLog.w(TAG, "the unknown-sources setting could not be opened", it) }
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    private const val TAG = "PackageInstall"
}
