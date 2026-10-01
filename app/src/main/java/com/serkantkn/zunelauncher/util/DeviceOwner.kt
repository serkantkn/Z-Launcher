package com.serkantkn.zunelauncher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Whose tablet this is, as the phone's own "me" contact says: the name and the picture the
 * Windows 8 Start screen kept in its top-right corner.
 *
 * Reading the profile needs the contacts permission the People hub already asks for; without it
 * both parts are null and the corner shows a plain person instead, which is what Windows did for
 * an account with no picture.
 */
data class DeviceOwner(val name: String? = null, val photo: Bitmap? = null) {
    companion object {
        val NONE = DeviceOwner()

        suspend fun load(context: Context): DeviceOwner = withContext(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED
            ) return@withContext NONE
            runCatching {
                context.contentResolver.query(
                    ContactsContract.Profile.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Profile.DISPLAY_NAME,
                        ContactsContract.Profile.PHOTO_URI
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use NONE
                    val name = cursor.getString(0)?.takeIf { it.isNotBlank() }
                    val photo = cursor.getString(1)?.let { uri -> loadPhoto(context, Uri.parse(uri)) }
                    DeviceOwner(name, photo)
                } ?: NONE
            }.getOrElse {
                ZuneLog.w(TAG, "the owner's profile could not be read", it)
                NONE
            }
        }

        private fun loadPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        private const val TAG = "DeviceOwner"
    }
}
