// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import app.skipi.app.home.ProxyHomeServerKind
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard

/** Creates a fresh unsaved editor draft with the canonical Android manual-add defaults. */
fun createProxyServerDraft(kind: ProxyHomeServerKind): ProxyServer<*> = when (kind) {
    ProxyHomeServerKind.Http -> HTTP(port = "")
    ProxyHomeServerKind.Vmess -> VMess(port = "")
    ProxyHomeServerKind.Vless -> VLESS()
    ProxyHomeServerKind.Trojan -> Trojan(port = "")
    ProxyHomeServerKind.Shadowsocks -> Shadowsocks(port = "")
    ProxyHomeServerKind.Socks -> Socks(port = "")
    ProxyHomeServerKind.Hysteria2 -> Hysteria2(port = "")
    ProxyHomeServerKind.Wireguard -> Wireguard(port = "", reserved = "", address = "", mtu = "")
    ProxyHomeServerKind.AmneziaWg -> AmneziaWg(
        server = "",
        port = "",
        secretKey = "",
        publicKey = "",
        preSharedKey = "",
        reserved = "",
        address = "",
        mtu = "",
    )
    ProxyHomeServerKind.OlcRtc -> OlcRtc()
    ProxyHomeServerKind.StrategyGroup -> StrategyGroup()
    ProxyHomeServerKind.ChainProxy -> ChainProxy()
    ProxyHomeServerKind.Custom -> Custom()
}
