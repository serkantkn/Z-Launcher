package com.serkantkn.zunelauncher.data.model

import android.net.Uri
import android.provider.ContactsContract

data class ContactModel(
    val id: String,
    val name: String,
    val photoUri: Uri?,
    val isFavorite: Boolean,
    val lastTimeContacted: Long,
    val hasPhoneNumber: Boolean
)

/**
 * One of a person's numbers.
 *
 * [rowId] is what makes an edit land on the right line: rewriting a contact by matching only the
 * person and "this is a phone number" overwrote every number they had with the same one.
 */
data class ContactNumber(
    val rowId: Long = 0L,
    val number: String,
    val type: Int = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
    val customLabel: String? = null
)

/** One of a person's email addresses. */
data class ContactEmail(
    val rowId: Long = 0L,
    val address: String,
    val type: Int = ContactsContract.CommonDataKinds.Email.TYPE_HOME
)

/** Somewhere a person lives or works. */
data class ContactAddress(
    val rowId: Long = 0L,
    val address: String,
    val type: Int = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
)

/** A contact group, as the phone's own address book keeps them. */
data class ContactGroup(
    val id: Long,
    val title: String,
    val accountName: String? = null,
    val memberCount: Int = 0
)

/** Everything an edit can change about a person, gathered so the call is not ten arguments long. */
data class ContactEdit(
    val firstName: String,
    val lastName: String,
    val numbers: List<ContactNumber> = emptyList(),
    val emails: List<ContactEmail> = emptyList(),
    val addresses: List<ContactAddress> = emptyList(),
    val birthday: String = "",
    val note: String = "",
    val organization: String = "",
    val jobTitle: String = ""
)

data class ContactDetailModel(
    val contact: ContactModel,
    val numbers: List<ContactNumber> = emptyList(),
    val emails: List<ContactEmail> = emptyList(),
    val addresses: List<ContactAddress> = emptyList(),
    /** As the address book stores it: "1986-04-03", or "--04-03" when the year is unknown. */
    val birthday: String = "",
    val note: String = "",
    val organization: String = "",
    val jobTitle: String = "",
    /** The groups this person belongs to. */
    val groupIds: List<Long> = emptyList(),
    /**
     * How many separate records the phone has joined together into this one person. More than one
     * means the contact is already linked; one means there may still be a duplicate about.
     */
    val linkedRecords: Int = 1
) {
    /** The numbers alone, for everything that only wants to dial or match them. */
    val phoneNumbers: List<String> get() = numbers.map { it.number }

    val hasExtraDetails: Boolean
        get() = addresses.isNotEmpty() || birthday.isNotBlank() || note.isNotBlank() ||
            organization.isNotBlank() || jobTitle.isNotBlank()
}
