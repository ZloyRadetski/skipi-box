// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.networkautomation.policy.NetworkAutomationAction
import features.networkautomation.policy.NetworkObservationPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val NetworkAutomationPollMillis = 5_000L

/** Polls the network and applies the automation rules (disconnect, restart or start the tunnel) forever. */
internal suspend fun runDesktopNetworkAutomation(
    plan: NetworkObservationPlan,
    readSettings: () -> DesktopAppSettings,
    readServers: () -> DesktopServerLibrary,
    tunnel: DesktopTunnelSession,
    proxyServerRepository: DesktopProxyServerRepository,
    toggleTunnel: () -> Unit,
) {
    var previousNetworkId: String? = null
    while (true) {
        val observed = withContext(Dispatchers.IO) {
            observeDesktopNetwork(
                interfaces = readDesktopNetworkInterfaces(),
                ssidProvider = { if (plan.requiresWifiSsid) readDesktopWifiSsid() else null },
            )
        }
        val servers = readServers()
        val step = evaluateDesktopNetworkAutomation(
            settings = readSettings(),
            availableServerIds = servers.servers.mapTo(mutableSetOf()) { it.id },
            selectedServerId = servers.selectedServerId,
            vpnRunning = tunnel.coreState.isRunning,
            previousNetworkId = previousNetworkId,
            observed = observed,
        )
        previousNetworkId = step.networkId
        when (val action = step.action) {
            NetworkAutomationAction.None -> Unit
            NetworkAutomationAction.Disconnect -> if (tunnel.desiredRunning && !tunnel.operationInProgress) {
                toggleTunnel()
            }
            is NetworkAutomationAction.Restart -> {
                runCatching { proxyServerRepository.selectAndCommit(action.serverId) }
                tunnel.requestReconnect("Автоматизация сети: смена сервера.")
            }
            is NetworkAutomationAction.Start -> {
                runCatching { proxyServerRepository.selectAndCommit(action.serverId) }
                if (!tunnel.desiredRunning && !tunnel.operationInProgress) toggleTunnel()
            }
        }
        delay(NetworkAutomationPollMillis)
    }
}
