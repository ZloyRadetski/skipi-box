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
fun buildXrayOutbounds(
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
        transport = appState.xrayTransportOptions(),
        mux = appState.xrayMuxOptions(),
        fragment = appState.xrayFragmentOptions(),
    ),
)

fun AppState.xrayTransportOptions() = XrayOutboundTransportOptions(
    directDomainStrategy = xrayDirectOutboundDomainStrategy(),
    wireguardDomainStrategy = wireguardDomainStrategy(),
    tcpSockopt = xrayTcpSockopt(),
)

fun AppState.xrayMuxOptions(): XrayMuxOutboundOptions? = if (enableMux) {
    XrayMuxOutboundOptions(
        concurrency = muxConcurrency,
        xudpConcurrency = muxXudpConcurrency,
        xudpProxyUdp443Mode = muxXudpProxyUdp443,
    )
} else {
    null
}

fun AppState.xrayFragmentOptions(): XrayFragmentOutboundOptions? = if (enableFragment) {
    XrayFragmentOutboundOptions(
        domainStrategy = xrayDirectOutboundDomainStrategy(),
        packets = fragmentPackets,
        length = fragmentLength,
        interval = fragmentInterval,
    )
} else {
    null
}

/** Android adapter for the shared direct-outbound strategy selection. */
fun AppState.xrayDirectOutboundDomainStrategy(): String =
    xrayDirectOutboundDomainStrategy(
        enableIpv6 = enableIpv6,
        enableIpv6Prefer = enableIpv6Prefer,
    )

/** Kept for Android callers that need the same direct-outbound sockopt policy. */
fun buildFreedomOutbound(
    tag: String,
    domainStrategy: String,
    appState: AppState,
): JsonObject = buildFreedomOutbound(
    tag = tag,
    domainStrategy = domainStrategy,
    tcpSockopt = appState.xrayTcpSockopt(),
)

private fun AppState.xrayTcpSockopt(): XrayTcpSockopt =
    xrayTcpSockopt(tunTcpKeepAliveInterval, tunTcpUserTimeout)

/** The direct outbound's TCP keep-alive and user timeout from the stored tunnel settings. */
fun xrayTcpSockopt(tcpKeepAliveInterval: String, tcpUserTimeout: String): XrayTcpSockopt {
    val keepAlive = tcpKeepAliveInterval.toIntCoercedInOrDefault(
        VpnDefaults.TCP_KEEP_ALIVE_INTERVAL_MIN..VpnDefaults.TCP_KEEP_ALIVE_INTERVAL_MAX,
        default = VpnDefaults.TCP_KEEP_ALIVE_INTERVAL.toInt(),
    )
    val userTimeout = tcpUserTimeout.toIntCoercedInOrDefault(
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
