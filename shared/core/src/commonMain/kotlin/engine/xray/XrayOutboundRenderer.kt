// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.proxy.server.model.ProxyServerConstants
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Inputs that Android already derives from its persisted transport settings. */
data class XrayOutboundTransportOptions(
    val directDomainStrategy: String,
    val wireguardDomainStrategy: String,
    val tcpSockopt: XrayTcpSockopt,
)

/** Persisted mux values needed to render the shared mux helper's JSON. */
data class XrayMuxOutboundOptions(
    val concurrency: String,
    val xudpConcurrency: String,
    val xudpProxyUdp443Mode: Int,
)

/** Explicit host inputs for rendering planned proxy outbounds. */
data class XrayOutboundRenderingOptions(
    val primaryOutboundTag: String?,
    val enableLocalDns: Boolean,
    val dnsPlan: XrayDnsPlan?,
    val dnsProxyOutboundTag: String?,
    /** Null preserves hosts, such as Desktop today, with no transport overlay settings. */
    val transport: XrayOutboundTransportOptions?,
    /** Null means the host has no enabled mux configuration. */
    val mux: XrayMuxOutboundOptions?,
    /** Null means the host has no enabled fragment configuration. */
    val fragment: XrayFragmentOutboundOptions?,
)

/** Renders portable Xray outbounds from the planner's ordered outbound list. */
fun renderXrayOutbounds(
    proxyOutbounds: List<XrayProxyOutboundServer>,
    options: XrayOutboundRenderingOptions,
): JsonArray {
    val outbounds = buildJsonArray {
        if (options.primaryOutboundTag?.trim() == XrayTags.DEFAULT_ROUTE_LOOPBACK) {
            add(buildDefaultRouteOutbound())
        }
        if (options.enableLocalDns && options.dnsProxyOutboundTag == XrayTags.DNS_PROXY_LOOPBACK) {
            add(buildDnsProxyLoopbackOutbound())
        }
        proxyOutbounds.forEach { outboundServer ->
            add(buildProxyOutbound(outboundServer, options))
        }
        options.transport?.let { transport ->
            add(buildFreedomOutbound(XrayTags.DIRECT, transport.directDomainStrategy, transport.tcpSockopt))
        } ?: add(buildSimpleOutbound(XrayTags.DIRECT, XrayProtocols.FREEDOM))
        add(buildSimpleOutbound(XrayTags.BLOCK, XrayProtocols.BLACKHOLE))
        if (options.enableLocalDns) {
            val dnsOutbound = options.dnsPlan?.let { plan ->
                buildXrayDnsOutbound(
                    fallback = plan.nonIpQueryFallback,
                    proxyOutboundTag = options.dnsProxyOutboundTag,
                )
            } ?: buildSimpleOutbound(XrayTags.DNS_OUT, XrayProtocols.DNS)
            add(dnsOutbound)
        }
        options.fragment?.let { fragment ->
            add(buildXrayFragmentOutbound(fragment))
        }
    }
    val primaryIndex = outbounds.indexOfFirst { outbound ->
        (outbound as? JsonObject)?.stringValue("tag") == options.primaryOutboundTag?.trim()
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

private fun buildProxyOutbound(
    outboundServer: XrayProxyOutboundServer,
    options: XrayOutboundRenderingOptions,
): JsonObject {
    val tag = outboundServer.tag
    val source = outboundServer.customOutbound
        ?: checkNotNull(outboundServer.server) { "Proxy outbound source is missing" }.toXrayOutbound(tag).toJsonObject()
    var outbound = options.transport?.let { transport ->
        source.applyProxyOutboundTransport(transport)
    } ?: source
    outbound = outbound.updated {
        put("tag", tag)
    }
    outboundServer.dialerProxyTag?.let { dialerProxyTag ->
        outbound = outbound.withDialerProxyTag(dialerProxyTag)
    }
    options.mux?.let { mux ->
        outbound = outbound.updated {
            put(
                "mux",
                buildXrayMuxConfig(
                    concurrency = mux.concurrency,
                    xudpConcurrency = mux.xudpConcurrency,
                    xudpProxyUdp443Mode = mux.xudpProxyUdp443Mode,
                ),
            )
        }
    }
    if (options.fragment != null && outboundServer.allowFragment) {
        // Xray removed outbound.proxySettings; dialerProxy is its supported
        // replacement and keeps this generated config valid on current core.
        outbound = outbound.withDialerProxyTag(XrayTags.FRAGMENT)
    }
    return outbound
}

private fun JsonObject.applyProxyOutboundTransport(transport: XrayOutboundTransportOptions): JsonObject {
    if (stringValue("protocol") == ProxyServerConstants.PROTOCOL_WIREGUARD) {
        val settings = objectValue("settings") ?: buildJsonObject {}
        return updated {
            put(
                "settings",
                settings.updated {
                    put("domainStrategy", transport.wireguardDomainStrategy)
                    put("keepAlive", transport.tcpSockopt.tcpKeepAliveInterval)
                },
            )
        }
    }

    return withSockopt {
        put("domainStrategy", transport.directDomainStrategy)
        put("tcpKeepAliveInterval", transport.tcpSockopt.tcpKeepAliveInterval)
        put("tcpKeepAliveIdle", transport.tcpSockopt.tcpKeepAliveIdle)
        put("tcpUserTimeout", transport.tcpSockopt.tcpUserTimeout)
    }
}
