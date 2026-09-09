package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import org.json.JSONObject

/**
 * Cloud storage in the Files hub talks to the services directly over their own web APIs, so a
 * service works whether or not its Android app is installed: the user signs in once with Google
 * or Microsoft, the launcher keeps the OAuth tokens and browses the drive itself.
 */

/** A service the hub can sign in to. */
enum class CloudService(
    val id: String,
    @StringRes val titleRes: Int
) {
    GOOGLE_DRIVE("google_drive", R.string.cloud_service_google),
    ONEDRIVE("onedrive", R.string.cloud_service_microsoft);

    companion object {
        fun fromId(id: String?): CloudService? = entries.firstOrNull { it.id == id }
    }
}

/** A signed-in account. Tokens never live here; they are kept in the encrypted secret store. */
data class CloudAccount(
    val id: String,
    val service: CloudService,
    val displayName: String,
    val email: String
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("service", service.id)
        .put("displayName", displayName)
        .put("email", email)

    companion object {
        fun fromJson(json: JSONObject): CloudAccount? {
            val service = CloudService.fromId(json.optString("service")) ?: return null
            val id = json.optString("id").ifEmpty { return null }
            return CloudAccount(
                id = id,
                service = service,
                displayName = json.optString("displayName").ifEmpty { json.optString("email") },
                email = json.optString("email")
            )
        }
    }
}

/** One file or folder on a drive. [id] is what the service calls it. */
data class CloudItem(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    /** Google Docs, Sheets and Slides have no bytes of their own; they are exported instead. */
    val isGoogleDocument: Boolean = false
)

/** One step of the path inside a drive, used by the breadcrumb and the back gesture. */
data class CloudCrumb(val id: String, val title: String)

/** A failure worth showing to the user, with the localized text to show. */
class CloudException(
    @StringRes val messageRes: Int,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(detail, cause)
