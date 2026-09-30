// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.ProxyServerLatencyTesting
import app.modes.ConnectionDisplayModeCompact
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxySubscriptionSummary
import app.activeTrafficConfig
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.UrlProxyServer
import features.proxy.server.model.getTransportDisplay
import features.subscription.DefaultSubscriptionGroupId
import platform.TunnelPhase
import platform.TunnelSnapshot

/** Maps Android's persisted/runtime state into the platform-neutral Home input. */
internal fun AppState.toProxyHomeInput(
    groupState: ProxyServerListGroups,
    tunnelSnapshot: TunnelSnapshot,
    tunnelBusy: Boolean,
    isTestingLatency: Boolean,
    refreshingSubscriptionGroupIds: Set<Int> = emptySet(),
    pingingSubscriptionGroupIds: Set<Int> = emptySet(),
    presentationFormatter: ProxyServerListItemTextFormatter? = null,
    runtimeOutboundMetric: String? = null,
): ProxyHomeInput {
    val selectedServerId = selectedProxyServerId
    // Shared Home owns presentation sorting. Keep canonical remarks in sortKey below so
    // flag-stripped display titles cannot change the platform's name-sort order.
    val visibleServers = groupState.visibleServers
    val serverSummaries = visibleServers.map { server ->
        server.toProxyHomeSummary(
            selectedServerId = selectedServerId,
            groupName = groupState.groupNames[server.groupId].orEmpty(),
            allServers = visibleServers,
            presentationFormatter = presentationFormatter,
        )
    }
    val groups = groupState.groupTabs.map { tab ->
        val members = if (tab.id == AllProxyGroupId) {
            visibleServers.filterNot { server ->
                (server.server as? StrategyGroup)?.sourceTrafficConfigId != null
            }
        } else {
            visibleServers.filter { server -> server.groupId == tab.id }
        }
        val subscriptionGroup = subscriptionGroups.firstOrNull { group -> group.id == tab.id }
        val kind = when {
            tab.id == AllProxyGroupId -> ProxyHomeGroupKind.All
            tab.id == AutoBalancerGroupId -> ProxyHomeGroupKind.AutoBalancer
            subscriptionGroup?.url?.isNotBlank() == true -> ProxyHomeGroupKind.Subscription
            subscriptionGroup != null -> ProxyHomeGroupKind.Manual
            else -> ProxyHomeGroupKind.Other
        }
        ProxyGroupSummary(
            id = tab.id.toString(),
            title = tab.name,
            serverCount = members.size,
            enabled = subscriptionGroup?.enabled ?: true,
            serverIds = members.mapTo(linkedSetOf()) { server -> server.id.toString() },
            canEdit = subscriptionGroup != null && !subscriptionGroup.builtIn,
            canDelete = subscriptionGroup != null && !subscriptionGroup.builtIn,
            canMove = subscriptionGroup != null && !subscriptionGroup.builtIn && tab.id != DefaultSubscriptionGroupId,
            subscription = subscriptionGroup
                ?.takeIf { group -> group.url.isNotBlank() }
                ?.toProxyHomeSubscription(
                    serverCount = members.size,
                    refreshing = subscriptionGroup.id in refreshingSubscriptionGroupIds,
                    pinging = subscriptionGroup.id in pingingSubscriptionGroupIds,
                ),
            kind = kind,
        )
    }
    val selectedServer = proxyServers.firstOrNull { server -> server.id == selectedServerId }
    val canToggleTunnel = !tunnelBusy && when (tunnelSnapshot.phase) {
        TunnelPhase.Connected -> true
        TunnelPhase.Disconnected -> selectedServer != null
        TunnelPhase.Connecting,
        TunnelPhase.Disconnecting,
        TunnelPhase.Failed -> false
    }
    val availableActions = buildSet {
        if (canToggleTunnel) add(ProxyHomeActionId.ToggleTunnel)
        add(ProxyHomeActionId.SelectServer)
        add(ProxyHomeActionId.TestServer)
        add(ProxyHomeActionId.TestVisibleServers)
        add(ProxyHomeActionId.TestGroup)
        add(ProxyHomeActionId.CancelLatencyTests)
        if (subscriptionGroups.any { group -> group.url.isNotBlank() }) {
            add(ProxyHomeActionId.RefreshSubscription)
            add(ProxyHomeActionId.RefreshAllSubscriptions)
            add(ProxyHomeActionId.PingSubscription)
            add(ProxyHomeActionId.ToggleSubscriptionEnabled)
            add(ProxyHomeActionId.EditSubscription)
        }
        add(ProxyHomeActionId.AddServer)
        add(ProxyHomeActionId.AddSubscription)
        add(ProxyHomeActionId.ImportServers)
        add(ProxyHomeActionId.EditServer)
        add(ProxyHomeActionId.DeleteServer)
        add(ProxyHomeActionId.ShowServerQr)
        add(ProxyHomeActionId.CopyServer)
        add(ProxyHomeActionId.EditGroup)
        if (groups.any(ProxyGroupSummary::canDelete)) add(ProxyHomeActionId.DeleteGroup)
        if (groups.any(ProxyGroupSummary::canMove)) add(ProxyHomeActionId.MoveGroup)
        if (proxyServerListSort == app.modes.ProxyServerListSortDefault) {
            add(ProxyHomeActionId.MoveServer)
        }
        add(ProxyHomeActionId.OpenStrategyMemberPicker)
        add(ProxyHomeActionId.SelectStrategyMember)
        add(ProxyHomeActionId.SetSort)
        add(ProxyHomeActionId.RunServerTool)
        add(ProxyHomeActionId.OpenExternalLink)
    }

    return ProxyHomeInput(
        tunnelSnapshot = tunnelSnapshot,
        selectedServerId = selectedServerId.toString().takeIf { selectedServerId > 0 },
        selectedServerTitle = selectedServer?.toProxyHomeSummary(
            selectedServerId = selectedServerId,
            groupName = groupState.groupNames[selectedServer.groupId].orEmpty(),
            allServers = visibleServers,
            presentationFormatter = presentationFormatter,
        )?.title.orEmpty(),
        activeProfileName = activeTrafficConfig()?.name?.takeIf(String::isNotBlank),
        canToggleTunnel = canToggleTunnel,
        tunnelBusy = tunnelBusy,
        groups = groups,
        servers = serverSummaries,
        isTestingLatency = isTestingLatency,
        statusMessage = tunnelSnapshot.failure?.message,
        isStatusError = tunnelSnapshot.phase == TunnelPhase.Failed,
        sortMode = when (proxyServerListSort) {
            ProxyServerListSortName -> ProxyHomeSortMode.Name
            ProxyServerListSortLatency -> ProxyHomeSortMode.Latency
            else -> ProxyHomeSortMode.Default
        },
        searchEnabled = showServerSearch,
        subscriptionSwipeEnabled = enableSubscriptionSwipe,
        displayOptions = ProxyHomeDisplayOptions(
            connectionMode = if (connectionDisplayMode == ConnectionDisplayModeCompact) {
                ProxyHomeConnectionMode.Compact
            } else {
                ProxyHomeConnectionMode.Classic
            },
            pinConnectionPanel = pinConnectionPanelOnHome,
            classicFloatingPowerButton = classicShowFloatingPowerButton,
            requestedColumns = proxyServerListLayout.resolvedProxyServerListColumns(),
            showAllGroup = enableAllProxyGroup,
            showTunnelMemory = showTunnelMemoryOnHome,
            latencyFastColor = customPingFastColor,
            latencyMediumColor = customPingMediumColor,
            latencySlowColor = customPingSlowColor,
            latencyErrorColor = customStatusStoppedColor,
        ),
        runtimeOutboundMetric = runtimeOutboundMetric,
        availableActions = availableActions,
        availableImportSources = setOf(
            ProxyHomeImportSource.QrCode,
            ProxyHomeImportSource.Clipboard,
            ProxyHomeImportSource.File,
        ),
        availableCopyFormats = buildSet {
            add(ProxyHomeCopyFormat.FullJson)
            if (visibleServers.any { server -> server.server is UrlProxyServer<*> }) {
                add(ProxyHomeCopyFormat.Url)
                add(ProxyHomeCopyFormat.QrCode)
            }
        },
        availableServerKinds = setOf(
            ProxyHomeServerKind.Http,
            ProxyHomeServerKind.Vmess,
            ProxyHomeServerKind.Vless,
            ProxyHomeServerKind.Trojan,
            ProxyHomeServerKind.Shadowsocks,
            ProxyHomeServerKind.Socks,
            ProxyHomeServerKind.Hysteria2,
            ProxyHomeServerKind.Wireguard,
            ProxyHomeServerKind.AmneziaWg,
            ProxyHomeServerKind.OlcRtc,
            ProxyHomeServerKind.StrategyGroup,
            ProxyHomeServerKind.ChainProxy,
            ProxyHomeServerKind.Custom,
        ),
        availableServerTools = setOf(
            ProxyHomeServerTool.RestartService,
            ProxyHomeServerTool.UpdateSubscriptions,
            ProxyHomeServerTool.DeleteDuplicateServers,
            ProxyHomeServerTool.DeleteInvalidServers,
            ProxyHomeServerTool.DeleteAllServers,
        ),
    )
}

private fun ProxyServerState.toProxyHomeSummary(
    selectedServerId: Int,
    groupName: String,
    allServers: List<ProxyServerState>,
    presentationFormatter: ProxyServerListItemTextFormatter?,
): ProxyServerSummary {
    val info = server.getInfo()
    val flag = CountryFlagUtils.extractLeadingCountryFlag(info.remarks)
        ?: if (server is StrategyGroup) "⚡" else null
    val title = CountryFlagUtils.stripLeadingCountryFlag(info.remarks)
        .ifBlank { info.protocol.ifBlank { "#$id" } }
    val latencyText = latency.trim()
    val latencyTesting = latencyText == ProxyServerLatencyTesting
    val latencyMs = if (latencyText == ProxyServerLatencyTesting) {
        null
    } else {
        HomeLatencyNumber.find(latencyText)?.value?.toLongOrNull()
    }
    val latencyError = !latencyTesting && latencyMs == null && latencyText.isNotBlank()

    return ProxyServerSummary(
        id = id.toString(),
        title = title,
        address = presentationFormatter?.displayOf(this, allServers)?.summary ?: info.address,
        protocol = info.protocol,
        selected = id == selectedServerId,
        flag = flag,
        transport = server.getTransportDisplay(),
        latencyMs = latencyMs,
        latencyError = latencyError,
        latencyErrorText = latencyText.takeIf { latencyError },
        latencyTesting = latencyTesting,
        availableCopyFormats = if (server is UrlProxyServer<*>) {
            setOf(ProxyHomeCopyFormat.Url, ProxyHomeCopyFormat.FullJson, ProxyHomeCopyFormat.QrCode)
        } else {
            setOf(ProxyHomeCopyFormat.FullJson)
        },
        isStrategyGroup = server is StrategyGroup,
        groupId = groupId.toString(),
        searchText = "$title ${info.remarks} ${info.address} ${info.protocol} $groupName",
        sortKey = info.remarks.ifBlank { title },
    )
}

private fun SubscriptionGroupState.toProxyHomeSubscription(
    serverCount: Int,
    refreshing: Boolean,
    pinging: Boolean,
): ProxySubscriptionSummary = ProxySubscriptionSummary(
    id = id.toString(),
    title = profileTitle.takeIf(String::isNotBlank) ?: name,
    serverCount = serverCount,
    enabled = enabled,
    refreshing = refreshing,
    pinging = pinging,
    canCancelPing = true,
    usedBytes = saturatingByteSum(trafficUploadBytes, trafficDownloadBytes),
    totalBytes = trafficTotalBytes.takeIf { bytes -> bytes > 0 && bytes < UnlimitedSubscriptionTrafficThreshold },
    updateIntervalHours = updateInterval.takeIf { url.isNotBlank() && it.isNotBlank() },
    expireAtSeconds = trafficExpireAtSeconds.takeIf { it > 0 },
    description = null,
    announcement = announce.takeIf(String::isNotBlank),
    announcementUrl = announceUrl.takeIf(String::isNotBlank),
    supportUrl = supportUrl.takeIf(String::isNotBlank)
        ?: supportEmail.takeIf(String::isNotBlank)?.let { email -> "mailto:$email" },
    siteUrl = profileWebPageUrl.takeIf(String::isNotBlank),
    lastUpdatedAtMillis = lastUpdatedAtMillis.takeIf { it > 0 },
)

private fun saturatingByteSum(first: Long, second: Long): Long {
    val left = first.coerceAtLeast(0L)
    val right = second.coerceAtLeast(0L)
    return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}

private const val UnlimitedSubscriptionTrafficThreshold = Long.MAX_VALUE / 2L
private val HomeLatencyNumber = Regex("\\d+")
