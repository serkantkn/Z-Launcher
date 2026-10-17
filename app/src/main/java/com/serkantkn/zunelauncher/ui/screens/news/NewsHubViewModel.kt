package com.serkantkn.zunelauncher.ui.screens.news

import com.serkantkn.zunelauncher.util.Pro
import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.data.model.NewsSection
import com.serkantkn.zunelauncher.data.model.NewsSource
import com.serkantkn.zunelauncher.data.model.NewsTopic
import com.serkantkn.zunelauncher.data.repository.BrowserBridge
import com.serkantkn.zunelauncher.data.repository.HubBridge
import com.serkantkn.zunelauncher.data.repository.NewsException
import com.serkantkn.zunelauncher.data.repository.NewsRefresh
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The news hub's state: the headings it shows, the stories under each, the story that is open,
 * and the hub's settings - which topics, which feeds.
 */
class NewsHubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.appContainer.newsRepository
    private val readingList = application.appContainer.readingListRepository

    /** The addresses on the browser's reading list, to say whether a story is already there. */
    val readingLinks: StateFlow<Set<String>> = readingList.items
        .map { items -> items.map { it.url }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val topics: StateFlow<List<NewsTopic>> = repository.topics
        .stateIn(viewModelScope, SharingStarted.Eagerly, NewsTopic.DEFAULT)

    val sources: StateFlow<List<NewsSource>> = repository.sources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val enabledPresetIds: StateFlow<Set<String>> = repository.enabledPresetIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val customSources: StateFlow<List<NewsSource>> = repository.customSources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sections: StateFlow<Map<String, NewsSection>> = repository.sections

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())

    /** Headings being fetched right now. */
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    private val _status = MutableStateFlow<Int?>(null)

    /** A word on how the last refresh asked for by hand went, while it is worth saying. */
    val status: StateFlow<Int?> = _status.asStateFlow()

    private val _openStory = MutableStateFlow<NewsItem?>(null)
    val openStory: StateFlow<NewsItem?> = _openStory.asStateFlow()

    private val _sourceFilter = MutableStateFlow<String?>(null)

    /** The one feed "my sources" is narrowed to, by name, or null for all of them. */
    val sourceFilter: StateFlow<String?> = _sourceFilter.asStateFlow()

    private val _addingSource = MutableStateFlow(false)
    val addingSource: StateFlow<Boolean> = _addingSource.asStateFlow()

    private val _addError = MutableStateFlow<Int?>(null)
    val addError: StateFlow<Int?> = _addError.asStateFlow()

    init {
        viewModelScope.launch { repository.load() }
    }

    /** Fetches a heading if what is kept has gone stale; quiet, as opening a heading should be. */
    fun ensureFresh(key: String) = fetch(key, force = false, quiet = true)

    /** The bar's refresh: fetches whatever the age, and says so when it cannot. */
    fun refresh(key: String) = fetch(key, force = true, quiet = false)

    private fun fetch(key: String, force: Boolean, quiet: Boolean) {
        if (key in _refreshing.value) return
        _refreshing.update { it + key }
        viewModelScope.launch {
            val outcome = try {
                repository.refresh(key, force)
            } finally {
                _refreshing.update { it - key }
            }
            if (!quiet || sections.value[key] == null) {
                _status.value = when (outcome) {
                    NewsRefresh.NO_NETWORK -> R.string.news_status_offline
                    NewsRefresh.FAILED -> R.string.news_status_failed
                    else -> null
                }
            }
        }
    }

    fun clearStatus() {
        _status.value = null
    }

    fun setSourceFilter(name: String?) {
        _sourceFilter.value = name
    }

    /** Opens a story's summary, and fills in its picture from its page when the feed had none. */
    fun open(story: NewsItem) {
        _openStory.value = story
        viewModelScope.launch {
            val filled = repository.withPageCard(story)
            if (_openStory.value?.link == story.link) _openStory.value = filled
        }
    }

    fun close() {
        _openStory.value = null
    }

    /** Puts the story on the browser's reading list, or takes it off again. */
    fun toggleReadingList(story: NewsItem) {
        if (readingList.contains(story.readUrl)) {
            readingList.remove(story.readUrl)
        } else if (!Pro.unlocked) {
            // Saving for later is Pro's; taking a story off the list is not.
            _status.value = R.string.pro_only_feature
        } else {
            readingList.add(
                com.serkantkn.zunelauncher.data.model.ReadingItem(
                    url = story.readUrl,
                    title = story.title,
                    source = story.source,
                    imageUrl = story.imageUrl,
                    excerpt = story.summary
                )
            )
            _status.value = R.string.browser_reading_added
        }
    }

    /** Goes on reading on the story's own site, in the launcher's browser. */
    fun readOnSite(link: String) {
        BrowserBridge.openFromNews(link)
        HubBridge.open(HubType.INTERNET)
    }

    // --- Settings --------------------------------------------------------------------------------

    fun setTopicShown(topic: NewsTopic, shown: Boolean) {
        viewModelScope.launch {
            val current = topics.value
            val next = if (shown) NewsTopic.entries.filter { it in current || it == topic } else current - topic
            repository.setTopics(next)
        }
    }

    fun setPresetEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setPresetEnabled(id, enabled)
            _sourceFilter.value = null
            ensureFresh(NewsSection.SOURCES_KEY)
        }
    }

    /** Adds a feed by address; [onAdded] is told when it was, so the box can be emptied. */
    fun addSource(address: String, onAdded: () -> Unit) {
        if (address.isBlank() || _addingSource.value) return
        _addingSource.value = true
        _addError.value = null
        viewModelScope.launch {
            try {
                repository.addSource(address)
                onAdded()
                ensureFresh(NewsSection.SOURCES_KEY)
            } catch (e: NewsException) {
                _addError.value = e.messageRes
            } finally {
                _addingSource.value = false
            }
        }
    }

    fun removeSource(id: String) {
        viewModelScope.launch {
            val removed = customSources.value.firstOrNull { it.id == id }
            repository.removeSource(id)
            if (removed != null && _sourceFilter.value == removed.name) _sourceFilter.value = null
            ensureFresh(NewsSection.SOURCES_KEY)
        }
    }

    fun clearAddError() {
        _addError.value = null
    }

    /** Topic headings take the topic's name; the feeds' heading is "my sources". */
    companion object {
        @StringRes
        fun titleOf(key: String): Int =
            NewsTopic.entries.firstOrNull { it.sectionKey == key }?.titleRes ?: R.string.news_tab_sources
    }
}
