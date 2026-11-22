package com.limelight.ps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** The Senkusha ping (spec 6.4, 5.7): a wrong byte means no pong, and the stream falls back to the spec's defaults. */
class PsSenkushaTest {
    @Test fun pingLayout() {
        val d = PsSenkusha.ping(3, 0x12345678, 0x224, null)
        assertEquals(0x224, d.size)
        assertEquals(3, d[0].toInt())
        val b = ByteBuffer.wrap(d)
        assertEquals(0, b.getShort(3).toInt())
        assertEquals((3 shl 21) or (0x7FF shl 10), b.getInt(5))
        assertEquals(0xFF, d[9].toInt() and 0xFF)
        assertEquals(0x12345678, b.getInt(0x16))
        assertTrue(PsSenkusha.isPong(d, 3, 0x12345678))
        assertFalse(PsSenkusha.isPong(d, 4, 0x12345678))
        assertFalse(PsSenkusha.isPong(d, 3, 0x12345679))
    }

    @Test fun mtuProbeHasTheCandidateSizeAndFiller() {
        val d = PsSenkusha.ping(11, 7, 1400 - 28, "CHIAKI".toByteArray())
        assertEquals(1372, d.size)
        assertEquals('C'.code, d[0x1A].toInt())
        assertEquals('I'.code, d[0x1A + 5].toInt())
    }
}
