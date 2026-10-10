package com.serkantkn.zunelauncher.ui.screens.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.MeasureSystem
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.WeatherException
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.data.model.WindUnit
import com.serkantkn.zunelauncher.data.repository.RefreshOutcome
import com.serkantkn.zunelauncher.data.repository.WeatherPolicy
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The weather hub's state.
 *
 * The forecast is fetched when the hub is opened and what is cached has gone stale, not on a
 * background timer — a free build's daily allowance is better spent on the days its owner actually
 * looks at the weather. Whatever happens, the cached forecast stays on screen; the status line
 * says when it was last brought up to date.
 */
class WeatherHubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.appContainer.weatherRepository
    private val dataStore = application.appContainer.weatherDataStore

    val places: StateFlow<List<WeatherPlace>> = repository.places
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The place on screen: the one that was picked, else the first one saved. */
    val selectedPlace: StateFlow<WeatherPlace?> = combine(
        repository.places,
        repository.selectedPlaceId
    ) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Last forecast for the selected place, straight off the disk cache. */
    val snapshot: StateFlow<WeatherSnapshot?> = combine(
        repository.cache,
        selectedPlace
    ) { cache, place ->
        place?.let { cache[it.id] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Every place with whatever is cached for it, for the places page. */
    val placeSummaries: StateFlow<List<Pair<WeatherPlace, WeatherSnapshot?>>> = combine(
        repository.places,
        repository.cache
    ) { list, cache ->
        list.map { place -> place to cache[place.id] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val temperatureUnit: StateFlow<TemperatureUnit> = dataStore.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemperatureUnit.defaultFor(Locale.getDefault()))

    val windUnit: StateFlow<WindUnit> = dataStore.windUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WindUnit.defaultFor(Locale.getDefault()))

    val measureSystem: StateFlow<MeasureSystem> = dataStore.measureSystem
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MeasureSystem.defaultFor(Locale.getDefault()))

    val animatedSky: StateFlow<Boolean> = dataStore.animatedSky
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Updates left in today's allowance; the paid build always reads as full. */
    val updatesLeft: StateFlow<Int> = repository.fetchesToday
        .map { used -> (WeatherPolicy.dailyFetches - used).coerceAtLeast(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherPolicy.dailyFetches)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** A line to show over the forecast: an error, or what the last refresh did. */
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _searchResults = MutableStateFlow<List<WeatherPlace>>(emptyList())
    val searchResults: StateFlow<List<WeatherPlace>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val canAddPlace: StateFlow<Boolean> = repository.places
        .map { it.size < WeatherPolicy.placeLimit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isPremium: Boolean get() = WeatherPolicy.isPremium

    val dailyAllowance: Int get() = WeatherPolicy.dailyFetches

    init {
        // A first run has no place at all: take the phone's own position when it is already known.
        viewModelScope.launch {
            if (repository.places.first().isEmpty() && repository.hasLocationPermission()) {
                runCatching { repository.herePlace() }.getOrNull()?.let { here ->
                    repository.putPlace(here)
                    repository.selectPlace(here.id)
                }
            }
            refresh(force = false)
        }
    }

    /** Called when the hub comes to the front. Fetches only if the forecast has gone stale. */
    fun onOpened() {
        viewModelScope.launch { refresh(force = false) }
    }

    /**
     * The refresh button. It always runs the full turn of the animation so the hub answers the
     * touch, spends an update when the allowance still has one, and leaves the cached forecast
     * (and its honest "updated at" line) alone when it does not.
     */
    fun refreshManually() {
        viewModelScope.launch { refresh(force = true) }
    }

    /** When the "here" place was last matched against where the phone actually is. */
    private var hereResolvedAt = 0L

    private suspend fun refresh(force: Boolean) {
        // Read off the store, not the hot flow: the flow is shared with a five-second grace and
        // can still be holding the place that was selected before the one just chosen.
        val list = repository.places.first()
        val selectedId = repository.selectedPlaceId.first()
        var place = list.firstOrNull { it.id == selectedId } ?: list.firstOrNull() ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        val startedAt = System.currentTimeMillis()
        try {
            // "Here" was fixed at wherever the phone first was; a phone that has since moved
            // should be asked again, cheaply, when the last answer is old enough.
            if (place.isHere && startedAt - hereResolvedAt > HERE_MAX_AGE_MILLIS) {
                place = repository.refreshedHere(place, HERE_MAX_AGE_MILLIS)
                hereResolvedAt = startedAt
            }
            val result = repository.refresh(place, force)
            // The "updated at" line under the temperature says how old the forecast is; the
            // status line only speaks when a refresh that was asked for could not happen.
            _message.value = when (result.outcome) {
                RefreshOutcome.UPDATED -> null
                RefreshOutcome.ALREADY_FRESH ->
                    if (force) getApplication<Application>().localizedString(R.string.weather_status_fresh) else null
                RefreshOutcome.QUOTA_REACHED ->
                    if (force) getApplication<Application>().localizedString(R.string.weather_status_quota) else null
                RefreshOutcome.NO_NETWORK ->
                    getApplication<Application>().localizedString(R.string.weather_status_no_network)
            }
        } catch (e: WeatherException) {
            ZuneLog.w(TAG, "refresh failed", e)
            _message.value = getApplication<Application>().localizedString(
                if (e.messageRes == R.string.weather_error_network) R.string.weather_status_no_network else e.messageRes
            )
        } catch (e: Exception) {
            ZuneLog.e(TAG, "refresh failed", e)
            _message.value = getApplication<Application>().localizedString(R.string.weather_error_service)
        } finally {
            // The spinner is worth watching: hold it long enough to read as a refresh.
            val spent = System.currentTimeMillis() - startedAt
            if (spent < MIN_SPINNER_MILLIS) delay(MIN_SPINNER_MILLIS - spent)
            _isRefreshing.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // --- Places -----------------------------------------------------------------------------------

    private var searchJob: Job? = null

    /**
     * Typed into the search box. Nothing is asked until the typing pauses, the previous question
     * is withdrawn when a new one comes, and an answer that arrives for an old question is
     * ignored — one network trip per place looked for, not one per letter.
     */
    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _isSearching.value = true
            try {
                val results = repository.search(query)
                if (isActive) _searchResults.value = results
            } catch (e: WeatherException) {
                if (isActive) {
                    _message.value = getApplication<Application>().localizedString(e.messageRes)
                    _searchResults.value = emptyList()
                }
            } finally {
                if (isActive) _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun addPlace(place: WeatherPlace) {
        viewModelScope.launch {
            if (!repository.canAddPlace()) {
                _message.value = getApplication<Application>().localizedString(R.string.weather_place_limit)
                return@launch
            }
            repository.putPlace(place)
            repository.selectPlace(place.id)
            _searchResults.value = emptyList()
            refresh(force = false)
        }
    }

    fun removePlace(id: String) {
        viewModelScope.launch { repository.removePlace(id) }
    }

    fun selectPlace(id: String) {
        viewModelScope.launch {
            repository.selectPlace(id)
            refresh(force = false)
        }
    }

    /** Adds (or moves) the "my location" place. Reports the missing permission as a message. */
    fun useMyLocation() {
        viewModelScope.launch {
            try {
                val here = repository.herePlace()
                if (here == null) {
                    _message.value = getApplication<Application>().localizedString(R.string.weather_error_no_location)
                    return@launch
                }
                val alreadyThere = repository.places.first().any { it.isHere }
                if (!alreadyThere && !repository.canAddPlace()) {
                    _message.value = getApplication<Application>().localizedString(R.string.weather_place_limit)
                    return@launch
                }
                repository.putPlace(here)
                repository.selectPlace(here.id)
                refresh(force = true)
            } catch (e: WeatherException) {
                _message.value = getApplication<Application>().localizedString(e.messageRes)
            }
        }
    }

    // --- Preferences ------------------------------------------------------------------------------

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { dataStore.setTemperatureUnit(unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch { dataStore.setWindUnit(unit) }
    }

    fun setMeasureSystem(system: MeasureSystem) {
        viewModelScope.launch { dataStore.setMeasureSystem(system) }
    }

    fun toggleAnimatedSky() {
        viewModelScope.launch { dataStore.setAnimatedSky(!animatedSky.value) }
    }

    private companion object {
        const val TAG = "WeatherHubViewModel"
        const val MIN_SPINNER_MILLIS = 900L
        const val SEARCH_DEBOUNCE_MILLIS = 350L

        /** How old the phone's position behind "here" may get before it is asked for again. */
        const val HERE_MAX_AGE_MILLIS = 30 * 60_000L
    }
}
