package com.limelight.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Detects whether the device is actively streaming over a cellular connection with weak signal
 * strength (level <= [CellSignalStrength.SIGNAL_STRENGTH_MODERATE], i.e. 0-2 of 4 bars), without
 * requiring any runtime permissions.
 */
object CellularSignal {
    @JvmStatic
    fun isWeak(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connMgr.activeNetwork ?: return false
        val caps = connMgr.getNetworkCapabilities(network) ?: return false
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                connMgr.isActiveNetworkMetered)
        if (!isCellular) return false
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        val level = runCatching { telephony.signalStrength?.level }.getOrNull() ?: return false
        return isWeakLevel(level)
    }

    internal fun isWeakLevel(level: Int): Boolean = level <= CellSignalStrength.SIGNAL_STRENGTH_MODERATE
}

@Composable
fun rememberWeakMobileSignal(): Boolean {
    val context = LocalContext.current.applicationContext
    var weak by remember { mutableStateOf(CellularSignal.isWeak(context)) }
    DisposableEffect(context) {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connMgr == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    weak = CellularSignal.isWeak(context)
                }

                override fun onLost(network: Network) {
                    weak = CellularSignal.isWeak(context)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    weak = CellularSignal.isWeak(context)
                }
            }
            val registered = runCatching {
                connMgr.registerDefaultNetworkCallback(callback)
                true
            }.getOrDefault(false)
            onDispose {
                if (registered) runCatching { connMgr.unregisterNetworkCallback(callback) }
            }
        }
    }
    return weak
}
