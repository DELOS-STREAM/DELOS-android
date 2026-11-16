package com.limelight.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** What a release must carry for the updater to offer it (docs/VERSIONING.md "Release assets"): the APK of the device's ABI and the checksum file. */
object ReleaseAssets {
    const val CHECKSUMS = "SHA256SUMS.txt"

    /** The file name of the APK of [abi] in the release [version] (the tag without `v`): `delos-1.0.0-arm64-v8a.apk`. */
    fun apkName(version: String, abi: String): String = "delos-$version-$abi.apk"

    /** What [select] found. */
    sealed interface Pick {
        class Found(val apk: ReleaseAsset, val checksums: ReleaseAsset) : Pick

        /** Nothing is offered; [reason] is for the log. */
        class Missing(val reason: String) : Pick
    }

    /** The APK for the first of [abis] (the order of `Build.SUPPORTED_ABIS`) that [release] has one for, and its checksum file. */
    fun select(release: Release, abis: List<String>): Pick {
        val checksums = release.assets.firstOrNull { it.name == CHECKSUMS }
            ?: return Pick.Missing("release ${release.tag} has no $CHECKSUMS")
        val version = release.version.toString()
        for (abi in abis) {
            val name = apkName(version, abi)
            val apk = release.assets.firstOrNull { it.name == name } ?: continue
            return Pick.Found(apk, checksums)
        }
        return Pick.Missing("release ${release.tag} has no APK for ${abis.joinToString()}")
    }
}

/** `SHA256SUMS.txt`: lines of `<64 hex digits>  <file name>`, as `sha256sum` writes them (a `*` before the name marks binary mode). */
object Checksums {
    private val line = Regex("""^([0-9a-fA-F]{64})[ \t]+\*?(.+?)\s*$""")

    /** The lower-case SHA-256 of each file name listed in [text]; lines that do not match are ignored. */
    fun parse(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val match = line.matchEntire(raw.trim()) ?: continue
            result.putIfAbsent(match.groupValues[2], match.groupValues[1].lowercase())
        }
        return result
    }

    /** The lower-case hex SHA-256 of [file]. */
    @Throws(IOException::class)
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** How a downloaded file compares with the published checksum. */
    enum class Verdict { OK, MISMATCH, MISSING }

    /** [Verdict.MISSING] when [sums] has no line for [fileName], [Verdict.MISMATCH] when the digest of [file] differs from it. */
    @Throws(IOException::class)
    fun verify(file: File, fileName: String, sums: Map<String, String>): Verdict {
        val expected = sums[fileName] ?: return Verdict.MISSING
        return if (sha256(file) == expected) Verdict.OK else Verdict.MISMATCH
    }
}
