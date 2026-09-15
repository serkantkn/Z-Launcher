package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

private val Context.browsingDataStore: DataStore<Preferences> by preferencesDataStore(name = "browsing")

/**
 * Where somebody has been, and what they brought back: the browser's history and its downloads.
 *
 * These used to live in the settings store, next to the accent colour and the tile sizes, which
 * meant that backing up "the launcher's settings" quietly backed up a list of every page
 * visited. They have a file of their own now, and that file is excluded from backup — see
 * res/xml/backup_rules.xml. Bookmarks stayed behind in settings on purpose: they are something
 * somebody chose to keep, and worth having again on a new phone.
 *
 * Anything written before the split is moved across once, and the old keys are deleted.
 */
class BrowsingDataStore(private val context: Context, private val settings: SettingsDataStore) {

    companion object {
        private const val TAG = "BrowsingDataStore"
        private val HISTORY_KEY = stringPreferencesKey("browser_history")
        private val DOWNLOADS_KEY = stringPreferencesKey("browser_downloads")
    }

    val history: Flow<String?> = flow {
        migrateFromSettingsIfNeeded()
        emitAll(context.browsingDataStore.data.map { it[HISTORY_KEY] })
    }

    val downloads: Flow<String?> = flow {
        migrateFromSettingsIfNeeded()
        emitAll(context.browsingDataStore.data.map { it[DOWNLOADS_KEY] })
    }

    suspend fun setHistory(json: String) {
        context.browsingDataStore.edit { it[HISTORY_KEY] = json }
    }

    suspend fun setDownloads(json: String) {
        context.browsingDataStore.edit { it[DOWNLOADS_KEY] = json }
    }

    /** "Clear history" means clear it here too, not only in the WebView. */
    suspend fun clearHistory() {
        setHistory("[]")
    }

    @Volatile
    private var migrated = false

    /**
     * Moves history and downloads out of the settings store, once.
     *
     * The old keys are removed rather than left behind: a copy of somebody's browsing history
     * sitting in the file that *is* backed up would defeat the point of moving it.
     */
    private suspend fun migrateFromSettingsIfNeeded() {
        if (migrated) return
        migrated = true
        try {
            val existing = context.browsingDataStore.data.first()
            if (existing.contains(HISTORY_KEY) || existing.contains(DOWNLOADS_KEY)) return

            val oldHistory = settings.browserHistory.first()
            val oldDownloads = settings.browserDownloads.first()
            if (oldHistory == null && oldDownloads == null) return

            context.browsingDataStore.edit { moved ->
                oldHistory?.let { moved[HISTORY_KEY] = it }
                oldDownloads?.let { moved[DOWNLOADS_KEY] = it }
            }
            settings.clearLegacyBrowsingKeys()
            ZuneLog.d(TAG, "browsing history moved out of the settings store")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            migrated = false
            ZuneLog.w(TAG, "browsing history could not be moved; leaving it where it is", e)
        }
    }
}
