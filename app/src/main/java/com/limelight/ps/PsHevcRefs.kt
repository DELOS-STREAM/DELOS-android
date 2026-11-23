package com.limelight.ps

import android.util.Log

/**
 * HEVC reference rewrite (spec 7.3.1, F1, owner 2026-11-23): after a lost frame the following P pictures reference a picture the
 * decoder never got. The console writes each P slice's reference picture set in its header with one entry marked used; when that
 * picture was not delivered, the used flag moves to an older listed picture the decoder holds (a flip of 1-bit flags, no length
 * change of any field), so the picture decodes from it instead of the whole stream freezing until a keyframe. It can show
 * artefacts until the console references a good picture again. Every assumption about the console's headers is checked: on any
 * mismatch the frame passes unchanged and the feeder falls back to waiting for a keyframe.
 */
internal class PsHevcRefs {
    /** The SPS and PPS facts the rewrite needs; null while they are missing or do not fit the rewrite (then it stays off). */
    private class Params(val pocBits: Int, val addressBits: Int, val dependentSlices: Boolean, val outputFlag: Boolean, val extraBits: Int)

    private var sps: IntArray? = null   // pocBits, addressBits, valid (1)
    private var pps: Params? = null
    private var params: Params? = null
    private var spsLogged = false
    private var pFramesLogged = 0

    // The pictures given to the decoder since the last keyframe, by the feeder's frame number (newest last). Numbers, not the
    // picture order count: its LSBs are only 4 bits on the PS5 (logged 2026-11-23), so a POC repeats every 16 frames and a lost
    // picture would match an older delivered one with the same LSBs
    private val delivered = ArrayDeque<Int>()

    // Per 10 s for the log: frames whose used entry was not the newest (the console recovered by itself), frames rewritten,
    // frames dropped for want of a held reference
    var consoleRecovered = 0
    var rewritten = 0
    var dropped = 0

    /** Whether the rewrite can run: the parameter sets fit it. */
    val usable: Boolean get() = params != null

    /** The parameter sets of the stream ([nal] with its start code; VPS ignored). */
    fun parameterSet(nal: ByteArray, type: Int) {
        val rbsp = unescape(nal, Nal.payloadOffset(nal) + 2, nal.size)
        when (type) {
            33 -> sps = runCatching { parseSps(rbsp) }.getOrNull()
            34 -> pps = runCatching {
                val r = Nal.BitReader(rbsp)
                r.ue(); r.ue()
                val dependent = r.bit() == 1
                val output = r.bit() == 1
                Params(0, 0, dependent, output, r.bits(3))
            }.getOrNull()
        }
        val s = sps
        val p = pps
        params = if (s != null && p != null && s[2] == 1) Params(s[0], s[1], p.dependentSlices, p.outputFlag, p.extraBits) else null
    }

    /**
     * A picture before the decoder ([slices] its NALs with start codes, [number] the feeder's frame number, which rises by one per
     * frame index as the POC does): P slices possibly rewritten in place; false drops it.
     */
    fun process(slices: MutableList<ByteArray>, number: Int): Boolean {
        val p = params ?: return true
        val first = slices.firstOrNull { nalType(it) in 0..21 } ?: return true
        val header = runCatching { parseSlice(first, p) }.getOrNull() ?: return true
        if (header.intra) { delivered.clear(); deliver(number); return true }
        if (!header.p) return true
        // The frame number of each listed entry, newest first (entry i is Σ deltas frames back)
        var back = 0
        val numbers = IntArray(header.deltas.size) { i -> back += header.deltas[i]; number - back }
        if (pFramesLogged < 3) {
            pFramesLogged++
            Log.i(TAG, "P frame: poc ${header.poc}, ${slices.size} slice NALs, entries ${header.deltas.toList()} used ${header.used.toList()}")
        }
        val r = header.used.indexOfFirst { it }
        if (r < 0) { deliver(number); return true }
        if (r > 0) consoleRecovered++
        if (numbers[r] in delivered) { deliver(number); return true }
        // The used picture never reached the decoder: the next older listed picture it holds
        val j = (r + 1 until numbers.size).firstOrNull { numbers[it] in delivered }
        if (j == null) { dropped++; return false }
        for (k in slices.indices) {
            val nal = slices[k]
            if (nalType(nal) !in 0..21) continue
            val h = runCatching { parseSlice(nal, p) }.getOrNull() ?: return true
            if (!h.p || h.flagBits.size <= j) continue
            slices[k] = flip(nal, h.flagBits, j)
        }
        rewritten++
        deliver(number)
        return true
    }

    private fun deliver(number: Int) {
        delivered.addLast(number)
        while (delivered.size > 16) delivered.removeFirst()
    }

    private class Slice(val p: Boolean, val poc: Int, val deltas: IntArray, val used: BooleanArray, val flagBits: IntArray, val intra: Boolean = false)

    /** The slice segment header (7.3.6.1) up to the explicit short-term reference picture set; null when it does not fit. */
    private fun parseSlice(nal: ByteArray, p: Params): Slice? {
        val type = nalType(nal)
        val r = Nal.BitReader(unescape(nal, Nal.payloadOffset(nal) + 2, minOf(nal.size, Nal.payloadOffset(nal) + 2 + 128)))
        val firstInPicture = r.bit() == 1
        if (type in 16..23) r.bit()
        r.ue()
        if (!firstInPicture) {
            if (p.dependentSlices && r.bit() == 1) return null
            r.bits(p.addressBits)
        }
        r.bits(p.extraBits)
        val sliceType = r.ue()
        if (p.outputFlag) r.bit()
        // An IDR has no POC LSB (it is 0)
        val poc = if (type == 19 || type == 20) 0 else r.bits(p.pocBits)
        if (sliceType == 2) return Slice(false, poc, IntArray(0), BooleanArray(0), IntArray(0), intra = true)
        if (sliceType != 1) return Slice(false, poc, IntArray(0), BooleanArray(0), IntArray(0))
        if (r.bit() != 0) return null
        val negative = r.ue()
        val positive = r.ue()
        if (positive != 0 || negative !in 1..16) return null
        val deltas = IntArray(negative)
        val used = BooleanArray(negative)
        val bits = IntArray(negative)
        for (i in 0 until negative) {
            deltas[i] = r.ue() + 1
            bits[i] = r.position
            used[i] = r.bit() == 1
        }
        return Slice(true, poc, deltas, used, bits)
    }

    /** [nal] with the used flags rewritten: entry [j] used, every entry before it not (RBSP bit positions [flagBits]). */
    private fun flip(nal: ByteArray, flagBits: IntArray, j: Int): ByteArray {
        val start = Nal.payloadOffset(nal) + 2
        val rbsp = unescape(nal, start, nal.size)
        for (i in 0..j) {
            val bit = flagBits[i]
            val mask = 1 shl (7 - bit % 8)
            rbsp[bit / 8] = (if (i == j) rbsp[bit / 8].toInt() or mask else rbsp[bit / 8].toInt() and mask.inv()).toByte()
        }
        return nal.copyOfRange(0, start) + escape(rbsp)
    }

    /** SPS (7.3.2.2): [POC LSB bits, slice address bits, 1 when it fits the rewrite]. */
    private fun parseSps(rbsp: ByteArray): IntArray {
        val r = Nal.BitReader(rbsp)
        r.bits(4)
        val subLayers = r.bits(3)
        r.bit()
        r.bits(32); r.bits(32); r.bits(32)  // profile_tier_level(1, 0): 96 bits
        r.ue()
        val chroma = r.ue()
        val separatePlanes = chroma == 3 && r.bit() == 1
        val width = r.ue()
        val height = r.ue()
        if (r.bit() == 1) { r.ue(); r.ue(); r.ue(); r.ue() }
        r.ue(); r.ue()
        val pocBits = r.ue() + 4
        val orderingAll = r.bit() == 1
        for (i in (if (orderingAll) 0 else subLayers)..subLayers) { r.ue(); r.ue(); r.ue() }
        val minCb = r.ue() + 3
        val ctbLog2 = minCb + r.ue()
        r.ue(); r.ue(); r.ue(); r.ue()
        var setsInSps = 0
        var longTerm = false
        var checked = true
        if (r.bit() == 1 && r.bit() == 1) checked = false  // scaling list data present: the rest cannot be read
        if (checked) {
            r.bit(); r.bit()
            if (r.bit() == 1) { r.bits(8); r.ue(); r.ue(); r.bit() }
            setsInSps = r.ue()
            longTerm = r.bit() == 1
        }
        val ctb = 1 shl ctbLog2
        val ctbs = ((width + ctb - 1) / ctb) * ((height + ctb - 1) / ctb)
        var addressBits = 0
        while ((1 shl addressBits) < ctbs) addressBits++
        val fits = subLayers == 0 && !separatePlanes && setsInSps == 0
        if (!spsLogged) {
            spsLogged = true
            Log.i(TAG, "SPS: ${width}x$height, POC LSB $pocBits bits, sub-layers $subLayers, SPS reference sets $setsInSps, long-term $longTerm, " +
                "address bits $addressBits, rewrite ${if (fits) "possible" else "off"}")
        }
        return intArrayOf(pocBits, addressBits, if (fits) 1 else 0)
    }

    companion object {
        private const val TAG = "PsHevcRefs"

        private fun nalType(nal: ByteArray) = (nal[Nal.payloadOffset(nal)].toInt() shr 1) and 0x3F

        /** [data] from [from] to [to] without emulation-prevention bytes (00 00 03 → 00 00). */
        fun unescape(data: ByteArray, from: Int, to: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(to - from)
            var zeros = 0
            for (i in from until to) {
                val b = data[i].toInt() and 0xFF
                if (zeros >= 2 && b == 3) { zeros = 0; continue }
                zeros = if (b == 0) zeros + 1 else 0
                out.write(b)
            }
            return out.toByteArray()
        }

        /** [rbsp] with emulation-prevention bytes put in (00 00 0x with x ≤ 3 → 00 00 03 0x). */
        fun escape(rbsp: ByteArray): ByteArray {
            val out = java.io.ByteArrayOutputStream(rbsp.size + 8)
            var zeros = 0
            for (v in rbsp) {
                val b = v.toInt() and 0xFF
                if (zeros >= 2 && b <= 3) { out.write(3); zeros = 0 }
                out.write(b)
                zeros = if (b == 0) zeros + 1 else 0
            }
            return out.toByteArray()
        }
    }
}
