package com.serkantkn.zunelauncher.data.model

import android.net.Uri

data class ContactModel(
    val id: String,
    val name: String,
    val photoUri: Uri?,
    val isFavorite: Boolean,
    val lastTimeContacted: Long,
    val hasPhoneNumber: Boolean
)

data class ContactDetailModel(
    val contact: ContactModel,
    val phoneNumbers: List<String>
)
