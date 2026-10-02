// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import platform.TunnelPhase
import platform.TunnelSnapshot

/** Neutral snapshot assembled by a host before Home policy is applied. */
data class ProxyHomeInputSnapshot(
    val tunnelSnapshot: TunnelSnapshot = TunnelSnapshot(),
    val selectedServerId: String? = null,
    val selectedServerTitle: String = "",
    val hasSelectedServer: Boolean = selectedServerId != null,
    val activeProfileName: String? = null,
    val tunnelBusy: Boolean = false,
    val groups: List<ProxyGroupSummary> = emptyList(),
    val servers: List<ProxyServerSummary> = emptyList(),
    val hasSubscriptions: Boolean = false,
    val isTestingLatency: Boolean = false,
    val sortMode: ProxyHomeSortMode = ProxyHomeSortMode.Default,
    val searchEnabled: Boolean = true,
    val subscriptionSwipeEnabled: Boolean = true,
    val displayOptions: ProxyHomeDisplayOptions = ProxyHomeDisplayOptions(),
    val runtimeOutboundMetric: String? = null,
    val supportedActions: Set<ProxyHomeActionId> = ProxyHomeActionId.values().toSet(),
    val availableImportSources: Set<ProxyHomeImportSource> = emptySet(),
    val availableCopyFormats: Set<ProxyHomeCopyFormat> = emptySet(),
    val availableServerKinds: Set<ProxyHomeServerKind> = emptySet(),
    val availableServerTools: Set<ProxyHomeServerTool> = emptySet(),
)

/** Applies cross-platform Home capability, selection, and status rules to a host snapshot. */
fun ProxyHomeInputSnapshot.toProxyHomeInput(): ProxyHomeInput {
    val selectedServer = servers.firstOrNull { server -> server.id == selectedServerId }
    val canToggleTunnel = ProxyHomeActionId.ToggleTunnel in supportedActions &&
        !tunnelBusy && when (tunnelSnapshot.phase) {
            TunnelPhase.Connected -> true
            TunnelPhase.Disconnected -> hasSelectedServer
            TunnelPhase.Connecting,
            TunnelPhase.Disconnecting,
            TunnelPhase.Failed -> false
        }

    val actions = supportedActions.toMutableSet()
    if (!canToggleTunnel) actions.remove(ProxyHomeActionId.ToggleTunnel)
    if (!hasSubscriptions) {
        actions.removeAll(subscriptionActions)
    }
    if (groups.none(ProxyGroupSummary::canDelete)) actions.remove(ProxyHomeActionId.DeleteGroup)
    if (groups.none(ProxyGroupSummary::canMove)) actions.remove(ProxyHomeActionId.MoveGroup)
    if (sortMode != ProxyHomeSortMode.Default) actions.remove(ProxyHomeActionId.MoveServer)

    return ProxyHomeInput(
        tunnelSnapshot = tunnelSnapshot,
        selectedServerId = selectedServerId,
        selectedServerTitle = selectedServer?.title?.takeIf(String::isNotBlank) ?: selectedServerTitle,
        activeProfileName = activeProfileName,
        canToggleTunnel = canToggleTunnel,
        tunnelBusy = tunnelBusy,
        groups = groups,
        servers = servers,
        isTestingLatency = isTestingLatency,
        statusMessage = tunnelSnapshot.failure?.message,
        isStatusError = tunnelSnapshot.phase == TunnelPhase.Failed,
        sortMode = sortMode,
        searchEnabled = searchEnabled,
        subscriptionSwipeEnabled = subscriptionSwipeEnabled,
        displayOptions = displayOptions,
        runtimeOutboundMetric = runtimeOutboundMetric,
        availableActions = actions,
        availableImportSources = availableImportSources,
        availableCopyFormats = availableCopyFormats,
        availableServerKinds = availableServerKinds,
        availableServerTools = availableServerTools,
    )
}

private val subscriptionActions = setOf(
    ProxyHomeActionId.RefreshSubscription,
    ProxyHomeActionId.RefreshAllSubscriptions,
    ProxyHomeActionId.PingSubscription,
    ProxyHomeActionId.ToggleSubscriptionEnabled,
    ProxyHomeActionId.EditSubscription,
)
