// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.engine

import app.AppState
import app.ProxyServerState
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import features.proxy.server.model.VLESS
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetworkAutomationEvaluatorTest {

    private val server1 = ProxyServerState(
        id = 10,
        server = VLESS(remarks = "LTE Server", id = "uuid-1", server = "lte.example.com", port = "443"),
        groupId = 0,
    )
    private val server2 = ProxyServerState(
        id = 20,
        server = VLESS(remarks = "Home Server", id = "uuid-2", server = "home.example.com", port = "443"),
        groupId = 0,
    )
    private val appState = AppState(
        proxyServers = listOf(server1, server2),
        selectedProxyServerId = 10,
        enableNetworkAutomation = true,
        enableOnDemandVpn = true,
    )

    @Test
    fun testFindMatchingRuleForCellular() {
        val cellularRule = NetworkAutomationRule(
            id = "r1",
            type = NetworkRuleType.CELLULAR,
            action = NetworkRuleAction.SWITCH_SERVER,
            targetServerId = 10,
        )
        val wifiRule = NetworkAutomationRule(
            id = "r2",
            type = NetworkRuleType.ANY_WIFI,
            action = NetworkRuleAction.DISCONNECT_VPN,
        )
        val rules = listOf(cellularRule, wifiRule)

        val matched = NetworkAutomationEvaluator.findMatchingRule(
            enabledRules = rules,
            isWifi = false,
            isCellular = true,
            currentSsid = null,
        )

        assertEquals(cellularRule, matched)
    }

    @Test
    fun testFindMatchingRuleForSpecificWifiSsid() {
        val anyWifiRule = NetworkAutomationRule(
            id = "r1",
            type = NetworkRuleType.ANY_WIFI,
            action = NetworkRuleAction.SWITCH_SERVER,
            targetServerId = 10,
        )
        val homeWifiRule = NetworkAutomationRule(
            id = "r2",
            type = NetworkRuleType.SPECIFIC_WIFI,
            ssid = "MyHome_5G",
            action = NetworkRuleAction.SWITCH_IF_CONNECTED,
            targetServerId = 20,
        )
        val rules = listOf(anyWifiRule, homeWifiRule)

        val matched = NetworkAutomationEvaluator.findMatchingRule(
            enabledRules = rules,
            isWifi = true,
            isCellular = false,
            currentSsid = "MyHome_5G",
        )

        // Specific Wi-Fi should take precedence over ANY_WIFI
        assertEquals(homeWifiRule, matched)
    }

    @Test
    fun testFindMatchingRuleFallbackToAnyWifi() {
        val anyWifiRule = NetworkAutomationRule(
            id = "r1",
            type = NetworkRuleType.ANY_WIFI,
            action = NetworkRuleAction.SWITCH_SERVER,
            targetServerId = 10,
        )
        val homeWifiRule = NetworkAutomationRule(
            id = "r2",
            type = NetworkRuleType.SPECIFIC_WIFI,
            ssid = "MyHome_5G",
            action = NetworkRuleAction.SWITCH_IF_CONNECTED,
            targetServerId = 20,
        )
        val rules = listOf(anyWifiRule, homeWifiRule)

        val matched = NetworkAutomationEvaluator.findMatchingRule(
            enabledRules = rules,
            isWifi = true,
            isCellular = false,
            currentSsid = "CoffeeShop_Free",
        )

        // Since CoffeeShop does not match MyHome_5G, it falls back to ANY_WIFI
        assertEquals(anyWifiRule, matched)
    }

    @Test
    fun testFindMatchingRuleWifiTakesPrecedenceOverCellularWhenBothReported() {
        val cellularRule = NetworkAutomationRule(
            id = "r1",
            type = NetworkRuleType.CELLULAR,
            action = NetworkRuleAction.SWITCH_SERVER,
            targetServerId = 10,
        )
        val wifiRule = NetworkAutomationRule(
            id = "r2",
            type = NetworkRuleType.ANY_WIFI,
            action = NetworkRuleAction.DISCONNECT_VPN,
        )
        val rules = listOf(cellularRule, wifiRule)

        // Even if both isWifi and isCellular flags are true (e.g. background cellular radio on Wi-Fi),
        // Wi-Fi rule MUST take absolute precedence!
        val matched = NetworkAutomationEvaluator.findMatchingRule(
            enabledRules = rules,
            isWifi = true,
            isCellular = true,
            currentSsid = "MyHome_5G",
        )

        assertEquals(wifiRule, matched)
    }

    @Test
    fun testMakeDecisionForSwitchServerAlways() {
        val rule = NetworkAutomationRule(
            id = "r1",
            type = NetworkRuleType.CELLULAR,
            action = NetworkRuleAction.SWITCH_SERVER,
            targetServerId = 10,
        )

        val decision = NetworkAutomationEvaluator.makeDecision(rule, appState)

        assertIs<NetworkAutomationDecision.SwitchServer>(decision)
        assertEquals(10, decision.serverId)
        assertEquals(false, decision.requireAlreadyRunning)
    }

    @Test
    fun testMakeDecisionForSwitchIfConnected() {
        val rule = NetworkAutomationRule(
            id = "r1",
            type = NetworkRuleType.CELLULAR,
            action = NetworkRuleAction.SWITCH_IF_CONNECTED,
            targetServerId = 20,
        )

        val decision = NetworkAutomationEvaluator.makeDecision(rule, appState)

        assertIs<NetworkAutomationDecision.SwitchServer>(decision)
        assertEquals(20, decision.serverId)
        assertEquals(true, decision.requireAlreadyRunning)
    }

    @Test
    fun testMakeDecisionForDisconnectVpn() {
        val rule = NetworkAutomationRule(
            id = "r1",
            type = NetworkRuleType.ANY_WIFI,
            action = NetworkRuleAction.DISCONNECT_VPN,
        )

        val decision = NetworkAutomationEvaluator.makeDecision(rule, appState)

        assertEquals(NetworkAutomationDecision.DisconnectVpn, decision)
    }

    @Test
    fun testMakeDecisionForNullRule() {
        val decision = NetworkAutomationEvaluator.makeDecision(null, appState)
        assertEquals(NetworkAutomationDecision.NoChange, decision)
    }
}
