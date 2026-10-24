package com.serkantkn.zeta.data.billing

import android.content.Context
import android.os.Build
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import com.serkantkn.zeta.BuildConfig
import com.serkantkn.zeta.util.Pro
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Ten days of everything: the free build, installed from Google Play, is Pro for its first ten
 * days, and then is the free build again. What was set in Pro meanwhile is kept, as it always is
 * (see Pro): it waits, untouched, for Pro to be bought.
 *
 * There is no server, so the day the trial began is kept in three places, and the earliest wins:
 * - the app's own preferences, which go with a backup and come back with it on a reinstall;
 * - Google Play services' Block Store, which with the phone's backup on outlives an uninstall at
 *   once - before any backup has run - and, end-to-end encrypted, moves to a new phone (it is
 *   emptied when the app's data is cleared, as the preferences are);
 * - when the app was first installed - or, for an install older than the trial, last updated -
 *   which clearing the data does not change.
 * Uninstalled and installed again with the phone's backup off, it starts again: nothing on the
 * phone outlives that but what the user keeps, and the trial is not worth asking them for.
 * A clock turned back does not give days back: time is never taken to be earlier than the latest
 * moment the trial has already seen.
 *
 * Only an install from Google Play has a trial: that is where Pro can be bought once it ends.
 * The APK on GitHub is the free build from its first day, as it always was.
 */
object ProTrial {

    const val DAYS = 10
    const val LENGTH_MILLIS = DAYS * DAY_MILLIS

    /**
     * When the trial came in (2026-10-24). An install from before then has no trial behind it to
     * be measured from its install day: its ten days run from the update that brought the trial.
     */
    const val INTRODUCED_AT = 1_792_800_000_000L

    sealed interface State {
        /** Not offered here: a Pro build, or an install not from Google Play. */
        data object None : State

        /** Pro until [endsAt]. */
        data class Running(val endsAt: Long, val daysLeft: Int) : State

        data object Over : State
    }

    private const val PREFS = "pro_trial"
    private const val KEY_STARTED = "started"
    private const val KEY_LATEST = "latest_seen"
    private const val KEY_OVER_TOLD = "over_told"
    private const val BLOCK_KEY = "com.serkantkn.zeta.pro_trial"
    private const val PLAY_STORE = "com.android.vending"
    private const val TAG = "ProTrial"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private var started = 0L
    private var latest = 0L
    private var ending: Job? = null

    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Called once, as the process starts, before Pro is first read. */
    fun start(context: Context) {
        app = context.applicationContext
        if (BuildConfig.IS_PREMIUM || !offeredHere()) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val (installed, updated) = installTimes()
        started = earliestStart(
            stored = listOfNotNull(prefs.getLong(KEY_STARTED, 0L).takeIf { it > 0L }),
            installedAt = installed,
            updatedAt = updated,
            now = now
        )
        latest = maxOf(prefs.getLong(KEY_LATEST, 0L), now)
        remember()
        update()
        // Block Store answers later; a start it remembers from before the data was cleared, or
        // from before a reinstall, takes the trial back to when it really began.
        reconcileWithBlockStore()
    }

    /** Looked at again: the launcher coming back to the front, the Pro page opening. */
    fun check() {
        if (!::app.isInitialized || _state.value == State.None) return
        latest = maxOf(latest, System.currentTimeMillis())
        remember()
        update()
    }

    /**
     * True once, the first time the launcher comes to the front after the trial has ended: the
     * moment to say so, rather than have its Pro things simply go.
     */
    fun takeOverNotice(): Boolean {
        if (_state.value != State.Over) return false
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OVER_TOLD, false)) return false
        prefs.edit().putBoolean(KEY_OVER_TOLD, true).apply()
        return true
    }

    private fun update() {
        val left = remainingMillis(started, latest, System.currentTimeMillis())
        _state.value = if (left > 0) State.Running(started + LENGTH_MILLIS, daysLeft(left)) else State.Over
        Pro.setTrial(left > 0)
        ending?.cancel()
        if (left > 0) {
            // Ends while the launcher runs, not only at the next start; the day count goes down
            // on the way.
            ending = scope.launch {
                delay(minOf(left, DAY_MILLIS))
                check()
            }
        }
    }

    private fun remember() {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_STARTED, started)
            .putLong(KEY_LATEST, latest)
            .apply()
    }

    private fun reconcileWithBlockStore() {
        val client = runCatching { Blockstore.getClient(app) }.getOrNull() ?: return
        runCatching {
            client.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(BLOCK_KEY)).build())
                .addOnSuccessListener { response ->
                    val kept = response.blockstoreDataMap[BLOCK_KEY]?.bytes?.let(::decode)
                    if (kept != null) {
                        started = minOf(started, kept.first)
                        latest = maxOf(latest, kept.second)
                        remember()
                        update()
                    }
                    storeInBlockStore()
                }
                .addOnFailureListener {
                    ZuneLog.w(TAG, "Block Store could not be read", it)
                    storeInBlockStore()
                }
        }.onFailure { ZuneLog.w(TAG, "Block Store is not here", it) }
    }

    private fun storeInBlockStore() {
        val client = runCatching { Blockstore.getClient(app) }.getOrNull() ?: return
        val bytes = encode(started, latest)
        runCatching {
            // To a new phone as well, where the backup is end-to-end encrypted; on this phone
            // either way.
            client.isEndToEndEncryptionAvailable.addOnCompleteListener { e2ee ->
                val data = StoreBytesData.Builder()
                    .setKey(BLOCK_KEY)
                    .setBytes(bytes)
                    .setShouldBackupToCloud(e2ee.isSuccessful && e2ee.result == true)
                    .build()
                client.storeBytes(data).addOnFailureListener { ZuneLog.w(TAG, "Block Store could not be written", it) }
            }
        }.onFailure { ZuneLog.w(TAG, "Block Store is not here", it) }
    }

    /** Installed by Google Play - or a debug build, so the trial can be tried. */
    private fun offeredHere(): Boolean {
        if (BuildConfig.DEBUG) return true
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                app.packageManager.getInstallerPackageName(app.packageName)
            }
        }.getOrNull()
        return installer == PLAY_STORE
    }

    /** When the app was first installed, and last updated; neither moves when its data is cleared. */
    private fun installTimes(): Pair<Long, Long> =
        runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            info.firstInstallTime to info.lastUpdateTime
        }.getOrDefault(0L to 0L)

    /**
     * When the trial began: the earliest of what was kept, and the install day - or, for an
     * install older than the trial itself, the day it was last updated, since the trial came with
     * an update. Failing all of them, [now].
     */
    fun earliestStart(stored: List<Long>, installedAt: Long, updatedAt: Long, now: Long): Long {
        val anchor = (if (installedAt >= INTRODUCED_AT) installedAt else updatedAt).takeIf { it in INTRODUCED_AT..now }
        return (stored.filter { it in 1..now } + listOfNotNull(anchor)).minOrNull() ?: now
    }

    /** What is left of the trial, never more for a clock turned back past [latestSeen]. */
    fun remainingMillis(started: Long, latestSeen: Long, now: Long): Long =
        (started + LENGTH_MILLIS - maxOf(now, latestSeen)).coerceAtLeast(0L)

    /** Days left, a part of a day counting as one: "1 day left" until it is over. */
    fun daysLeft(remainingMillis: Long): Int =
        if (remainingMillis <= 0L) 0 else ((remainingMillis + DAY_MILLIS - 1) / DAY_MILLIS).toInt()

    private fun encode(started: Long, latest: Long): ByteArray =
        ByteBuffer.allocate(16).putLong(started).putLong(latest).array()

    private fun decode(bytes: ByteArray): Pair<Long, Long>? =
        if (bytes.size < 16) null else ByteBuffer.wrap(bytes).let { it.long to it.long }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
