// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeActionId
import java.awt.Desktop
import app.skipi.app.home.proxyHomeProtocolColors
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeServerTool

/** Capabilities backed by concrete Desktop callbacks and current subscription rows. */
internal data class DesktopProxyHomeActionCapabilities(
    val actions: Set<ProxyHomeActionId>,
    val serverTools: Set<ProxyHomeServerTool>,
)

internal fun desktopProxyHomeActionCapabilities(
    subscriptions: List<DesktopStoredSubscription>,
    onRefreshAllSubscriptions: (() -> Unit)?,
    onDeleteDuplicateServers: ((Set<Int>) -> Unit)?,
    onDeleteInvalidServers: ((Set<Int>) -> Unit)?,
    onRestartService: (() -> Unit)? = null,
    onDeleteAllServers: ((Set<Int>) -> Unit)? = null,
): DesktopProxyHomeActionCapabilities {
    val serverTools = buildSet {
        if (onRefreshAllSubscriptions != null && subscriptions.any { it.url.isNotBlank() }) {
            add(ProxyHomeServerTool.UpdateSubscriptions)
        }
        if (onDeleteDuplicateServers != null) add(ProxyHomeServerTool.DeleteDuplicateServers)
        if (onDeleteInvalidServers != null) add(ProxyHomeServerTool.DeleteInvalidServers)
        if (onRestartService != null) add(ProxyHomeServerTool.RestartService)
        if (onDeleteAllServers != null) add(ProxyHomeServerTool.DeleteAllServers)
    }
    val actions = buildSet {
        if (ProxyHomeServerTool.UpdateSubscriptions in serverTools) {
            add(ProxyHomeActionId.RefreshAllSubscriptions)
        }
        if (serverTools.isNotEmpty()) add(ProxyHomeActionId.RunServerTool)
    }
    return DesktopProxyHomeActionCapabilities(actions = actions, serverTools = serverTools)
}

/** Actions the Home surface may dispatch, from the host capabilities and the current library contents. */
internal fun desktopProxyHomeAvailableActions(
    capabilityActions: Set<ProxyHomeActionId>,
    servers: List<ProxyServerSummary>,
    hasStrategyGroup: Boolean,
    subscriptions: List<DesktopStoredSubscription>,
    groupCatalog: DesktopProxyGroupCatalog,
    canToggleTunnel: Boolean,
    hasCancelableLatencyTests: Boolean,
    pingingSubscriptionIds: Set<Int>,
    updatingSubscription: Boolean,
    sortMode: ProxyHomeSortMode,
): Set<ProxyHomeActionId> = buildSet {
    addAll(capabilityActions)
    addAll(
        setOf(
            ProxyHomeActionId.SelectServer,
            ProxyHomeActionId.AddServer,
            ProxyHomeActionId.AddSubscription,
            ProxyHomeActionId.ImportServers,
            ProxyHomeActionId.SetSort,
            ProxyHomeActionId.EditGroup,
        ),
    )
    if (canToggleTunnel) add(ProxyHomeActionId.ToggleTunnel)
    if (servers.isNotEmpty()) {
        add(ProxyHomeActionId.EditServer)
        add(ProxyHomeActionId.DeleteServer)
        add(ProxyHomeActionId.CopyServer)
    }
    if (servers.any { server -> ProxyHomeCopyFormat.QrCode in server.availableCopyFormats }) {
        add(ProxyHomeActionId.ShowServerQr)
    }
    if (hasCancelableLatencyTests) add(ProxyHomeActionId.CancelLatencyTests)
    if (hasStrategyGroup) {
        add(ProxyHomeActionId.OpenStrategyMemberPicker)
        add(ProxyHomeActionId.SelectStrategyMember)
    }
    if (servers.any(ProxyServerSummary::canTest)) {
        add(ProxyHomeActionId.TestServer)
        add(ProxyHomeActionId.TestVisibleServers)
        add(ProxyHomeActionId.TestGroup)
    }
    val pingableSubscription = subscriptions.any { subscription ->
        subscription.url.isNotBlank() &&
            groupCatalog.group(DesktopProxyGroupIds.subscription(subscription.id))
                ?.serverIds
                ?.any { serverId -> servers.firstOrNull { it.id == serverId.toString() }?.canTest == true } == true
    }
    if (pingableSubscription && pingingSubscriptionIds.isEmpty()) add(ProxyHomeActionId.PingSubscription)
    if (subscriptions.any { it.url.isNotBlank() } && !updatingSubscription) {
        add(ProxyHomeActionId.RefreshSubscription)
    }
    if (subscriptions.isNotEmpty()) {
        add(ProxyHomeActionId.ToggleSubscriptionEnabled)
        add(ProxyHomeActionId.EditSubscription)
        add(ProxyHomeActionId.DeleteGroup)
    }
    if (subscriptions.size > 1) add(ProxyHomeActionId.MoveGroup)
    if (sortMode == ProxyHomeSortMode.Default && servers.size > 1) {
        add(ProxyHomeActionId.MoveServer)
        add(ProxyHomeActionId.ReorderServer)
    }
    if (Desktop.isDesktopSupported()) add(ProxyHomeActionId.OpenExternalLink)
}

internal fun DesktopAppSettings.toProxyHomeDisplayOptions() = ProxyHomeDisplayOptions(
    connectionMode = if (compactHome) ProxyHomeConnectionMode.Compact else ProxyHomeConnectionMode.Classic,
    pinConnectionPanel = pinConnectionPanelOnHome,
    classicFloatingPowerButton = classicShowFloatingPowerButton,
    requestedColumns = proxyServerListColumns,
    showAllGroup = enableAllProxyGroup,
    showTunnelMemory = showTunnelMemory,
    showTunnelIp = showTunnelIp,
    maskTunnelIp = maskTunnelIp,
    latencyFastColor = customPingFastColor,
    latencyMediumColor = customPingMediumColor,
    latencySlowColor = customPingSlowColor,
    latencyErrorColor = customStatusStoppedColor,
    protocolColors = proxyHomeProtocolColors(
        vless = customProtocolVlessColor,
        vmess = customProtocolVmessColor,
        hysteria2 = customProtocolHysteria2Color,
        trojan = customProtocolTrojanColor,
        shadowsocks = customProtocolShadowsocksColor,
        wireguard = customProtocolWireguardColor,
        socks = customProtocolSocksColor,
        http = customProtocolHttpColor,
        strategy = customProtocolStrategyColor,
        chain = customProtocolChainColor,
        json = customProtocolJsonColor,
    ),
)
