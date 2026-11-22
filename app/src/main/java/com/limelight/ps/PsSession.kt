package com.limelight.ps

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The video the client asks for (6.2, 7.2). */
enum class PsCodec(val streamingType: Int, val launchName: String, val hdr: Boolean) {
    H264(1, "avc", false), HEVC(2, "hevc", false), HEVC_HDR(3, "hevc", true)
}

data class PsStreamRequest(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val bitrateKbps: Int = 50_000,
    val codec: PsCodec = PsCodec.HEVC,
    /** Senkusha's results (6.4): the largest packet from and to the console, the round trip; the spec's fallback without it. */
    val mtuIn: Int = 1454,
    val mtuOut: Int = 1454,
    val rttMs: Int = 1,
    /** "Don't fragment" on the stream socket: only with measured MTUs (5.1). */
    val dontFragment: Boolean = false,
)

/** The pieces of the session start that are pure computation: ctrl headers (4.4), the DID (4.3), the launch spec (6.2), BIG (6.1). */
object PsSession {
    private val rng = SecureRandom()

    /** A fresh device id: a fixed 10-byte prefix, 16 random bytes, 6 zero bytes (4.3). */
    fun newDid(random: ByteArray = ByteArray(16).also { rng.nextBytes(it) }): ByteArray =
        byteArrayOf(0x00, 0x18, 0x00, 0x00, 0x00, 0x07, 0x00, 0x40, 0x00, 0x80.toByte()) + random + ByteArray(6)

    /** The registration key NUL-padded to 16 bytes. */
    fun registKey16(registKey: ByteArray): ByteArray = registKey.copyOf(16)

    /** The hex form of the registration key up to its first NUL, for RP-Registkey (4.1). */
    fun registKeyHeader(registKey: ByteArray): String {
        val end = registKey.indexOf(0).let { if (it < 0) registKey.size else it }
        return PsHex.encode(registKey.copyOf(end))
    }

    /** The encrypted ctrl header values (4.4), in local-counter order. Returns header name → base64 value and the next counter. */
    fun ctrlHeaders(crypt: RpCrypt, registKey: ByteArray, did: ByteArray, codec: PsCodec): Pair<List<Pair<String, String>>, Long> {
        val b64 = Base64.getEncoder()
        val out = ArrayList<Pair<String, String>>()
        var counter = 0L
        fun add(name: String, plain: ByteArray) { out += name to b64.encodeToString(crypt.encrypt(counter++, plain)) }
        add("RP-Auth", registKey16(registKey))
        add("RP-Did", did)
        add("RP-OSType", "Win10.0.0".toByteArray(Charsets.US_ASCII) + 0.toByte())
        val target = crypt.target
        if (target == PsTarget.PS4_10 || target.ps5) add("RP-StartBitrate", ByteArray(4))
        if (target.ps5) add("RP-StreamingType", byteArrayOf(codec.streamingType.toByte(), 0, 0, 0))
        return out to counter
    }

    fun launchSpecJson(target: PsTarget, request: PsStreamRequest, handshakeKey: ByteArray): String {
        val ps5 = target.ps5
        val gameSpec = "{\"model\":\"bravia_tv\",\"platform\":\"android\",\"audioChannels\":\"5.1\",\"language\":\"sp\",\"acceptButton\":\"X\"," +
            "\"connectedControllers\":[\"xinput\",\"ds3\",\"ds4\"],\"yuvCoefficient\":\"bt601\",\"videoEncoderProfile\":\"hw4.1\"," +
            "\"audioEncoderProfile\":\"audio1\"" + (if (ps5) ",\"adaptiveStreamMode\": \"resize\"" else "") + "}"
        return buildString {
            append("{\"sessionId\":\"sessionId4321\",")
            append("\"streamResolutions\":[{\"resolution\":{\"width\":${request.width},\"height\":${request.height}},\"maxFps\":${request.fps},\"score\":10}],")
            append("\"network\":{\"bwKbpsSent\":${request.bitrateKbps},\"bwLoss\":0.001000,\"mtu\":${request.mtuIn},\"rtt\":${request.rttMs},\"ports\":[53,2053]},")
            append("\"slotId\":1,")
            append("\"appSpecification\":{\"minFps\":30,\"minBandwidth\":0,\"extTitleId\":\"ps3\",\"version\":1,\"timeLimit\":1,\"startTimeout\":100,\"afkTimeout\":100,\"afkTimeoutDisconnect\":100},")
            append("\"konan\":{\"ps3AccessToken\":\"accessToken\",\"ps3RefreshToken\":\"refreshToken\"},")
            append("\"requestGameSpecification\":$gameSpec,")
            append("\"userProfile\":{\"onlineId\":\"psnId\",\"npId\":\"npId\",\"region\":\"US\",\"languagesUsed\":[\"en\",\"jp\"]},")
            if (ps5) {
                append("\"videoCodec\":\"${request.codec.launchName}\",")
                append("\"dynamicRange\":\"${if (request.codec.hdr) "HDR" else "SDR"}\",")
            }
            append("\"handshakeKey\":\"${Base64.getEncoder().encodeToString(handshakeKey)}\"}")
        }
    }

    /** The launch spec as BIG carries it (6.2): JSON ‖ NUL XOR the RP-crypt keystream of zeros at counter 0, base64. */
    fun launchSpecEncrypted(crypt: RpCrypt, json: String): String {
        val plain = json.toByteArray(Charsets.UTF_8) + 0.toByte()
        val ks = crypt.encrypt(0, ByteArray(plain.size))
        return Base64.getEncoder().encodeToString(ByteArray(plain.size) { (plain[it].toInt() xor ks[it].toInt()).toByte() })
    }

    fun big(target: PsTarget, sessionKey: String, launchSpec: String, ecdh: PsEcdh, handshakeKey: ByteArray): ByteArray {
        val payload = PsProtoWriter()
            .uint(1, target.takionVersion.toLong())
            .string(2, sessionKey)
            .string(3, launchSpec)
            .bytes(4, ByteArray(4))
            .bytes(5, ecdh.publicKey)
            .bytes(6, ecdh.signature(handshakeKey))
        return PsProtoWriter().uint(1, PsMsg.BIG.toLong()).message(2, payload).toByteArray()
    }
}

/** ECDH on secp256k1 (6.1); Android's own providers lack this curve, so BouncyCastle's lightweight API does the maths. */
class PsEcdh(privateKey: BigInteger? = null) {
    private val domain = SECNamedCurves.getByName("secp256k1").let { ECDomainParameters(it.curve, it.g, it.n, it.h) }
    private val d: BigInteger = privateKey ?: run {
        val rng = SecureRandom()
        var k: BigInteger
        do { k = BigInteger(256, rng) } while (k.signum() == 0 || k >= domain.n)
        k
    }

    /** The uncompressed public point, 65 bytes (04 ‖ X ‖ Y). */
    val publicKey: ByteArray = domain.g.multiply(d).normalize().getEncoded(false)

    /** HMAC-SHA256(handshake_key, public key): the signature BIG carries. */
    fun signature(handshakeKey: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(handshakeKey, "HmacSHA256")); doFinal(publicKey)
    }

    /** The 32-byte X coordinate of d · peer. */
    fun sharedSecret(peerPublic: ByteArray): ByteArray {
        val point: ECPoint = domain.curve.decodePoint(peerPublic).multiply(d).normalize()
        val x = point.affineXCoord.toBigInteger().toByteArray()
        return ByteArray(32).also { out -> val src = if (x.size > 32) x.copyOfRange(x.size - 32, x.size) else x; src.copyInto(out, 32 - src.size) }
    }
}
