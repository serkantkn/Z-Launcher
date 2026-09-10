package com.serkantkn.zunelauncher.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.BuildConfig
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.datastore.WeatherDataStore
import com.serkantkn.zunelauncher.data.model.WeatherException
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

/**
 * What the free tier is allowed. The paid build has none of these limits.
 *
 * The forecast is not fetched on a timer: it is fetched when the hub is opened and what is cached
 * has gone stale, so the allowance is spent on days the user actually looks at the weather.
 */
object WeatherPolicy {

    /** Fetches a free build may spend in one calendar day. */
    const val FREE_DAILY_FETCHES = 10

    /** How old a free forecast may get before it is fetched again. */
    const val FREE_STALE_MILLIS = 3 * 60 * 60 * 1000L

    /** The paid build refreshes far more eagerly. */
    const val PREMIUM_STALE_MILLIS = 30 * 60 * 1000L

    /** Places a free build may keep. */
    const val FREE_PLACE_LIMIT = 1

    val isPremium: Boolean get() = BuildConfig.IS_PREMIUM

    val dailyFetches: Int get() = if (isPremium) Int.MAX_VALUE else FREE_DAILY_FETCHES

    val staleMillis: Long get() = if (isPremium) PREMIUM_STALE_MILLIS else FREE_STALE_MILLIS

    val placeLimit: Int get() = if (isPremium) Int.MAX_VALUE else FREE_PLACE_LIMIT
}

/** Why a refresh ended the way it did; the hub words its status line from this. */
enum class RefreshOutcome {
    /** New readings arrived. */
    UPDATED,

    /** What was already cached is still inside the refresh window. */
    ALREADY_FRESH,

    /** The free build has spent today's allowance; the cached forecast stands. */
    QUOTA_REACHED
}

data class RefreshResult(val outcome: RefreshOutcome, val snapshot: WeatherSnapshot?)

/**
 * The weather hub's data: fetching through [WeatherApi], the cache and allowance in
 * [WeatherDataStore], and the phone's own position for the "here" place.
 */
class WeatherRepository(
    private val context: Context,
    private val dataStore: WeatherDataStore
) {

    val places = dataStore.places
    val selectedPlaceId = dataStore.selectedPlaceId
    val cache = dataStore.cache
    val fetchesToday = dataStore.fetchesToday

    /**
     * Brings [place] up to date when its forecast has gone stale and there is allowance left.
     *
     * [force] is the pull-to-refresh case: it skips the staleness check but still respects the
     * daily allowance, so a free build can spend its remaining fetches whenever it likes.
     */
    suspend fun refresh(place: WeatherPlace, force: Boolean): RefreshResult = withContext(Dispatchers.IO) {
        val cached = dataStore.cache.first()[place.id]
        val age = cached?.let { System.currentTimeMillis() - it.fetchedAt }

        if (!force && cached != null && age != null && age < WeatherPolicy.staleMillis) {
            return@withContext RefreshResult(RefreshOutcome.ALREADY_FRESH, cached)
        }
        if (dataStore.fetchesToday.first() >= WeatherPolicy.dailyFetches) {
            ZuneLog.d(TAG, "daily allowance spent, keeping the cached forecast for ${place.name}")
            return@withContext RefreshResult(RefreshOutcome.QUOTA_REACHED, cached)
        }

        val fetched = WeatherApi.forecast(place, System.currentTimeMillis() / 1000)
        // Air quality is a second endpoint but the same trip for the user, so it is not counted twice.
        val snapshot = fetched.copy(airQuality = WeatherApi.airQuality(place))
        dataStore.putSnapshot(snapshot)
        dataStore.countFetch()
        RefreshResult(RefreshOutcome.UPDATED, snapshot)
    }

    suspend fun search(query: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) emptyList()
        else WeatherApi.search(query.trim(), Locale.getDefault().language.ifBlank { "en" })
    }

    suspend fun putPlace(place: WeatherPlace) = dataStore.putPlace(place)

    suspend fun removePlace(id: String) = dataStore.removePlace(id)

    suspend fun selectPlace(id: String) = dataStore.selectPlace(id)

    /** True while the build is allowed to keep another place. */
    suspend fun canAddPlace(): Boolean = dataStore.places.first().size < WeatherPolicy.placeLimit

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The phone's own position as a place, named through the platform geocoder.
     *
     * Only the last position each provider remembers is read — a launcher has no business turning
     * on the GPS — so this returns null on a phone that has not had a fix in a while.
     */
    suspend fun herePlace(): WeatherPlace? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) throw WeatherException(R.string.weather_error_no_location_permission)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val newest = try {
            manager.allProviders
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            ZuneLog.w(TAG, "location read refused", e)
            null
        } ?: return@withContext null

        WeatherPlace(
            id = WeatherPlace.HERE_ID,
            name = nameOf(newest) ?: context.getString(R.string.weather_here),
            region = "",
            country = "",
            latitude = newest.latitude,
            longitude = newest.longitude,
            isHere = true
        )
    }

    @Suppress("DEPRECATION")
    private fun nameOf(location: Location): String? = try {
        Geocoder(context, Locale.getDefault())
            .getFromLocation(location.latitude, location.longitude, 1)
            ?.firstOrNull()
            ?.let { address ->
                address.locality ?: address.subAdminArea ?: address.adminArea ?: address.countryName
            }
    } catch (e: IOException) {
        ZuneLog.w(TAG, "reverse geocoding unavailable", e)
        null
    }

    private companion object {
        const val TAG = "WeatherRepository"
    }
}
