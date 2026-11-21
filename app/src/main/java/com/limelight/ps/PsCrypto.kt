package com.limelight.ps

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Hex text and bytes. */
internal object PsHex {
    fun decode(hex: String): ByteArray = ByteArray(hex.length / 2) { ((Character.digit(hex[it * 2], 16) shl 4) or Character.digit(hex[it * 2 + 1], 16)).toByte() }
    fun encode(bytes: ByteArray): String = buildString(bytes.size * 2) { bytes.forEach { append("%02x".format(it.toInt() and 0xFF)) } }
}

/**
 * Which console and which RP-Version it speaks (docs/research/ps-remote-play-protocol.md, 1.3). The HMAC key of the RP crypt
 * follows the target: PS5 1.0 its own, PS4 8.0/9.0 the old one, everything else the PS4 one.
 */
enum class PsTarget(val rpVersion: String, val ps5: Boolean, val registPath: String, val sessionPrefix: String, val takionVersion: Int) {
    PS4_8("8.0", false, "/sce/rp/regist", "/sce/rp/session", 9),
    PS4_9("9.0", false, "/sce/rp/regist", "/sce/rp/session", 9),
    PS4_10("10.0", false, "/sie/ps4/rp/sess/rgst", "/sie/ps4/rp/sess/", 9),
    PS5_1("1.0", true, "/sie/ps5/rp/sess/rgst", "/sie/ps5/rp/sess/", 12);

    val pre10: Boolean get() = this == PS4_8 || this == PS4_9

    internal val hmacKey: ByteArray get() = when (this) {
        PS5_1 -> PsConstants.HMAC_KEY_PS5
        PS4_8, PS4_9 -> PsConstants.HMAC_KEY_PS4_PRE10
        PS4_10 -> PsConstants.HMAC_KEY_PS4
    }

    companion object {
        /** The target for a discovery answer: PS5 by its protocol version, PS4 by its firmware code (1.3). */
        fun fromDiscovery(protocolVersion: String?, systemVersion: String?): PsTarget {
            val firmware = systemVersion?.toLongOrNull() ?: 0L
            return when {
                protocolVersion == PsDiscovery.PS5_PROTOCOL -> PS5_1
                firmware >= 8_000_000L -> PS4_10
                firmware >= 7_000_000L -> PS4_9
                else -> PS4_8
            }
        }

        /** The target for an RP-Version string the console sent back; "5.0" counts as 9.0 (1.3). */
        fun fromRpVersion(version: String, ps5: Boolean): PsTarget? = when (version.trim()) {
            "1.0" -> if (ps5) PS5_1 else null
            "8.0" -> PS4_8
            "9.0", "5.0" -> PS4_9
            "10.0" -> PS4_10
            else -> null
        }
    }
}

/**
 * The "RP crypt" of registration, session and ctrl (3.2): AES-128-CFB with key [bright]; the IV of counter n is the first 16 bytes
 * of HMAC-SHA256(target key, ambassador ‖ n as 8 bytes BE). Every call starts a fresh stream at its counter.
 */
class RpCrypt internal constructor(internal val target: PsTarget, internal val bright: ByteArray, internal val ambassador: ByteArray) {
    fun iv(counter: Long): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(target.hmacKey, "HmacSHA256"))
        mac.update(ambassador)
        mac.update(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(counter).array())
        return mac.doFinal().copyOf(16)
    }

    fun encrypt(counter: Long, data: ByteArray): ByteArray = cfb(Cipher.ENCRYPT_MODE, counter, data)

    fun decrypt(counter: Long, data: ByteArray): ByteArray = cfb(Cipher.DECRYPT_MODE, counter, data)

    private fun cfb(mode: Int, counter: Long, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CFB/NoPadding")
        cipher.init(mode, SecretKeySpec(bright, "AES"), IvParameterSpec(iv(counter)))
        return cipher.doFinal(data)
    }

    companion object {
        /** The session crypt from the console's nonce and the stored morning (4.2). */
        fun session(target: PsTarget, nonce: ByteArray, morning: ByteArray): RpCrypt {
            require(nonce.size == 16 && morning.size == 16)
            val amb = ByteArray(16)
            val bright = ByteArray(16)
            when (target) {
                PsTarget.PS4_8, PsTarget.PS4_9 -> {
                    val echoA = PsConstants.ECHO_A
                    val echoB = PsConstants.ECHO_B
                    for (i in 0 until 16) {
                        amb[i] = ((u(nonce[i]) - i - 0x27) xor u(echoA[i])).toByte()
                        bright[i] = (((u(morning[i]) - i + 0x34) xor u(echoB[i])) xor u(nonce[i])).toByte()
                    }
                }
                PsTarget.PS4_10 -> {
                    val a = row(PsConstants.KEYS_A_PS4, u(nonce[0]) shr 3)
                    val b = row(PsConstants.KEYS_B_PS4, u(nonce[7]) shr 3)
                    for (i in 0 until 16) {
                        amb[i] = ((u(nonce[i]) + 0x36 + i) xor u(a[i])).toByte()
                        bright[i] = (((u(b[i]) xor u(morning[i])) + 0x21 + i) xor u(nonce[i])).toByte()
                    }
                }
                PsTarget.PS5_1 -> {
                    val a = row(PsConstants.KEYS_A_PS5, u(nonce[0]) shr 3)
                    val b = row(PsConstants.KEYS_B_PS5, u(nonce[7]) shr 3)
                    for (i in 0 until 16) {
                        amb[i] = ((u(nonce[i]) - 0x2D - i) xor u(a[i])).toByte()
                        bright[i] = (((u(morning[i]) + 0x18 + i) xor u(nonce[i])) xor u(b[i])).toByte()
                    }
                }
            }
            return RpCrypt(target, bright, amb)
        }

        /** The registration crypt (3.4): [pin] is the 8-digit number; [key0Off] comes from the payload filler. */
        fun registration(target: PsTarget, ambassador: ByteArray, pin: Int, key0Off: Int): RpCrypt {
            val bright = if (target.pre10) PsConstants.REGIST_AES_KEY_PS4_PRE10.copyOf()
            else {
                val table = if (target.ps5) PsConstants.KEYS_0_PS5 else PsConstants.KEYS_0_PS4
                ByteArray(16) { table[it * 32 + key0Off] }
            }
            val pinBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(pin).array()
            val at = if (target.pre10) 0 else 12
            for (i in 0 until 4) bright[at + i] = (bright[at + i].toInt() xor pinBytes[i].toInt()).toByte()
            return RpCrypt(target, bright, ambassador.copyOf())
        }

        private fun row(table: ByteArray, index: Int) = table.copyOfRange(index * 16, index * 16 + 16)
    }
}

internal fun u(b: Byte): Int = b.toInt() and 0xFF
