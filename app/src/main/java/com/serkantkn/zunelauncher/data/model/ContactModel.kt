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
 * One number of one person, for lists that are about numbers rather than people: the dialler's
 * matches, the speed dial picker, the new-message picker. Somebody with a home and a work line
 * is two rows here, each saying which is which.
 */
data class ContactPhone(
    val contact: ContactModel,
    val number: String,
    /** "mobile", "work", or a custom label — as the phone's own settings would name it. */
    val typeLabel: String = ""
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

/**
 * A name as the address book keeps it: in parts, not as one string. "Dr Ayşe Nur Kaya" is a
 * prefix, a given name, a middle name and a family name, and editing it by splitting the display
 * name on the first space would have made "Ayşe" the whole first name and "Nur Kaya" the last.
 */
data class StructuredName(
    val given: String = "",
    val family: String = "",
    val middle: String = "",
    val prefix: String = "",
    val suffix: String = ""
) {
    val isEmpty: Boolean get() = given.isBlank() && family.isBlank() && middle.isBlank() && prefix.isBlank() && suffix.isBlank()
}

/** Everything an edit can change about a person, gathered so the call is not ten arguments long. */
data class ContactEdit(
    val firstName: String,
    val lastName: String,
    /**
     * The name in full, or null when the name was not touched — in which case nothing about it
     * is written back, and a middle name or a title the editor never showed is left as it was.
     */
    val name: StructuredName? = StructuredName(given = firstName, family = lastName),
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
    /** The name in its parts, for the editor; empty when the record has no structured name row. */
    val name: StructuredName = StructuredName(),
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
