package com.serkantkn.zeta.data.billing

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
 * Ten days of everything, once, when the user asks for it: the free build offers Pro's trial (a
 * question on Start, a button on the Pro page), and for ten days from the moment it is taken is
 * Pro; then it is the free build again. What was set in Pro meanwhile is kept, as it always is
 * (see Pro): it waits, untouched, for Pro.
 *
 * There is no server, so the moment the trial began is kept in three places, and the earliest
 * wins:
 * - the app's own preferences, which go with a backup and come back with it on a reinstall;
 * - Google Play services' Block Store, which with the phone's backup on outlives an uninstall at
 *   once - before any backup has run - and, end-to-end encrypted, moves to a new phone;
 * - the day itself, as twelve of the app's components switched on or off (the `trial.Mark`
 *   aliases in the manifest). Android keeps that outside the app's data: clearing the data, which
 *   empties the other two, leaves it.
 * A clock turned back does not give days back: time is never taken to be earlier than the latest
 * moment the trial has already seen.
 * Uninstalled and installed again with the phone's backup off, the trial can be taken again:
 * nothing on the phone outlives that but what the user keeps, and the trial is not worth asking
 * them for.
 */
object ProTrial {

    const val DAYS = 10
    const val LENGTH_MILLIS = DAYS * DAY_MILLIS

    /** Day 0 of the days the components count (2026-10-24, when the trial came in). */
    const val EPOCH = 1_792_800_000_000L

    sealed interface State {
        /** No trial here: the premium build. */
        data object None : State

        /** Not taken yet: it can be begun. */
        data object Available : State

        /** Pro until [endsAt]. */
        data class Running(val endsAt: Long, val daysLeft: Int) : State

        data object Over : State
    }

    private const val PREFS = "pro_trial"
    private const val KEY_STARTED = "started"
    private const val KEY_LATEST = "latest_seen"
    private const val KEY_OFFERED = "offered"
    private const val KEY_OVER_TOLD = "over_told"
    private const val BLOCK_KEY = "com.serkantkn.zeta.pro_trial"
    private const val MARK_BITS = 12
    private const val TAG = "ProTrial"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private var started = 0L
    private var latest = 0L
    private var ending: Job? = null

    private val _state = MutableStateFlow<State>(State.None)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _offered = MutableStateFlow(true)

    /** Whether the trial has already been offered on Start (taken or not). */
    val offered: StateFlow<Boolean> = _offered.asStateFlow()

    /** Called once, as the process starts, before Pro is first read. */
    fun start(context: Context) {
        app = context.applicationContext
        if (BuildConfig.IS_PREMIUM) return
        val prefs = prefs()
        val now = System.currentTimeMillis()
        started = earliestStart(listOf(prefs.getLong(KEY_STARTED, 0L), readMarks()), now)
        latest = maxOf(prefs.getLong(KEY_LATEST, 0L), now)
        _offered.value = prefs.getBoolean(KEY_OFFERED, false) || started > 0L
        if (started > 0L) remember()
        update()
        // Block Store answers later; a start it remembers from before a reinstall takes the
        // trial back to when it really began.
        reconcileWithBlockStore()
    }

    /** Begins the trial, if it has not been taken: Pro from now for ten days. */
    fun begin() {
        if (!::app.isInitialized || _state.value != State.Available) return
        val now = System.currentTimeMillis()
        started = now
        latest = now
        markOffered()
        remember()
        storeInBlockStore()
        update()
    }

    /** The question on Start has been asked: it is not asked again, whatever the answer. */
    fun markOffered() {
        if (!::app.isInitialized) return
        _offered.value = true
        prefs().edit().putBoolean(KEY_OFFERED, true).apply()
    }

    /** Looked at again: the launcher coming back to the front, the Pro page opening. */
    fun check() {
        if (!::app.isInitialized || started == 0L) return
        latest = maxOf(latest, System.currentTimeMillis())
        remember()
        update()
    }

    /**
     * True once, the first time the launcher comes to the front after the trial has ended: the
     * moment to say so, rather than have its Pro things simply go.
     */
    fun takeOverNotice(): Boolean {
        if (_state.value != State.Over || Pro.purchased) return false
        val prefs = prefs()
        if (prefs.getBoolean(KEY_OVER_TOLD, false)) return false
        prefs.edit().putBoolean(KEY_OVER_TOLD, true).apply()
        return true
    }

    private fun update() {
        ending?.cancel()
        if (started == 0L) {
            _state.value = State.Available
            Pro.setTrial(false)
            return
        }
        val left = remainingMillis(started, latest, System.currentTimeMillis())
        _state.value = if (left > 0) State.Running(started + LENGTH_MILLIS, daysLeft(left)) else State.Over
        Pro.setTrial(left > 0)
        if (left > 0) {
            // Ends while the launcher runs, not only at the next start; the day count goes down
            // on the way.
            ending = scope.launch {
                delay(minOf(left, DAY_MILLIS))
                check()
            }
        }
    }

    private fun prefs() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun remember() {
        prefs().edit()
            .putLong(KEY_STARTED, started)
            .putLong(KEY_LATEST, latest)
            .apply()
        writeMarks(started)
    }

    // ── The day, in the components ──

    private fun mark(bit: Int) = ComponentName(app, "com.serkantkn.zeta.trial.Mark$bit")

    private fun readMarks(): Long {
        val pm = app.packageManager
        val bits = (0 until MARK_BITS).fold(0) { value, bit ->
            val on = runCatching { pm.getComponentEnabledSetting(mark(bit)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                .getOrDefault(false)
            if (on) value or (1 shl bit) else value
        }
        return startOfMarked(bits)
    }

    private fun writeMarks(start: Long) {
        val bits = markedDay(start)
        val pm = app.packageManager
        for (bit in 0 until MARK_BITS) {
            val wanted = if (bits and (1 shl bit) != 0) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            runCatching {
                if (pm.getComponentEnabledSetting(mark(bit)) != wanted) {
                    pm.setComponentEnabledSetting(mark(bit), wanted, PackageManager.DONT_KILL_APP)
                }
            }.onFailure { ZuneLog.w(TAG, "the trial's day could not be marked", it) }
        }
    }

    // ── Block Store ──

    private fun reconcileWithBlockStore() {
        val client = runCatching { Blockstore.getClient(app) }.getOrNull() ?: return
        runCatching {
            client.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(BLOCK_KEY)).build())
                .addOnSuccessListener { response ->
                    val kept = response.blockstoreDataMap[BLOCK_KEY]?.bytes?.let(::decode) ?: return@addOnSuccessListener
                    val earlier = earliestStart(listOf(started, kept.first), System.currentTimeMillis())
                    if (earlier != started || kept.second > latest) {
                        started = earlier
                        latest = maxOf(latest, kept.second)
                        _offered.value = true
                        remember()
                        update()
                    }
                    storeInBlockStore()
                }
                .addOnFailureListener { ZuneLog.w(TAG, "Block Store could not be read", it) }
        }.onFailure { ZuneLog.w(TAG, "Block Store is not here", it) }
    }

    private fun storeInBlockStore() {
        if (started == 0L) return
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

    // ── Reckoning ──

    /**
     * When the trial began: the earliest of what was kept, none of it from the future; 0 if
     * never. The components keep only the day, so of what falls on the earliest day the exact
     * moment is taken over the day's start.
     */
    fun earliestStart(kept: List<Long>, now: Long): Long {
        val valid = kept.filter { it in 1..now }
        val day = valid.minOfOrNull { markedDay(it) } ?: return 0L
        return valid.filter { markedDay(it) == day }.max()
    }

    /** What is left of the trial, never more for a clock turned back past [latestSeen]. */
    fun remainingMillis(started: Long, latestSeen: Long, now: Long): Long =
        (started + LENGTH_MILLIS - maxOf(now, latestSeen)).coerceAtLeast(0L)

    /** Days left, a part of a day counting as one: "1 day left" until it is over. */
    fun daysLeft(remainingMillis: Long): Int =
        if (remainingMillis <= 0L) 0 else ((remainingMillis + DAY_MILLIS - 1) / DAY_MILLIS).toInt()

    /**
     * The day [start] fell on, as the components keep it: days since [EPOCH], plus one so that
     * no component on means no trial. Before the epoch is day 0; past what twelve bits hold, the
     * last day they can.
     */
    fun markedDay(start: Long): Int =
        if (start <= 0L) 0 else (((start - EPOCH).coerceAtLeast(0L) / DAY_MILLIS) + 1).toInt().coerceAtMost((1 shl MARK_BITS) - 1)

    /** The start of the day the components keep; 0 when they keep none. */
    fun startOfMarked(bits: Int): Long = if (bits <= 0) 0L else EPOCH + (bits - 1) * DAY_MILLIS

    private fun encode(started: Long, latest: Long): ByteArray =
        ByteBuffer.allocate(16).putLong(started).putLong(latest).array()

    private fun decode(bytes: ByteArray): Pair<Long, Long>? =
        if (bytes.size < 16) null else ByteBuffer.wrap(bytes).let { it.long to it.long }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
