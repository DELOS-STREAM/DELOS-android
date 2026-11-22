package com.limelight.ps

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The feeder ends a keyframe wait on an I slice in a non-IDR NAL unit (spec 7.3): the slice type must be read right. */
class NalSliceTypeTest {
    private fun nal(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

    @Test fun h264NonIdrSliceTypes() {
        // first_mb_in_slice 0 ('1'), slice_type 7 ('0001000'): an I slice
        assertEquals(Nal.SLICE_I, Nal.sliceType(nal(0, 0, 1, 0x41, 0x88), hevc = false, extraBits = 0))
        // slice_type 5 ('00110'): a P slice
        assertEquals(0, Nal.sliceType(nal(0, 0, 1, 0x41, 0x98), hevc = false, extraBits = 0))
    }

    @Test fun hevcFirstSliceTypes() {
        // TRAIL_R: first slice '1', PPS id 0 '1', slice_type 2 '011': an I slice; slice_type 1 '010': a P slice
        assertEquals(Nal.SLICE_I, Nal.sliceType(nal(0, 0, 0, 1, 0x02, 0x01, 0xD8), hevc = true, extraBits = 0))
        assertEquals(1, Nal.sliceType(nal(0, 0, 0, 1, 0x02, 0x01, 0xD0), hevc = true, extraBits = 0))
        // Not the picture's first slice: no answer
        assertNull(Nal.sliceType(nal(0, 0, 0, 1, 0x02, 0x01, 0x58), hevc = true, extraBits = 0))
        // Two extra slice header bits (from the PPS) come before the type: '1' '1' '00' '011'
        assertEquals(Nal.SLICE_I, Nal.sliceType(nal(0, 0, 0, 1, 0x02, 0x01, 0xC6), hevc = true, extraBits = 2))
    }

    @Test fun emulationPreventionBytesAreRemoved() {
        assertArrayEquals(nal(0, 0, 1, 0, 0, 2), Nal.rbsp(nal(0x41, 0, 0, 3, 1, 0, 0, 3, 2), 1))
    }
}
