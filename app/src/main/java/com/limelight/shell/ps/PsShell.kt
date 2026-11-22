package com.limelight.shell.ps

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.limelight.ps.PsRegisteredConsole
import com.limelight.ps.PsTarget
import com.limelight.shell.ui.DelosColors
import com.limelight.shell.ui.hsl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Which world the start screen shows: the PCs (the palette the user picked) or the PlayStation consoles (DELOS in PlayStation
 * blue). The switch at the top of the start screen and L1/R1 change it; the PlayStation pairing screens keep it on.
 */
object PsMode {
    private val state = MutableStateFlow(false)
    val active: StateFlow<Boolean> get() = state
    fun set(on: Boolean) { state.value = on }
}

/**
 * The blue DELOS theme of the PlayStation mode (prototype F): deep navy backgrounds, PlayStation blue as the main color,
 * a lighter blue as the second one; always dark, whatever the user's palette.
 */
fun playStationColors(): DelosColors = DelosColors(
    dark = true, materialYou = false, baseHue = 220f, accentHue = 212f, accent2Hue = 205f, accent3Hue = 190f,
    bg = hsl(220f, 85f, 7f), bg2 = hsl(218f, 80f, 12f),
    surface = hsl(216f, 60f, 16f, .72f), surface2 = hsl(214f, 62f, 21f, .84f), surface3 = hsl(214f, 58f, 25f, .94f),
    line = hsl(210f, 80f, 75f, .16f), text = hsl(210f, 40f, 97f), text2 = hsl(212f, 45f, 80f), text3 = hsl(214f, 30f, 62f),
    accent = hsl(212f, 100f, 59f), accent2 = hsl(205f, 100f, 72f), accent3 = hsl(190f, 85f, 68f),
    onAccent = Color.White,
    glow = hsl(212f, 100f, 58f, .5f),
    scrim = hsl(222f, 80f, 3f, .62f),
)

/** A paired console as the shell lists it. */
data class PsConsole(val id: String, val name: String, val address: String, val console: PsRegisteredConsole)

/**
 * The consoles registered with DELOS (pairing result plus where it was found). The registration key and the morning are
 * credentials; they are kept in the app's private preferences for now [Keystore-backed storage is a follow-up step of #25].
 * Consoles paired on the debug bench are picked up once.
 */
class PsConsoleStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ps_consoles", Context.MODE_PRIVATE)
    private val bench = context.applicationContext.getSharedPreferences("ps_debug", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(load())
    val consoles: StateFlow<List<PsConsole>> get() = flow

    /** The PSN account id (base64) of the last sign-in, reused for the next pairing. */
    var accountId: String?
        get() = prefs.getString("account", null) ?: bench.getString("account", null)
        set(value) { prefs.edit().putString("account", value).apply() }

    fun add(name: String, address: String, console: PsRegisteredConsole) {
        val id = PsHexId.of(console.mac)
        val next = flow.value.filter { it.id != id } + PsConsole(id, name, address, console)
        save(next)
    }

    fun remove(id: String) = save(flow.value.filter { it.id != id })

    /** Names the console [id] in DELOS; a blank name goes back to the console's own name (its nickname from pairing). */
    fun rename(id: String, name: String) = save(flow.value.map { c ->
        if (c.id != id) c else c.copy(name = name.trim().ifEmpty { c.console.nickname ?: c.name })
    })

    /** The console [id] answered from [address] (another network interface, a new lease): it is found there from now on. */
    fun moved(id: String, address: String) {
        if (flow.value.none { it.id == id && it.address != address }) return
        save(flow.value.map { c -> if (c.id != id) c else c.copy(address = address) })
    }

    private fun save(list: List<PsConsole>) {
        val enc = Base64.getEncoder()
        val array = JSONArray()
        for (c in list) array.put(JSONObject().put("id", c.id).put("name", c.name).put("address", c.address).put("target", c.console.target.name)
            .put("regist", enc.encodeToString(c.console.registKey)).put("morning", enc.encodeToString(c.console.morning)).put("mac", enc.encodeToString(c.console.mac))
            .put("nickname", c.console.nickname ?: c.name))
        prefs.edit().putString("list", array.toString()).apply()
        flow.value = list
    }

    private fun load(): List<PsConsole> {
        val dec = Base64.getDecoder()
        val text = prefs.getString("list", null)
        if (text == null) {
            // A console paired on the debug bench before the shell could pair one
            val address = bench.getString("address", null) ?: return emptyList()
            val target = runCatching { PsTarget.valueOf(bench.getString("target", "")!!) }.getOrNull() ?: return emptyList()
            val console = PsRegisteredConsole(target, dec.decode(bench.getString("regist", "")), dec.decode(bench.getString("morning", "")),
                dec.decode(bench.getString("mac", "")), bench.getString("nickname", null), null)
            return listOf(PsConsole(PsHexId.of(console.mac), console.nickname ?: "PlayStation", address, console))
        }
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PsConsole(o.getString("id"), o.getString("name"), o.getString("address"), PsRegisteredConsole(
                    PsTarget.valueOf(o.getString("target")), dec.decode(o.getString("regist")), dec.decode(o.getString("morning")),
                    dec.decode(o.getString("mac")), o.optString("nickname", o.getString("name")), null))
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        @Volatile private var instance: PsConsoleStore? = null
        fun get(context: Context): PsConsoleStore = instance ?: synchronized(this) { instance ?: PsConsoleStore(context).also { instance = it } }
    }
}

internal object PsHexId {
    fun of(mac: ByteArray): String = mac.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
