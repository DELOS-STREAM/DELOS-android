package com.limelight.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    private fun usable(json: String) = (ReleaseParser.parse(json) as ParsedRelease.Usable).release

    @Test fun readsTheGitHubAnswer() {
        val release = usable(releaseJson("v1.2.0"))
        assertEquals("v1.2.0", release.tag)
        assertEquals(SemVer.parse("1.2.0"), release.version)
        assertEquals("## Added\n- Something new", release.notes)
        assertEquals(listOf("delos-1.2.0-arm64-v8a.apk", "delos-1.2.0-armeabi-v7a.apk", "SHA256SUMS.txt"), release.assets.map { it.name })
        assertEquals(52_428_800L, release.assets[0].size)
        assertEquals("https://github.com/DELOS-STREAM/DELOS-android/releases/download/v1.2.0/SHA256SUMS.txt", release.assets[2].url)
    }

    @Test fun draftsAndPreReleasesAreIgnored() {
        assertTrue(ReleaseParser.parse(releaseJson("v1.2.0", draft = true)) is ParsedRelease.Ignored)
        assertTrue(ReleaseParser.parse(releaseJson("v1.2.0-rc.1", prerelease = true)) is ParsedRelease.Ignored)
    }

    @Test fun aTagThatIsNoVersionIsIgnored() {
        assertTrue(ReleaseParser.parse(releaseJson("nightly")) is ParsedRelease.Ignored)
        assertTrue(ReleaseParser.parse(releaseJson("v1.2")) is ParsedRelease.Ignored)
    }

    @Test fun garbageIsMalformed() {
        for (text in listOf("", "<html>rate limited</html>", "[]", "42", "null")) {
            assertTrue("'$text'", ReleaseParser.parse(text) === ParsedRelease.Malformed)
        }
        // A valid object that is no release (GitHub's error body) is ignored, not offered
        assertTrue(ReleaseParser.parse("{\"message\":\"Not Found\"}") is ParsedRelease.Ignored)
    }

    @Test fun anAssetWithoutAnHttpsUrlOrSizeIsLeftOut() {
        val release = usable(releaseJson("v1.2.0", assets = listOf(
            Triple("delos-1.2.0-arm64-v8a.apk", "http://github.com/x.apk", 10L),
            Triple("SHA256SUMS.txt", "https://github.com/x/SHA256SUMS.txt", 10L),
        )))
        assertEquals(listOf("SHA256SUMS.txt"), release.assets.map { it.name })
        val noSize = """{"tag_name":"v1.0.1","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"},{"browser_download_url":"https://x/b"}],"body":null}"""
        assertTrue(usable(noSize).assets.isEmpty())
        assertEquals("", usable(noSize).notes)
    }

    @Test fun notesBecomeAFewPlainLines() {
        val markdown = "<!-- hidden -->\r\n## Added\r\n\r\n- **In-app updates**: see [the docs](https://example.test/docs)\r\n- Uses `OkHttp`\r\n- fourth line\r\n- fifth line"
        assertEquals("Added\n• In-app updates: see the docs\n• Uses OkHttp", ReleaseNotes.summary(markdown))
        assertEquals("", ReleaseNotes.summary("  \n\n"))
        val long = ReleaseNotes.summary("x".repeat(500))
        assertEquals(ReleaseNotes.MAX_LINE_CHARS, long.length)
        assertTrue(long.endsWith("…"))
        assertFalse(ReleaseNotes.summary("a\nb\nc\nd\ne").contains("d"))
    }
}
