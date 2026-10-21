package com.serkantkn.zeta.data.link

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * How a phone and a TV running Zeta talk on the same network: one short TCP connection at a
 * time, carrying length-prefixed JSON frames. No server anywhere, nothing leaves the network.
 *
 * A pairing is one connection: hello, hello, proof, proof. A notice is one connection: one sealed
 * frame from the phone, one "ok" back from the TV.
 */
internal object LinkProtocol {
    const val VERSION = 1
    const val SERVICE_TYPE = "_zeta._tcp."
    /** The port the TV listens on when it can have it, so an address typed by hand needs no port. */
    const val DEFAULT_PORT = 52741

    fun write(out: DataOutputStream, frame: JSONObject) {
        val bytes = frame.toString().toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun read(input: DataInputStream): JSONObject {
        val size = input.readInt()
        if (size <= 0 || size > MAX_FRAME) throw IOException("frame of $size bytes")
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private const val MAX_FRAME = 64 * 1024
}

/** Who a device is to the other: its id (made once, kept), its name, and its key for this pairing. */
internal data class LinkIdentity(val id: String, val name: String)

/**
 * The pairing handshake, the same for both sides; [phone] says which side this is. Each side
 * sends a hello with a fresh public key, works out the key from the shared secret and the six
 * digits, then proves it has it. The phone proves first, so a wrong code is told by the TV.
 *
 * Returns the pairing: the other device and the key their notices will be sealed with.
 */
internal class PairingHandshake(
    private val own: LinkIdentity,
    private val code: String,
    private val phone: Boolean
) {
    class Result(val peer: LinkIdentity, val key: ByteArray)

    class WrongCode : IOException("wrong code")

    private val keys = LinkCrypto.newKeyPair()

    fun hello(): JSONObject = JSONObject()
        .put("t", "hello")
        .put("v", LinkProtocol.VERSION)
        .put("id", own.id)
        .put("name", own.name)
        .put("pub", LinkCrypto.b64(keys.public.encoded))

    private var peer: LinkIdentity? = null
    private var pairKey: ByteArray? = null
    private var transcript: ByteArray? = null

    /** Takes the other side's hello; afterwards [proof] and [check] can be used. */
    fun receiveHello(frame: JSONObject) {
        if (frame.optString("t") != "hello") throw IOException("expected hello, got ${frame.optString("t")}")
        val theirPub = LinkCrypto.unb64(frame.getString("pub"))
        val theirs = LinkIdentity(frame.getString("id"), frame.optString("name"))
        val ownPub = keys.public.encoded
        // The transcript is written the same way on both sides: phone first, then TV.
        val (phoneId, tvId) = if (phone) own.id to theirs.id else theirs.id to own.id
        val (phonePub, tvPub) = if (phone) ownPub to theirPub else theirPub to ownPub
        val t = LinkCrypto.sha256(phoneId.toByteArray(), tvId.toByteArray(), phonePub, tvPub)
        val shared = LinkCrypto.agree(keys, LinkCrypto.publicKeyOf(theirPub))
        pairKey = LinkCrypto.hkdf(shared, code.toByteArray(), "z link pair".toByteArray() + t)
        transcript = t
        peer = theirs
    }

    fun proof(): JSONObject = JSONObject()
        .put("t", "proof")
        .put("mac", LinkCrypto.b64(mac(if (phone) "phone" else "tv")))

    /** Checks the other side's proof; throws [WrongCode] when it does not match. */
    fun check(frame: JSONObject): Result {
        if (frame.optString("t") == "err") throw WrongCode()
        val expected = mac(if (phone) "tv" else "phone")
        val got = runCatching { LinkCrypto.unb64(frame.getString("mac")) }.getOrNull() ?: throw WrongCode()
        if (!LinkCrypto.sameBytes(expected, got)) throw WrongCode()
        val key = LinkCrypto.hkdf(pairKey!!, ByteArray(0), "z link notices".toByteArray())
        return Result(peer!!, key)
    }

    private fun mac(side: String): ByteArray = LinkCrypto.hmac(pairKey!!, side.toByteArray(), transcript!!)
}

/** What a phone tells its TV. */
internal data class LinkNotice(
    val kind: Kind,
    /** The caller's or sender's name as the phone knows it, or their number. */
    val name: String,
    val number: String = "",
    /** The message, only when the phone was allowed to send it. */
    val text: String? = null,
    /** For a message from another app: the app's name. */
    val app: String? = null,
    val at: Long = System.currentTimeMillis()
) {
    /** [SYSTEM] is the TV's own news (an app put on, a thing pinned): never sent between devices. */
    enum class Kind { CALL, CALL_ENDED, MISSED_CALL, SMS, APP_MESSAGE, SYSTEM }

    fun toJson(): JSONObject = JSONObject()
        .put("k", kind.name)
        .put("name", name)
        .put("num", number)
        .apply { text?.let { put("text", it) } }
        .apply { app?.let { put("app", it) } }
        .put("at", at)

    companion object {
        fun fromJson(json: JSONObject): LinkNotice = LinkNotice(
            kind = Kind.valueOf(json.getString("k")),
            name = json.optString("name"),
            number = json.optString("num"),
            text = json.optString("text").takeIf { json.has("text") },
            app = json.optString("app").takeIf { json.has("app") },
            at = json.optLong("at")
        )
    }
}

/** Sealing a notice for one paired TV, and opening it there. The phone's id is bound in. */
internal object NoticeSeal {
    fun seal(phoneId: String, key: ByteArray, notice: LinkNotice): JSONObject {
        val (iv, sealed) = LinkCrypto.seal(key, notice.toJson().toString().toByteArray(Charsets.UTF_8), phoneId.toByteArray())
        return JSONObject()
            .put("t", "notice")
            .put("id", phoneId)
            .put("iv", LinkCrypto.b64(iv))
            .put("c", LinkCrypto.b64(sealed))
    }

    /** Null when it was not sealed with [key] - another phone's, or tampered with. */
    fun open(frame: JSONObject, key: ByteArray): LinkNotice? = runCatching {
        val plain = LinkCrypto.open(
            key,
            LinkCrypto.unb64(frame.getString("iv")),
            LinkCrypto.unb64(frame.getString("c")),
            frame.getString("id").toByteArray()
        )
        LinkNotice.fromJson(JSONObject(String(plain, Charsets.UTF_8)))
    }.getOrNull()
}
