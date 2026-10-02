// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import app.AppState
import features.networkautomation.policy.NetworkAutomationDecision as SharedNetworkAutomationDecision
import features.networkautomation.policy.NetworkAutomationPolicy
import features.networkautomation.policy.NetworkAutomationPolicyInput
import features.networkautomation.policy.ObservedNetwork
import features.networkautomation.policy.ObservedNetworkTransport
import features.networkautomation.model.NetworkAutomationRule

sealed interface NetworkAutomationDecision {
    data class SwitchServer(val serverId: Int, val requireAlreadyRunning: Boolean = false) : NetworkAutomationDecision
    data object DisconnectVpn : NetworkAutomationDecision
    data object NoChange : NetworkAutomationDecision
}

object NetworkAutomationEvaluator {

    internal const val DisconnectedNetworkIdentifier = "DISCONNECTED"

    /**
     * Do not read the SSID merely to identify a Wi-Fi transport. SSID is
     * location-sensitive on Android and is only needed for an explicit
     * SPECIFIC_WIFI rule with the runtime permission already granted.
     */
    fun getPhysicalNetworkIdentifier(
        context: Context,
        capabilities: NetworkCapabilities? = null,
        includeWifiSsid: Boolean = false,
    ): String {
        val physicalCaps = getActivePhysicalCapabilities(context, capabilities) ?: return DisconnectedNetworkIdentifier
        return when {
            physicalCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val ssid = if (includeWifiSsid) getCurrentWifiSsid(context, physicalCaps) else null
                if (!ssid.isNullOrBlank()) "WIFI:$ssid" else "WIFI:ANY"
            }
            physicalCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            physicalCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "UNKNOWN"
        }
    }

    @Suppress("DEPRECATION")
    fun getActivePhysicalCapabilities(context: Context, capabilities: NetworkCapabilities? = null): NetworkCapabilities? {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return capabilities?.takeIf { it.isPhysicalInternetNetwork() }

        // 1. If activeNetwork is directly a physical Internet network, use it.
        val active = cm.activeNetwork
        val activeCaps = active?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps?.isPhysicalInternetNetwork() == true) {
            return activeCaps
        }

        // 2. If VPN is active, inspect physical networks.
        // Wi-Fi or Ethernet ALWAYS take strict priority over Cellular.
        val networks = runCatching { cm.allNetworks }.getOrNull() ?: emptyArray()
        var wifiCaps: NetworkCapabilities? = null
        var ethernetCaps: NetworkCapabilities? = null
        var cellularCaps: NetworkCapabilities? = null

        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.isPhysicalInternetNetwork()) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifiCaps = caps
                break // Found active Wi-Fi, take priority
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                ethernetCaps = caps
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                cellularCaps = caps
            }
        }

        if (wifiCaps != null) return wifiCaps
        if (ethernetCaps != null) return ethernetCaps
        if (cellularCaps != null) return cellularCaps

        // 3. Fallback only when the callback itself describes a physical Internet network.
        return capabilities?.takeIf { it.isPhysicalInternetNetwork() }
    }

    @Suppress("DEPRECATION")
    fun getCurrentWifiSsid(context: Context, capabilities: NetworkCapabilities? = null): String? {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java)

        var rawSsid: String? = null

        // 1. Try getting SSID from passed capabilities TransportInfo (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities != null) {
            val transportInfo = capabilities.transportInfo
            if (transportInfo is WifiInfo) {
                rawSsid = transportInfo.ssid
            }
        }

        // 2. Try getting SSID from active network
        if ((rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") && cm != null) {
            val active = cm.activeNetwork
            if (active != null) {
                val caps = cm.getNetworkCapabilities(active)
                if (caps?.isPhysicalInternetNetwork() == true && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val transportInfo = caps.transportInfo
                        if (transportInfo is WifiInfo) {
                            val candidate = transportInfo.ssid
                            if (!candidate.isNullOrBlank() && candidate != "<unknown ssid>") {
                                rawSsid = candidate
                            }
                        }
                    }
                }
            }
        }

        // 3. Try looking across all Wi-Fi networks if not yet found
        if ((rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") && cm != null) {
            val networks = runCatching { cm.allNetworks }.getOrNull() ?: emptyArray()
            for (net in networks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                if (caps.isPhysicalInternetNetwork() && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val transportInfo = caps.transportInfo
                        if (transportInfo is WifiInfo) {
                            val candidate = transportInfo.ssid
                            if (!candidate.isNullOrBlank() && candidate != "<unknown ssid>") {
                                rawSsid = candidate
                                break
                            }
                        }
                    }
                }
            }
        }

        // 4. Fallback to WifiManager
        if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
            runCatching {
                val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                rawSsid = wm?.connectionInfo?.ssid
            }
        }

        val cleaned = rawSsid?.trim('"', ' ')
        return if (!cleaned.isNullOrBlank() && cleaned != "<unknown ssid>" && cleaned != "0x") cleaned else null
    }

    fun evaluate(
        context: Context,
        state: AppState,
        capabilities: NetworkCapabilities? = null,
        includeWifiSsid: Boolean = false,
    ): NetworkAutomationDecision {
        if (!state.enableNetworkAutomation && !state.enableOnDemandVpn) {
            return NetworkAutomationDecision.NoChange
        }

        val enabledRules = state.networkAutomationRules.filter { it.enabled }
        if (enabledRules.isEmpty()) {
            return NetworkAutomationDecision.NoChange
        }

        val physicalCaps = getActivePhysicalCapabilities(context, capabilities)
            ?: return NetworkAutomationDecision.NoChange

        val isWifi = physicalCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val currentSsid = if (isWifi && includeWifiSsid) getCurrentWifiSsid(context, physicalCaps) else null
        val transports = buildSet {
            if (isWifi) add(ObservedNetworkTransport.WIFI)
            if (physicalCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(ObservedNetworkTransport.ETHERNET)
            if (physicalCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(ObservedNetworkTransport.CELLULAR)
        }

        val decision = NetworkAutomationPolicy.evaluate(
            NetworkAutomationPolicyInput(
                networkAutomationEnabled = state.enableNetworkAutomation,
                onDemandVpnEnabled = state.enableOnDemandVpn,
                rules = enabledRules,
                network = ObservedNetwork(transports = transports, wifiSsid = currentSsid),
                availableServerIds = state.proxyServers.mapTo(mutableSetOf()) { it.id },
            ),
        )
        return decision.toAndroidDecision()
    }

    fun requiresWifiSsid(rules: List<NetworkAutomationRule>): Boolean {
        return NetworkAutomationPolicy.requiresWifiSsid(rules)
    }

    fun findMatchingRule(
        enabledRules: List<NetworkAutomationRule>,
        isWifi: Boolean,
        isCellular: Boolean,
        currentSsid: String?,
    ): NetworkAutomationRule? {
        val transports = buildSet {
            if (isWifi) add(ObservedNetworkTransport.WIFI)
            if (isCellular) add(ObservedNetworkTransport.CELLULAR)
        }
        return NetworkAutomationPolicy.findMatchingRule(
            enabledRules = enabledRules,
            network = ObservedNetwork(transports = transports, wifiSsid = currentSsid),
        )
    }

    fun makeDecision(
        matchedRule: NetworkAutomationRule?,
        state: AppState,
    ): NetworkAutomationDecision {
        return NetworkAutomationPolicy.makeDecision(
            matchedRule = matchedRule,
            availableServerIds = state.proxyServers.mapTo(mutableSetOf()) { it.id },
        ).toAndroidDecision()
    }
}

private fun SharedNetworkAutomationDecision.toAndroidDecision(): NetworkAutomationDecision =
    when (this) {
        is SharedNetworkAutomationDecision.SwitchServer ->
            NetworkAutomationDecision.SwitchServer(serverId, requireAlreadyRunning)
        SharedNetworkAutomationDecision.DisconnectVpn -> NetworkAutomationDecision.DisconnectVpn
        SharedNetworkAutomationDecision.NoChange -> NetworkAutomationDecision.NoChange
    }

/**
 * Identifies a physical transport that may be considered by a network rule.
 * Availability itself is tracked by [NetworkAutomationMonitor] from callback
 * lifecycle events, rather than relying on VALIDATED during an in-flight
 * Wi-Fi/LTE handover.
 */
internal fun isPhysicalInternetNetwork(
    hasPhysicalTransport: Boolean,
    hasInternetCapability: Boolean,
    isVpn: Boolean,
): Boolean {
    return hasPhysicalTransport && hasInternetCapability && !isVpn
}

internal fun NetworkCapabilities.isPhysicalInternetNetwork(): Boolean {
    return isPhysicalInternetNetwork(
        hasPhysicalTransport = hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        hasInternetCapability = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        isVpn = hasTransport(NetworkCapabilities.TRANSPORT_VPN),
    )
}
