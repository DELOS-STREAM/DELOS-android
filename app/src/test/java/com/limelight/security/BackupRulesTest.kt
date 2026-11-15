package com.limelight.security

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Secrets stay out of backups (S-05): the pairing key and certificate, the PC database and the raw diagnostic log are
 * excluded from cloud backup and from device-to-device transfer, for Android 10-11 and for Android 12+ alike, and the
 * manifest points at both rule files. The names are checked against the code that writes the files.
 */
class BackupRulesTest {
    private val main = File("src/main")

    private fun document(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(main, path)).documentElement

    private fun excludes(parent: Element): Set<String> {
        val nodes = parent.getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as Element }.map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }.toSet()
    }

    private val required = setOf(
        "file:client.key", "file:client.crt", "file:uniqueid", "database:.", "file:diagnostics", "file:sgsr-diagnostics", "sharedpref:.",
    )

    @Test
    fun android10And11ExcludeTheSecrets() {
        val root = document("res/xml/backup_rules.xml")
        assertEquals("full-backup-content", root.tagName)
        assertEquals(required, excludes(root))
        // An <include> would turn the rules into an allow-list and silently drop the excludes' meaning
        assertEquals(0, root.getElementsByTagName("include").length)
    }

    @Test
    fun android12ExcludesTheSecretsFromCloudBackupAndDeviceTransfer() {
        val root = document("res/xml/backup_rules_s.xml")
        assertEquals("data-extraction-rules", root.tagName)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val nodes = root.getElementsByTagName(section)
            assertEquals(section, 1, nodes.length)
            assertEquals(section, required, excludes(nodes.item(0) as Element))
        }
        assertEquals(0, root.getElementsByTagName("include").length)
    }

    @Test
    fun theManifestUsesBothRuleFiles() {
        val application = document("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"))
        assertEquals("@xml/backup_rules_s", application.getAttribute("android:dataExtractionRules"))
    }

    @Test
    fun theExcludedNamesAreTheOnesTheCodeWrites() {
        fun source(path: String) = File(main, "java/com/limelight/$path").readText()
        val crypto = source("binding/crypto/AndroidCryptoProvider.java")
        assertTrue("\"client.crt\"" in crypto && "\"client.key\"" in crypto)
        assertTrue("UNIQUE_ID_FILE_NAME = \"uniqueid\"" in source("computers/IdentityManager.java"))
        assertTrue("File(app.filesDir, \"diagnostics\")" in source("diag/CrashReporter.kt"))
        assertTrue("const val DIR = \"diagnostics\"" in source("diag/DiagLog.kt") || "DIR = \"diagnostics\"" in source("diag/DiagLog.kt"))
        assertTrue("\"sgsr-diagnostics\"" in source("binding/video/SgsrDiagnostics.java"))
        // The launch secret lives where Android never backs anything up
        assertTrue("getNoBackupFilesDir()" in source("utils/LaunchAuth.java"))
    }
}
