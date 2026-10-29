// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.policy

/** What the host must do after a network change; the host performs the VPN command itself. */
sealed interface NetworkAutomationAction {
    data object None : NetworkAutomationAction
    data object Disconnect : NetworkAutomationAction
    data class Restart(val serverId: Int) : NetworkAutomationAction
    data class Start(val serverId: Int) : NetworkAutomationAction
}

data class NetworkReconcileInput(
    val networkAutomationEnabled: Boolean,
    val onDemandVpnEnabled: Boolean,
    /** Identifier observed before this change; null before the first observation. */
    val previousNetworkId: String?,
    val currentNetworkId: String,
    val vpnRunning: Boolean,
    val decision: NetworkAutomationDecision,
    val selectedServerId: Int?,
    /** True when the target network activates a different traffic profile than the current one. */
    val activeProfileChangeNeeded: Boolean,
)

/**
 * Turns the rule decision for an observed network change into a VPN action. Auto-switch only
 * restarts a running VPN; On-Demand additionally starts a stopped one and disconnects on a rule.
 */
object NetworkAutomationReconciler {
    fun reconcile(input: NetworkReconcileInput): NetworkAutomationAction {
        if (!input.networkAutomationEnabled && !input.onDemandVpnEnabled) return NetworkAutomationAction.None
        // Without an observed physical upstream never start, restart, or switch a VPN.
        if (input.currentNetworkId == NetworkAutomationPolicy.DisconnectedNetworkIdentifier) {
            return NetworkAutomationAction.None
        }
        val isTransition = input.previousNetworkId != null && input.previousNetworkId != input.currentNetworkId
        // A VPN the user stopped on the same network is never auto-connected.
        if (!isTransition && !input.vpnRunning) return NetworkAutomationAction.None

        return when (val decision = input.decision) {
            NetworkAutomationDecision.NoChange -> NetworkAutomationAction.None
            NetworkAutomationDecision.DisconnectVpn ->
                if (input.onDemandVpnEnabled && input.vpnRunning && isTransition) {
                    NetworkAutomationAction.Disconnect
                } else {
                    NetworkAutomationAction.None
                }

            is NetworkAutomationDecision.SwitchServer -> when {
                input.vpnRunning -> {
                    val needsChange = input.selectedServerId != decision.serverId || input.activeProfileChangeNeeded
                    if (input.networkAutomationEnabled && isTransition && needsChange) {
                        NetworkAutomationAction.Restart(decision.serverId)
                    } else {
                        NetworkAutomationAction.None
                    }
                }

                input.onDemandVpnEnabled && isTransition && !decision.requireAlreadyRunning ->
                    NetworkAutomationAction.Start(decision.serverId)

                else -> NetworkAutomationAction.None
            }
        }
    }
}
