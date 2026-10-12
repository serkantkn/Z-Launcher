package com.serkantkn.zunelauncher.ui.screens.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ContactPhone
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.PhoneNumbers
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.mutableStateListOf
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen

/**
 * Who the new message is for.
 *
 * The old picker could only offer people already in the address book, which left no way at all to
 * write to a number that is not in it — a courier, a code, somebody met an hour ago. What is typed
 * here is both the filter over the address book and, when it looks like a number, a recipient in
 * its own right.
 *
 * Tapping a name writes to that one person straight away, as it always did. The ring beside each
 * name gathers several instead: once one is gathered, tapping further names adds them too, and
 * "write" opens one conversation with all of them.
 *
 * On a tablet it fills the pane beside the list rather than the whole screen, so it draws no
 * ground of its own and closes with a ringed button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NewMessageScreen(
    contacts: List<ContactPhone>,
    onClose: () -> Unit,
    onRecipientsChosen: (List<Pair<String, String>>) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val wide = LocalIsWideScreen.current
    var query by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<Pair<String, String>>() }

    fun isChosen(number: String) = chosen.any { PhoneNumbers.sameNumber(it.second, number) }
    fun toggle(name: String, number: String) {
        val at = chosen.indexOfFirst { PhoneNumbers.sameNumber(it.second, number) }
        if (at >= 0) chosen.removeAt(at) else chosen += name to number
    }
    // A tap on a name: straight to that person, unless several are being gathered.
    fun pick(name: String, number: String) {
        if (chosen.isEmpty()) onRecipientsChosen(listOf(name to number)) else toggle(name, number)
    }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) {
            contacts
        } else {
            val digits = PhoneNumbers.digitsOf(query)
            contacts.filter { entry ->
                entry.contact.name.contains(query, ignoreCase = true) ||
                    (digits.isNotEmpty() && PhoneNumbers.digitsOf(entry.number).contains(digits))
            }
        }
    }

    // Enough digits to be a phone number rather than a slip of the finger.
    val typedNumber = remember(query) {
        val dialable = PhoneNumbers.toDialable(query)
        dialable.takeIf { PhoneNumbers.digitsOf(it).length >= MINIMUM_DIGITS }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = when {
            wide -> Color.Transparent
            zuneColors.isDark -> Color(0xFF0F0F0F)
            else -> Color(0xFFFAFAFA)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (wide) Modifier else Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.msg_new),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 36.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (wide) {
                    RingButton(Icons.Default.Close, stringResource(R.string.common_close_cap), enabled = true, onClick = onClose)
                } else {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close_cap),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MessageSearchBar(
                query = query,
                onQueryChange = { query = it },
                isVisible = true,
                modifier = Modifier.padding(bottom = 12.dp),
                hint = stringResource(R.string.msg_recipient_hint)
            )

            // ── Who is gathered so far ──
            if (chosen.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.msg_recipients),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.textMuted
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)
                ) {
                    chosen.forEach { (name, number) ->
                        Text(
                            text = name.ifBlank { number },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier
                                .background(zuneColors.accentColor)
                                .clickable { toggle(name, number) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .border(2.dp, zuneColors.accentColor)
                        .clickable { onRecipientsChosen(chosen.toList()) }
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = stringResource(R.string.msg_write_to),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.msg_group_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (typedNumber != null) {
                    item(key = "typed") {
                        RecipientRow(
                            name = "#",
                            photoUri = null,
                            title = typedNumber,
                            subtitle = stringResource(R.string.msg_start_conversation),
                            subtitleAccent = true,
                            chosen = isChosen(typedNumber),
                            onClick = { pick(typedNumber, typedNumber) },
                            onToggle = { toggle(typedNumber, typedNumber) }
                        )
                    }
                }

                items(filtered, key = { entry -> "${entry.contact.id}-${entry.number}" }) { entry ->
                    val contact = entry.contact
                    // Which of their numbers, for somebody who has more than one.
                    val label = entry.typeLabel.lowercase(java.util.Locale.getDefault())
                    RecipientRow(
                        name = contact.name,
                        photoUri = contact.photoUri?.toString(),
                        title = contact.name,
                        subtitle = if (label.isBlank()) entry.number else "${entry.number} · $label",
                        subtitleAccent = false,
                        chosen = isChosen(entry.number),
                        onClick = { pick(contact.name, entry.number) },
                        onToggle = { toggle(contact.name, entry.number) }
                    )
                }

                if (filtered.isEmpty() && typedNumber == null) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.common_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = zuneColors.textDim
                        )
                    }
                }
            }
        }
    }
}

/** One person to write to, and the ring that gathers them into a message to several. */
@Composable
private fun RecipientRow(
    name: String,
    photoUri: String?,
    title: String,
    subtitle: String,
    subtitleAccent: Boolean,
    chosen: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chosen) SelectionBox(checked = true) else MessageAvatar(name = name, photoUri = photoUri)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (subtitleAccent) zuneColors.accentColor else zuneColors.textMuted
            )
        }
        RingButton(
            icon = if (chosen) Icons.Default.Check else Icons.Default.Add,
            label = stringResource(if (chosen) R.string.msg_remove_recipient else R.string.msg_add_recipient),
            enabled = true,
            size = 34.dp,
            onClick = onToggle
        )
    }
}

/** Shorter than this and it is not a number anybody could be reached on. */
private const val MINIMUM_DIGITS = 3
