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
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.PhoneNumbers

/**
 * Who the new message is for.
 *
 * The old picker could only offer people already in the address book, which left no way at all to
 * write to a number that is not in it — a courier, a code, somebody met an hour ago. What is typed
 * here is both the filter over the address book and, when it looks like a number, a recipient in
 * its own right.
 */
@Composable
internal fun NewMessageScreen(
    contacts: List<Pair<ContactModel, String>>,
    onClose: () -> Unit,
    onRecipientChosen: (name: String, number: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    var query by remember { mutableStateOf("") }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) {
            contacts
        } else {
            val digits = PhoneNumbers.digitsOf(query)
            contacts.filter { (contact, number) ->
                contact.name.contains(query, ignoreCase = true) ||
                    (digits.isNotEmpty() && PhoneNumbers.digitsOf(number).contains(digits))
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
        color = if (zuneColors.isDark) Color(0xFF0F0F0F) else Color(0xFFFAFAFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
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
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close_cap),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            MessageSearchBar(
                query = query,
                onQueryChange = { query = it },
                isVisible = true,
                modifier = Modifier.padding(bottom = 16.dp),
                hint = stringResource(R.string.msg_recipient_hint)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (typedNumber != null) {
                    item(key = "typed") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRecipientChosen(typedNumber, typedNumber) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MessageAvatar(name = "#", photoUri = null, seed = typedNumber)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = typedNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.msg_start_conversation),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = zuneColors.accentColor
                                )
                            }
                        }
                    }
                }

                items(filtered, key = { (contact, number) -> "${contact.id}-$number" }) { (contact, number) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecipientChosen(contact.name, number) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MessageAvatar(
                            name = contact.name,
                            photoUri = contact.photoUri?.toString(),
                            seed = contact.id
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = number,
                                style = MaterialTheme.typography.bodyMedium,
                                color = zuneColors.textMuted
                            )
                        }
                    }
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

/** Shorter than this and it is not a number anybody could be reached on. */
private const val MINIMUM_DIGITS = 3
