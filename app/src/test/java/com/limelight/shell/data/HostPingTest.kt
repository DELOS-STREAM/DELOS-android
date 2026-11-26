package com.limelight.shell.data

import com.limelight.nvstream.http.ComputerDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class HostPingTest {
    @Test
    fun targetForPrefersActiveThenLocalAddress() {
        val details = ComputerDetails().apply {
            localAddress = ComputerDetails.AddressTuple("192.168.1.10", 47989)
            remoteAddress = ComputerDetails.AddressTuple("8.8.8.8", 47989)
            activeAddress = ComputerDetails.AddressTuple("10.0.0.5", 47989)
        }
        assertEquals("10.0.0.5" to 47989, HostPing.targetFor(details))

        details.activeAddress = null
        assertEquals("192.168.1.10" to 47989, HostPing.targetFor(details))
    }

    @Test
    fun measureConnectsToListeningSocketAndReturnsLatency() = runBlocking {
        ServerSocket(0).use { server ->
            val port = server.localPort
            val acceptThread = thread {
                repeat(3) {
                    runCatching { server.accept().close() }
                }
            }
            val result = HostPing.measure("127.0.0.1", port, samples = 3, timeoutMs = 1000)
            acceptThread.join(2000)
            assertNotNull(result)
            assertEquals(3, result!!.samples)
            assertTrue(result.avgMs >= 1)
            assertTrue(result.jitterMs >= 0)
        }
    }

    @Test
    fun measureReturnsNullWhenUnreachable() = runBlocking {
        // Reserve a free port and close it immediately so nothing is listening
        val closedPort = ServerSocket(0).use { it.localPort }
        val result = HostPing.measure("127.0.0.1", closedPort, samples = 2, timeoutMs = 200)
        assertNull(result)
    }
}
