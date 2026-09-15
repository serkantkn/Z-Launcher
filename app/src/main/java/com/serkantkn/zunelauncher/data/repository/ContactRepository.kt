package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.serkantkn.zunelauncher.data.model.ContactAddress
import com.serkantkn.zunelauncher.data.model.ContactDetailModel
import com.serkantkn.zunelauncher.data.model.ContactEmail
import com.serkantkn.zunelauncher.data.model.ContactEdit
import com.serkantkn.zunelauncher.data.model.ContactGroup
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.ContactNumber
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

    suspend fun getPhoneNumbers(contactId: String): List<String> =
        getNumbers(contactId).map { it.number }

    /**
     * A person's numbers, each with the row it lives on and what kind of number it is.
     *
     * The row id matters: an edit that matches only "this person's phone numbers" writes to all of
     * them at once, which is how a contact with a mobile and a work line ended up with the same
     * number twice.
     */
    suspend fun getNumbers(contactId: String): List<ContactNumber> = withContext(Dispatchers.IO) {
        val numbers = mutableListOf<ContactNumber>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(1)
                if (number.isNullOrBlank()) continue
                if (numbers.any { it.number == number }) continue
                numbers.add(
                    ContactNumber(
                        rowId = cursor.getLong(0),
                        number = number,
                        type = cursor.getInt(2),
                        customLabel = cursor.getString(3)
                    )
                )
            }
        }
        numbers
    }

    /**
     * A person's email addresses.
     *
     * The hub asked for an email when a contact was created and then never showed it again — it
     * was written to the phone's contacts and only visible in some other app.
     */
    suspend fun getEmails(contactId: String): List<ContactEmail> = withContext(Dispatchers.IO) {
        val emails = mutableListOf<ContactEmail>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email._ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.TYPE
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(1)
                if (address.isNullOrBlank()) continue
                if (emails.any { it.address == address }) continue
                emails.add(
                    ContactEmail(
                        rowId = cursor.getLong(0),
                        address = address,
                        type = cursor.getInt(2)
                    )
                )
            }
        }
        emails
    }

    /** Everything about one person — numbers, addresses, birthday, note, groups — in one query. */
    suspend fun getDetail(contact: ContactModel): ContactDetailModel = withContext(Dispatchers.IO) {
        readContactDetail(context, contact)
    }

    // ══════════════════════════════════════════════════════════
    // GROUPS
    // ══════════════════════════════════════════════════════════

    /**
     * The groups the address book keeps, with how many people are in each.
     *
     * The ones Android adds for its own bookkeeping — the automatic "my contacts" and the starred
     * group behind the favourites page — are left out; they are not groups anybody made.
     */
    suspend fun getGroups(): List<ContactGroup> = withContext(Dispatchers.IO) {
        val counts = groupMemberCounts()
        val groups = mutableListOf<ContactGroup>()
        try {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(
                    ContactsContract.Groups._ID,
                    ContactsContract.Groups.TITLE,
                    ContactsContract.Groups.ACCOUNT_NAME
                ),
                "${ContactsContract.Groups.DELETED} = 0 AND " +
                    "${ContactsContract.Groups.AUTO_ADD} = 0 AND " +
                    "${ContactsContract.Groups.FAVORITES} = 0",
                null,
                "${ContactsContract.Groups.TITLE} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val title = cursor.getString(1)
                    if (title.isNullOrBlank()) continue
                    val id = cursor.getLong(0)
                    groups += ContactGroup(id, title, cursor.getString(2), counts[id] ?: 0)
                }
            }
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "getGroups failed", e)
        }
        groups
    }

    private fun groupMemberCounts(): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val groupId = cursor.getLong(0)
                    counts[groupId] = (counts[groupId] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            ZuneLog.w("ContactRepository", "could not count group members", e)
        }
        return counts
    }

    /** Everyone in a group. */
    suspend fun getGroupMembers(groupId: Long): List<String> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<String>()
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ? AND " +
                    "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
                arrayOf(
                    ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                    groupId.toString()
                ),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { if (it !in ids) ids += it }
                }
            }
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "getGroupMembers failed", e)
        }
        ids
    }

    suspend fun createGroup(title: String): Boolean = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext false
        try {
            val account = firstGoogleAccount()
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Groups.TITLE, title.trim())
                put(ContactsContract.Groups.GROUP_VISIBLE, 1)
                put(ContactsContract.Groups.ACCOUNT_NAME, account?.name)
                put(ContactsContract.Groups.ACCOUNT_TYPE, account?.type)
            }
            context.contentResolver.insert(ContactsContract.Groups.CONTENT_URI, values) != null
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "createGroup failed", e)
            false
        }
    }

    /**
     * Removes a group, not the people in it.
     *
     * The row is marked deleted rather than erased, which is what lets the account it belongs to
     * carry the removal to wherever else the address book lives.
     */
    suspend fun deleteGroup(groupId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                ContactsContract.Groups.CONTENT_URI,
                "${ContactsContract.Groups._ID} = ?",
                arrayOf(groupId.toString())
            ) > 0
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "deleteGroup failed", e)
            false
        }
    }

    /** Puts somebody in a group, or takes them out of it. */
    suspend fun setGroupMembership(
        contactId: String,
        groupId: Long,
        member: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (member) {
                val rawContactId = rawContactIdOf(contactId) ?: return@withContext false
                val values = android.content.ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
                    )
                    put(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                }
                context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values) != null
            } else {
                context.contentResolver.delete(
                    ContactsContract.Data.CONTENT_URI,
                    "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                        "${ContactsContract.Data.MIMETYPE} = ? AND " +
                        "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
                    arrayOf(
                        contactId,
                        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                        groupId.toString()
                    )
                ) > 0
            }
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "setGroupMembership failed", e)
            false
        }
    }

    // ══════════════════════════════════════════════════════════
    // LINKING DUPLICATES
    // ══════════════════════════════════════════════════════════

    /**
     * Joins two contacts into one person.
     *
     * The same person saved twice — once from the SIM, once from an account — shows up as two
     * entries. Android keeps them as separate records and decides for itself whether they are the
     * same; this tells it they are, for every pair of records involved.
     */
    suspend fun linkContacts(contactId: String, otherContactId: String): Boolean =
        withContext(Dispatchers.IO) {
            aggregate(
                contactId,
                otherContactId,
                ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER
            )
        }

    /** Splits a joined contact back into the records it was made of. */
    suspend fun unlinkContact(contactId: String): Boolean = withContext(Dispatchers.IO) {
        aggregate(contactId, contactId, ContactsContract.AggregationExceptions.TYPE_KEEP_SEPARATE)
    }

    private fun aggregate(contactId: String, otherContactId: String, type: Int): Boolean = try {
        val left = rawContactIdsOf(contactId)
        val right = if (contactId == otherContactId) left else rawContactIdsOf(otherContactId)
        val pairs = buildList {
            left.forEach { a -> right.forEach { b -> if (a != b) add(minOf(a, b) to maxOf(a, b)) } }
        }.distinct()

        pairs.forEach { (first, second) ->
            val values = android.content.ContentValues().apply {
                put(ContactsContract.AggregationExceptions.TYPE, type)
                put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, first)
                put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, second)
            }
            context.contentResolver.update(
                ContactsContract.AggregationExceptions.CONTENT_URI,
                values,
                null,
                null
            )
        }
        pairs.isNotEmpty()
    } catch (e: Exception) {
        ZuneLog.e("ContactRepository", "aggregate failed", e)
        false
    }

    private fun rawContactIdsOf(contactId: String): List<Long> = try {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }.orEmpty()
    } catch (e: Exception) {
        ZuneLog.w("ContactRepository", "no records for $contactId", e)
        emptyList()
    }

    private fun firstGoogleAccount(): android.accounts.Account? = try {
        android.accounts.AccountManager.get(context).getAccountsByType("com.google").firstOrNull()
    } catch (e: Exception) {
        null
    }

    // ══════════════════════════════════════════════════════════
    // PHOTOGRAPHS
    // ══════════════════════════════════════════════════════════

    /**
     * Gives somebody a photograph.
     *
     * The picture is written to the record's display-photo file, which is the way the address book
     * asks for it: the provider makes its own thumbnail from it, so the small face on a tile and
     * the large one on the card both come out right and both survive a sync.
     */
    suspend fun setPhoto(contactId: String, photo: android.graphics.Bitmap): Boolean =
        withContext(Dispatchers.IO) {
            val rawContactId = rawContactIdOf(contactId) ?: return@withContext false
            try {
                val square = squareOf(photo, PHOTO_EDGE_PX)
                val bytes = java.io.ByteArrayOutputStream().use { out ->
                    square.compress(android.graphics.Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, out)
                    out.toByteArray()
                }
                val displayPhotoUri = Uri.withAppendedPath(
                    android.content.ContentUris.withAppendedId(
                        ContactsContract.RawContacts.CONTENT_URI,
                        rawContactId.toLong()
                    ),
                    ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY
                )
                context.contentResolver.openAssetFileDescriptor(displayPhotoUri, "rw")?.use { file ->
                    file.createOutputStream().use { it.write(bytes) }
                } ?: return@withContext false
                true
            } catch (e: Exception) {
                ZuneLog.e("ContactRepository", "setPhoto failed", e)
                false
            }
        }

    /** Takes somebody's photograph away; the initial goes back in its place. */
    suspend fun removePhoto(contactId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(
                ContactsContract.Data.CONTENT_URI,
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            ) > 0
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "removePhoto failed", e)
            false
        }
    }

    /** The middle of the picture, as a square of the size the address book wants. */
    private fun squareOf(source: android.graphics.Bitmap, edge: Int): android.graphics.Bitmap {
        val side = minOf(source.width, source.height)
        val cropped = if (source.width == source.height) {
            source
        } else {
            android.graphics.Bitmap.createBitmap(
                source,
                (source.width - side) / 2,
                (source.height - side) / 2,
                side,
                side
            )
        }
        return if (cropped.width == edge) {
            cropped
        } else {
            android.graphics.Bitmap.createScaledBitmap(cropped, edge, edge, true)
        }
    }

    /** Stars or unstars a person, which is what the favourites page is made of. */
    suspend fun setFavorite(contactId: String, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (favorite) 1 else 0)
            }
            context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId)
            ) > 0
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "setFavorite failed", e)
            false
        }
    }

    /**
     * Every number in the phone book, by the person it belongs to.
     *
     * One query rather than one per person, because this is used to work out who has been in
     * touch lately and that means looking at all of them at once.
     */
    suspend fun getAllNumbersByContact(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val byContact = mutableMapOf<String, MutableList<String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val contactId = cursor.getString(0) ?: continue
                val number = cursor.getString(1) ?: continue
                if (number.isBlank()) continue
                byContact.getOrPut(contactId) { mutableListOf() }.add(number)
            }
        }
        byContact
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
                    ZuneLog.e("ContactRepository", "saveContact failed", e)
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
            ZuneLog.e("ContactRepository", "saveContact failed", e)
            false
        }
    }

    suspend fun deleteContact(contactId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            rowsDeleted > 0
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "deleteContact failed", e)
            false
        }
    }

    /**
     * Rewrites a person: their name, every number and every email address.
     *
     * A row that came back from [getNumbers] or [getEmails] is written where it already lives; a
     * new one is added; one the user deleted is removed. The old version matched on "this person's
     * phone numbers" without a row, so a contact with two numbers had both replaced by whichever
     * one was typed.
     */
    suspend fun updateContact(contactId: String, edit: ContactEdit): Boolean =
        withContext(Dispatchers.IO) {
        try {
            val firstName = edit.firstName
            val lastName = edit.lastName
            val numbers = edit.numbers
            val emails = edit.emails
            val ops = ArrayList<android.content.ContentProviderOperation>()
            val fullName = "$firstName $lastName".trim()

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

            val rawContactId = rawContactIdOf(contactId)

            // ── Numbers ──
            val keptNumbers = numbers.filter { it.number.isNotBlank() }
            getNumbers(contactId).forEach { existing ->
                if (keptNumbers.none { it.rowId == existing.rowId }) {
                    ops.add(deleteRow(existing.rowId))
                }
            }
            keptNumbers.forEach { number ->
                if (number.rowId > 0L) {
                    ops.add(
                        android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(number.rowId.toString()))
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number.number)
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, number.type)
                            .build()
                    )
                } else if (rawContactId != null) {
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number.number)
                            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, number.type)
                            .build()
                    )
                }
            }

            // ── Email addresses ──
            val keptEmails = emails.filter { it.address.isNotBlank() }
            getEmails(contactId).forEach { existing ->
                if (keptEmails.none { it.rowId == existing.rowId }) {
                    ops.add(deleteRow(existing.rowId))
                }
            }
            keptEmails.forEach { email ->
                if (email.rowId > 0L) {
                    ops.add(
                        android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(email.rowId.toString()))
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, email.type)
                            .build()
                    )
                } else if (rawContactId != null) {
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, email.type)
                            .build()
                    )
                }
            }

            // ── Addresses ──
            val existing = readContactDetail(context, ContactModel(contactId, "", null, false, 0, false))
            val keptAddresses = edit.addresses.filter { it.address.isNotBlank() }
            existing.addresses.forEach { old ->
                if (keptAddresses.none { it.rowId == old.rowId }) ops.add(deleteRow(old.rowId))
            }
            keptAddresses.forEach { address ->
                if (address.rowId > 0L) {
                    ops.add(
                        android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(address.rowId.toString()))
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address.address)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, address.type)
                            .build()
                    )
                } else if (rawContactId != null) {
                    ops.add(
                        android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address.address)
                            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, address.type)
                            .build()
                    )
                }
            }

            // ── The fields there is only ever one of ──
            upsertSingle(
                ops, contactId, rawContactId,
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                mapOf(ContactsContract.CommonDataKinds.Note.NOTE to edit.note),
                edit.note.isNotBlank()
            )
            upsertSingle(
                ops, contactId, rawContactId,
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                mapOf(
                    ContactsContract.CommonDataKinds.Organization.COMPANY to edit.organization,
                    ContactsContract.CommonDataKinds.Organization.TITLE to edit.jobTitle
                ),
                edit.organization.isNotBlank() || edit.jobTitle.isNotBlank()
            )
            upsertSingle(
                ops, contactId, rawContactId,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                mapOf(
                    ContactsContract.CommonDataKinds.Event.START_DATE to edit.birthday,
                    ContactsContract.CommonDataKinds.Event.TYPE to
                        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
                ),
                edit.birthday.isNotBlank(),
                extraSelection = "${ContactsContract.CommonDataKinds.Event.TYPE} = " +
                    "${ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY}"
            )

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            ZuneLog.e("ContactRepository", "updateContact failed", e)
            false
        }
    }

    /**
     * A field a person only has one of: written where it already is, added if it is new, removed
     * if the user cleared it.
     */
    private fun upsertSingle(
        ops: MutableList<android.content.ContentProviderOperation>,
        contactId: String,
        rawContactId: String?,
        mimeType: String,
        values: Map<String, String>,
        wanted: Boolean,
        extraSelection: String? = null
    ) {
        val selection = buildString {
            append("${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?")
            if (extraSelection != null) append(" AND $extraSelection")
        }
        val args = arrayOf(contactId, mimeType)

        if (!wanted) {
            ops.add(
                android.content.ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(selection, args)
                    .build()
            )
            return
        }

        val exists = try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                selection,
                args,
                null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }

        if (exists) {
            val builder = android.content.ContentProviderOperation
                .newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(selection, args)
            values.forEach { (column, value) -> builder.withValue(column, value) }
            ops.add(builder.build())
        } else if (rawContactId != null) {
            val builder = android.content.ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValue(ContactsContract.Data.MIMETYPE, mimeType)
            values.forEach { (column, value) -> builder.withValue(column, value) }
            ops.add(builder.build())
        }
    }

    private fun deleteRow(rowId: Long): android.content.ContentProviderOperation =
        android.content.ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
            .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(rowId.toString()))
            .build()

    /** The writable record behind an aggregated contact; new rows have to hang off one. */
    private fun rawContactIdOf(contactId: String): String? = try {
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) {
        ZuneLog.w("ContactRepository", "no raw contact for $contactId", e)
        null
    }

    private companion object {
        /** Big enough for the card, small enough to travel with an account. */
        const val PHOTO_EDGE_PX = 512
        const val PHOTO_QUALITY = 92
    }
}
