// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.policy

import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A rule for a named Wi-Fi network must beat the rules for every Wi-Fi and for the cellular network,
 * wherever it stands in the list: "any Wi-Fi: connect server X" plus "Wi-Fi Home: disconnect" must
 * disconnect the tunnel on Home.
 */
class NetworkAutomationSsidPriorityTest {
    private val anyWifiConnect = NetworkAutomationRule(
        id = "any", type = NetworkRuleType.ANY_WIFI, action = NetworkRuleAction.SWITCH_SERVER, targetServerId = 5,
    )
    private val homeDisconnect = NetworkAutomationRule(
        id = "home", type = NetworkRuleType.SPECIFIC_WIFI, ssid = "Home", action = NetworkRuleAction.DISCONNECT_VPN,
    )
    private val cellularConnect = NetworkAutomationRule(
        id = "cell", type = NetworkRuleType.CELLULAR, action = NetworkRuleAction.SWITCH_SERVER, targetServerId = 6,
    )

    private fun wifi(ssid: String?) = ObservedNetwork(setOf(ObservedNetworkTransport.WIFI), ssid)
    private val cellular = ObservedNetwork(setOf(ObservedNetworkTransport.CELLULAR))

    private fun step(
        rules: List<NetworkAutomationRule>,
        from: ObservedNetwork?,
        to: ObservedNetwork,
        vpnRunning: Boolean = true,
        onDemand: Boolean = true,
        autoSwitch: Boolean = true,
        selected: Int? = 5,
    ): NetworkAutomationAction {
        val decision = NetworkAutomationPolicy.evaluate(
            NetworkAutomationPolicyInput(autoSwitch, onDemand, rules, to, setOf(5, 6)),
        )
        return NetworkAutomationReconciler.reconcile(
            NetworkReconcileInput(
                networkAutomationEnabled = autoSwitch,
                onDemandVpnEnabled = onDemand,
                previousNetworkId = from?.let(NetworkAutomationPolicy::networkIdentifier),
                currentNetworkId = NetworkAutomationPolicy.networkIdentifier(to),
                vpnRunning = vpnRunning,
                decision = decision,
                selectedServerId = selected,
                activeProfileChangeNeeded = false,
            ),
        )
    }

    @Test
    fun theNamedNetworkDisconnectsWhateverTheRuleOrder() {
        listOf(listOf(anyWifiConnect, homeDisconnect), listOf(homeDisconnect, anyWifiConnect)).forEach { rules ->
            assertEquals(
                NetworkAutomationAction.Disconnect,
                step(rules, from = cellular, to = wifi("Home")),
                "order ${rules.map { it.id }}",
            )
        }
    }

    @Test
    fun anotherNetworkStillGetsTheAnyWifiRule() {
        val rules = listOf(homeDisconnect, anyWifiConnect)
        assertEquals(
            NetworkAutomationAction.None,
            step(rules, from = cellular, to = wifi("Cafe"), selected = 5),
        )
        assertEquals(
            NetworkAutomationAction.Restart(5),
            step(rules, from = cellular, to = wifi("Cafe"), selected = 9),
        )
    }

    @Test
    fun theSsidMatchIgnoresCaseAndSurroundingSpaces() {
        assertEquals(
            NetworkAutomationAction.Disconnect,
            step(listOf(anyWifiConnect, homeDisconnect), from = cellular, to = wifi("  hOmE ")),
        )
    }

    @Test
    fun theNamedNetworkBeatsTheCellularRuleToo() {
        val rules = listOf(cellularConnect, homeDisconnect, anyWifiConnect)
        assertEquals(NetworkAutomationAction.Disconnect, step(rules, from = cellular, to = wifi("Home")))
        assertEquals(NetworkAutomationAction.Restart(6), step(rules, from = wifi("Cafe"), to = cellular, selected = 5))
    }

    @Test
    fun aStoppedTunnelStaysStoppedOnTheDisconnectNetwork() {
        assertEquals(
            NetworkAutomationAction.None,
            step(listOf(anyWifiConnect, homeDisconnect), from = cellular, to = wifi("Home"), vpnRunning = false),
        )
    }

    @Test
    fun movingFromAnotherWifiToTheNamedOneDisconnects() {
        assertEquals(
            NetworkAutomationAction.Disconnect,
            step(listOf(anyWifiConnect, homeDisconnect), from = wifi("Cafe"), to = wifi("Home")),
        )
    }
}
