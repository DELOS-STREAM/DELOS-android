package com.limelight.shell.data

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrailerStillsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `failed movie is downloaded only once across requests and cache instances`() = runBlocking {
        val requests = AtomicInteger()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(403).message("Forbidden").body("".toResponseBody()).build()
        }.build()
        val first = TrailerStills(temporary.newFolder(), http)
        val id = UUID.randomUUID().toString()
        val stream = "https://example.com/trailer.m3u8"
        (0 until 12).map { async { first.still(id, stream) } }.awaitAll().forEach { assertNull(it) }
        assertNull(first.still(id, stream))
        val second = TrailerStills(temporary.newFolder(), http)
        assertNull(second.still(id, stream))
        assertEquals(1, requests.get())
        assertNull(second.still(UUID.randomUUID().toString(), stream))
        assertEquals(2, requests.get())
    }
}
