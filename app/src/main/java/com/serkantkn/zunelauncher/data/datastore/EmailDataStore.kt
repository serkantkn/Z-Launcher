package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.EmailAccount
import com.serkantkn.zunelauncher.data.model.EmailDraft
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.emailDataStore: DataStore<Preferences> by preferencesDataStore(name = "email")

/**
 * Accounts, local drafts and the selected mailbox of the Email hub. Same convention as the
 * other stores: org.json via the model serializers, read-only Flows, suspend writers.
 * Passwords are NOT here (see SecretStore); cached messages live in EmailCache files.
 */
class EmailDataStore(private val context: Context) {

    private val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")
    private val DRAFTS_KEY = stringPreferencesKey("drafts_json")
    private val SELECTED_ACCOUNT_KEY = stringPreferencesKey("selected_account_id")

    val accountsFlow: Flow<List<EmailAccount>> = context.emailDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[ACCOUNTS_KEY], TAG, EmailAccount::fromJson)
    }

    val draftsFlow: Flow<List<EmailDraft>> = context.emailDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[DRAFTS_KEY], TAG, EmailDraft::fromJson)
    }

    /** Selected account id; "" means the unified inbox, null means "not chosen yet". */
    val selectedAccountIdFlow: Flow<String?> = context.emailDataStore.data.map { it[SELECTED_ACCOUNT_KEY] }

    suspend fun saveAccounts(accounts: List<EmailAccount>) {
        context.emailDataStore.edit { it[ACCOUNTS_KEY] = accounts.toJsonArrayString { a -> a.toJson() } }
    }

    suspend fun upsertAccount(account: EmailAccount) {
        val current = accountsFlow.first()
        val updated = if (current.any { it.id == account.id }) current.map { if (it.id == account.id) account else it } else current + account
        saveAccounts(updated)
    }

    suspend fun removeAccount(accountId: String) {
        saveAccounts(accountsFlow.first().filter { it.id != accountId })
        saveDrafts(draftsFlow.first().filter { it.accountId != accountId })
    }

    suspend fun saveDrafts(drafts: List<EmailDraft>) {
        context.emailDataStore.edit { it[DRAFTS_KEY] = drafts.toJsonArrayString { d -> d.toJson() } }
    }

    suspend fun upsertDraft(draft: EmailDraft) {
        val current = draftsFlow.first()
        val updated = if (current.any { it.id == draft.id }) current.map { if (it.id == draft.id) draft else it } else current + draft
        saveDrafts(updated.sortedByDescending { it.updatedAt })
    }

    suspend fun removeDraft(draftId: String) {
        saveDrafts(draftsFlow.first().filter { it.id != draftId })
    }

    suspend fun setSelectedAccountId(accountId: String) {
        context.emailDataStore.edit { it[SELECTED_ACCOUNT_KEY] = accountId }
    }

    private companion object {
        const val TAG = "EmailDataStore"
    }
}
