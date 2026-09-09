package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.CalcHistoryEntry
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.calculatorDataStore: DataStore<Preferences> by preferencesDataStore(name = "calculator")

/** History and small preferences of the calculator hub (org.json, house convention). */
class CalculatorDataStore(private val context: Context) {

    private val HISTORY_KEY = stringPreferencesKey("history_json")
    private val DEGREES_KEY = booleanPreferencesKey("angle_degrees")
    private val MEMORY_KEY = stringPreferencesKey("memory_value")

    /** Newest first, at most [MAX_HISTORY] entries. */
    val historyFlow: Flow<List<CalcHistoryEntry>> = context.calculatorDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[HISTORY_KEY], TAG, CalcHistoryEntry::fromJson)
    }

    val degreesFlow: Flow<Boolean> = context.calculatorDataStore.data.map { it[DEGREES_KEY] ?: true }

    /** Memory register as a plain decimal string, or null when empty. */
    val memoryFlow: Flow<String?> = context.calculatorDataStore.data.map { it[MEMORY_KEY] }

    suspend fun addEntry(entry: CalcHistoryEntry) {
        val current = historyFlow.first()
        saveHistory((listOf(entry) + current).take(MAX_HISTORY))
    }

    suspend fun removeEntry(id: String) {
        saveHistory(historyFlow.first().filter { it.id != id })
    }

    suspend fun clearHistory() = saveHistory(emptyList())

    private suspend fun saveHistory(entries: List<CalcHistoryEntry>) {
        context.calculatorDataStore.edit { it[HISTORY_KEY] = entries.toJsonArrayString { e -> e.toJson() } }
    }

    suspend fun setDegrees(degrees: Boolean) {
        context.calculatorDataStore.edit { it[DEGREES_KEY] = degrees }
    }

    suspend fun setMemory(value: String?) {
        context.calculatorDataStore.edit { if (value == null) it.remove(MEMORY_KEY) else it[MEMORY_KEY] = value }
    }

    private companion object {
        const val TAG = "CalculatorDataStore"
        const val MAX_HISTORY = 100
    }
}
