// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveLocalDnsEnabled
import engine.vpn.VpnDefaults
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import utils.toIntCoercedInOrDefault

/** Android adapter preserving the existing state-derived outbound inputs. */
internal fun buildXrayOutbounds(
    appState: AppState,
    proxyOutbounds: List<XrayProxyOutboundServer>,
    primaryOutboundTag: String? = appState.defaultRouteOutboundTag,
    dnsPlan: XrayDnsPlan? = null,
    dnsProxyOutboundTag: String? = proxyOutbounds.firstOrNull()?.tag,
): JsonArray = renderXrayOutbounds(
    proxyOutbounds = proxyOutbounds,
    options = XrayOutboundRenderingOptions(
        primaryOutboundTag = primaryOutboundTag,
        enableLocalDns = appState.effectiveLocalDnsEnabled,
        dnsPlan = dnsPlan,
        dnsProxyOutboundTag = dnsProxyOutboundTag,
        transport = XrayOutboundTransportOptions(
            directDomainStrategy = appState.xrayDirectOutboundDomainStrategy(),
            wireguardDomainStrategy = appState.wireguardDomainStrategy(),
            tcpSockopt = appState.xrayTcpSockopt(),
        ),
        mux = if (appState.enableMux) {
            XrayMuxOutboundOptions(
                concurrency = appState.muxConcurrency,
                xudpConcurrency = appState.muxXudpConcurrency,
                xudpProxyUdp443Mode = appState.muxXudpProxyUdp443,
            )
        } else {
            null
        },
        fragment = if (appState.enableFragment) {
            XrayFragmentOutboundOptions(
                domainStrategy = appState.xrayDirectOutboundDomainStrategy(),
                packets = appState.fragmentPackets,
                length = appState.fragmentLength,
                interval = appState.fragmentInterval,
            )
        } else {
            null
        },
    ),
)

/** Android adapter for the shared direct-outbound strategy selection. */
internal fun AppState.xrayDirectOutboundDomainStrategy(): String =
    xrayDirectOutboundDomainStrategy(
        enableIpv6 = enableIpv6,
        enableIpv6Prefer = enableIpv6Prefer,
    )

/** Kept for Android callers that need the same direct-outbound sockopt policy. */
internal fun buildFreedomOutbound(
    tag: String,
    domainStrategy: String,
    appState: AppState,
): JsonObject = buildFreedomOutbound(
    tag = tag,
    domainStrategy = domainStrategy,
    tcpSockopt = appState.xrayTcpSockopt(),
)

private fun AppState.xrayTcpSockopt(): XrayTcpSockopt {
    val keepAlive = tunTcpKeepAliveInterval.toIntCoercedInOrDefault(
        VpnDefaults.TCP_KEEP_ALIVE_INTERVAL_MIN..VpnDefaults.TCP_KEEP_ALIVE_INTERVAL_MAX,
        default = VpnDefaults.TCP_KEEP_ALIVE_INTERVAL.toInt(),
    )
    val userTimeout = tunTcpUserTimeout.toIntCoercedInOrDefault(
        VpnDefaults.TCP_USER_TIMEOUT_MIN..VpnDefaults.TCP_USER_TIMEOUT_MAX,
        default = VpnDefaults.TCP_USER_TIMEOUT.toInt(),
    )
    return XrayTcpSockopt(
        tcpKeepAliveInterval = keepAlive,
        tcpKeepAliveIdle = keepAlive,
        tcpUserTimeout = userTimeout,
    )
}

private fun AppState.wireguardDomainStrategy(): String =
    xrayWireguardDomainStrategy(
        enableIpv6 = enableIpv6,
        enableIpv6Prefer = enableIpv6Prefer,
    )
