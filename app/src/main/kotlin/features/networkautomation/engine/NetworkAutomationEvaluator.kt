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

    fun getCurrentWifiSsid(context: Context, capabilities: NetworkCapabilities? = null): String? {
        val appContext = context.applicationContext
        val caps = capabilities ?: run {
            val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return null
            val active = cm.activeNetwork ?: return null
            cm.getNetworkCapabilities(active)
        }

        var rawSsid: String? = null

        // Try getting SSID from NetworkCapabilities TransportInfo (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && caps != null) {
            val transportInfo = caps.transportInfo
            if (transportInfo is WifiInfo) {
                rawSsid = transportInfo.ssid
            }
        }

        // Fallback to WifiManager
        if (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") {
            runCatching {
                val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                rawSsid = wm?.connectionInfo?.ssid
            }
        }

        val cleaned = rawSsid?.trim('"', ' ')
        return if (!cleaned.isNullOrBlank() && cleaned != "<unknown ssid>") cleaned else null
    }

    fun evaluate(context: Context, state: AppState, capabilities: NetworkCapabilities? = null): NetworkAutomationDecision {
        if (!state.enableNetworkAutomation && !state.enableOnDemandVpn) {
            return NetworkAutomationDecision.NoChange
        }

        val enabledRules = state.networkAutomationRules.filter { it.enabled }
        if (enabledRules.isEmpty()) {
            return NetworkAutomationDecision.NoChange
        }

        val appContext = context.applicationContext
        val caps = capabilities ?: run {
            val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return NetworkAutomationDecision.NoChange
            val active = cm.activeNetwork ?: return NetworkAutomationDecision.NoChange
            cm.getNetworkCapabilities(active) ?: return NetworkAutomationDecision.NoChange
        }

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        val matchedRule: NetworkAutomationRule? = when {
            isWifi -> {
                val currentSsid = getCurrentWifiSsid(appContext, caps)
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
