package com.limelight.ps

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/** What a console returned on a successful registration (3.5). The registration key and the morning are credentials. */
data class PsRegisteredConsole(
    val target: PsTarget,
    /** The registration key bytes (ASCII hex text, ≤ 16 bytes). */
    val registKey: ByteArray,
    /** RP-Key, 16 bytes: the input of the session key derivation. */
    val morning: ByteArray,
    val mac: ByteArray,
    val nickname: String?,
    val keyType: Int?,
)

/** Registration failure reasons the console reports in `RP-Application-Reason` (3.5). */
enum class PsRegistError(val code: Long) {
    FAILED(0x80108b09), INVALID_PSN_ID(0x80108b02), IN_USE(0x80108b10), CRASHED(0x80108b15), RP_VERSION(0x80108b11), UNKNOWN(0x80108bff);

    companion object {
        fun of(code: Long): PsRegistError = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** The PSN account id (3.1): the numeric user id as 8 bytes little-endian; shown and entered as base64. */
object PsAccountId {
    fun fromUserId(userId: String): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(java.lang.Long.parseUnsignedLong(userId)).array()

    fun toBase64(id: ByteArray): String = java.util.Base64.getEncoder().encodeToString(id)

    fun fromBase64(text: String): ByteArray? = runCatching { java.util.Base64.getDecoder().decode(text.trim()) }.getOrNull()?.takeIf { it.size == 8 }
}

/**
 * The registration request (3.4): a 0x1E0-byte filler carrying the aeropause (the ambassador in disguise), then the inner header
 * encrypted with the registration crypt at counter 0. [build] is pure so the test vectors cover it.
 */
class PsRegistrationRequest(val target: PsTarget, val accountId: ByteArray, val pin: Int, val ambassador: ByteArray = random(16)) {
    /** The filler the reference uses ('A'): key0_off = 1, key1_off = 8. */
    private val filler = ByteArray(0x1E0) { 0x41 }
    val key0Off: Int = u(filler[0x18D]) and 0x1F
    val key1Off: Int = u(filler[0x000]) shr 3
    val crypt: RpCrypt = RpCrypt.registration(target, ambassador, pin, key0Off)

    fun aeropause(): ByteArray = ByteArray(16) { i ->
        val a = u(ambassador[i])
        when (target) {
            PsTarget.PS4_8, PsTarget.PS4_9 -> ((a - i - 0x29) xor u(PsConstants.ECHO_B[i])).toByte()
            PsTarget.PS4_10 -> ((a xor u(PsConstants.KEYS_1_PS4[i * 32 + key1Off])) + 0x29 + i).toByte()
            PsTarget.PS5_1 -> ((a xor u(PsConstants.KEYS_1_PS5[i * 32 + key1Off])) - 0x2D + i).toByte()
        }
    }

    fun innerHeader(): String {
        val account = PsAccountId.toBase64(accountId)
        return if (target.pre10) "Client-Type: Windows\r\nNp-AccountId: $account\r\n"
        else "Client-Type: $CLIENT_TYPE\r\nNp-AccountId: $account\r\n"
    }

    fun payload(): ByteArray {
        val out = filler.copyOf()
        val aero = aeropause()
        if (target.pre10) aero.copyInto(out, 0x11C)
        else {
            aero.copyInto(out, 0xC7, 8, 16)
            aero.copyInto(out, 0x191, 0, 8)
        }
        return out + crypt.encrypt(0, innerHeader().toByteArray(Charsets.US_ASCII))
    }

    /** The whole request: the HTTP header as the console expects it, literal quirks included, then the payload. */
    fun bytes(): ByteArray {
        val body = payload()
        val head = buildString {
            append("POST ${target.registPath} HTTP/1.1\r\n HTTP/1.1\r\n")
            append("HOST: 10.0.2.15\r\n")
            append("User-Agent: remoteplay Windows\r\n")
            append("Connection: close\r\n")
            append("Content-Length: ${body.size}\r\n")
            if (target != PsTarget.PS4_8) append("RP-Version: ${target.rpVersion}\r\n")
            append("\r\n")
        }
        return ByteArrayOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(body) }.toByteArray()
    }

    /** Decrypts and reads the body of a 200 answer; null when a required key is missing. */
    fun parseBody(body: ByteArray): PsRegisteredConsole? {
        val text = String(crypt.decrypt(0, body), Charsets.ISO_8859_1)
        val fields = text.split("\r\n").mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim() to line.substring(it + 1).trim() } }.toMap()
        val prefix = if (target.ps5) "PS5" else "PS4"
        val regist = fields["$prefix-RegistKey"]?.let { runCatching { PsHex.decode(it) }.getOrNull() } ?: return null
        val morning = fields["RP-Key"]?.let { runCatching { PsHex.decode(it) }.getOrNull() }?.takeIf { it.size == 16 } ?: return null
        val mac = fields["$prefix-Mac"]?.let { runCatching { PsHex.decode(it) }.getOrNull() }?.takeIf { it.size == 6 } ?: return null
        return PsRegisteredConsole(target, regist.copyOf(minOf(regist.size, 16)), morning, mac, fields["$prefix-Nickname"], fields["RP-KeyType"]?.toIntOrNull())
    }

    companion object {
        /** The Client-Type value of 10.0 and PS5 registrations [LEGAL constant, 3.4]. */
        const val CLIENT_TYPE = "dabfa2ec873de5839bee8d3f4c0239c4282c07c25c6077a2931afcf0adc0d34f"

        private val rng = SecureRandom()
        internal fun random(n: Int) = ByteArray(n).also { rng.nextBytes(it) }
    }
}
