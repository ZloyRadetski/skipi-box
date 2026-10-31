// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.server.createProxyServerDraft
import data.AndroidAppStateStore
import data.repository.reconcileTrafficConfigProxyGroups
import features.proxy.server.list.ProxyServerListAddAction
import features.proxy.server.model.ProxyServer

/** Android entry point: the shared commit logic over the Room-backed store and catalog repository. */
internal suspend fun applyProxySubscriptionUpdates(
    stateStore: AndroidAppStateStore,
    updates: List<ProxyServerListSubscriptionUpdate>,
    updatedAtMillis: Long,
    updateAppState: ((AppState) -> AppState) -> Unit,
) = applyProxySubscriptionUpdates(
    stateStore = stateStore,
    proxyServers = stateStore.proxyServerRepository,
    reconcileTrafficConfigProxyGroups = { stateStore.reconcileTrafficConfigProxyGroups() },
    updates = updates,
    updatedAtMillis = updatedAtMillis,
    updateAppState = updateAppState,
)

internal fun createProxyServer(action: ProxyServerListAddAction): ProxyServer<*> {
    return createProxyServerDraft(action.toProxyHomeServerKind())
}

internal fun ProxyServerListAddAction.toProxyHomeServerKind(): ProxyHomeServerKind = when (this) {
    ProxyServerListAddAction.ScanQrCode,
    ProxyServerListAddAction.Clipboard,
    ProxyServerListAddAction.File -> error("Import action cannot create a proxy server")

    ProxyServerListAddAction.Shadowsocks -> ProxyHomeServerKind.Shadowsocks
    ProxyServerListAddAction.ChainProxy -> ProxyHomeServerKind.ChainProxy
    ProxyServerListAddAction.StrategyGroup -> ProxyHomeServerKind.StrategyGroup
    ProxyServerListAddAction.HTTP -> ProxyHomeServerKind.Http
    ProxyServerListAddAction.VMess -> ProxyHomeServerKind.Vmess
    ProxyServerListAddAction.VLESS -> ProxyHomeServerKind.Vless
    ProxyServerListAddAction.Trojan -> ProxyHomeServerKind.Trojan
    ProxyServerListAddAction.Socks -> ProxyHomeServerKind.Socks
    ProxyServerListAddAction.Hysteria2 -> ProxyHomeServerKind.Hysteria2
    ProxyServerListAddAction.Wireguard -> ProxyHomeServerKind.Wireguard
    ProxyServerListAddAction.AmneziaWg -> ProxyHomeServerKind.AmneziaWg
    ProxyServerListAddAction.Custom -> ProxyHomeServerKind.Custom
}
