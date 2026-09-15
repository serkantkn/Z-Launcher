package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.serkantkn.zunelauncher.data.model.ContactAddress
import com.serkantkn.zunelauncher.data.model.ContactDetailModel
import com.serkantkn.zunelauncher.data.model.ContactEmail
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.ContactNumber
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * Everything the address book knows about one person, in a single query.
 *
 * Contacts keeps numbers, addresses, birthdays, notes and the rest in one table, told apart by a
 * MIME type. Asking for each kind separately means a query per kind for one card; asking once and
 * sorting the rows out here is a single trip to the provider.
 */
internal fun readContactDetail(context: Context, contact: ContactModel): ContactDetailModel {
    val numbers = mutableListOf<ContactNumber>()
    val emails = mutableListOf<ContactEmail>()
    val addresses = mutableListOf<ContactAddress>()
    val groupIds = mutableListOf<Long>()
    var birthday = ""
    var note = ""
    var organization = ""
    var jobTitle = ""
    val rawContacts = mutableSetOf<Long>()

    try {
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.DATA4
            ),
            "${ContactsContract.Data.CONTACT_ID} = ?",
            arrayOf(contact.id),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val mimeType = cursor.getString(1) ?: continue
                rawContacts.add(cursor.getLong(2))
                val data1 = cursor.getString(3)
                val data2 = cursor.getInt(4)
                val data3 = cursor.getString(5)
                val data4 = cursor.getString(6)

                when (mimeType) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank() && numbers.none { it.number == data1 }) {
                            numbers += ContactNumber(rowId, data1, data2, data3)
                        }
                    }

                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank() && emails.none { it.address == data1 }) {
                            emails += ContactEmail(rowId, data1, data2)
                        }
                    }

                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank() && addresses.none { it.address == data1 }) {
                            addresses += ContactAddress(rowId, data1, data2)
                        }
                    }

                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                        if (data2 == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY &&
                            !data1.isNullOrBlank()
                        ) {
                            birthday = data1
                        }
                    }

                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank()) note = data1
                    }

                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        if (!data1.isNullOrBlank()) organization = data1
                        if (!data4.isNullOrBlank()) jobTitle = data4
                    }

                    ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> {
                        data1?.toLongOrNull()?.let { groupIds += it }
                    }
                }
            }
        }
    } catch (e: Exception) {
        ZuneLog.e(TAG, "could not read ${contact.id}", e)
    }

    return ContactDetailModel(
        contact = contact,
        numbers = numbers,
        emails = emails,
        addresses = addresses,
        birthday = birthday,
        note = note,
        organization = organization,
        jobTitle = jobTitle,
        groupIds = groupIds.distinct(),
        linkedRecords = rawContacts.size.coerceAtLeast(1)
    )
}

private const val TAG = "ContactDetailReader"
