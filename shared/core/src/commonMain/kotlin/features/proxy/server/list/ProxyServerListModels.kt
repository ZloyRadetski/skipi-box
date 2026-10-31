// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

enum class ProxyServerListAddAction {
    ScanQrCode,
    Clipboard,
    File,
    StrategyGroup,
    ChainProxy,
    Custom,
    HTTP,
    VMess,
    VLESS,
    Trojan,
    Shadowsocks,
    Socks,
    Hysteria2,
    Wireguard,
    AmneziaWg,
}

enum class ProxyServerListToolAction {
    RestartService,
    TestLatency,
    TestRealConnection,
    UpdateSubscriptions,
    SetSortDefault,
    SetSortName,
    SetSortLatency,
    DeleteDuplicateServers,
    DeleteInvalidServers,
    DeleteAllServers,
}

enum class ProxyServerListCopyAction {
    QrCode,
    Url,
    FullJson,
}

data class ProxyServerListGroupTabUi(
    val id: Int,
    val name: String,
    val serverCount: Int,
)
