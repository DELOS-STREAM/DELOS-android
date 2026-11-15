package com.limelight.security

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The license texts packaged into the app (`src/main/assets/licenses/`, rule B6): the app's own GPL-3.0 text is the
 * repository's `LICENSE`, and `INDEX.txt` names exactly the files of the folder.
 */
class LicenseAssetsTest {
    private val folder = File("src/main/assets/licenses")
    private val index = File(folder, "INDEX.txt").readLines()

    /** The last column of every `Component | Version | Licence | File` row. */
    private val indexed = index.filter { it.count { c -> c == '|' } == 3 }.map { it.substringAfterLast('|').trim() }
        .filter { it != "File" }

    @Test fun theAppsOwnLicenseIsTheRepositorysLicenseByteForByte() {
        assertArrayEquals(File("../LICENSE").readBytes(), File(folder, "Delos-GPL-3.0.txt").readBytes())
        assertTrue(File(folder, "Delos-GPL-3.0.txt").readText().contains("GNU GENERAL PUBLIC LICENSE"))
    }

    @Test fun theIndexNamesTheAppsOwnLicense() {
        assertTrue(index.any { it.startsWith("Delos") && it.endsWith("| GPL-3.0 | Delos-GPL-3.0.txt") })
    }

    @Test fun theIndexAndTheFolderNameTheSameFiles() {
        val files = folder.listFiles()!!.map { it.name }.filter { it != "INDEX.txt" }.toSet()
        assertEquals("files without a row in INDEX.txt", emptySet<String>(), files - indexed.toSet())
        assertEquals("rows of INDEX.txt without a file", emptySet<String>(), indexed.toSet() - files)
    }

    @Test fun noLicenseTextIsEmpty() {
        for (name in indexed) assertTrue(name, File(folder, name).length() > 200)
    }
}
