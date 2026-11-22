package com.limelight.ps

import java.io.ByteArrayOutputStream

/** A minimal protobuf (proto2) writer for the Takion messages DELOS sends (5.4). */
class PsProtoWriter {
    private val out = ByteArrayOutputStream()

    fun uint(field: Int, value: Long): PsProtoWriter { tag(field, 0); varint(value); return this }
    fun bool(field: Int, value: Boolean): PsProtoWriter = uint(field, if (value) 1 else 0)
    fun bytes(field: Int, value: ByteArray): PsProtoWriter { tag(field, 2); varint(value.size.toLong()); out.write(value); return this }
    fun string(field: Int, value: String): PsProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))
    fun message(field: Int, value: PsProtoWriter): PsProtoWriter = bytes(field, value.toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())

    private fun varint(v: Long) {
        var x = v
        while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }
        out.write(x.toInt())
    }
}

/** A minimal protobuf reader: the fields of one message by number, repeated fields kept in order. */
class PsProtoMessage(bytes: ByteArray) {
    private val varints = HashMap<Int, MutableList<Long>>()
    private val lengths = HashMap<Int, MutableList<ByteArray>>()

    init {
        var p = 0
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                val b = bytes[p++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (p < bytes.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> varints.getOrPut(field) { ArrayList() } += varint()
                1 -> { varints.getOrPut(field) { ArrayList() } += readLe(bytes, p, 8); p += 8 }
                2 -> { val n = varint().toInt(); lengths.getOrPut(field) { ArrayList() } += bytes.copyOfRange(p, p + n); p += n }
                5 -> { varints.getOrPut(field) { ArrayList() } += readLe(bytes, p, 4); p += 4 }
                else -> throw IllegalArgumentException("wire type ${key and 7}")
            }
        }
    }

    fun uint(field: Int): Long? = varints[field]?.lastOrNull()
    fun bool(field: Int): Boolean? = uint(field)?.let { it != 0L }
    /** A 64-bit field (wire type 1) read as a double. */
    fun double(field: Int): Double? = uint(field)?.let { java.lang.Double.longBitsToDouble(it) }
    fun bytes(field: Int): ByteArray? = lengths[field]?.lastOrNull()
    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)
    fun message(field: Int): PsProtoMessage? = bytes(field)?.let { PsProtoMessage(it) }
    fun messages(field: Int): List<PsProtoMessage> = lengths[field].orEmpty().map { PsProtoMessage(it) }

    private fun readLe(b: ByteArray, at: Int, n: Int): Long { var v = 0L; for (i in n - 1 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF); return v }
}

/** `TakionMessage` types (5.4). */
object PsMsg {
    const val BIG = 0; const val BANG = 1; const val HEARTBEAT = 3; const val CORRUPTFRAME = 5; const val DISCONNECT = 8
    const val STREAMINFO = 13; const val STREAMINFOACK = 14; const val CONNECTIONQUALITY = 16; const val CONTROLLERCONNECTION = 21
    const val IDRREQUEST = 25
    const val SENKUSHA = 12; const val TAKIONPROTOCOLREQUEST = 31; const val TAKIONPROTOCOLREQUESTACK = 32

    fun simple(type: Int): ByteArray = PsProtoWriter().uint(1, type.toLong()).toByteArray()

    /** SENKUSHA (5.4, 6.4): the command and its payload message in field [field] of SenkushaPayload. */
    fun senkusha(command: Int, field: Int, payload: PsProtoWriter): ByteArray =
        PsProtoWriter().uint(1, SENKUSHA.toLong()).message(14, PsProtoWriter().uint(1, command.toLong()).message(field, payload)).toByteArray()
}
