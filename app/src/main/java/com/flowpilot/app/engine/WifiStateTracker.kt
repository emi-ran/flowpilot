package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.util.concurrent.ConcurrentLinkedQueue

enum class WifiStateEvent {
    CONNECTED,
    DISCONNECTED,
}

data class WifiTransition(
    val event: WifiStateEvent,
    val ssid: String,
)

data class WifiTrackerState(
    val currentNetwork: Any? = null,
    val currentSsid: String? = null,
    val baselineEstablished: Boolean = false,
)

object WifiStateReducer {

    fun establishBaseline(
        state: WifiTrackerState,
        initialSsid: String?,
        initialNetwork: Any? = null,
    ): WifiTrackerState {
        return state.copy(
            currentNetwork = if (initialSsid != null) initialNetwork else null,
            currentSsid = initialSsid,
            baselineEstablished = true,
        )
    }

    data class TransitionResult(
        val state: WifiTrackerState,
        val transitions: List<WifiTransition>,
    )

    fun onCapabilitiesChanged(
        state: WifiTrackerState,
        network: Any,
        ssid: String?,
    ): TransitionResult {
        if (ssid != null) {
            if (state.currentSsid == null) {
                val newState = state.copy(currentNetwork = network, currentSsid = ssid)
                val transitions = if (state.baselineEstablished) {
                    listOf(WifiTransition(WifiStateEvent.CONNECTED, ssid))
                } else {
                    emptyList()
                }
                return TransitionResult(newState, transitions)
            } else if (state.currentSsid != ssid) {
                val previousSsid = state.currentSsid
                val newState = state.copy(currentNetwork = network, currentSsid = ssid)
                val transitions = if (state.baselineEstablished) {
                    listOf(
                        WifiTransition(WifiStateEvent.DISCONNECTED, previousSsid),
                        WifiTransition(WifiStateEvent.CONNECTED, ssid),
                    )
                } else {
                    emptyList()
                }
                return TransitionResult(newState, transitions)
            } else {
                val newState = state.copy(currentNetwork = network)
                return TransitionResult(newState, emptyList())
            }
        } else {
            if (state.currentNetwork == network && state.currentSsid != null) {
                val previousSsid = state.currentSsid
                val newState = state.copy(currentNetwork = null, currentSsid = null)
                val transitions = if (state.baselineEstablished) {
                    listOf(WifiTransition(WifiStateEvent.DISCONNECTED, previousSsid))
                } else {
                    emptyList()
                }
                return TransitionResult(newState, transitions)
            }
            return TransitionResult(state, emptyList())
        }
    }

    fun onNetworkLost(
        state: WifiTrackerState,
        network: Any,
    ): TransitionResult {
        if (state.currentNetwork != null && state.currentNetwork == network && state.currentSsid != null) {
            val previousSsid = state.currentSsid
            val newState = state.copy(currentNetwork = null, currentSsid = null)
            val transitions = if (state.baselineEstablished) {
                listOf(WifiTransition(WifiStateEvent.DISCONNECTED, previousSsid))
            } else {
                emptyList()
            }
            return TransitionResult(newState, transitions)
        }
        return TransitionResult(state, emptyList())
    }

    fun onLegacyDisconnected(state: WifiTrackerState): TransitionResult {
        if (state.currentSsid != null) {
            val previousSsid = state.currentSsid
            val newState = state.copy(currentNetwork = null, currentSsid = null)
            val transitions = if (state.baselineEstablished) {
                listOf(WifiTransition(WifiStateEvent.DISCONNECTED, previousSsid))
            } else {
                emptyList()
            }
            return TransitionResult(newState, transitions)
        }
        return TransitionResult(state, emptyList())
    }
}

/**
 * Tracks Wi-Fi connectivity transitions with startup baseline and duplicate prevention.
 * Derives SSID directly from callback NetworkCapabilities to maintain correct state
 * even when mobile data is the default active network.
 * Respects privacy: does not log SSIDs, network history, or persist transient SSIDs.
 */
class WifiStateTracker(private val context: Context) {
    private val transitions = ConcurrentLinkedQueue<WifiTransition>()
    private var registered = false
    private var state = WifiTrackerState()

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val ssid = extractSsidFromNetwork(network, caps, connectivityManager, wifiManager)
                handleCapabilitiesChanged(network, ssid)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val ssid = extractSsidFromNetwork(network, networkCapabilities, connectivityManager, wifiManager)
                handleCapabilitiesChanged(network, ssid)
            }
        }

        override fun onLost(network: Network) {
            handleNetworkLost(network)
        }
    }

    private val legacyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION,
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    checkLegacyState()
                }
            }
        }
    }

    fun start() {
        if (registered) return
        val initialSsid = queryCurrentSsid(connectivityManager, wifiManager)
        synchronized(this) {
            state = WifiStateReducer.establishBaseline(state, initialSsid)
        }

        val cm = connectivityManager
        if (cm != null) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            try {
                cm.registerNetworkCallback(request, networkCallback)
            } catch (_: Throwable) {
                registerLegacyReceiver()
            }
        } else {
            registerLegacyReceiver()
        }
        registered = true
    }

    private fun registerLegacyReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(legacyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(legacyReceiver, filter)
        }
    }

    @Synchronized
    private fun handleCapabilitiesChanged(network: Network, ssid: String?) {
        val result = WifiStateReducer.onCapabilitiesChanged(state, network, ssid)
        state = result.state
        transitions.addAll(result.transitions)
    }

    @Synchronized
    private fun handleNetworkLost(network: Network) {
        val result = WifiStateReducer.onNetworkLost(state, network)
        state = result.state
        transitions.addAll(result.transitions)
    }

    @Synchronized
    private fun checkLegacyState() {
        val currentSsid = queryCurrentSsid(connectivityManager, wifiManager)
        if (currentSsid != null) {
            val result = WifiStateReducer.onCapabilitiesChanged(state, "legacy_wifi", currentSsid)
            state = result.state
            transitions.addAll(result.transitions)
        } else {
            val result = WifiStateReducer.onLegacyDisconnected(state)
            state = result.state
            transitions.addAll(result.transitions)
        }
    }

    @Synchronized
    fun getCurrentConnectedSsid(): String? {
        return state.currentSsid ?: queryCurrentSsid(connectivityManager, wifiManager)
    }

    fun isConnectedTo(ssid: String): Boolean {
        val current = getCurrentConnectedSsid() ?: return false
        return normalizeSsid(current).equals(normalizeSsid(ssid), ignoreCase = true)
    }

    fun drainTransitions(): List<WifiTransition> = buildList {
        while (true) add(transitions.poll() ?: break)
    }

    fun stop() {
        if (!registered) return
        val cm = connectivityManager
        try {
            cm?.unregisterNetworkCallback(networkCallback)
        } catch (_: Throwable) {}
        try {
            context.unregisterReceiver(legacyReceiver)
        } catch (_: Throwable) {}
        registered = false
        synchronized(this) {
            state = WifiTrackerState()
        }
        transitions.clear()
    }

    companion object {
        fun extractSsidFromNetwork(
            network: Network,
            capabilities: NetworkCapabilities?,
            cm: ConnectivityManager?,
            wm: WifiManager?,
        ): String? {
            val caps = capabilities ?: cm?.getNetworkCapabilities(network) ?: return null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = caps.transportInfo as? WifiInfo
                if (wifiInfo != null) {
                    val raw = wifiInfo.ssid
                    val cleaned = normalizeSsid(raw)
                    if (isValidSsid(cleaned)) return cleaned
                }
            }

            val info = try { wm?.connectionInfo } catch (_: Throwable) { null }
            if (info != null) {
                val cleaned = normalizeSsid(info.ssid)
                if (isValidSsid(cleaned)) return cleaned
            }
            return null
        }

        fun queryCurrentSsid(cm: ConnectivityManager?, wm: WifiManager?): String? {
            if (cm != null) {
                try {
                    val networks = cm.allNetworks
                    for (network in networks) {
                        val caps = cm.getNetworkCapabilities(network)
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            val ssid = extractSsidFromNetwork(network, caps, cm, wm)
                            if (ssid != null) return ssid
                        }
                    }
                } catch (_: Throwable) {}
            }

            val info = try { wm?.connectionInfo } catch (_: Throwable) { null }
            if (info != null) {
                val cleaned = normalizeSsid(info.ssid)
                if (isValidSsid(cleaned)) return cleaned
            }
            return null
        }

        fun normalizeSsid(raw: String?): String {
            if (raw == null) return ""
            var s = raw.trim()
            if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
                s = s.substring(1, s.length - 1)
            }
            return s
        }

        fun isValidSsid(ssid: String): Boolean {
            return ssid.isNotBlank() && ssid != "<unknown ssid>" && ssid != "0x"
        }
    }
}
