package com.limelight.shell.system

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.CellInfo
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import com.limelight.BuildConfig

/** How the cellular indicator is coloured: as the other status icons, or in the warning or error colour (StatusColors). */
enum class SignalSeverity { OK, WARN, BAD }

/** What the pill under the status bar says; null: nothing to say, no pill. */
enum class SignalProblem { WEAK_CELLULAR, NO_SERVICE, UNSTABLE_WIFI }

/** The cellular indicator of the status bar: 0 to 4 bars, the network's short name ("5G", "LTE", …; empty when unknown) and its colour. */
@Immutable
data class CellStatus(val bars: Int, val network: String, val severity: SignalSeverity)

/**
 * What the shell shows about the network beside Wi-Fi's own glyph: the cellular indicator ([cell], null on a device without a
 * modem or a ready SIM, and while nothing is known) and the problem the pill names ([problem]). [simulated]: a debug build's
 * preview ([NetworkPreview]), which a screen reader says.
 */
@Immutable
data class NetworkSignal(val cell: CellStatus? = null, val problem: SignalProblem? = null, val simulated: Boolean = false)

/** One cell the modem reports: its technology, its level (0 to 4) and, for LTE, its RSRP in dBm (null when unknown). */
internal data class CellSample(val network: String, val level: Int, val lteRsrp: Int? = null)

/** The bars and the network name of what the modem reports. */
internal data class CellReading(val bars: Int, val network: String)

/** What the device's data goes over: the route a stream would take. */
internal enum class Route { NONE, CELLULAR, WIFI, OTHER }

/**
 * The reading of the cells the modem reports, null for none (unknown is not zero bars).
 *
 * The bars are the weakest cell's (a 5G connection anchored on LTE is as good as its weaker leg), and an LTE cell at an RSRP of
 * −105 dBm or less counts as two bars at most, as phones show it (both from the contribution this is built on, #134). The name
 * is the newest technology among the cells.
 */
internal fun cellReading(cells: List<CellSample>): CellReading? {
    if (cells.isEmpty()) return null
    val bars = cells.minOf { cell ->
        if (cell.lteRsrp != null && cell.lteRsrp <= WEAK_LTE_RSRP) minOf(cell.level, WEAK_BARS) else cell.level
    }.coerceIn(0, 4)
    val network = NETWORK_ORDER.firstOrNull { name -> cells.any { it.network == name } } ?: ""
    return CellReading(bars, network)
}

/**
 * A condition that counts only once it has lasted: true after [raiseMs] of the raw value being true, false again after [clearMs]
 * of it being false. A signal that hovers at a threshold therefore neither shows nor hides the pill over and over.
 */
internal class Debounced(private val raiseMs: Long, private val clearMs: Long) {
    var active = false
        private set
    private var differsSince = -1L

    fun update(raw: Boolean, now: Long): Boolean {
        if (raw == active) differsSince = -1L
        else {
            if (differsSince < 0L) differsSince = now
            if (now - differsSince >= (if (raw) raiseMs else clearMs)) { active = raw; differsSince = -1L }
        }
        return active
    }

    /** When the pending change takes effect if the raw value stays as it is; null while nothing is pending. */
    fun dueAt(): Long? = if (differsSince < 0L) null else differsSince + (if (active) clearMs else raiseMs)
}

/**
 * Whether the Wi-Fi link is unstable, from the samples the system reports about the connected link in the last [windowMs]
 * (RSSI in dBm, the negotiated link speed in Mbit/s where known) and the times the link was lost.
 *
 * Unstable: the link was lost twice; or the RSSI moved by [RSSI_SWING] dB or more; or the link speed fell to half while the RSSI
 * is [WEAK_RSSI] dBm or less (a speed that falls on a strong signal is the access point saving power, not a bad link). Fewer than
 * [minSamples] samples say nothing ([unstable] is null): the system reports only changes, so a quiet link stays unknown, which
 * shows no pill. This is the local link only, not the way to the host.
 */
internal class WifiStability(private val windowMs: Long = WIFI_WINDOW_MS, private val minSamples: Int = WIFI_MIN_SAMPLES) {
    private class Sample(val at: Long, val rssi: Int, val mbps: Int?)

    private val samples = ArrayDeque<Sample>()
    private val losses = ArrayDeque<Long>()

    fun sample(rssi: Int, mbps: Int?, now: Long) {
        samples.addLast(Sample(now, rssi, mbps?.takeIf { it > 0 }))
        prune(now)
    }

    fun lost(now: Long) {
        losses.addLast(now)
        prune(now)
    }

    fun unstable(now: Long): Boolean? {
        prune(now)
        if (losses.size >= 2) return true
        if (samples.size < minSamples) return null
        val swing = samples.maxOf { it.rssi } - samples.minOf { it.rssi }
        if (swing >= RSSI_SWING) return true
        val speeds = samples.mapNotNull { it.mbps }
        return speeds.size >= 2 && speeds.last() * 2 <= speeds.max() && samples.last().rssi <= WEAK_RSSI
    }

    /** When the oldest thing in the window leaves it (the verdict may change then); null while the window is empty. */
    fun nextExpiry(): Long? = listOfNotNull(samples.firstOrNull()?.at, losses.firstOrNull()).minOrNull()?.plus(windowMs)

    private fun prune(now: Long) {
        while (samples.isNotEmpty() && now - samples.first().at >= windowMs) samples.removeFirst()
        while (losses.isNotEmpty() && now - losses.first() >= windowMs) losses.removeFirst()
    }
}

/**
 * Turns what the system reports into what the shell shows. Pure: the caller feeds it readings with their time and asks for the
 * [state]; [nextCheck] says when to ask again without a new reading (a pending change, a sample leaving the window).
 *
 * The cellular signal is a problem only while a stream would go over it: the route is cellular, or there is none (no service and
 * no Wi-Fi). On Wi-Fi the bars still show the modem's level, uncoloured. Two bars or fewer are weak, none is no service; an
 * unstable Wi-Fi link is a problem while the route is Wi-Fi. Each counts after [raiseMs] and ends after [clearMs].
 */
internal class SignalJudge(raiseMs: Long = RAISE_MS, clearMs: Long = CLEAR_MS) {
    var cell: CellReading? = null
    var route: Route = Route.NONE
    val wifi = WifiStability()
    private val weak = Debounced(raiseMs, clearMs)
    private val noService = Debounced(raiseMs, clearMs)
    private val unstable = Debounced(raiseMs, clearMs)

    fun state(now: Long): NetworkSignal {
        val reading = cell
        val overCellular = route == Route.CELLULAR || route == Route.NONE
        val none = noService.update(reading != null && reading.bars == 0 && overCellular, now)
        val low = weak.update(reading != null && reading.bars in 1..WEAK_BARS && overCellular, now)
        val shaky = unstable.update(route == Route.WIFI && wifi.unstable(now) == true, now)
        val severity = when { none -> SignalSeverity.BAD; low -> SignalSeverity.WARN; else -> SignalSeverity.OK }
        val problem = when { none -> SignalProblem.NO_SERVICE; low -> SignalProblem.WEAK_CELLULAR; shaky -> SignalProblem.UNSTABLE_WIFI; else -> null }
        return NetworkSignal(reading?.let { CellStatus(it.bars, it.network, severity) }, problem)
    }

    fun nextCheck(): Long? = listOfNotNull(weak.dueAt(), noService.dueAt(), unstable.dueAt(), wifi.nextExpiry()).minOrNull()
}

/**
 * A debug build's stand-in for the network, so the indicator and the pill can be seen on a device without a modem (the AYN
 * Odin): set from the debug-only settings row, kept in memory only (off at every process start) and never read in a release build.
 */
object NetworkPreview {
    enum class Mode(val label: String, val signal: NetworkSignal?) {
        OFF("Off", null), // l10n-ignore: debug build only
        GOOD_5G("Good 5G", NetworkSignal(CellStatus(4, "5G", SignalSeverity.OK), null, simulated = true)), // l10n-ignore
        WEAK_LTE("Weak LTE", NetworkSignal(CellStatus(2, "LTE", SignalSeverity.WARN), SignalProblem.WEAK_CELLULAR, simulated = true)), // l10n-ignore
        NO_SERVICE("No service", NetworkSignal(CellStatus(0, "LTE", SignalSeverity.BAD), SignalProblem.NO_SERVICE, simulated = true)), // l10n-ignore
        STABLE_WIFI("Stable Wi-Fi", NetworkSignal(CellStatus(4, "LTE", SignalSeverity.OK), null, simulated = true)), // l10n-ignore
        UNSTABLE_WIFI("Unstable Wi-Fi", NetworkSignal(CellStatus(4, "LTE", SignalSeverity.OK), SignalProblem.UNSTABLE_WIFI, simulated = true)), // l10n-ignore
    }

    var mode by mutableStateOf(Mode.OFF)
}

/**
 * The network signal for the status bar and its pill, one source for the shell. It listens only while the shell is started (not
 * under a stream, not in the background) and never polls: the modem's signal reports, the default network's changes, and one
 * delayed look when a pending change is due ([SignalJudge.nextCheck]).
 */
@Composable
fun rememberNetworkSignal(): State<NetworkSignal> {
    val context = LocalContext.current.applicationContext
    val monitor = remember(context) { NetworkSignalMonitor(context) }
    LifecycleStartEffect(monitor) {
        monitor.start()
        onStopOrDispose { monitor.stop() }
    }
    return remember(monitor) { derivedStateOf { (if (BuildConfig.DEBUG) NetworkPreview.mode.signal else null) ?: monitor.state } }
}

/**
 * Listens to the system for [SignalJudge], on the main thread.
 *
 * Cellular needs a modem ([PackageManager.FEATURE_TELEPHONY] and a phone type) and a ready SIM, and no permission: the signal
 * strength listener needs none, and the network's name comes from the kinds of cells it reports, not from the data network type
 * (which needs READ_PHONE_STATE). The listener follows the default data SIM: it is set up again when that changes, checked on
 * every change of the default network.
 */
private class NetworkSignalMonitor(private val context: Context) {
    var state by mutableStateOf(NetworkSignal())
        private set

    private val judge = SignalJudge()
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { publish() }
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val telephony: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)?.takeIf {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) && it.phoneType != TelephonyManager.PHONE_TYPE_NONE
    }
    private var started = false
    private var listening: TelephonyManager? = null
    private var listeningSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var modern: Any? = null
    private var legacy: PhoneStateListener? = null

    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val now = now()
            judge.route = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Route.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Route.CELLULAR
                else -> Route.OTHER
            }
            if (judge.route == Route.WIFI) {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) caps.transportInfo as? WifiInfo else null
                val rssi = info?.rssi ?: caps.signalStrength
                if (rssi != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && rssi > MIN_RSSI) judge.wifi.sample(rssi, info?.linkSpeed, now)
            }
            followDataSim()
            publish()
        }

        override fun onLost(network: Network) {
            if (judge.route == Route.WIFI) judge.wifi.lost(now())
            judge.route = Route.NONE
            followDataSim()
            publish()
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching { connectivity?.registerDefaultNetworkCallback(network, handler) }
        followDataSim()
        publish()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(recheck)
        runCatching { connectivity?.unregisterNetworkCallback(network) }
        unlisten()
    }

    /** Listens to the default data SIM while it is ready; to nothing (and shows no cellular indicator) otherwise. */
    private fun followDataSim() {
        val base = telephony ?: return
        val sub = SubscriptionManager.getDefaultDataSubscriptionId()
        if (listening != null && sub == listeningSub) return
        unlisten()
        val manager = if (sub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) runCatching { base.createForSubscriptionId(sub) }.getOrDefault(base) else base
        if (runCatching { manager.simState }.getOrNull() != TelephonyManager.SIM_STATE_READY) return
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) modern = listenModern(manager)
            else legacy = listenLegacy(manager)
        }.isSuccess
        if (registered) { listening = manager; listeningSub = sub }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun listenModern(manager: TelephonyManager): Any {
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = report(signalStrength)
        }
        manager.registerTelephonyCallback(context.mainExecutor, callback)
        return callback
    }

    @Suppress("DEPRECATION")
    private fun listenLegacy(manager: TelephonyManager): PhoneStateListener {
        val listener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = report(signalStrength)
        }
        manager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        return listener
    }

    @Suppress("DEPRECATION")
    private fun unlisten() {
        val manager = listening ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (modern as? TelephonyCallback)?.let(manager::unregisterTelephonyCallback)
            else legacy?.let { manager.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
        modern = null
        legacy = null
        listening = null
        listeningSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        judge.cell = null
    }

    private fun report(signal: SignalStrength) {
        judge.cell = cellReading(signal.cellSignalStrengths.map { cell ->
            val network = when (cell) {
                is CellSignalStrengthNr -> NETWORK_5G
                is CellSignalStrengthLte -> NETWORK_LTE
                is CellSignalStrengthWcdma, is CellSignalStrengthTdscdma, is CellSignalStrengthCdma -> NETWORK_3G
                is CellSignalStrengthGsm -> NETWORK_2G
                else -> ""
            }
            CellSample(network, cell.level, (cell as? CellSignalStrengthLte)?.rsrp?.takeIf { it != CellInfo.UNAVAILABLE })
        })
        publish()
    }

    /** Shows the judge's verdict and, when a change is pending, looks once more when it is due. */
    private fun publish() {
        if (!started) return
        val now = now()
        state = judge.state(now)
        handler.removeCallbacks(recheck)
        judge.nextCheck()?.let { handler.postDelayed(recheck, (it - now).coerceAtLeast(0L) + 1L) }
    }

    private fun now() = SystemClock.elapsedRealtime()
}

internal const val NETWORK_5G = "5G" // l10n-ignore: a network's name
internal const val NETWORK_LTE = "LTE" // l10n-ignore
internal const val NETWORK_3G = "3G" // l10n-ignore
internal const val NETWORK_2G = "2G" // l10n-ignore
private val NETWORK_ORDER = listOf(NETWORK_5G, NETWORK_LTE, NETWORK_3G, NETWORK_2G)

/** Two bars or fewer are a weak signal (the contribution's threshold, #134). */
private const val WEAK_BARS = 2
private const val WEAK_LTE_RSRP = -105

/** A problem shows after it lasted this long and goes after the signal was fine for this long. */
internal const val RAISE_MS = 5_000L
internal const val CLEAR_MS = 10_000L

internal const val WIFI_WINDOW_MS = 30_000L
internal const val WIFI_MIN_SAMPLES = 4
internal const val RSSI_SWING = 15
internal const val WEAK_RSSI = -70

/** An RSSI at or below this is the system's "no reading" (−127 dBm). */
private const val MIN_RSSI = -127
