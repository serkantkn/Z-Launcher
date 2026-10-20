package com.serkantkn.zunelauncher.data.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** A TV found on the network. */
internal data class FoundTv(val id: String, val name: String, val host: String, val port: Int)

/**
 * The phone's end of the phone link: it finds TVs running Z Launcher on the same Wi-Fi, pairs
 * with one by the code it shows, and tells the paired TVs who is calling or writing.
 */
internal object PhoneLink {
    private const val TAG = "PhoneLink"
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The TVs on the network, as they are found; closing the flow stops looking. */
    fun discover(context: Context): Flow<List<FoundTv>> = callbackFlow {
        val app = context.applicationContext
        val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
        // Some phones drop multicast while the screen is on its own; mDNS needs it.
        val lock = (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("z link")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        val found = linkedMapOf<String, FoundTv>()
        val toResolve = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        fun resolveNext() {
            if (resolving) return
            val next = toResolve.removeFirstOrNull() ?: return
            resolving = true
            @Suppress("DEPRECATION")
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                    resolving = false
                    resolveNext()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    resolving = false
                    val id = info.attributes["id"]?.let { String(it) }
                    val name = info.attributes["name"]?.let { String(it) } ?: info.serviceName.removePrefix("Z Launcher ")
                    @Suppress("DEPRECATION")
                    val host = info.host?.hostAddress
                    if (id != null && host != null) {
                        found[id] = FoundTv(id, name, host, info.port)
                        trySend(found.values.toList())
                    }
                    resolveNext()
                }
            })
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { ZuneLog.w(TAG, "discovery failed: $code") }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                toResolve.addLast(info)
                resolveNext()
            }

            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        trySend(emptyList())
        runCatching { nsd.discoverServices(LinkProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { ZuneLog.w(TAG, "discovery could not start", it) }
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock?.release() }
        }
    }

    sealed interface PairResult {
        data class Paired(val tv: String) : PairResult
        data object WrongCode : PairResult
        data object NotPairing : PairResult
        data object Unreachable : PairResult
    }

    /** Pairs with the TV at [host]:[port] by the [code] it shows. */
    suspend fun pair(context: Context, host: String, port: Int, code: String): PairResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        LinkStore.attach(app)
        val handshake = PairingHandshake(LinkStore.identity(app), code.trim(), phone = true)
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
                s.soTimeout = READ_TIMEOUT_MILLIS
                val input = DataInputStream(s.getInputStream().buffered())
                val output = DataOutputStream(s.getOutputStream().buffered())
                LinkProtocol.write(output, handshake.hello())
                val reply = LinkProtocol.read(input)
                if (reply.optString("t") == "err") return@runCatching PairResult.NotPairing
                handshake.receiveHello(reply)
                LinkProtocol.write(output, handshake.proof())
                val result = try {
                    handshake.check(LinkProtocol.read(input))
                } catch (wrong: PairingHandshake.WrongCode) {
                    return@runCatching PairResult.WrongCode
                }
                LinkStore.save(LinkPeer(result.peer.id, result.peer.name, LinkCrypto.wrap(result.key), host, port))
                PairResult.Paired(result.peer.name) as PairResult
            }
        }.getOrElse {
            ZuneLog.w(TAG, "pairing failed", it)
            PairResult.Unreachable
        }
    }

    /**
     * Tells every paired TV, all at once; a TV that is off or away simply is not told. Waits until
     * each has answered or given up, so a receiver can hold its broadcast open until then.
     */
    suspend fun announce(context: Context, notice: LinkNotice) {
        val app = context.applicationContext
        LinkStore.attach(app)
        val tvs = LinkStore.peers.value
        if (tvs.isEmpty()) return
        val own = LinkStore.identity(app)
        withContext(Dispatchers.IO) {
            tvs.map { tv -> async { runCatching { tell(app, own, tv, notice) } } }.awaitAll()
        }
    }

    private suspend fun tell(context: Context, own: LinkIdentity, tv: LinkPeer, notice: LinkNotice) {
        val key = LinkCrypto.unwrap(tv.wrappedKey) ?: return
        val frame = NoticeSeal.seal(own.id, key, notice)
        if (tv.host.isNotBlank() && send(tv.host, tv.port, frame)) return
        // Not where it was: the TV's address changed, or it moved to another port. Look for it.
        val found = withTimeoutOrNull(FIND_TIMEOUT_MILLIS) {
            discover(context).first { list -> list.any { it.id == tv.id } }.first { it.id == tv.id }
        } ?: return
        if (send(found.host, found.port, frame)) LinkStore.save(tv.copy(host = found.host, port = found.port))
    }

    private fun send(host: String, port: Int, frame: org.json.JSONObject): Boolean = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            s.soTimeout = READ_TIMEOUT_MILLIS
            LinkProtocol.write(DataOutputStream(s.getOutputStream().buffered()), frame)
            LinkProtocol.read(DataInputStream(s.getInputStream().buffered())).optString("t") == "ok"
        }
    }.getOrDefault(false)

    private const val CONNECT_TIMEOUT_MILLIS = 1_500
    private const val READ_TIMEOUT_MILLIS = 4_000
    private const val FIND_TIMEOUT_MILLIS = 4_000L
}
