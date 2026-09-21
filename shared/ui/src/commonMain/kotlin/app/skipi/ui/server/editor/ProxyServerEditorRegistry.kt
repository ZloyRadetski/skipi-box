// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import app.skipi.ui.resources.*
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
import org.jetbrains.compose.resources.stringResource

data class ProxyServerEditorOptions(
    val groupOptions: List<ProxyServerEditorGroupOption> = emptyList(),
    val memberOptions: List<ProxyServerEditorMemberOption> = emptyList(),
    val strategyGroupSelectedMemberCount: Int = 0,
    val onOpenStrategyGroupMembers: (() -> Unit)? = null,
)

fun ProxyServer<*>.editableCopy(): ProxyServer<*> {
    return when (this) {
        is StrategyGroup -> copy()
        is ChainProxy -> copy()
        is Custom -> copy()
        is HTTP -> copy()
        is Socks -> copy()
        is Shadowsocks -> copy(parms = parms.copy())
        is VMess -> copy(parms = parms.copy())
        is Trojan -> copy(parms = parms.copy())
        is VLESS -> copy(parms = parms.copy())
        is Wireguard -> copy()
        is AmneziaWg -> copy()
        is OlcRtc -> copy()
        is Hysteria2 -> copy()
        else -> unsupportedProxyServerEditor()
    }
}

@Composable
fun ProxyServer<*>.editorTitle(): String {
    return when (this) {
        is StrategyGroup -> stringResource(Res.string.proxy_editor_strategy_group_title)
        is ChainProxy -> stringResource(Res.string.proxy_editor_chain_proxy_title)
        is Custom -> getInfo().protocol
        else -> getInfo().protocol
    }
}

fun LazyListScope.proxyServerEditorContent(
    proxyServer: ProxyServer<*>,
    options: ProxyServerEditorOptions = ProxyServerEditorOptions(),
) {
    when (proxyServer) {
        is StrategyGroup -> strategyGroupProxyServer(
            strategyGroupEdit = proxyServer,
            groupOptions = options.groupOptions,
            selectedMemberCount = options.strategyGroupSelectedMemberCount,
            onOpenMembers = options.onOpenStrategyGroupMembers,
        )
        is ChainProxy -> chainProxyServer(proxyServer, options.memberOptions)
        is HTTP -> httpProxyServer(proxyServer)
        is Socks -> socksProxyServer(proxyServer)
        is Shadowsocks -> shadowsocksProxyServer(proxyServer)
        is VMess -> vmessProxyServer(proxyServer)
        is Trojan -> trojanProxyServer(proxyServer)
        is VLESS -> vlessProxyServer(proxyServer)
        is Wireguard -> wireguardProxyServer(proxyServer)
        is AmneziaWg -> amneziaWgProxyServer(proxyServer)
        is OlcRtc -> olcRtcProxyServer(proxyServer)
        is Hysteria2 -> hysteria2ProxyServer(proxyServer)
        else -> proxyServer.unsupportedProxyServerEditor()
    }
}

private fun ProxyServer<*>.unsupportedProxyServerEditor(): Nothing {
    error("Unsupported proxy server editor: ${this::class.simpleName}")
}
