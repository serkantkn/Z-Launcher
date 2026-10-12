package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.NewsSource
import com.serkantkn.zunelauncher.data.model.NewsTopic
import com.serkantkn.zunelauncher.data.model.parseJsonObjectList
import com.serkantkn.zunelauncher.data.model.parseJsonStringList
import com.serkantkn.zunelauncher.data.model.toJsonArrayString
import com.serkantkn.zunelauncher.data.model.toJsonStringArray
import com.serkantkn.zunelauncher.data.repository.NewsPresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.newsDataStore: DataStore<Preferences> by preferencesDataStore(name = "news")

/**
 * What the news hub is set to follow: which Google News topics stand as headings, which of the
 * ready-made feeds are on, and the feeds the user added by address. The stories themselves are
 * kept by [com.serkantkn.zunelauncher.data.repository.NewsRepository] in a file of their own.
 */
class NewsDataStore(private val context: Context) {

    private val TOPICS = stringPreferencesKey("topics")
    private val PRESETS = stringPreferencesKey("enabled_presets")
    private val CUSTOM = stringPreferencesKey("custom_sources")

    /** The topics shown, in order; the front page is always first. */
    val topics: Flow<List<NewsTopic>> = context.newsDataStore.data.map { preferences ->
        val saved = preferences[TOPICS] ?: return@map NewsTopic.DEFAULT
        val chosen = parseJsonStringList(saved, TAG).mapNotNull { name -> NewsTopic.entries.firstOrNull { it.name == name } }
        listOf(NewsTopic.TOP) + chosen.filter { it != NewsTopic.TOP }
    }

    /** Ids of the ready-made feeds that are on; before any choice, the ones for the phone's language. */
    val enabledPresetIds: Flow<Set<String>> = context.newsDataStore.data.map { preferences ->
        val saved = preferences[PRESETS] ?: return@map NewsPresets.defaultIdsFor(Locale.getDefault().language)
        parseJsonStringList(saved, TAG).toSet()
    }

    val customSources: Flow<List<NewsSource>> = context.newsDataStore.data.map { preferences ->
        parseJsonObjectList(preferences[CUSTOM], TAG, NewsSource::fromJson)
    }

    suspend fun setTopics(topics: List<NewsTopic>) {
        context.newsDataStore.edit { it[TOPICS] = topics.filter { topic -> topic != NewsTopic.TOP }.map { topic -> topic.name }.toJsonStringArray() }
    }

    suspend fun setEnabledPresets(ids: Set<String>) {
        context.newsDataStore.edit { it[PRESETS] = ids.toList().toJsonStringArray() }
    }

    suspend fun setCustomSources(sources: List<NewsSource>) {
        context.newsDataStore.edit { it[CUSTOM] = sources.toJsonArrayString { source -> source.toJson() } }
    }

    private companion object {
        const val TAG = "NewsDataStore"
    }
}
