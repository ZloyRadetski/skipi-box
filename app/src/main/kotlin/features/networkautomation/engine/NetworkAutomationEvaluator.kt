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
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType

sealed interface NetworkAutomationDecision {
    data class SwitchServer(val serverId: Int) : NetworkAutomationDecision
    data object DisconnectVpn : NetworkAutomationDecision
    data object NoChange : NetworkAutomationDecision
}

object NetworkAutomationEvaluator {

    fun getActivePhysicalCapabilities(context: Context, capabilities: NetworkCapabilities? = null): NetworkCapabilities? {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return capabilities

        // If passed capabilities is already physical (not VPN), use it
        if (capabilities != null && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                return capabilities
            }
        }

        // Search allNetworks for an active physical network
        val networks = runCatching { cm.allNetworks }.getOrNull() ?: emptyArray()
        var wifiCaps: NetworkCapabilities? = null
        var cellularCaps: NetworkCapabilities? = null
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifiCaps = caps
                break // Prefer Wi-Fi if available
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                cellularCaps = caps
            }
        }

        return wifiCaps ?: cellularCaps ?: run {
            val active = cm.activeNetwork ?: return@run null
            val activeCaps = cm.getNetworkCapabilities(active) ?: return@run null
            if (!activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) activeCaps else null
        } ?: capabilities
    }

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

        // 2. Try looking across all Wi-Fi networks if not yet found
        if ((rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") && cm != null) {
            val networks = runCatching { cm.allNetworks }.getOrNull() ?: emptyArray()
            for (net in networks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
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

        // 3. Fallback to WifiManager
        if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
            runCatching {
                val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                rawSsid = wm?.connectionInfo?.ssid
            }
        }

        val cleaned = rawSsid?.trim('"', ' ')
        return if (!cleaned.isNullOrBlank() && cleaned != "<unknown ssid>" && cleaned != "0x") cleaned else null
    }

    fun evaluate(context: Context, state: AppState, capabilities: NetworkCapabilities? = null): NetworkAutomationDecision {
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
        val isCellular = physicalCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        val matchedRule: NetworkAutomationRule? = when {
            isWifi -> {
                val currentSsid = getCurrentWifiSsid(context, physicalCaps)
                val specificMatch = if (!currentSsid.isNullOrBlank()) {
                    enabledRules.firstOrNull { rule ->
                        rule.type == NetworkRuleType.SPECIFIC_WIFI &&
                            rule.ssid?.trim()?.equals(currentSsid, ignoreCase = true) == true
                    }
                } else {
                    null
                }
                specificMatch ?: enabledRules.firstOrNull { rule -> rule.type == NetworkRuleType.ANY_WIFI }
            }
            isCellular -> {
                enabledRules.firstOrNull { rule -> rule.type == NetworkRuleType.CELLULAR }
            }
            else -> null
        }

        if (matchedRule == null) {
            return NetworkAutomationDecision.NoChange
        }

        return when (matchedRule.action) {
            NetworkRuleAction.DISCONNECT_VPN -> NetworkAutomationDecision.DisconnectVpn
            NetworkRuleAction.SWITCH_SERVER -> {
                val targetServerId = matchedRule.targetServerId
                if (targetServerId != null && state.proxyServers.any { it.id == targetServerId }) {
                    NetworkAutomationDecision.SwitchServer(targetServerId)
                } else {
                    NetworkAutomationDecision.NoChange
                }
            }
        }
    }
}
