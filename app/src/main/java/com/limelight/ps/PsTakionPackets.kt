package com.limelight.ps

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Takion CONTROL datagrams (5.2): 0x00, tag, GMAC, key position, then one chunk (type, flags, length = payload + 4). */
object PsTakionPackets {
    const val CHUNK_DATA = 0x00
    const val CHUNK_INIT = 0x01
    const val CHUNK_INIT_ACK = 0x02
    const val CHUNK_DATA_ACK = 0x03
    const val CHUNK_COOKIE = 0x0A
    const val CHUNK_COOKIE_ACK = 0x0B
    const val HEADER = 0x11
    const val A_RWND = 0x19000

    fun control(tag: Int, keyPosition: Int, chunkType: Int, flags: Int, payload: ByteArray): ByteArray =
        ByteBuffer.allocate(HEADER + payload.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(0); putInt(tag); putInt(0); putInt(keyPosition)
            put(chunkType.toByte()); put(flags.toByte()); putShort((payload.size + 4).toShort()); put(payload)
        }.array()

    fun init(ownTag: Int): ByteArray = control(0, 0, CHUNK_INIT, 0, ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
        putInt(ownTag); putInt(A_RWND); putShort(100); putShort(100); putInt(ownTag)
    }.array())

    fun cookie(consoleTag: Int, cookie: ByteArray): ByteArray = control(consoleTag, 0, CHUNK_COOKIE, 0, cookie)

    /** A DATA chunk's payload (5.3): TSN, channel, 0, then (first fragment only) the data type and the data. */
    fun dataPayload(tsn: Int, channel: Int, dataType: Int?, data: ByteArray): ByteArray =
        ByteBuffer.allocate(8 + (if (dataType != null) 1 else 0) + data.size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(tsn); putShort(channel.toShort()); putShort(0); if (dataType != null) put(dataType.toByte()); put(data)
        }.array()

    fun dataAck(consoleTag: Int, keyPosition: Int, cumulativeTsn: Int): ByteArray = control(consoleTag, keyPosition, CHUNK_DATA_ACK, 0,
        ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply { putInt(cumulativeTsn); putInt(A_RWND); putShort(0); putShort(0) }.array())

    /** A received CONTROL datagram; null when malformed. */
    data class Control(val tag: Int, val gmac: Int, val keyPosition: Int, val chunkType: Int, val flags: Int, val payload: ByteArray)

    fun parseControl(d: ByteArray, length: Int = d.size): Control? {
        if (length < HEADER || d[0].toInt() and 0x0F != 0) return null
        val b = ByteBuffer.wrap(d, 0, length).order(ByteOrder.BIG_ENDIAN)
        b.get()
        val tag = b.int; val gmac = b.int; val pos = b.int
        val type = u(b.get()); val flags = u(b.get()); val len = b.short.toInt() and 0xFFFF
        if (len < 4 || 0x0D + len != length) return null
        return Control(tag, gmac, pos, type, flags, d.copyOfRange(HEADER, 0x0D + len))
    }

    /**
     * Splits a message into DATA chunk payloads (5.3): with m = min(mtu) − 50 the first carries the data type and m − 26 bytes,
     * the rest m − 25; the flags are 0 except on the last, which is 1.
     */
    fun fragments(data: ByteArray, mtu: Int): List<Pair<Int, ByteArray>> {
        val m = mtu - 50
        if (data.size <= m - 26) return listOf(1 to data)
        val out = ArrayList<Pair<Int, ByteArray>>()
        var at = 0
        var first = true
        while (at < data.size) {
            val n = minOf(if (first) m - 26 else m - 25, data.size - at)
            out += 0 to data.copyOfRange(at, at + n)
            at += n
            first = false
        }
        out[out.size - 1] = 1 to out.last().second
        return out
    }
}
