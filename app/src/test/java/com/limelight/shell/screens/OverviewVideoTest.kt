package com.limelight.shell.screens

import com.limelight.shell.data.YouTubeEmbeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewVideoTest {
    @Test fun `times read m ss and h mm ss`() {
        assertEquals("0:00", videoTime(0f))
        assertEquals("0:09", videoTime(9.9f))
        assertEquals("2:05", videoTime(125f))
        assertEquals("1:00:01", videoTime(3601f))
        assertEquals("0:00", videoTime(-3f))
    }

    @Test fun `the player page uses the privacy enhanced host, the app's origin and no YouTube controls`() {
        val html = youTubePlayerHtml("dQw4w9WgXcQ", "https://com.example.app")
        assertNotNull(html)
        html!!
        assertTrue("https://www.youtube-nocookie.com" in html)
        assertTrue("videoId:'dQw4w9WgXcQ'" in html)
        assertTrue("origin:'https://com.example.app'" in html)
        assertTrue("controls:0" in html && "rel:0" in html && "playsinline:1" in html)
    }

    @Test fun `only YouTube ids get into the page`() {
        assertNull(youTubePlayerHtml("x'};alert(1);//", "https://com.example.app"))
        assertNull(youTubePlayerHtml("", "https://com.example.app"))
    }

    @Test fun `oEmbed refusals and lasting player errors make a video unplayable`() {
        for (status in listOf(400, 401, 403, 404)) assertTrue(YouTubeEmbeds.unplayableStatus(status))
        for (status in listOf(200, 429, 500, 503)) assertFalse(YouTubeEmbeds.unplayableStatus(status))
        for (code in listOf(100, 101, 150)) assertTrue(YouTubeEmbeds.unplayableError(code))
        for (code in listOf(-1, 2, 5, 153)) assertFalse(YouTubeEmbeds.unplayableError(code))
        assertTrue(YouTubeEmbeds.blockedByOwner(150))
        assertFalse(YouTubeEmbeds.blockedByOwner(100))
    }

    @Test fun `the oEmbed URL asks about the watch page`() {
        assertEquals(
            "https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dabc_DEF-123",
            YouTubeEmbeds.oembedUrl("abc_DEF-123"),
        )
    }
}
