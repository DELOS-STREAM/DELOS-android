package com.limelight.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stream encryption readout says what was negotiated, never more (S-01). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamEncryptionTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun theFlagsOfTheConnectionBecomeTheReadout() {
        val all = StreamEncryption.of(StreamEncryption.FLAG_VIDEO or StreamEncryption.FLAG_AUDIO, "7.1.431.-1")
        assertEquals("Stream encryption: video on, audio on, control on", all.logLine())
        assertEquals("Video: On · Audio: On · Control: On", all.text(res))

        // What a Sunshine-family host does on a local network unless told otherwise: video in clear
        val lan = StreamEncryption.of(StreamEncryption.FLAG_AUDIO, "7.1.431.-1")
        assertEquals("Stream encryption: video off, audio on, control on", lan.logLine())
        assertEquals("Video: Off · Audio: On · Control: On", lan.text(res))

        assertEquals("Stream encryption: video off, audio off, control off", StreamEncryption.of(0, "7.1.415.0").logLine())
    }

    @Test
    fun aLibraryThatCannotTellSaysUnknownInsteadOfGuessing() {
        val unknown = StreamEncryption.of(null, "7.1.431.-1")
        assertNull(unknown.video)
        assertNull(unknown.audio)
        assertEquals("Stream encryption: video unknown, audio unknown, control on", unknown.logLine())
        assertEquals("Video: unknown · Audio: unknown · Control: On", unknown.text(res))
    }

    @Test
    @Config(sdk = [33], shadows = [com.limelight.shadows.ShadowMoonBridge::class])
    fun whatTheConnectionNegotiatedIsRememberedForDebugInfo() {
        // The test's native bridge reports no encrypted stream (flags 0)
        val read = StreamEncryption.read("7.1.431.-1")

        assertEquals(StreamEncryption(video = false, audio = false, control = true), read)
        assertEquals(read, StreamEncryption.last)

        // The next stream replaces it: an older host does not encrypt the control stream either
        assertEquals("Stream encryption: video off, audio off, control off", StreamEncryption.read("7.1.415.0").logLine())
        assertFalse(StreamEncryption.last!!.control)
    }

    @Test
    fun theControlStreamIsEncryptedFromAppVersion7_1_431() {
        assertTrue(StreamEncryption.controlEncrypted("7.1.431.-1"))
        assertTrue(StreamEncryption.controlEncrypted("7.1.450.0"))
        assertTrue(StreamEncryption.controlEncrypted("7.2.0.0"))
        assertTrue(StreamEncryption.controlEncrypted("8.0.0"))
        assertFalse(StreamEncryption.controlEncrypted("7.1.430.0"))
        assertFalse(StreamEncryption.controlEncrypted("7.0.999.0"))
        assertFalse(StreamEncryption.controlEncrypted("3.20.4"))
        assertFalse(StreamEncryption.controlEncrypted(null))
        assertFalse(StreamEncryption.controlEncrypted(""))
        assertFalse(StreamEncryption.controlEncrypted("garbage"))
    }
}
