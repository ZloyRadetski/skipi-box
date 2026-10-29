// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import features.networkautomation.policy.NetworkAutomationAction
import features.networkautomation.policy.NetworkAutomationPolicy
import features.networkautomation.policy.ObservedNetwork
import features.networkautomation.policy.ObservedNetworkTransport
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNetworkAutomationEngineTest {
    private val wifiHome = ObservedNetwork(setOf(ObservedNetworkTransport.WIFI), "Home")
    private val wifiOffice = ObservedNetwork(setOf(ObservedNetworkTransport.WIFI), "Office")
    private val rules = listOf(
        NetworkAutomationRule(type = NetworkRuleType.SPECIFIC_WIFI, ssid = "Office", targetServerId = 2),
        NetworkAutomationRule(type = NetworkRuleType.ANY_WIFI, action = NetworkRuleAction.DISCONNECT_VPN),
    )

    private fun settings(automation: Boolean = true, onDemand: Boolean = false) =
        DesktopAppSettings(enableNetworkAutomation = automation, enableOnDemandVpn = onDemand, networkAutomationRules = rules)

    @Test
    fun firstObservationNeverActsButRemembersTheNetwork() {
        val step = evaluateDesktopNetworkAutomation(settings(), setOf(1, 2), selectedServerId = 1, vpnRunning = true, previousNetworkId = null, observed = wifiOffice)
        assertEquals(NetworkAutomationAction.None, step.action)
        assertEquals("WIFI:Office", step.networkId)
    }

    @Test
    fun movingToARuleNetworkRestartsARunningVpnOnTheRuleServer() {
        val step = evaluateDesktopNetworkAutomation(settings(), setOf(1, 2), selectedServerId = 1, vpnRunning = true, previousNetworkId = "WIFI:Home", observed = wifiOffice)
        assertEquals(NetworkAutomationAction.Restart(2), step.action)
    }

    @Test
    fun stoppedVpnIsStartedOnlyByOnDemand() {
        val auto = evaluateDesktopNetworkAutomation(settings(), setOf(1, 2), 1, vpnRunning = false, previousNetworkId = "WIFI:Home", observed = wifiOffice)
        assertEquals(NetworkAutomationAction.None, auto.action)
        val onDemand = evaluateDesktopNetworkAutomation(settings(automation = false, onDemand = true), setOf(1, 2), 1, vpnRunning = false, previousNetworkId = "WIFI:Home", observed = wifiOffice)
        assertEquals(NetworkAutomationAction.Start(2), onDemand.action)
    }

    @Test
    fun disconnectRuleStopsARunningVpnOnDemandOnly() {
        val step = evaluateDesktopNetworkAutomation(settings(automation = false, onDemand = true), setOf(1, 2), 1, vpnRunning = true, previousNetworkId = "ETHERNET", observed = wifiHome)
        assertEquals(NetworkAutomationAction.Disconnect, step.action)
    }

    @Test
    fun missingUpstreamKeepsStateAndDoesNothing() {
        val step = evaluateDesktopNetworkAutomation(settings(), setOf(1, 2), 1, vpnRunning = true, previousNetworkId = "WIFI:Home", observed = null)
        assertEquals(NetworkAutomationAction.None, step.action)
        assertEquals(NetworkAutomationPolicy.DisconnectedNetworkIdentifier, step.networkId)
    }

    @Test
    fun ruleServerThatNoLongerExistsIsIgnored() {
        val step = evaluateDesktopNetworkAutomation(settings(), setOf(1), 1, vpnRunning = true, previousNetworkId = "WIFI:Home", observed = wifiOffice)
        assertEquals(NetworkAutomationAction.None, step.action)
    }

    @Test
    fun observationIsOnlyNeededWhenFeaturesAndRulesAreEnabled() {
        assertEquals(true, desktopNetworkObservationPlan(settings()).shouldListen)
        assertEquals(false, desktopNetworkObservationPlan(DesktopAppSettings()).shouldListen)
        assertEquals(true, desktopNetworkObservationPlan(settings()).requiresWifiSsid)
    }
}
