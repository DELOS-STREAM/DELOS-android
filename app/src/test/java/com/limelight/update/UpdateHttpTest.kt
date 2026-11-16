package com.limelight.update

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateHttpTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(5000) { (it % 251).toByte() }
    private val web = FakeWeb { FakeWeb.reply(it, 404) }
    private fun http(cleartext: Boolean = false) = UpdateHttp(web.client(), "Delos/1.0.0", cleartext)
    private fun download(url: String, target: java.io.File, max: Long = 1_000_000, onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        runBlocking { http().download(url, target, max, onProgress) }

    @Test fun downloadsOverHttpsAndReportsProgress() {
        web.handler = { FakeWeb.replyBytes(it, bytes) }
        val target = tmp.newFile("a.apk")
        val seen = mutableListOf<Pair<Long, Long>>()
        download("https://github.com/a.apk", target) { done, total -> seen += done to total }
        assertArrayEquals(bytes, target.readBytes())
        assertEquals(bytes.size.toLong() to bytes.size.toLong(), seen.last())
        assertEquals("Delos/1.0.0", web.requests.single().header("User-Agent"))
    }

    @Test fun refusesAPlainHttpUrl() {
        val e = assertThrows(UpdateException::class.java) { download("http://github.com/a.apk", tmp.newFile("a.apk")) }
        assertEquals(UpdateErrorKind.NOT_HTTPS, e.kind)
        assertTrue(web.requests.isEmpty())
    }

    @Test fun followsRedirectsToHttps() {
        web.handler = { r ->
            when (r.url.host) {
                "github.com" -> FakeWeb.reply(r, 302, "", "Location" to "https://objects.githubusercontent.com/a.apk?sig=1")
                else -> FakeWeb.replyBytes(r, bytes)
            }
        }
        val target = tmp.newFile("a.apk")
        download("https://github.com/a.apk", target)
        assertArrayEquals(bytes, target.readBytes())
        assertEquals(listOf("github.com", "objects.githubusercontent.com"), web.requests.map { it.url.host })
    }

    @Test fun refusesARedirectToHttp() {
        web.handler = { r -> FakeWeb.reply(r, 302, "", "Location" to "http://evil.test/a.apk") }
        val target = tmp.newFile("a.apk")
        val e = assertThrows(UpdateException::class.java) { download("https://github.com/a.apk", target) }
        assertEquals(UpdateErrorKind.NOT_HTTPS, e.kind)
        assertEquals(1, web.requests.size)
        assertFalse(target.exists() && target.length() > 0)
    }

    @Test fun stopsAnEndlessRedirectChain() {
        web.handler = { r -> FakeWeb.reply(r, 301, "", "Location" to "https://github.com/loop") }
        assertThrows(UpdateException::class.java) { download("https://github.com/a.apk", tmp.newFile("a.apk")) }
        assertEquals(UpdateHttp.MAX_REDIRECTS + 1, web.requests.size)
    }

    @Test fun aRelativeRedirectStaysHttps() {
        web.handler = { r -> if (r.url.encodedPath == "/a.apk") FakeWeb.reply(r, 302, "", "Location" to "/b.apk") else FakeWeb.replyBytes(r, bytes) }
        download("https://github.com/a.apk", tmp.newFile("a.apk"))
        assertEquals("https://github.com/b.apk", web.requests.last().url.toString())
    }

    @Test fun aBodyOverTheCapIsRefusedByItsAnnouncedLength() {
        web.handler = { FakeWeb.replyBytes(it, bytes) }
        val target = tmp.newFile("a.apk")
        val e = assertThrows(UpdateException::class.java) { download("https://github.com/a.apk", target, max = 4999) }
        assertEquals(UpdateErrorKind.TOO_LARGE, e.kind)
        assertFalse(target.exists() && target.length() > 0)
    }

    @Test fun aBodyOverTheCapIsStoppedWhileReadingAndDeleted() {
        web.handler = { FakeWeb.replyUnknownLength(it, 200_000) }
        val target = tmp.newFile("a.apk")
        val e = assertThrows(UpdateException::class.java) { download("https://github.com/a.apk", target, max = 100_000) }
        assertEquals(UpdateErrorKind.TOO_LARGE, e.kind)
        assertFalse(target.exists())
    }

    @Test fun aBodyExactlyAtTheCapIsAccepted() {
        web.handler = { FakeWeb.replyBytes(it, bytes) }
        val target = tmp.newFile("a.apk")
        download("https://github.com/a.apk", target, max = 5000)
        assertEquals(5000L, target.length())
    }

    @Test fun anErrorStatusIsAFailure() {
        assertEquals(UpdateErrorKind.HTTP_STATUS, assertThrows(UpdateException::class.java) { download("https://github.com/a.apk", tmp.newFile("a.apk")) }.kind)
        web.handler = { FakeWeb.reply(it, 429) }
        assertEquals(UpdateErrorKind.RATE_LIMITED, assertThrows(UpdateException::class.java) { download("https://github.com/a.apk", tmp.newFile("b.apk")) }.kind)
    }

    @Test fun aCleartextUrlIsAllowedOnlyWhenTheDebugOverrideSaysSo() {
        web.handler = { FakeWeb.reply(it, 200, "{}") }
        assertEquals("{}", UpdateHttp(web.client(), "Delos/1.0.0", allowCleartext = true).text("http://10.0.2.2:8080/latest"))
        assertThrows(UpdateException::class.java) { UpdateHttp(web.client(), "Delos/1.0.0").text("http://10.0.2.2:8080/latest") }
    }

    @Test fun aTextOverTheCapIsRefused() {
        web.handler = { FakeWeb.reply(it, 200, "x".repeat(2000)) }
        assertEquals(UpdateErrorKind.TOO_LARGE, assertThrows(UpdateException::class.java) { http().text("https://github.com/s", maxBytes = 1000) }.kind)
        web.handler = { FakeWeb.replyUnknownLength(it, 5000) }
        assertEquals(UpdateErrorKind.TOO_LARGE, assertThrows(UpdateException::class.java) { http().text("https://github.com/s", maxBytes = 1000) }.kind)
    }

    @Test fun anIfNoneMatchHeaderIsSentOnlyWithAnEtag() {
        web.handler = { FakeWeb.reply(it, 304) }
        assertEquals(Latest.NotModified, http().latest("https://api.github.com/x", "\"e\""))
        assertEquals("\"e\"", web.requests[0].header("If-None-Match"))
        web.handler = { FakeWeb.reply(it, 200, "{}", "ETag" to "\"n\"") }
        val body = http().latest("https://api.github.com/x", null) as Latest.Body
        assertEquals("\"n\"", body.etag)
        assertEquals(null, web.requests[1].header("If-None-Match"))
    }

}
