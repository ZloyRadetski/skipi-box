// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveLocalDnsEnabled
import engine.vpn.VpnDefaults
import features.proxy.server.model.ProxyServerConstants
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toIntCoercedInOrDefault

internal fun buildXrayOutbounds(
    appState: AppState,
    proxyOutbounds: List<XrayProxyOutboundServer>,
    primaryOutboundTag: String? = appState.defaultRouteOutboundTag,
    dnsPlan: XrayDnsPlan? = null,
    dnsProxyOutboundTag: String? = proxyOutbounds.firstOrNull()?.tag,
): JsonArray {
    val outbounds = buildJsonArray {
        if (primaryOutboundTag?.trim() == XrayTags.DEFAULT_ROUTE_LOOPBACK) {
            add(buildDefaultRouteOutbound())
        }
        if (appState.effectiveLocalDnsEnabled && dnsProxyOutboundTag == XrayTags.DNS_PROXY_LOOPBACK) {
            add(buildDnsProxyLoopbackOutbound())
        }
        proxyOutbounds.forEach { outboundServer ->
            add(buildProxyOutbound(appState, outboundServer))
        }
        add(buildFreedomOutbound(XrayTags.DIRECT, appState.xrayDirectOutboundDomainStrategy(), appState))
        add(buildSimpleOutbound(XrayTags.BLOCK, XrayProtocols.BLACKHOLE))
        if (appState.effectiveLocalDnsEnabled) {
            val dnsOutbound = dnsPlan?.let { plan ->
                buildXrayDnsOutbound(
                    fallback = plan.nonIpQueryFallback,
                    proxyOutboundTag = dnsProxyOutboundTag,
                )
            } ?: buildSimpleOutbound(XrayTags.DNS_OUT, XrayProtocols.DNS)
            add(dnsOutbound)
        }
        if (appState.enableFragment) {
            add(buildFragmentOutbound(appState))
        }
    }
    val primaryIndex = outbounds.indexOfFirst { outbound ->
        (outbound as? JsonObject)?.stringValue("tag") == primaryOutboundTag?.trim()
    }
    if (primaryIndex <= 0) return outbounds
    return buildJsonArray {
        add(outbounds[primaryIndex])
        outbounds.forEachIndexed { index, outbound ->
            if (index != primaryIndex) {
                add(outbound)
            }
        }
    }
}

/** Android adapter for the shared direct-outbound strategy selection. */
internal fun AppState.xrayDirectOutboundDomainStrategy(): String =
    xrayDirectOutboundDomainStrategy(
        enableIpv6 = enableIpv6,
        enableIpv6Prefer = enableIpv6Prefer,
    )

private fun buildProxyOutbound(appState: AppState, outboundServer: XrayProxyOutboundServer): JsonObject {
    val tag = outboundServer.tag
    val source = outboundServer.customOutbound
        ?: checkNotNull(outboundServer.server) { "Proxy outbound source is missing" }.toXrayOutbound(tag).toJsonObject()
    var outbound = source
        .applyProxyOutboundDomainStrategy(appState)
        .updated {
            put("tag", tag)
        }
    outboundServer.dialerProxyTag?.let { dialerProxyTag ->
        outbound = outbound.withDialerProxyTag(dialerProxyTag)
    }
    if (appState.enableMux) {
        outbound = outbound.updated {
            put("mux", buildMuxConfig(appState))
        }
    }
    if (appState.enableFragment && outboundServer.allowFragment) {
        // Xray removed outbound.proxySettings; dialerProxy is its supported
        // replacement and keeps this generated config valid on current core.
        outbound = outbound.withDialerProxyTag(XrayTags.FRAGMENT)
    }
    return outbound
}

private fun JsonObject.applyProxyOutboundDomainStrategy(appState: AppState): JsonObject {
    val tcpSockopt = appState.xrayTcpSockopt()
    if (stringValue("protocol") == ProxyServerConstants.PROTOCOL_WIREGUARD) {
        val settings = objectValue("settings") ?: buildJsonObject {}
        return updated {
            put(
                "settings",
                settings.updated {
                    put("domainStrategy", appState.wireguardDomainStrategy())
                    put("keepAlive", tcpSockopt.tcpKeepAliveInterval)
                },
            )
        }
    }

    return withSockopt {
        put("domainStrategy", appState.xrayDirectOutboundDomainStrategy())
        put("tcpKeepAliveInterval", tcpSockopt.tcpKeepAliveInterval)
        put("tcpKeepAliveIdle", tcpSockopt.tcpKeepAliveIdle)
        put("tcpUserTimeout", tcpSockopt.tcpUserTimeout)
    }
}

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

private fun buildFragmentOutbound(appState: AppState): JsonObject {
    return buildXrayFragmentOutbound(
        XrayFragmentOutboundOptions(
            domainStrategy = appState.xrayDirectOutboundDomainStrategy(),
            packets = appState.fragmentPackets,
            length = appState.fragmentLength,
            interval = appState.fragmentInterval,
        ),
    )
}

private fun buildMuxConfig(appState: AppState): JsonObject {
    return buildXrayMuxConfig(
        concurrency = appState.muxConcurrency,
        xudpConcurrency = appState.muxXudpConcurrency,
        xudpProxyUdp443Mode = appState.muxXudpProxyUdp443,
    )
}

private fun AppState.wireguardDomainStrategy(): String =
    xrayWireguardDomainStrategy(
        enableIpv6 = enableIpv6,
        enableIpv6Prefer = enableIpv6Prefer,
    )
