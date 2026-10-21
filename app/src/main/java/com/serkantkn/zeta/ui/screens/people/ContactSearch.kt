package com.serkantkn.zeta.ui.screens.people

import com.serkantkn.zeta.data.model.ContactModel
import com.serkantkn.zeta.util.matchScore
import java.text.Collator
import java.util.Locale

/**
 * The people that answer [query], best first: a name that is what was typed, then one that starts
 * with it, then one with a word that starts with it, then one with it somewhere inside - scored as
 * the app list scores apps, Turkish letters and all - and last of all somebody whose number holds
 * the digits typed. Alphabetical order would put the one being looked for wherever their name
 * happens to fall, which is as often the bottom of the list as the top.
 *
 * [numbersOf] gives a person's numbers as bare digits.
 */
internal fun rankContacts(
    contacts: List<ContactModel>,
    query: String,
    numbersOf: (String) -> List<String>,
    locale: Locale = Locale.getDefault()
): List<ContactModel> {
    if (query.isBlank()) return emptyList()
    // Typing digits should find the person those digits belong to, not nothing.
    val digits = query.filter { it.isDigit() }
    val collator = Collator.getInstance(locale)
    return contacts
        .map { contact ->
            val byName = matchScore(contact.name, "", query)
            val score = when {
                byName > 0 -> byName
                digits.length >= 3 && numbersOf(contact.id).any { it.contains(digits) } -> NUMBER_SCORE
                else -> 0
            }
            contact to score
        }
        .filter { (_, score) -> score > 0 }
        // Same score, the language's own order, so the list does not shuffle as you type.
        .sortedWith(
            compareByDescending<Pair<ContactModel, Int>> { it.second }
                .thenBy(collator) { it.first.name }
        )
        .map { (contact, _) -> contact }
}

private const val NUMBER_SCORE = 200
