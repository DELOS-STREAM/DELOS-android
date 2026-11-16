package com.limelight.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    private fun v(text: String) = SemVer.parse(text)!!

    @Test fun releaseCandidateIsBelowItsRelease() {
        assertTrue(v("1.0.0-rc.1") < v("1.0.0"))
        assertTrue(v("1.0.0-rc.1") < v("1.0.0-rc.2"))
        assertTrue(v("1.0.0") > v("1.0.0-rc.2"))
    }

    @Test fun numbersCompareAsNumbersNotText() {
        assertTrue(v("1.10.0") > v("1.9.0"))
        assertTrue(v("1.0.10") > v("1.0.9"))
        assertTrue(v("2.0.0") > v("1.99.99"))
        assertTrue(v("0.15.0") < v("1.0.0"))
    }

    @Test fun theSpecificationsOwnPrecedenceExample() {
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0")
        for (i in 0 until ordered.size - 1) assertTrue("${ordered[i]} < ${ordered[i + 1]}", v(ordered[i]) < v(ordered[i + 1]))
    }

    @Test fun buildMetadataIsIgnored() {
        assertEquals(v("1.0.0+g912bc0d"), v("1.0.0+other"))
        assertEquals(0, v("1.0.0+a").compareTo(v("1.0.0")))
        assertTrue(v("1.0.1+a") > v("1.0.0+z"))
        assertEquals("1.0.0-rc.1", v("1.0.0-rc.1+build.5").toString())
    }

    @Test fun tagsDropTheV() {
        assertEquals(v("1.2.3"), SemVer.parseTag("v1.2.3"))
        assertEquals(v("1.2.3"), SemVer.parseTag("1.2.3"))
        assertNull(SemVer.parseTag("vv1.2.3"))
    }

    @Test fun invalidStringsDoNotParse() {
        for (text in listOf("", "1", "1.2", "1.2.3.4", "01.2.3", "1.02.3", "1.2.-3", "1.2.3-", "1.2.3-rc..1", "1.2.3-01", "1.2.3+", "v", "latest", "1.2.x", "1.2.3 ", "a.b.c")) {
            assertNull("'$text'", SemVer.parse(text))
        }
    }

    @Test fun numericPreReleaseIdentifiersRankBelowTextOnes() {
        assertTrue(v("1.0.0-1") < v("1.0.0-a"))
        assertTrue(v("1.0.0-2") < v("1.0.0-10"))
        assertTrue(v("1.0.0-rc") < v("1.0.0-rc.1"))
    }
}
