// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_reason_network_automation
import features.networkautomation.policy.NetworkAutomationAction
import features.networkautomation.policy.NetworkObservationPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import utils.runCatchingCancellable

private const val NetworkAutomationPollMillis = 5_000L
private const val WifiSsidMaxAgeMillis = 30_000L

/**
 * `netsh`/`nmcli` start a process per call. The SSID is kept while the adapter list stays the same and is
 * younger than [maxAgeMillis]; a changed adapter list (a new connection) reads it again at once.
 */
internal class DesktopWifiSsidCache(
    private val maxAgeMillis: Long = WifiSsidMaxAgeMillis,
    private val now: () -> Long = System::currentTimeMillis,
    private val read: () -> String?,
) {
    private var key: List<DesktopNetworkInterface>? = null
    private var readAt = 0L
    private var ssid: String? = null

    fun get(interfaces: List<DesktopNetworkInterface>): String? {
        val current = now()
        if (key == interfaces && current - readAt in 0..maxAgeMillis) return ssid
        ssid = read()
        key = interfaces
        readAt = current
        return ssid
    }
}

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
    val ssidCache = DesktopWifiSsidCache(read = ::readDesktopWifiSsid)
    while (true) {
        val observed = withContext(Dispatchers.IO) {
            val interfaces = readDesktopNetworkInterfaces()
            observeDesktopNetwork(
                interfaces = interfaces,
                ssidProvider = { if (plan.requiresWifiSsid) ssidCache.get(interfaces) else null },
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
                runCatchingCancellable { proxyServerRepository.selectAndCommit(action.serverId) }
                tunnel.requestReconnect(desktopText(Res.string.desktop_reason_network_automation))
            }
            is NetworkAutomationAction.Start -> {
                runCatchingCancellable { proxyServerRepository.selectAndCommit(action.serverId) }
                if (!tunnel.desiredRunning && !tunnel.operationInProgress) toggleTunnel()
            }
        }
        delay(NetworkAutomationPollMillis)
    }
}
