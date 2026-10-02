// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.policy

import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkAutomationPolicyTest {

    private val anyWifi = NetworkAutomationRule(
        id = "any-wifi",
        type = NetworkRuleType.ANY_WIFI,
        action = NetworkRuleAction.SWITCH_SERVER,
        targetServerId = 10,
    )
    private val homeWifi = NetworkAutomationRule(
        id = "home-wifi",
        type = NetworkRuleType.SPECIFIC_WIFI,
        ssid = "MyHome_5G",
        action = NetworkRuleAction.SWITCH_IF_CONNECTED,
        targetServerId = 20,
    )
    private val cellular = NetworkAutomationRule(
        id = "cellular",
        type = NetworkRuleType.CELLULAR,
        action = NetworkRuleAction.DISCONNECT_VPN,
    )

    @Test
    fun `specific Wi-Fi rule overrides any Wi-Fi and matches SSID ignoring case and surrounding spaces`() {
        val result = NetworkAutomationPolicy.evaluate(
            input(
                rules = listOf(anyWifi, homeWifi),
                network = observed(ObservedNetworkTransport.WIFI, ssid = " myhome_5g "),
            ),
        )

        assertEquals(NetworkAutomationDecision.SwitchServer(20, requireAlreadyRunning = true), result)
    }

    @Test
    fun `missing SSID falls back to any Wi-Fi rule`() {
        val result = NetworkAutomationPolicy.evaluate(
            input(
                rules = listOf(homeWifi, anyWifi),
                network = observed(ObservedNetworkTransport.WIFI),
            ),
        )

        assertEquals(NetworkAutomationDecision.SwitchServer(10), result)
    }

    @Test
    fun `missing SSID with no any Wi-Fi rule makes no change`() {
        val result = NetworkAutomationPolicy.evaluate(
            input(
                rules = listOf(homeWifi),
                network = observed(ObservedNetworkTransport.WIFI),
            ),
        )

        assertEquals(NetworkAutomationDecision.NoChange, result)
    }

    @Test
    fun `Wi-Fi takes priority over Ethernet and cellular when multiple transports are reported`() {
        val network = observed(
            ObservedNetworkTransport.CELLULAR,
            ObservedNetworkTransport.ETHERNET,
            ObservedNetworkTransport.WIFI,
            ssid = "MyHome_5G",
        )

        assertEquals(ObservedNetworkTransport.WIFI, NetworkAutomationPolicy.preferredTransport(network))
        assertEquals(
            NetworkAutomationDecision.SwitchServer(20, requireAlreadyRunning = true),
            NetworkAutomationPolicy.evaluate(input(listOf(homeWifi, cellular), network)),
        )
    }

    @Test
    fun `Ethernet takes priority over cellular and has no configured rule action`() {
        val network = observed(ObservedNetworkTransport.CELLULAR, ObservedNetworkTransport.ETHERNET)

        assertEquals(ObservedNetworkTransport.ETHERNET, NetworkAutomationPolicy.preferredTransport(network))
        assertEquals(NetworkAutomationDecision.NoChange, NetworkAutomationPolicy.evaluate(input(listOf(cellular), network)))
    }

    @Test
    fun `cellular rule is used when cellular is the preferred observed transport`() {
        assertEquals(
            NetworkAutomationDecision.DisconnectVpn,
            NetworkAutomationPolicy.evaluate(input(listOf(cellular), observed(ObservedNetworkTransport.CELLULAR))),
        )
    }

    @Test
    fun `disabled automation and on-demand VPN do not produce actions`() {
        val result = NetworkAutomationPolicy.evaluate(
            input(
                rules = listOf(anyWifi),
                network = observed(ObservedNetworkTransport.WIFI),
                networkAutomationEnabled = false,
                onDemandVpnEnabled = false,
            ),
        )

        assertEquals(NetworkAutomationDecision.NoChange, result)
    }

    @Test
    fun `manual rule can switch only to an available server`() {
        val available = NetworkAutomationPolicy.evaluate(
            input(listOf(anyWifi), observed(ObservedNetworkTransport.WIFI), availableServerIds = setOf(10, 20)),
        )
        val missing = NetworkAutomationPolicy.evaluate(
            input(listOf(anyWifi), observed(ObservedNetworkTransport.WIFI), availableServerIds = setOf(20)),
        )

        assertIs<NetworkAutomationDecision.SwitchServer>(available)
        assertEquals(10, available.serverId)
        assertEquals(NetworkAutomationDecision.NoChange, missing)
    }

    @Test
    fun `disabled rules and absent network do not match`() {
        val disabled = anyWifi.copy(enabled = false)

        assertNull(NetworkAutomationPolicy.findMatchingRule(listOf(disabled), observed(ObservedNetworkTransport.WIFI)))
        assertEquals(NetworkAutomationDecision.NoChange, NetworkAutomationPolicy.evaluate(input(listOf(anyWifi), null)))
    }

    @Test
    fun `SSID collection is required only for enabled specific Wi-Fi rules`() {
        assertFalse(NetworkAutomationPolicy.requiresWifiSsid(listOf(anyWifi, homeWifi.copy(enabled = false))))
        assertTrue(NetworkAutomationPolicy.requiresWifiSsid(listOf(anyWifi, homeWifi)))
    }

    private fun input(
        rules: List<NetworkAutomationRule>,
        network: ObservedNetwork?,
        networkAutomationEnabled: Boolean = true,
        onDemandVpnEnabled: Boolean = true,
        availableServerIds: Set<Int> = setOf(10, 20),
    ) = NetworkAutomationPolicyInput(
        networkAutomationEnabled = networkAutomationEnabled,
        onDemandVpnEnabled = onDemandVpnEnabled,
        rules = rules,
        network = network,
        availableServerIds = availableServerIds,
    )

    private fun observed(
        vararg transports: ObservedNetworkTransport,
        ssid: String? = null,
    ) = ObservedNetwork(transports = transports.toSet(), wifiSsid = ssid)
}
