package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.SpeedDialEntry
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import com.serkantkn.zunelauncher.util.PhoneNumbers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.phoneDataStore: DataStore<Preferences> by preferencesDataStore(name = "phone")

/**
 * What the phone hub remembers between visits: the people kept one press away, and which SIM the
 * user would rather call from. Windows Phone called the first of those speed dial and put it beside
 * the history; the order is the user's, so it is stored as a list rather than a set.
 */
class PhoneDataStore(private val context: Context) {

    private val SPEED_DIAL = stringPreferencesKey("speed_dial")
    private val PREFERRED_SIM = stringPreferencesKey("preferred_sim")

    /**
     * The SIM calls go out on, or null while the user would rather be asked each time. Only ever
     * looked at on a phone with more than one line.
     */
    val preferredSim: Flow<String?> = context.phoneDataStore.data.map { prefs ->
        prefs[PREFERRED_SIM]?.takeIf { it.isNotBlank() }
    }

    /** Passing null here goes back to being asked before every call. */
    suspend fun setPreferredSim(key: String?) {
        context.phoneDataStore.edit { prefs ->
            if (key.isNullOrBlank()) prefs.remove(PREFERRED_SIM) else prefs[PREFERRED_SIM] = key
        }
    }

    val speedDial: Flow<List<SpeedDialEntry>> = context.phoneDataStore.data.map { prefs ->
        parseJsonObjectList(prefs[SPEED_DIAL], TAG) { SpeedDialEntry.fromJson(it) }
    }

    /** Adding someone already on the list moves nothing: one line each. */
    suspend fun add(entry: SpeedDialEntry) {
        val current = speedDial.first()
        if (current.any { PhoneNumbers.sameNumber(it.number, entry.number) }) return
        write(current + entry)
    }

    suspend fun remove(number: String) {
        write(speedDial.first().filterNot { PhoneNumbers.sameNumber(it.number, number) })
    }

    suspend fun move(from: Int, to: Int) {
        val current = speedDial.first().toMutableList()
        if (from !in current.indices || to !in current.indices) return
        current.add(to, current.removeAt(from))
        write(current)
    }

    private suspend fun write(entries: List<SpeedDialEntry>) {
        context.phoneDataStore.edit { prefs ->
            prefs[SPEED_DIAL] = entries.toJsonArrayString { it.toJson() }
        }
    }

    private companion object {
        const val TAG = "PhoneDataStore"
    }
}
