package com.limelight.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseAssetsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun release(vararg names: String, tag: String = "v1.2.0") =
        Release(tag, SemVer.parseTag(tag)!!, "", names.map { ReleaseAsset(it, "https://x.test/$it", 100) })

    @Test fun picksTheApkOfTheFirstSupportedAbi() {
        val r = release("delos-1.2.0-armeabi-v7a.apk", "delos-1.2.0-arm64-v8a.apk", "SHA256SUMS.txt")
        val pick = ReleaseAssets.select(r, listOf("arm64-v8a", "armeabi-v7a", "armeabi")) as ReleaseAssets.Pick.Found
        assertEquals("delos-1.2.0-arm64-v8a.apk", pick.apk.name)
        assertEquals("SHA256SUMS.txt", pick.checksums.name)
        // A 32-bit device gets the 32-bit APK
        assertEquals("delos-1.2.0-armeabi-v7a.apk", (ReleaseAssets.select(r, listOf("armeabi-v7a", "armeabi")) as ReleaseAssets.Pick.Found).apk.name)
    }

    @Test fun fallsBackToALaterAbiOfTheDevice() {
        val r = release("delos-1.2.0-armeabi-v7a.apk", "SHA256SUMS.txt")
        assertEquals("delos-1.2.0-armeabi-v7a.apk", (ReleaseAssets.select(r, listOf("arm64-v8a", "armeabi-v7a")) as ReleaseAssets.Pick.Found).apk.name)
    }

    @Test fun noMatchingApkMeansNoOffer() {
        val r = release("delos-1.2.0-arm64-v8a.apk", "SHA256SUMS.txt")
        val pick = ReleaseAssets.select(r, listOf("x86_64", "x86")) as ReleaseAssets.Pick.Missing
        assertTrue(pick.reason, "x86_64" in pick.reason)
    }

    @Test fun anApkOfAnotherVersionDoesNotMatch() {
        val r = release("delos-1.1.0-arm64-v8a.apk", "SHA256SUMS.txt")
        assertTrue(ReleaseAssets.select(r, listOf("arm64-v8a")) is ReleaseAssets.Pick.Missing)
    }

    @Test fun noChecksumFileMeansNoOffer() {
        val pick = ReleaseAssets.select(release("delos-1.2.0-arm64-v8a.apk"), listOf("arm64-v8a")) as ReleaseAssets.Pick.Missing
        assertTrue(pick.reason, ReleaseAssets.CHECKSUMS in pick.reason)
    }

    @Test fun aPreReleaseVersionKeepsItsSuffixInTheName() {
        val r = release("delos-1.0.0-rc.1-arm64-v8a.apk", "SHA256SUMS.txt", tag = "v1.0.0-rc.1")
        assertEquals("delos-1.0.0-rc.1-arm64-v8a.apk", (ReleaseAssets.select(r, listOf("arm64-v8a")) as ReleaseAssets.Pick.Found).apk.name)
    }

    @Test fun parsesSha256sumLines() {
        val a = "a".repeat(64)
        val b = "B".repeat(64)
        val sums = Checksums.parse("$a  delos-1.2.0-arm64-v8a.apk\n$b *delos-1.2.0-armeabi-v7a.apk\r\nnot a line\n${"c".repeat(63)}  short.apk\n\n")
        assertEquals(mapOf("delos-1.2.0-arm64-v8a.apk" to a, "delos-1.2.0-armeabi-v7a.apk" to "b".repeat(64)), sums)
    }

    @Test fun verifiesAFileAgainstTheList() {
        val file = tmp.newFile("delos.apk").apply { writeBytes("apk bytes".toByteArray()) }
        val good = mapOf("delos.apk" to sha256Hex("apk bytes".toByteArray()))
        assertEquals(Checksums.Verdict.OK, Checksums.verify(file, "delos.apk", good))
        assertEquals(Checksums.Verdict.MISMATCH, Checksums.verify(file, "delos.apk", mapOf("delos.apk" to sha256Hex("other".toByteArray()))))
        assertEquals(Checksums.Verdict.MISSING, Checksums.verify(file, "delos.apk", mapOf("other.apk" to good.getValue("delos.apk"))))
        assertEquals(Checksums.Verdict.MISSING, Checksums.verify(file, "delos.apk", emptyMap()))
    }
}
