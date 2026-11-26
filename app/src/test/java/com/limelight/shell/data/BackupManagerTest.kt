package com.limelight.shell.data

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.shadows.ShadowGameManager
import com.limelight.shadows.ShadowMoonBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ShadowMoonBridge::class, ShadowGameManager::class])
class BackupManagerTest {
    @Test
    fun exportAndImportRestoresProfilesSettingsIdentityAndComputers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        val store = ProfileStore(context)
        val created = store.create("LivingRoom", 2)
        store.settings().edit()
            .putString("list_resolution", "1920x1080")
            .putInt("seekbar_bitrate_kbps", 35000)
            .putBoolean("ui_reduced_motion", true)
            .putStringSet("ui_hidden_games", setOf("101", "202"))
            .commit()

        File(context.filesDir, "uniqueid").writeText("0123456789abcdef")
        File(context.filesDir, "client.crt").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(context.filesDir, "client.key").writeBytes(byteArrayOf(5, 6, 7, 8))

        val pc = ComputerDetails().apply {
            uuid = "pc-uuid-1"
            name = "Gaming-Rig"
            httpsPort = 47984
            macAddress = "AA:BB:CC:DD:EE:FF"
            localAddress = ComputerDetails.AddressTuple("192.168.1.50", 47989)
        }
        val db = ComputerDatabaseManager(context)
        try {
            db.updateComputer(pc)
        } finally {
            db.close()
        }

        val backupJson = BackupManager.exportJson(context, store)

        // Wipe local state to simulate a fresh install
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        context.getSharedPreferences("ui_profile_${created.id}", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "uniqueid").delete()
        File(context.filesDir, "client.crt").delete()
        File(context.filesDir, "client.key").delete()
        val dbWipe = ComputerDatabaseManager(context)
        try {
            dbWipe.allComputers.forEach { dbWipe.deleteComputer(it) }
        } finally {
            dbWipe.close()
        }
        store.reload()
        assertTrue(store.profiles.value.all.isEmpty())

        val result = BackupManager.importJson(context, backupJson, store)
        assertEquals(1, result.profileCount)
        assertEquals(1, result.pcCount)

        assertEquals("LivingRoom", store.profiles.value.current?.name)
        assertEquals(2, store.profiles.value.current?.avatar)
        assertEquals("1920x1080", store.settings().getString("list_resolution", null))
        assertEquals(35000, store.settings().getInt("seekbar_bitrate_kbps", 0))
        assertEquals(true, store.settings().getBoolean("ui_reduced_motion", false))
        assertEquals(setOf("101", "202"), store.settings().getStringSet("ui_hidden_games", emptySet()))

        assertEquals("0123456789abcdef", File(context.filesDir, "uniqueid").readText())
        assertTrue(File(context.filesDir, "client.crt").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(File(context.filesDir, "client.key").readBytes().contentEquals(byteArrayOf(5, 6, 7, 8)))

        val dbRestored = ComputerDatabaseManager(context)
        try {
            val restored = dbRestored.allComputers
            assertEquals(1, restored.size)
            assertEquals("pc-uuid-1", restored[0].uuid)
            assertEquals("Gaming-Rig", restored[0].name)
            assertEquals("192.168.1.50", restored[0].localAddress?.address)
        } finally {
            dbRestored.close()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsNonDelosJson() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ProfileStore(context)
        BackupManager.importJson(context, """{"app":"other","version":1}""", store)
    }
}
