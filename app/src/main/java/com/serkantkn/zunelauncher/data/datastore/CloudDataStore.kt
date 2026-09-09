package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudDataStore: DataStore<Preferences> by preferencesDataStore(name = "cloud_accounts")

/**
 * The cloud accounts the user signed in to. Only the identity is stored here — access and refresh
 * tokens live in [com.serkantkn.zunelauncher.util.SecretStore], encrypted with a Keystore key.
 */
class CloudDataStore(private val context: Context) {

    private val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")

    val accounts: Flow<List<CloudAccount>> = context.cloudDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[ACCOUNTS_KEY], TAG) { CloudAccount.fromJson(it) }
            .filterNotNull()
    }

    /** Adds the account, or refreshes the name of one that is signed in again. */
    suspend fun putAccount(account: CloudAccount) {
        val current = accounts.first().filterNot { it.id == account.id }
        save(current + account)
    }

    suspend fun removeAccount(id: String) {
        save(accounts.first().filterNot { it.id == id })
    }

    private suspend fun save(accounts: List<CloudAccount>) {
        context.cloudDataStore.edit { preferences ->
            preferences[ACCOUNTS_KEY] = accounts.toJsonArrayString { it.toJson() }
        }
    }

    private companion object {
        const val TAG = "CloudDataStore"
    }
}
