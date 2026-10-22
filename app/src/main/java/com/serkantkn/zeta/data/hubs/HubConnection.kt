package com.serkantkn.zeta.data.hubs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.hubkit.protocol.HubNode
import com.serkantkn.zeta.hubkit.protocol.HubProtocol
import com.serkantkn.zeta.hubkit.protocol.IZetaHost
import com.serkantkn.zeta.hubkit.protocol.IZetaHub
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Zeta's side of one open external hub (PLATFORM.md, "Konuşma"): binds to the app's service,
 * says hello, keeps the last description it sent and passes touches back. The app runs in its
 * own process; if it dies or never answers, the hub says so and Zeta carries on.
 */
class HubConnection(
    private val context: Context,
    val hub: ExternalHub
) {
    enum class Status { CONNECTING, READY, FAILED }

    private val _spec = MutableStateFlow<HubNode?>(null)
    /** The hub as the app last described it. */
    val spec: StateFlow<HubNode?> = _spec.asStateFlow()

    private val _status = MutableStateFlow(Status.CONNECTING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _commands = MutableSharedFlow<Bundle>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** What the app asks Zeta to do: [HubProtocol.Command]. */
    val commands: SharedFlow<Bundle> = _commands.asSharedFlow()

    @Volatile
    private var remote: IZetaHub? = null
    private var bound = false
    private var host: Bundle = Bundle.EMPTY
    private var rebinds = 0

    private val callback = object : IZetaHost.Stub() {
        override fun spec(spec: HubNode?) {
            if (spec == null || spec.type != HubProtocol.Node.HUB) return
            if (count(spec) > MAX_NODES) {
                ZuneLog.w(TAG, "${hub.key} described more than $MAX_NODES things; not drawn")
                return
            }
            _spec.value = spec
            _status.value = Status.READY
        }

        override fun command(command: Bundle?) {
            if (command != null) _commands.tryEmit(Bundle(command))
        }
    }

    private val connection = object : ServiceConnection {
        // Also after the app's process died and came back: the system binds again by itself.
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val hubBinder = IZetaHub.Stub.asInterface(service)
            remote = hubBinder
            try {
                hubBinder.hello(host, callback)
            } catch (e: RemoteException) {
                fail("hello", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            _status.value = Status.FAILED
        }

        // The app was updated or stopped for good: the binding has to be made again.
        override fun onBindingDied(name: ComponentName?) {
            remote = null
            _status.value = Status.FAILED
            if (rebinds++ < MAX_REBINDS) retry()
        }

        override fun onNullBinding(name: ComponentName?) {
            _status.value = Status.FAILED
        }
    }

    /** Binds and says hello with [host] ([HubProtocol.Host]). */
    fun connect(host: Bundle) {
        this.host = host
        if (bound) return
        _status.value = Status.CONNECTING
        val intent = Intent(HubProtocol.ACTION_HUB).setComponent(ComponentName(hub.packageName, hub.className))
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { ZuneLog.w(TAG, "could not bind ${hub.key}", it) }
            .getOrDefault(false)
        if (!bound) _status.value = Status.FAILED
    }

    /** Gives up waiting for a first description. */
    fun timeOut() {
        if (_status.value == Status.CONNECTING) _status.value = Status.FAILED
    }

    /** Tells the app something happened: [HubProtocol.Event]. */
    fun send(kind: String, key: String, fill: Bundle.() -> Unit = {}) {
        val to = remote ?: return
        try {
            to.event(Bundle().apply {
                putString(HubProtocol.Event.KIND, kind)
                putString(HubProtocol.Event.KEY, key)
                fill()
            })
        } catch (e: RemoteException) {
            fail("event", e)
        }
    }

    /** Lets the app go. */
    fun close() {
        runCatching { remote?.bye() }
        remote = null
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
        }
    }

    /** Lets go and binds again. */
    fun retry() {
        close()
        connect(host)
    }

    /** The person asked to try again: a fresh start. */
    fun retryByHand() {
        rebinds = 0
        retry()
    }

    private fun fail(what: String, e: Exception) {
        ZuneLog.w(TAG, "${hub.key}: $what failed", e)
        remote = null
        _status.value = Status.FAILED
    }

    private fun count(node: HubNode): Int = 1 + node.children.sumOf { count(it) }

    private companion object {
        const val TAG = "HubConnection"
        /** More than any hub needs to describe at once; the rest belongs in a list fetched in parts. */
        const val MAX_NODES = 4000
        /** How often a binding that died is made again before the hub says it cannot open. */
        const val MAX_REBINDS = 3
    }
}
