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
}
