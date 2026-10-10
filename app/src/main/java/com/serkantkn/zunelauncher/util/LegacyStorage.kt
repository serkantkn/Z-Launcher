package com.serkantkn.zunelauncher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Writing to shared storage before Android 10.
 *
 * From Android 10 the MediaStore takes a file from any app without asking; on Android 9 the same
 * write needs WRITE_EXTERNAL_STORAGE, which the manifest only asks for up to that version. The
 * Files hub has always requested it; the camera, the photo editor and the notes export wrote
 * straight away and failed quietly on a phone that had never opened Files.
 */
object LegacyStorage {

    /** True when a write to shared storage would fail until the permission is granted. */
    fun needsWritePermission(context: Context): Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    /** The permission to ask for when [needsWritePermission] says so. */
    const val WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE
}
