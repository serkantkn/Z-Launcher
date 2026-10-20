package com.serkantkn.zunelauncher.data.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * The TV's end of the phone link: it listens on the local network, says so by mDNS (NSD) so a
 * phone can find it by name, takes pairings while the user has a code up on the screen, and opens
 * the notices its paired phones send, handing them to [TvLinkNotices].
 *
 * It lives in the launcher's process, which the TV keeps while the launcher's accessibility
 * service runs - that is, while Z Launcher answers the remote's Home key.
 */
internal object TvLink {
    private const val TAG = "TvLink"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null

    /** A pairing the user has asked for: the code on the screen, and how it went. */
    data class Pairing(
        val code: String,
        val expiresAt: Long,
        val name: String,
        val addresses: List<String>,
        val paired: String? = null,
        val failed: Boolean = false
    )

    private val _pairing = MutableStateFlow<Pairing?>(null)
    val pairing: StateFlow<Pairing?> = _pairing.asStateFlow()

    /** Starts listening, once; on anything but a TV it does nothing. */
    @Synchronized
    fun start(context: Context) {
        if (server != null) return
        if (!com.serkantkn.zunelauncher.ui.theme.isTelevision(context)) return
        val app = context.applicationContext
        LinkStore.attach(app)
        val socket = runCatching { ServerSocket(LinkProtocol.DEFAULT_PORT) }
            .recoverCatching { ServerSocket(0) }
            .onFailure { ZuneLog.e(TAG, "could not listen", it) }
            .getOrNull() ?: return
        server = socket
        ZuneLog.d(TAG, "listening on ${socket.localPort}")
        Thread({ acceptLoop(app, socket) }, "TvLink").apply { isDaemon = true }.start()
        advertise(app, socket.localPort)
    }

    val port: Int get() = server?.localPort ?: LinkProtocol.DEFAULT_PORT

    fun startPairing(context: Context) {
        val app = context.applicationContext
        start(app)
        _pairing.value = Pairing(
            code = LinkCrypto.pairingCode(),
            expiresAt = System.currentTimeMillis() + PAIRING_MILLIS,
            name = LinkStore.deviceName(app),
            addresses = localAddresses().map { if (port == LinkProtocol.DEFAULT_PORT) it else "$it:$port" }
        )
    }

    fun stopPairing() {
        _pairing.value = null
    }

    private fun acceptLoop(context: Context, socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: continue
            scope.launch { runCatching { handle(context, client) }.onFailure { ZuneLog.w(TAG, "a connection failed", it) } }
        }
    }

    private fun handle(context: Context, client: Socket) = client.use { s ->
        s.soTimeout = READ_TIMEOUT_MILLIS
        val input = DataInputStream(s.getInputStream().buffered())
        val output = DataOutputStream(s.getOutputStream().buffered())
        val first = LinkProtocol.read(input)
        when (first.optString("t")) {
            "hello" -> pair(context, first, input, output)
            "notice" -> notice(context, first, output)
            else -> LinkProtocol.write(output, error("unknown"))
        }
    }

    private fun pair(context: Context, hello: org.json.JSONObject, input: DataInputStream, output: DataOutputStream) {
        val pairing = _pairing.value
        if (pairing == null || pairing.paired != null || pairing.failed || System.currentTimeMillis() > pairing.expiresAt) {
            LinkProtocol.write(output, error("not_pairing"))
            return
        }
        val handshake = PairingHandshake(LinkStore.identity(context), pairing.code, phone = false)
        handshake.receiveHello(hello)
        LinkProtocol.write(output, handshake.hello())
        val result = try {
            handshake.check(LinkProtocol.read(input))
        } catch (wrong: PairingHandshake.WrongCode) {
            // One try per code: a wrong answer ends it, so the digits cannot be guessed.
            LinkProtocol.write(output, error("code"))
            _pairing.value = pairing.copy(failed = true)
            return
        }
        LinkStore.save(LinkPeer(result.peer.id, result.peer.name, LinkCrypto.wrap(result.key)))
        LinkProtocol.write(output, handshake.proof())
        _pairing.value = pairing.copy(paired = result.peer.name)
        ZuneLog.d(TAG, "paired with a phone")
    }

    private fun notice(context: Context, frame: org.json.JSONObject, output: DataOutputStream) {
        val peer = LinkStore.peer(frame.optString("id"))
        val key = peer?.let { LinkCrypto.unwrap(it.wrappedKey) }
        val notice = key?.let { NoticeSeal.open(frame, it) }
        if (peer == null || notice == null) {
            LinkProtocol.write(output, error("unknown"))
            return
        }
        // An old notice sent again - or one from a phone whose clock is far off - is not shown.
        val now = System.currentTimeMillis()
        if (notice.at <= peer.lastAt || kotlin.math.abs(now - notice.at) > MAX_AGE_MILLIS) {
            LinkProtocol.write(output, error("stale"))
            return
        }
        LinkStore.save(peer.copy(lastAt = notice.at))
        LinkProtocol.write(output, org.json.JSONObject().put("t", "ok"))
        if (LinkStore.showNotices.value) TvLinkNotices.show(context, notice, peer.name)
    }

    private fun error(what: String) = org.json.JSONObject().put("t", "err").put("e", what)

    private fun advertise(context: Context, port: Int) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val identity = LinkStore.identity(context)
        val info = NsdServiceInfo().apply {
            serviceName = "Z Launcher ${identity.name}"
            serviceType = LinkProtocol.SERVICE_TYPE
            setPort(port)
            setAttribute("id", identity.id)
            setAttribute("name", identity.name)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { ZuneLog.d(TAG, "advertised as ${info.serviceName}") }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { ZuneLog.w(TAG, "could not advertise: $code") }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onSuccess { registration = listener }
            .onFailure { ZuneLog.w(TAG, "could not advertise", it) }
    }

    /** This TV's addresses on the local network, for typing in by hand where mDNS does not reach. */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())

    private const val PAIRING_MILLIS = 3 * 60_000L
    private const val READ_TIMEOUT_MILLIS = 10_000
    private const val MAX_AGE_MILLIS = 10 * 60_000L
}
