package com.limelight.shell.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceManager
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Exports and imports DELOS profiles, settings, paired PCs, and client pairing identity as a
 * single JSON backup file so users can migrate between devices (e.g. via LocalSend) and skip
 * onboarding on a fresh installation.
 */
object BackupManager {
    private const val FORMAT_APP = "delos"
    private const val FORMAT_VERSION = 1

    data class ImportResult(val profileCount: Int, val pcCount: Int)

    fun exportJson(context: Context, store: ProfileStore): String {
        val app = context.applicationContext
        val profiles = store.profiles.value
        val root = JSONObject()
            .put("app", FORMAT_APP)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())

        val profilesObj = JSONObject()
            .put("currentId", profiles.currentId ?: JSONObject.NULL)
        val profileList = JSONArray()
        profiles.all.forEach { profile ->
            val profilePrefs = app.getSharedPreferences("ui_profile_${profile.id}", Context.MODE_PRIVATE)
            profileList.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("avatar", profile.avatar)
                    .put("prefs", encodePrefs(profilePrefs))
            )
        }
        profilesObj.put("list", profileList)
        root.put("profiles", profilesObj)

        val identityObj = JSONObject()
        readFileText(File(app.filesDir, "uniqueid"))?.let { identityObj.put("uniqueId", it) }
        readFileBytes(File(app.filesDir, "client.crt"))?.let {
            identityObj.put("clientCertBase64", Base64.encodeToString(it, Base64.NO_WRAP))
        }
        readFileBytes(File(app.filesDir, "client.key"))?.let {
            identityObj.put("clientKeyBase64", Base64.encodeToString(it, Base64.NO_WRAP))
        }
        root.put("identity", identityObj)

        val computersArray = JSONArray()
        val db = ComputerDatabaseManager(app)
        try {
            db.allComputers.forEach { computer ->
                computersArray.put(encodeComputer(computer))
            }
        } finally {
            db.close()
        }
        root.put("computers", computersArray)
        return root.toString(2)
    }

    fun importJson(context: Context, rawJson: String, store: ProfileStore): ImportResult {
        val app = context.applicationContext
        val root = JSONObject(rawJson)
        require(root.optString("app") == FORMAT_APP) { "Not a valid DELOS backup file" }
        val version = root.optInt("version", 0)
        require(version in 1..FORMAT_VERSION) { "Unsupported backup version: $version" }

        // 1. Restore client identity so existing PC pairings work immediately without re-pairing
        root.optJSONObject("identity")?.let { identity ->
            identity.optString("uniqueId").takeIf { it.isNotBlank() }?.let { uid ->
                File(app.filesDir, "uniqueid").writeText(uid)
            }
            identity.optString("clientCertBase64").takeIf { it.isNotBlank() }?.let { b64 ->
                File(app.filesDir, "client.crt").writeBytes(Base64.decode(b64, Base64.DEFAULT))
            }
            identity.optString("clientKeyBase64").takeIf { it.isNotBlank() }?.let { b64 ->
                File(app.filesDir, "client.key").writeBytes(Base64.decode(b64, Base64.DEFAULT))
            }
        }

        // 2. Restore paired computers
        var pcCount = 0
        val computers = root.optJSONArray("computers")
        if (computers != null && computers.length() > 0) {
            val db = ComputerDatabaseManager(app)
            try {
                for (i in 0 until computers.length()) {
                    val obj = computers.optJSONObject(i) ?: continue
                    val details = decodeComputer(obj) ?: continue
                    db.updateComputer(details)
                    pcCount++
                }
            } finally {
                db.close()
            }
        }

        // 3. Restore profiles and per-profile preferences
        var profileCount = 0
        val profilesObj = root.optJSONObject("profiles")
        if (profilesObj != null) {
            val list = profilesObj.optJSONArray("list")
            val restoredProfiles = JSONArray()
            if (list != null) {
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val avatar = item.optInt("avatar", 0)
                    restoredProfiles.put(
                        JSONObject().put("id", id).put("name", name).put("avatar", avatar)
                    )
                    item.optJSONObject("prefs")?.let { prefsObj ->
                        val target = app.getSharedPreferences("ui_profile_$id", Context.MODE_PRIVATE)
                        decodePrefs(prefsObj, target)
                    }
                    profileCount++
                }
            }
            if (profileCount > 0) {
                val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(app)
                val currentId = profilesObj.optString("currentId").takeIf { it.isNotBlank() && it != "null" }
                defaultPrefs.edit()
                    .putString("ui_profiles", restoredProfiles.toString())
                    .apply {
                        if (currentId != null) putString("ui_profile", currentId)
                    }
                    .commit()
            }
        }

        store.reload()
        return ImportResult(profileCount = profileCount, pcCount = pcCount)
    }

    private fun encodePrefs(prefs: SharedPreferences): JSONObject {
        val out = JSONObject()
        prefs.all.forEach { (key, value) ->
            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "bool").put("v", value)
                is Int -> JSONObject().put("t", "int").put("v", value)
                is Long -> JSONObject().put("t", "long").put("v", value)
                is Float -> JSONObject().put("t", "float").put("v", value.toDouble())
                is String -> JSONObject().put("t", "string").put("v", value)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> null
            }
            if (entry != null) out.put(key, entry)
        }
        return out
    }

    private fun decodePrefs(obj: JSONObject, target: SharedPreferences) {
        val editor = target.edit().clear()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = obj.optJSONObject(key) ?: continue
            when (entry.optString("t")) {
                "bool" -> editor.putBoolean(key, entry.optBoolean("v"))
                "int" -> editor.putInt(key, entry.optInt("v"))
                "long" -> editor.putLong(key, entry.optLong("v"))
                "float" -> editor.putFloat(key, entry.optDouble("v").toFloat())
                "string" -> editor.putString(key, entry.optString("v"))
                "set" -> {
                    val arr = entry.optJSONArray("v")
                    val set = mutableSetOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            arr.optString(i)?.let { set += it }
                        }
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.commit()
    }

    private fun encodeComputer(c: ComputerDetails): JSONObject {
        val obj = JSONObject()
            .put("uuid", c.uuid)
            .put("name", c.name)
            .put("httpsPort", c.httpsPort)
        c.macAddress?.let { obj.put("macAddress", it) }
        c.localAddress?.let { obj.put("localAddress", ComputerDatabaseManager.tupleToJson(it)) }
        c.remoteAddress?.let { obj.put("remoteAddress", ComputerDatabaseManager.tupleToJson(it)) }
        c.manualAddress?.let { obj.put("manualAddress", ComputerDatabaseManager.tupleToJson(it)) }
        c.ipv6Address?.let { obj.put("ipv6Address", ComputerDatabaseManager.tupleToJson(it)) }
        c.serverCert?.let {
            runCatching { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }.getOrNull()
                ?.let { b64 -> obj.put("serverCertBase64", b64) }
        }
        return obj
    }

    private fun decodeComputer(obj: JSONObject): ComputerDetails? {
        val uuid = obj.optString("uuid").takeIf { it.isNotBlank() } ?: return null
        val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return null
        val c = ComputerDetails()
        c.uuid = uuid
        c.name = name
        c.httpsPort = obj.optInt("httpsPort", 47984)
        c.macAddress = obj.optString("macAddress").takeIf { it.isNotBlank() }
        c.localAddress = decodeTuple(obj.optJSONObject("localAddress"))
        c.remoteAddress = decodeTuple(obj.optJSONObject("remoteAddress"))
        c.manualAddress = decodeTuple(obj.optJSONObject("manualAddress"))
        c.ipv6Address = decodeTuple(obj.optJSONObject("ipv6Address"))
        obj.optString("serverCertBase64").takeIf { it.isNotBlank() }?.let { b64 ->
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            }.getOrNull()?.let { c.serverCert = it }
        }
        return c
    }

    private fun decodeTuple(obj: JSONObject?): ComputerDetails.AddressTuple? {
        if (obj == null) return null
        val addr = obj.optString("address").takeIf { it.isNotBlank() } ?: return null
        val port = obj.optInt("port", 47989)
        return ComputerDetails.AddressTuple(addr, port)
    }

    private fun readFileText(file: File): String? =
        if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    private fun readFileBytes(file: File): ByteArray? =
        if (file.exists()) runCatching { file.readBytes() }.getOrNull() else null
}
