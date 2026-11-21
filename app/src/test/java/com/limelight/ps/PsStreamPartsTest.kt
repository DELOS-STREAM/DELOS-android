package com.limelight.ps

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PsStreamPartsTest {
    /** The answer a real PS5 gave on 2026-11-21 (host id changed). */
    @Test fun discoveryAnswerOfARealPs5() {
        val text = "HTTP/1.1 200 Ok\nhost-id:001122334455\nhost-type:PS5\nhost-name:PS5-113\nhost-request-port:997\n" +
            "device-discovery-protocol-version:00030010\nsystem-version:14230000\n\u0000"
        val host = PsDiscovery.parse(InetAddress.getByName("192.168.0.148"), text.toByteArray())!!
        assertTrue(host.ps5 && host.awake)
        assertEquals("PS5-113", host.name)
        assertEquals(PsTarget.PS5_1, host.target)
        assertEquals(997, host.requestPort)
    }

    private fun unit(content: ByteArray, size: Int): ByteArray {
        val padding = size - 2 - content.size
        return (byteArrayOf((padding shr 8).toByte(), padding.toByte()) + content).copyOf(size)
    }

    private fun packet(frame: Int, index: Int, total: Int, fec: Int, payload: ByteArray) =
        PsAvPacket(true, index, frame, index, total, fec, 0, 0, 0, 0, payload)

    @Test fun frameWithALostSourceUnitComesBackFromTheParity() {
        val size = 12
        val contents = listOf(byteArrayOf(0, 0, 0, 1, 0x40), byteArrayOf(1, 2, 3, 4, 5, 6, 7), byteArrayOf(9))
        val sources = contents.map { unit(it, size) }
        val parity = PsFec.encode(sources, 2)
        // On the wire the source units are cut after their content; the second one is lost
        val a = PsFrameAssembler()
        val out = ArrayList<PsFrameAssembler.Result>()
        out += a.add(packet(7, 0, 5, 2, sources[0].copyOf(2 + contents[0].size)))
        out += a.add(packet(7, 2, 5, 2, sources[2].copyOf(2 + contents[2].size)))
        out += a.add(packet(7, 3, 5, 2, parity[0]))
        out += a.add(packet(7, 4, 5, 2, parity[1]))
        val frame = out.filterIsInstance<PsFrameAssembler.Result.Frame>().single()
        assertTrue(frame.recovered)
        assertArrayEquals(contents.fold(ByteArray(0)) { x, y -> x + y }, frame.data)
        // A frame-index jump reports the missing range once
        a.add(packet(10, 0, 1, 0, unit(byteArrayOf(5), 4).copyOf(3)))
        assertEquals(8 to 9, a.takeLoss())
    }

    @Test fun fragmentsFollowTheMtuRule() {
        val parts = PsTakionPackets.fragments(ByteArray(3000) { it.toByte() }, 1454)
        assertEquals(1454 - 50 - 26, parts[0].second.size)
        assertTrue(parts.dropLast(1).all { it.first == 0 } && parts.last().first == 1)
        assertArrayEquals(ByteArray(3000) { it.toByte() }, parts.fold(ByteArray(0)) { x, y -> x + y.second })
    }

    @Test fun protobufRoundTrip() {
        val bytes = PsProtoWriter().uint(1, 1).message(3, PsProtoWriter().bool(3, true).bool(4, true).bytes(8, byteArrayOf(4, 5))).toByteArray()
        val m = PsProtoMessage(bytes)
        assertEquals(1L, m.uint(1))
        assertEquals(true, m.message(3)!!.bool(4))
        assertArrayEquals(byteArrayOf(4, 5), m.message(3)!!.bytes(8))
    }
}
