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
    private val gmacKeys = HashMap<Long, ByteArray>()
    private val gmacSpecs = HashMap<Long, SecretKeySpec>()

    /**
     * Per thread (the receive thread, the timers, input and the microphone use one direction at the same time; a Cipher is not
     * thread-safe): the AES-ECB cipher and the buffers of counter blocks and keystream, grown as needed and reused, so a
     * packet's keystream is one cipher call instead of one per 16 bytes (Fable's review, 2026-11-23).
     */
    private class Work(val ecb: Cipher) {
        var counters = ByteArray(2048)
        var stream = ByteArray(2048)
        var gcm: Cipher? = null
    }
    private val work: ThreadLocal<Work>

    init {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        mac.update(byteArrayOf(1, index.toByte(), 0))
        mac.update(handshakeKey)
        mac.update(byteArrayOf(1, 0))
        val h = mac.doFinal()
        key = h.copyOfRange(0, 16)
        iv = h.copyOfRange(16, 32)
        val aes = SecretKeySpec(key, "AES")
        work = ThreadLocal.withInitial { Work(Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, aes) }) }
    }

    /** The keystream covering [length] bytes from [position], in the thread's buffer from offset [position] mod 16. */
    private fun fill(w: Work, position: Long, length: Int): ByteArray {
        val first = position / 16
        val blocks = ((position % 16).toInt() + length + 15) / 16
        val bytes = blocks * 16
        if (w.counters.size < bytes) { w.counters = ByteArray(bytes); w.stream = ByteArray(bytes) }
        for (b in 0 until blocks) addLeInto(iv, first + b, w.counters, b * 16)
        w.ecb.doFinal(w.counters, 0, bytes, w.stream, 0)
        return w.stream
    }

    /** [length] keystream bytes starting at byte position [position]. */
    fun keystream(position: Long, length: Int): ByteArray {
        val w = work.get()!!
        val from = (position % 16).toInt()
        return fill(w, position, length).copyOfRange(from, from + length)
    }

    /** XOR in place with the keystream at [position] (encrypt and decrypt are the same). */
    fun apply(data: ByteArray, offset: Int, length: Int, position: Long) {
        val w = work.get()!!
        val ks = fill(w, position, length)
        val from = (position % 16).toInt()
        for (i in 0 until length) data[offset + i] = (data[offset + i].toInt() xor ks[from + i].toInt()).toByte()
    }

    fun gmacKey(index: Long): ByteArray = synchronized(gmacKeys) {
        gmacKeys[index] ?: run {
            val sha = MessageDigest.getInstance("SHA-256")
            val k = if (index == 0L) fold(sha.digest(key + iv)) else fold(sha.digest(gmacKey(0) + addLe(iv, index * 44910)))
            gmacKeys[index] = k
            k
        }
    }

    private fun gmacSpec(index: Long): SecretKeySpec = synchronized(gmacKeys) {
        gmacSpecs.getOrPut(index) { SecretKeySpec(gmacKey(index), "AES") }
    }

    /** The 4-byte MAC of [datagram] (its MAC and, for CONTROL/CONGESTION, key-position fields already zeroed) at [position]. */
    fun gmac(datagram: ByteArray, position: Long): ByteArray {
        val keyIndex = (maxOf(position, 1L) - 1) / 45000
        val w = work.get()!!
        val params = GCMParameterSpec(128, addLe(iv, position / 16))
        // The thread's GCM cipher is reused; a provider that refuses a key and IV pair it saw before gets a fresh one
        val cipher = w.gcm?.takeIf { runCatching { it.init(Cipher.ENCRYPT_MODE, gmacSpec(keyIndex), params) }.isSuccess }
            ?: Cipher.getInstance("AES/GCM/NoPadding").also { it.init(Cipher.ENCRYPT_MODE, gmacSpec(keyIndex), params); w.gcm = it }
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

        /** [base] + [n] as in [addLe], written into [out] at [at] without allocating. */
        private fun addLeInto(base: ByteArray, n: Long, out: ByteArray, at: Int) {
            var carry = n.toULong()
            var c = 0uL
            for (i in 0 until 16) {
                val sum = u(base[i]).toULong() + (carry and 0xFFuL) + c
                out[at + i] = sum.toByte()
                c = sum shr 8
                carry = carry shr 8
            }
        }
    }
}
