// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.policy

import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkAutomationCoordinationTest {
    private fun rule(
        id: String,
        type: NetworkRuleType,
        ssid: String? = null,
        action: NetworkRuleAction = NetworkRuleAction.SWITCH_SERVER,
        target: Int? = 1,
        enabled: Boolean = true,
    ) = NetworkAutomationRule(id = id, type = type, ssid = ssid, action = action, targetServerId = target, enabled = enabled)

    // ---- rule list operations ----

    @Test
    fun upsertReplacesInPlaceKeepingPriorityOrderAndAppendsNewRules() {
        val a = rule("a", NetworkRuleType.CELLULAR)
        val b = rule("b", NetworkRuleType.ANY_WIFI)

        val replaced = NetworkAutomationRules.upsert(listOf(a, b), b.copy(targetServerId = 7))
        val appended = NetworkAutomationRules.upsert(listOf(a, b), rule("c", NetworkRuleType.SPECIFIC_WIFI, "Home"))

        assertEquals(listOf("a", "b"), replaced.map { it.id })
        assertEquals(7, replaced[1].targetServerId)
        assertEquals(listOf("a", "b", "c"), appended.map { it.id })
    }

    @Test
    fun removeAndToggleOnlyTouchTheNamedRule() {
        val rules = listOf(rule("a", NetworkRuleType.CELLULAR), rule("b", NetworkRuleType.ANY_WIFI))

        assertEquals(listOf("b"), NetworkAutomationRules.remove(rules, "a").map { it.id })
        val toggled = NetworkAutomationRules.setEnabled(rules, "b", enabled = false)
        assertTrue(toggled[0].enabled)
        assertFalse(toggled[1].enabled)
        assertEquals(rules, NetworkAutomationRules.remove(rules, "missing"))
    }

    // ---- editor validation ----

    @Test
    fun specificWifiNeedsAUniqueSsidIgnoringCaseAndSpaces() {
        val existing = listOf(rule("a", NetworkRuleType.SPECIFIC_WIFI, "Home_5G"))

        val duplicate = NetworkAutomationRules.validateDraft(
            type = NetworkRuleType.SPECIFIC_WIFI, ssid = "  home_5g ", action = NetworkRuleAction.DISCONNECT_VPN,
            editingRuleId = null, existingRules = existing, hasServers = false,
        )
        val editingItself = NetworkAutomationRules.validateDraft(
            type = NetworkRuleType.SPECIFIC_WIFI, ssid = "HOME_5G", action = NetworkRuleAction.DISCONNECT_VPN,
            editingRuleId = "a", existingRules = existing, hasServers = false,
        )
        val blank = NetworkAutomationRules.validateDraft(
            type = NetworkRuleType.SPECIFIC_WIFI, ssid = "  ", action = NetworkRuleAction.DISCONNECT_VPN,
            editingRuleId = null, existingRules = emptyList(), hasServers = false,
        )

        assertTrue(duplicate.ssidDuplicate)
        assertFalse(duplicate.canSave)
        assertTrue(editingItself.canSave)
        assertFalse(blank.canSave)
    }

    @Test
    fun cellularAndAnyWifiAllowOneRuleEach() {
        val existing = listOf(rule("a", NetworkRuleType.CELLULAR), rule("b", NetworkRuleType.ANY_WIFI))

        val dup = NetworkAutomationRules.validateDraft(
            NetworkRuleType.CELLULAR, null, NetworkRuleAction.DISCONNECT_VPN, null, existing, hasServers = true,
        )
        val own = NetworkAutomationRules.validateDraft(
            NetworkRuleType.CELLULAR, null, NetworkRuleAction.DISCONNECT_VPN, "a", existing, hasServers = true,
        )

        assertTrue(dup.typeDuplicate)
        assertFalse(dup.canSave)
        assertTrue(own.canSave)
    }

    @Test
    fun switchActionsNeedAtLeastOneServerButDisconnectDoesNot() {
        fun validate(action: NetworkRuleAction, hasServers: Boolean) = NetworkAutomationRules.validateDraft(
            NetworkRuleType.ANY_WIFI, null, action, null, emptyList(), hasServers,
        ).canSave

        assertFalse(validate(NetworkRuleAction.SWITCH_SERVER, hasServers = false))
        assertFalse(validate(NetworkRuleAction.SWITCH_IF_CONNECTED, hasServers = false))
        assertTrue(validate(NetworkRuleAction.DISCONNECT_VPN, hasServers = false))
        assertTrue(validate(NetworkRuleAction.SWITCH_SERVER, hasServers = true))
    }

    @Test
    fun savedRuleKeepsIdentityTrimsSsidAndDropsTheTargetWhenNotNeeded() {
        val editing = rule("a", NetworkRuleType.CELLULAR, enabled = false, target = 5)

        val toWifi = NetworkAutomationRules.buildRule(
            editing, NetworkRuleType.SPECIFIC_WIFI, "  Cafe ", NetworkRuleAction.SWITCH_SERVER, targetServerId = 9,
        )
        val disconnect = NetworkAutomationRules.buildRule(
            editing, NetworkRuleType.CELLULAR, "ignored", NetworkRuleAction.DISCONNECT_VPN, targetServerId = 9,
        )
        val fresh = NetworkAutomationRules.buildRule(null, NetworkRuleType.ANY_WIFI, null, NetworkRuleAction.SWITCH_SERVER, 3)

        assertEquals("a", toWifi.id)
        assertFalse(toWifi.enabled)
        assertEquals("Cafe", toWifi.ssid)
        assertEquals(9, toWifi.targetServerId)
        assertNull(disconnect.ssid)
        assertNull(disconnect.targetServerId)
        assertTrue(fresh.enabled)
        assertTrue(fresh.id.isNotBlank())
    }

    // ---- observation plan ----

    @Test
    fun listeningNeedsAnEnabledSwitchAndAnEnabledRule() {
        val wifi = rule("w", NetworkRuleType.SPECIFIC_WIFI, "Home")
        val off = rule("o", NetworkRuleType.CELLULAR, enabled = false)

        assertFalse(NetworkAutomationPolicy.observationPlan(false, false, listOf(wifi)).shouldListen)
        assertFalse(NetworkAutomationPolicy.observationPlan(true, false, listOf(off)).shouldListen)
        assertFalse(NetworkAutomationPolicy.observationPlan(true, true, emptyList()).shouldListen)
        val plan = NetworkAutomationPolicy.observationPlan(false, true, listOf(wifi))
        assertTrue(plan.shouldListen)
        assertTrue(plan.requiresWifiSsid)
        assertFalse(NetworkAutomationPolicy.observationPlan(true, false, listOf(off, rule("c", NetworkRuleType.CELLULAR))).requiresWifiSsid)
    }

    @Test
    fun networkIdentifierNamesTheTransitionTarget() {
        fun id(vararg t: ObservedNetworkTransport, ssid: String? = null) =
            NetworkAutomationPolicy.networkIdentifier(ObservedNetwork(t.toSet(), ssid))

        assertEquals(NetworkAutomationPolicy.DisconnectedNetworkIdentifier, NetworkAutomationPolicy.networkIdentifier(null))
        assertEquals("WIFI:Home", id(ObservedNetworkTransport.WIFI, ObservedNetworkTransport.CELLULAR, ssid = "Home"))
        assertEquals("WIFI:ANY", id(ObservedNetworkTransport.WIFI, ssid = " "))
        assertEquals("ETHERNET", id(ObservedNetworkTransport.ETHERNET, ObservedNetworkTransport.CELLULAR))
        assertEquals("CELLULAR", id(ObservedNetworkTransport.CELLULAR))
        assertEquals("UNKNOWN", id(ObservedNetworkTransport.OTHER))
    }

    // ---- reconcile ----

    private fun reconcile(
        decision: NetworkAutomationDecision,
        automation: Boolean = true,
        onDemand: Boolean = false,
        previous: String? = "CELLULAR",
        current: String = "WIFI:ANY",
        running: Boolean = true,
        selected: Int? = 1,
        configChange: Boolean = false,
    ) = NetworkAutomationReconciler.reconcile(
        NetworkReconcileInput(
            networkAutomationEnabled = automation, onDemandVpnEnabled = onDemand,
            previousNetworkId = previous, currentNetworkId = current, vpnRunning = running,
            decision = decision, selectedServerId = selected, activeProfileChangeNeeded = configChange,
        ),
    )

    private val switchTo2 = NetworkAutomationDecision.SwitchServer(2)

    @Test
    fun nothingHappensWhileOfflineWithoutATransitionOrWithBothFeaturesOff() {
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, current = NetworkAutomationPolicy.DisconnectedNetworkIdentifier))
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, previous = "WIFI:ANY"))
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, previous = null, running = false, onDemand = true))
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, automation = false, onDemand = false))
        // A stopped VPN that merely sees the same network again is never auto-started.
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, previous = "WIFI:ANY", running = false, onDemand = true))
    }

    @Test
    fun runningVpnRestartsOnANewServerOrProfileOnlyForAutoSwitch() {
        assertEquals(NetworkAutomationAction.Restart(2), reconcile(switchTo2))
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, selected = 2))
        assertEquals(NetworkAutomationAction.Restart(2), reconcile(switchTo2, selected = 2, configChange = true))
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, automation = false, onDemand = true))
    }

    @Test
    fun stoppedVpnStartsOnlyForOnDemandAndNotWhenTheRuleNeedsARunningVpn() {
        assertEquals(NetworkAutomationAction.Start(2), reconcile(switchTo2, running = false, onDemand = true))
        assertEquals(
            NetworkAutomationAction.None,
            reconcile(NetworkAutomationDecision.SwitchServer(2, requireAlreadyRunning = true), running = false, onDemand = true),
        )
        assertEquals(NetworkAutomationAction.None, reconcile(switchTo2, running = false, onDemand = false))
    }

    @Test
    fun disconnectNeedsOnDemandARunningVpnAndATransition() {
        assertEquals(NetworkAutomationAction.Disconnect, reconcile(NetworkAutomationDecision.DisconnectVpn, onDemand = true))
        assertEquals(NetworkAutomationAction.None, reconcile(NetworkAutomationDecision.DisconnectVpn, onDemand = false))
        assertEquals(NetworkAutomationAction.None, reconcile(NetworkAutomationDecision.DisconnectVpn, onDemand = true, running = false, previous = "X"))
        assertEquals(NetworkAutomationAction.None, reconcile(NetworkAutomationDecision.NoChange, onDemand = true))
    }
}
