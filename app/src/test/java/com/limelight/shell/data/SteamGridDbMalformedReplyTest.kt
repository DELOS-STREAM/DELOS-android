package com.limelight.shell.data

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A SteamGridDB answer that is not what the API sends (the sign-in page of a Wi-Fi network, a maintenance page, a changed
 * format) is a failed call with a sentence for a toast, never an exception that ends the app (C-02). The client talks to a
 * real local HTTP server.
 */
class SteamGridDbMalformedReplyTest {
    private val servers = mutableListOf<Closeable>()
    private val requests = CopyOnWriteArrayList<String>()

    @After
    fun stop() = servers.forEach { it.close() }

    /** A client whose every request is answered with [body] (status [status]). */
    private fun client(body: String, status: Int = 200): SteamGridDbClient {
        val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        servers += socket
        Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { peer ->
                        val input = peer.getInputStream().bufferedReader()
                        requests += input.readLine().orEmpty()
                        while (input.readLine().orEmpty().isNotEmpty()) Unit
                        val bytes = body.toByteArray()
                        peer.getOutputStream().apply {
                            write("HTTP/1.1 $status X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: IOException) {
                    // the closed server socket
                }
            }
        }.apply { isDaemon = true }.start()
        return SteamGridDbClient("key", OkHttpClient(), "http://127.0.0.1:${socket.localPort}/api/v2")
    }

    private fun failureOf(call: () -> Unit): SgdbFailure = try {
        call()
        fail("expected a SteamGridDbException")
        throw AssertionError()
    } catch (e: SteamGridDbException) {
        e.failure
    }

    private val malformed = listOf(
        "<html><body>Sign in to this network</body></html>",
        "",
        "not json",
        "{\"success\": true, \"data\": ",
        "[1, 2, 3]",
        "\"just a string\"",
        "null",
        "{\"success\": true, \"data\": {\"id\": 1}}",
        "{\"success\": true, \"data\": \"oops\"}",
    )

    @Test
    fun aReplyThatIsNotTheApiFormatIsAFailedSearch() {
        for (body in malformed) {
            assertEquals(body, SgdbFailure.BAD_REPLY, failureOf { client(body).search("Half-Life 2") })
        }
    }

    @Test
    fun aReplyThatIsNotTheApiFormatIsAFailedImageList() {
        for (body in malformed) {
            assertEquals(body, SgdbFailure.BAD_REPLY, failureOf { client(body).images(1, ArtKind.COVER) })
        }
    }

    @Test
    fun entriesOfAnotherShapeAreSkippedAndTheRestIsUsed() {
        val body = """{"success":true,"data":[
            {"id":"x","name":"No number id"}, {"name":"No id"}, {"id":7}, 42, null, "text", {"id":8,"name":null},
            {"id":220,"name":"Half-Life 2","release_date":1100563200}, {"id":221,"name":"No date","release_date":"soon"}
        ]}"""
        val games = client(body).search("Half-Life 2")
        assertEquals(listOf(220, 221), games.map { it.id })
        assertEquals("Half-Life 2", games[0].name)
        assertEquals(2004, games[0].year)
        assertEquals(null, games[1].year)

        val images = client("""{"success":true,"data":[42, null, {"id":1}, {"id":2,"url":"https://cdn.example/a.png"}]}""").images(1, ArtKind.COVER)
        assertEquals(listOf(2), images.map { it.id })
    }

    @Test
    fun anAnswerWithoutDataIsEmptyNotAFailure() {
        assertTrue(client("{\"success\":true}").search("x").isEmpty())
        assertTrue(client("{\"success\":true,\"data\":null}").search("x").isEmpty())
        assertTrue(client("{}", status = 404).search("x").isEmpty())
    }

    @Test
    fun aBadReplyIsNotRetriedAndHasItsOwnSentence() {
        assertFalse(SgdbRetry.isTransient(SteamGridDbException(SgdbFailure.BAD_REPLY)))
        assertEquals("SteamGridDB sent an unreadable reply", SteamGridDbException(SgdbFailure.BAD_REPLY).message)
    }

    @Test
    fun aGameNameIsOnePathSegmentWhateverItContains() {
        val api = client("{\"success\":true,\"data\":[]}")
        api.search("Fate/Grand Order")
        // Names a URL would resolve to another endpoint are not asked at all
        assertTrue(api.search("..").isEmpty())
        assertTrue(api.search(".").isEmpty())
        assertTrue(api.search("  ").isEmpty())
        api.search("a?b#c")
        assertEquals(
            listOf(
                "GET /api/v2/search/autocomplete/Fate%2FGrand%20Order HTTP/1.1",
                "GET /api/v2/search/autocomplete/a%3Fb%23c HTTP/1.1",
            ),
            requests.toList(),
        )
    }
}
