package com.limelight.ps

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A received AV datagram after decryption (5.6). */
class PsAvPacket(
    val video: Boolean,
    val packetIndex: Int,
    val frameIndex: Int,
    val unitIndex: Int,
    val unitsInFrame: Int,
    /** Video: FEC units; audio: the low 16 bits of the unit word. */
    val fecOrAudioDesc: Int,
    val codec: Int,
    val keyPosition: Int,
    val adaptiveIndex: Int,
    /** Audio v12 only: 0x02 marks DualSense haptics. */
    val audioMarker: Int,
    val payload: ByteArray,
)

object PsAvParser {
    /** Offset of the payload after the header, the AV header layout of Takion v9/v12 (5.6) [Q1: offsets from the spec, unverified]. */
    fun payloadOffset(d: ByteArray, v12: Boolean): Int {
        val nalu = d[0].toInt() and 0x10 != 0
        val video = d[0].toInt() and 0x0F == 2
        return if (video) (if (nalu) 0x18 else 0x15)
        else { val base = if (nalu) 0x16 else 0x13; if (v12) base + 1 else base }
    }

    /**
     * Checks the MAC and decrypts in place; [crypt] null means the keys do not exist yet (no check, no decryption).
     * Returns null on a bad MAC or a short datagram.
     */
    fun parse(d: ByteArray, length: Int, v12: Boolean, crypt: PsGkCrypt?, keyPosition64: Long): PsAvPacket? {
        if (length < 0x15) return null
        val b = ByteBuffer.wrap(d, 0, length).order(ByteOrder.BIG_ENDIAN)
        val type = d[0].toInt() and 0x0F
        val video = type == 2
        val packetIndex = b.getShort(1).toInt() and 0xFFFF
        val frameIndex = b.getShort(3).toInt() and 0xFFFF
        val unit = b.getInt(5)
        val codec = u(d[9])
        val keyPos = b.getInt(0x0E)
        val start = payloadOffset(d, v12)
        if (start > length) return null
        if (crypt != null) {
            // The MAC field is zeroed in place for the check and put back: no copy of the datagram ([d] is this packet's own array)
            val mac = b.getInt(0x0A)
            b.putInt(0x0A, 0)
            val expected = crypt.gmac(if (length == d.size) d else d.copyOf(length), keyPosition64)
            b.putInt(0x0A, mac)
            if (ByteBuffer.wrap(expected).int != mac) return null
            crypt.apply(d, start, length - start, keyPosition64 + 16)
        }
        val marker = if (!video && v12) u(d[start - 1]) else 0
        return if (video) PsAvPacket(true, packetIndex, frameIndex, unit ushr 21, ((unit ushr 10) and 0x7FF) + 1, unit and 0x3FF, codec, keyPos,
            u(d[0x14]) shr 5, 0, d.copyOfRange(start, length))
        else PsAvPacket(false, packetIndex, frameIndex, unit ushr 24, ((unit ushr 16) and 0xFF) + 1, unit and 0xFFFF, codec, keyPos, 0, marker, d.copyOfRange(start, length))
    }
}

/**
 * Rebuilds video frames from units (7.3). Units are stored at index × stride; when enough arrived the frame is flushed, FEC
 * restores missing source units, and the frame is the concatenation of each source unit's content (bytes 2.. after its padding word).
 */
class PsFrameAssembler {
    private var frameIndex = -1
    private var sources = 0
    private var fec = 0
    private var unitSize = 0
    private var units: Array<ByteArray?> = emptyArray()
    private var received = 0
    var lastComplete = -1
        private set

    /** Statistics for the congestion report: units received and missing since the last call. */
    val unitsReceived = java.util.concurrent.atomic.AtomicInteger()
    /** Units of frames FEC could not rebuild, and of whole frames that never came: the loss the picture shows. */
    val visibleLoss = java.util.concurrent.atomic.AtomicInteger()
    val unitsLost = java.util.concurrent.atomic.AtomicInteger()

    sealed interface Result {
        /** [receivedUs]: when the frame's first unit arrived (System.nanoTime / 1000), so the statistics include reassembly. */
        class Frame(val index: Int, val data: ByteArray, val adaptiveIndex: Int, val recovered: Boolean, val receivedUs: Long) : Result
        class Lost(val from: Int, val to: Int) : Result
    }

    private var adaptive = 0

    private var flushedIndex = -1

    /** Units expected and received for the frame being counted; settled when the next frame starts, so late parities count. */
    private var statTotal = 0
    private var statReceived = 0

    /** Adds a unit; returns a finished frame or a loss range when one becomes known. */
    fun add(p: PsAvPacket): List<Result> {
        val out = ArrayList<Result>()
        // Late units (parities) of a frame that is already out still count as received
        if (p.frameIndex == flushedIndex) {
            statReceived++
            // A unit of a frame that went out short of units: it was late (reordered across the frame border), not lost
            if (flushedShort) lateUnits.incrementAndGet()
            return out
        }
        if (p.frameIndex != frameIndex) {
            if (frameIndex >= 0 && received > 0) { flushedShort = received < sources; flush()?.let { out += it } }
            begin(p)
        }
        if (p.unitIndex >= units.size || p.payload.size > unitSize || units[p.unitIndex] != null) return out
        units[p.unitIndex] = if (p.payload.size == unitSize) p.payload else p.payload.copyOf(unitSize)
        received++
        statReceived++
        // S units of any kind are enough: missing sources come back from the parities. The parities after it are not late
        // (the flag was left from the last short frame, so every parity counted as a reordered unit)
        if (received >= sources) { flushedShort = false; flush()?.let { out += it } }
        return out
    }

    private fun begin(p: PsAvPacket) {
        unitsReceived.addAndGet(statReceived)
        unitsLost.addAndGet(maxOf(0, statTotal - statReceived))
        statTotal = p.unitsInFrame
        statReceived = 0
        val previous = lastComplete
        frameIndex = p.frameIndex
        beganUs = System.nanoTime() / 1000
        fec = p.fecOrAudioDesc
        sources = p.unitsInFrame - fec
        val parities = maxOf(1, fec)
        unitSize = if (p.unitIndex < sources && p.payload.size >= 2) p.payload.size + ((u(p.payload[0]) shl 8) or u(p.payload[1])) else p.payload.size
        units = arrayOfNulls(sources + parities)
        received = 0
        adaptive = p.adaptiveIndex
        if (previous >= 0 && ((frameIndex - previous) and 0xFFFF) > 1) {
            pendingLoss = ((previous + 1) and 0xFFFF) to ((frameIndex - 1) and 0xFFFF)
            // Whole frames missing, each counted with this frame's size
            visibleLoss.addAndGet((((frameIndex - previous) and 0xFFFF) - 1) * p.unitsInFrame)
        }
    }

    private var pendingLoss: Pair<Int, Int>? = null

    private fun flush(): Result? {
        if (frameIndex < 0) return null
        val idx = frameIndex
        frameIndex = -1
        flushedIndex = idx
        val missingSources = (0 until sources).count { units[it] == null }
        var recovered = false
        if (missingSources > 0) {
            if (!PsFec.decode(units, sources, units.size - sources)) { visibleLoss.addAndGet(units.size - received); return Result.Lost(idx, idx) }
            recovered = true
        }
        // The frame is each source unit's content: measured first, then copied once into an array of its size
        var total = 0
        for (i in 0 until sources) {
            val unit = units[i]!!
            val length = unitSize - ((u(unit[0]) shl 8) or u(unit[1]))
            if (length < 2 || length > unit.size) return Result.Lost(idx, idx)
            total += length - 2
        }
        val frame = ByteArray(total)
        var at = 0
        for (i in 0 until sources) {
            val unit = units[i]!!
            val n = unitSize - ((u(unit[0]) shl 8) or u(unit[1])) - 2
            unit.copyInto(frame, at, 2, 2 + n)
            at += n
        }
        lastComplete = idx
        return Result.Frame(idx, frame, adaptive, recovered, beganUs)
    }

    private var beganUs = 0L
    private var flushedShort = false

    /** Units that arrived after their frame was flushed without them (for the log: whether a reorder window would help). */
    val lateUnits = java.util.concurrent.atomic.AtomicInteger()

    /** A loss range noticed at the start of a frame (the frame index jumped), consumed once. */
    fun takeLoss(): Pair<Int, Int>? = pendingLoss.also { pendingLoss = null }
}
