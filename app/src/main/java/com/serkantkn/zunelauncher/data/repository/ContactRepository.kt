package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.serkantkn.zunelauncher.data.model.ContactModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(private val context: Context) {

    suspend fun getContacts(): List<ContactModel> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactModel>()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED,
            ContactsContract.Contacts.LAST_TIME_CONTACTED,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
            val starredIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
            val lastContactedIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LAST_TIME_CONTACTED)
            val hasPhoneIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                if (name.isNullOrBlank()) continue // Skip contacts without a name

                val photoStr = cursor.getString(photoIndex)
                val isFavorite = cursor.getInt(starredIndex) > 0
                val lastContacted = cursor.getLong(lastContactedIndex)
                val hasPhone = cursor.getInt(hasPhoneIndex) > 0

                contacts.add(
                    ContactModel(
                        id = id,
                        name = name,
                        photoUri = if (photoStr != null) Uri.parse(photoStr) else null,
                        isFavorite = isFavorite,
                        lastTimeContacted = lastContacted,
                        hasPhoneNumber = hasPhone
                    )
                )
            }
        }
        contacts
    }

    suspend fun getPhoneNumbers(contactId: String): List<String> = withContext(Dispatchers.IO) {
        val numbers = mutableListOf<String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId)

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val num = cursor.getString(numberIndex)
                if (!num.isNullOrBlank() && !numbers.contains(num)) {
                    numbers.add(num)
                }
            }
        }
        numbers
    }

    suspend fun getContactsWithNumbers(): List<Pair<ContactModel, String>> = withContext(Dispatchers.IO) {
        val contactsWithNumbers = mutableListOf<Pair<ContactModel, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"

        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                val photoStr = cursor.getString(photoIndex)
                val number = cursor.getString(numberIndex)

                if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                    val model = ContactModel(
                        id = id,
                        name = name,
                        photoUri = if (photoStr != null) Uri.parse(photoStr) else null,
                        isFavorite = false,
                        lastTimeContacted = 0,
                        hasPhoneNumber = true
                    )
                    contactsWithNumbers.add(model to number)
                }
            }
        }
        
        // Return unique contacts (first number found)
        contactsWithNumbers.distinctBy { it.first.id }
    }

    suspend fun saveContact(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        email: String,
        saveToGoogle: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<android.content.ContentProviderOperation>()

            var accountName: String? = null
            var accountType: String? = null

            if (saveToGoogle) {
                try {
                    val accounts = android.accounts.AccountManager.get(context).getAccountsByType("com.google")
                    if (accounts.isNotEmpty()) {
                        accountName = accounts[0].name
                        accountType = accounts[0].type
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            var builder = android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            if (accountType != null && accountName != null) {
                builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                       .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            } else {
                builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                       .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            }
            ops.add(builder.build())

            // Name
            val fullName = "$firstName $lastName".trim()
            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, fullName)
                    .build()
            )

            // Phone Number
            if (phoneNumber.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }

            // Email Address
            if (email.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteContact(contactId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            rowsDeleted > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateContact(
        contactId: String,
        firstName: String,
        lastName: String,
        phoneNumber: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<android.content.ContentProviderOperation>()
            val fullName = "$firstName $lastName".trim()

            // Update Name
            ops.add(
                android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, firstName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, lastName)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, fullName)
                    .build()
            )

            // Update Phone Number
            if (phoneNumber.isNotBlank()) {
                val cursor = context.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                    null
                )
                val phoneDataExists = cursor?.use { it.count > 0 } ?: false

                if (phoneDataExists) {
                    ops.add(
                        android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection(
                                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                                arrayOf(contactId, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            )
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                            .build()
                    )
                } else {
                    val rawContactIdCursor = context.contentResolver.query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        arrayOf(ContactsContract.RawContacts._ID),
                        "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )
                    var rawContactId: String? = null
                    rawContactIdCursor?.use {
                        if (it.moveToFirst()) {
                            rawContactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
                        }
                    }

                    if (rawContactId != null) {
                        ops.add(
                            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                                .build()
                        )
                    }
                }
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
