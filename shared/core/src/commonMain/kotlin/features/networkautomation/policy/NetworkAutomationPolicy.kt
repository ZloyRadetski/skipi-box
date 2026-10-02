// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.policy

import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType

/** Platform-neutral transports reported for a physical upstream network. */
enum class ObservedNetworkTransport {
    WIFI,
    ETHERNET,
    CELLULAR,
    OTHER,
}

/** Facts collected by a platform adapter; SSID is optional when unavailable or not permitted. */
data class ObservedNetwork(
    val transports: Set<ObservedNetworkTransport>,
    val wifiSsid: String? = null,
)

sealed interface NetworkAutomationDecision {
    data class SwitchServer(val serverId: Int, val requireAlreadyRunning: Boolean = false) : NetworkAutomationDecision
    data object DisconnectVpn : NetworkAutomationDecision
    data object NoChange : NetworkAutomationDecision
}

data class NetworkAutomationPolicyInput(
    val networkAutomationEnabled: Boolean,
    val onDemandVpnEnabled: Boolean,
    val rules: List<NetworkAutomationRule>,
    val network: ObservedNetwork?,
    val availableServerIds: Set<Int>,
)

/** Chooses the configured action from observed network facts without platform APIs. */
object NetworkAutomationPolicy {

    /** Transport order matches physical-network selection in the Android adapter. */
    fun preferredTransport(network: ObservedNetwork): ObservedNetworkTransport? = when {
        ObservedNetworkTransport.WIFI in network.transports -> ObservedNetworkTransport.WIFI
        ObservedNetworkTransport.ETHERNET in network.transports -> ObservedNetworkTransport.ETHERNET
        ObservedNetworkTransport.CELLULAR in network.transports -> ObservedNetworkTransport.CELLULAR
        else -> null
    }

    fun requiresWifiSsid(rules: List<NetworkAutomationRule>): Boolean =
        rules.any { it.enabled && it.type == NetworkRuleType.SPECIFIC_WIFI }

    fun findMatchingRule(
        enabledRules: List<NetworkAutomationRule>,
        network: ObservedNetwork?,
    ): NetworkAutomationRule? {
        val transport = network?.let(::preferredTransport) ?: return null
        return when (transport) {
            ObservedNetworkTransport.WIFI -> {
                val currentSsid = network.wifiSsid
                val specificMatch = if (!currentSsid.isNullOrBlank()) {
                    enabledRules.firstOrNull { rule ->
                        rule.enabled && rule.type == NetworkRuleType.SPECIFIC_WIFI &&
                            rule.ssid?.trim()?.equals(currentSsid.trim(), ignoreCase = true) == true
                    }
                } else {
                    null
                }
                specificMatch ?: enabledRules.firstOrNull { it.enabled && it.type == NetworkRuleType.ANY_WIFI }
            }
            ObservedNetworkTransport.CELLULAR ->
                enabledRules.firstOrNull { it.enabled && it.type == NetworkRuleType.CELLULAR }
            ObservedNetworkTransport.ETHERNET, ObservedNetworkTransport.OTHER -> null
        }
    }

    fun evaluate(input: NetworkAutomationPolicyInput): NetworkAutomationDecision {
        if (!input.networkAutomationEnabled && !input.onDemandVpnEnabled) {
            return NetworkAutomationDecision.NoChange
        }
        if (input.network == null) return NetworkAutomationDecision.NoChange

        val matchedRule = findMatchingRule(input.rules, input.network)
        return makeDecision(matchedRule, input.availableServerIds)
    }

    fun makeDecision(
        matchedRule: NetworkAutomationRule?,
        availableServerIds: Set<Int>,
    ): NetworkAutomationDecision {
        if (matchedRule == null) return NetworkAutomationDecision.NoChange
        return when (matchedRule.action) {
            NetworkRuleAction.DISCONNECT_VPN -> NetworkAutomationDecision.DisconnectVpn
            NetworkRuleAction.SWITCH_SERVER -> matchedRule.targetServerId
                ?.takeIf { it in availableServerIds }
                ?.let { NetworkAutomationDecision.SwitchServer(it) }
                ?: NetworkAutomationDecision.NoChange
            NetworkRuleAction.SWITCH_IF_CONNECTED -> matchedRule.targetServerId
                ?.takeIf { it in availableServerIds }
                ?.let { NetworkAutomationDecision.SwitchServer(it, requireAlreadyRunning = true) }
                ?: NetworkAutomationDecision.NoChange
        }
    }
}
