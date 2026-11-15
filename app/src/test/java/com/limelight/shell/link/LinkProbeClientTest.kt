package com.limelight.shell.link

import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PyroWaveHostInfo
import okhttp3.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the link test makes of the host's answers: the UDP port header, HTTP failures and the result
 * document. The UDP receiver itself needs a live host and is not covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LinkProbeClientTest {
    private val http = mock(NvHTTP::class.java)
    private val client = LinkProbeClient(http, PyroWaveHostInfo.NONE)

    private fun result(expected: String, sent: String, elapsed: String) =
        "<root status_code=\"200\"><expected>$expected</expected><sent>$sent</sent><elapsedMs>$elapsed</elapsedMs></root>"

    // ---- the announced UDP port ----

    @Test
    fun validPortHeader() {
        assertEquals(1024, client.validPort("1024"))
        assertEquals(47998, client.validPort("47998"))
        assertEquals(65535, client.validPort(" 65535 "))
    }

    @Test
    fun aPortOutsideTheUnprivilegedRangeIsRefused() {
        assertNull(client.validPort("0"))
        assertNull(client.validPort("80"))
        assertNull(client.validPort("1023"))
        assertNull(client.validPort("65536"))
        assertNull(client.validPort("99999"))
    }

    @Test
    fun aPortThatIsNotAPlainNumberIsRefused() {
        for (header in listOf("", "  ", "-1", "+5000", "5000a", "0x1388", "5 000", "50.0", "123456", "٥٠٠٠", "5000\n5001")) {
            assertNull(header, client.validPort(header))
        }
    }

    // ---- HTTP failures become reasons the UI can word ----

    @Test
    fun httpStatusBecomesAReason() {
        assertEquals(LinkFailure.DENIED, client.failure(403, "").failure)
        assertEquals(LinkFailure.HOST_UNSUPPORTED, client.failure(404, "<html>not found</html>").failure)
        assertEquals(LinkFailure.STREAM_ACTIVE, client.failure(400, "Stop streaming before testing bandwidth").failure)
        assertEquals(LinkFailure.HOST_ERROR, client.failure(400, "Bad request").failure)
        assertEquals(LinkFailure.HOST_ERROR, client.failure(500, "boom").failure)
        assertEquals(LinkFailure.HOST_ERROR, client.failure(503, "").failure)
    }

    @Test
    fun aHugeErrorBodyIsCutShort() {
        val failure = client.failure(500, "x".repeat(100_000))

        assertTrue(failure.message!!.length < 1000)
    }

    // ---- the result document ----

    @Test
    fun aWellFormedResultIsRead() {
        val response = client.parseResponse(result("1200", "1180", "1003.5"))

        assertEquals(1200, response.expected)
        assertEquals(1180, response.sent)
        assertEquals(1003.5, response.elapsedMs, 0.0001)
    }

    @Test
    fun malformedResponseIsAFailureNotACrash() {
        val malformed = listOf(
            "",
            "not xml at all",
            "<root status_code=\"200\"></root>",
            "<root status_code=\"200\"><expected>12",
            result("many", "1", "1"),
            result("10", "", "1"),
            result("10", "5", "soon"),
            result("10", "5", "NaN"),
            result("-10", "5", "1"),
            result("99999999999999999999", "5", "1"),
        )
        for (body in malformed) {
            val failure = assertThrows(body, LinkTestException::class.java) { client.parseResponse(body) }
            assertEquals(body, LinkFailure.INVALID_RESPONSE, failure.failure)
        }
    }

    @Test
    fun aHostErrorDocumentIsAHostErrorAndAHandshakeFailureMeansBlockedUdp() {
        val hostError = assertThrows(LinkTestException::class.java) {
            client.parseResponse("<root status_code=\"500\" status_message=\"Encoder busy\"></root>")
        }
        assertEquals(LinkFailure.HOST_ERROR, hostError.failure)

        val blocked = assertThrows(LinkTestException::class.java) {
            client.parseResponse("<root status_code=\"500\" status_message=\"UDP handshake timed out\"></root>")
        }
        assertEquals(LinkFailure.UDP_BLOCKED, blocked.failure)
    }

    // ---- before anything is sent ----

    @Test
    fun aRateOutsideTheHostsRangeIsRefusedBeforeAnyNetworkUse() {
        assertThrows(IllegalArgumentException::class.java) { client.probe(LinkPolicy.MIN_KBPS - 1, LinkCancel()) }
        assertThrows(IllegalArgumentException::class.java) { client.probe(LinkPolicy.MAX_KBPS + 1, LinkCancel()) }

        verifyNoInteractions(http)
    }

    @Test
    fun aHostWithoutTheProbeIsUnsupportedBeforeAnyNetworkUse() {
        val failure = assertThrows(LinkTestException::class.java) {
            runLinkTest(http, PyroWaveHostInfo.NONE, 50_000, LinkTransport.WIFI, LinkCancel())
        }

        assertEquals(LinkFailure.HOST_UNSUPPORTED, failure.failure)
        verifyNoInteractions(http)
    }

    // ---- cancelling ----

    @Test
    fun cancelStopsTheRunningRequest() {
        val call = mock(Call::class.java)
        val cancel = LinkCancel()
        cancel.attach(call)
        verify(call, never()).cancel()
        assertFalse(cancel.cancelled)

        cancel.cancel()

        assertTrue(cancel.cancelled)
        verify(call).cancel()
    }

    @Test
    fun aRequestStartedAfterCancelIsStoppedAtOnce() {
        val cancel = LinkCancel()
        cancel.cancel()
        val late = mock(Call::class.java)

        cancel.attach(late)

        verify(late).cancel()
    }
}
