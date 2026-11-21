package com.limelight.ps

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The stream encryption of one direction (5.5): index 2 is what the client sends, index 3 what the console sends.
 * key ‖ iv = HMAC-SHA256(secret, 01 ‖ index ‖ 00 ‖ handshake_key ‖ 01 00). The keystream is AES-ECB of iv + n (iv a 128-bit
 * little-endian integer); the 4-byte MAC is AES-GCM over the datagram with a per-45000-bytes key and the IV iv + ⌊p/16⌋.
 */
class PsGkCrypt(index: Int, handshakeKey: ByteArray, secret: ByteArray) {
    val key: ByteArray
    val iv: ByteArray
    private val ecb: Cipher
    private val gmacKeys = HashMap<Long, ByteArray>()

    init {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(byteArrayOf(1, index.toByte(), 0))
        mac.update(handshakeKey)
        mac.update(byteArrayOf(1, 0))
        val h = mac.doFinal()
        key = h.copyOfRange(0, 16)
        iv = h.copyOfRange(16, 32)
        ecb = Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
    }

    /** [length] keystream bytes starting at byte position [position]. */
    fun keystream(position: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        var p = position
        var o = 0
        while (o < length) {
            val block = ecb.doFinal(addLe(iv, p / 16))
            val from = (p % 16).toInt()
            val n = minOf(16 - from, length - o)
            block.copyInto(out, o, from, from + n)
            o += n
            p += n
        }
        return out
    }

    /** XOR in place with the keystream at [position] (encrypt and decrypt are the same). */
    fun apply(data: ByteArray, offset: Int, length: Int, position: Long) {
        val ks = keystream(position, length)
        for (i in 0 until length) data[offset + i] = (data[offset + i].toInt() xor ks[i].toInt()).toByte()
    }

    fun gmacKey(index: Long): ByteArray = gmacKeys.getOrPut(index) {
        val sha = MessageDigest.getInstance("SHA-256")
        if (index == 0L) fold(sha.digest(key + iv))
        else fold(sha.digest(gmacKey(0) + addLe(iv, index * 44910)))
    }

    /** The 4-byte MAC of [datagram] (its MAC and, for CONTROL/CONGESTION, key-position fields already zeroed) at [position]. */
    fun gmac(datagram: ByteArray, position: Long): ByteArray {
        val keyIndex = (maxOf(position, 1L) - 1) / 45000
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(gmacKey(keyIndex), "AES"), GCMParameterSpec(128, addLe(iv, position / 16)))
        cipher.updateAAD(datagram)
        return cipher.doFinal().copyOf(4)
    }

    companion object {
        private fun fold(x: ByteArray) = ByteArray(16) { (x[it].toInt() xor x[it + 16].toInt()).toByte() }

        /** [base] as a 128-bit little-endian integer plus [n], modulo 2^128. */
        internal fun addLe(base: ByteArray, n: Long): ByteArray {
            val out = base.copyOf()
            var carry = n.toULong()
            var i = 0
            var c = 0uL
            while (i < 16 && (carry != 0uL || c != 0uL)) {
                val sum = u(out[i]).toULong() + (carry and 0xFFuL) + c
                out[i] = sum.toByte()
                c = sum shr 8
                carry = carry shr 8
                i++
            }
            return out
        }
    }
}
