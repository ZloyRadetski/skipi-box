// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import engine.network.NetworkDefaults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toIntCoercedInOrDefault

data class XrayTcpSockopt(
    val tcpKeepAliveInterval: Int,
    val tcpKeepAliveIdle: Int,
    val tcpUserTimeout: Int,
)

data class XrayFragmentOutboundOptions(
    val domainStrategy: String,
    val packets: String,
    val length: String,
    val interval: String,
)

/**
 * A DNS dialer can only name an outbound. When the selected proxy is a
 * balancer, route it through the shared loopback so it can re-enter routing.
 */
fun XrayOutboundPlan.dnsDialerProxyTag(): String? = when (val proxyTarget = routeTargets[XrayTags.PROXY]) {
    null -> balancers
        .firstOrNull { balancer -> balancer.tag == XrayTags.PROXY }
        ?.fallbackTag
        ?: proxyOutbounds.firstOrNull()?.tag

    else -> when (proxyTarget.kind) {
        XrayRouteTargetKind.Balancer -> XrayTags.DNS_PROXY_LOOPBACK
        XrayRouteTargetKind.Outbound -> proxyTarget.tag
    }
}

fun buildXrayBurstObservatory(
    selectors: List<String>,
    probeUrl: String? = null,
    probeInterval: String? = null,
    probeTimeout: String? = null,
): JsonObject? {
    if (selectors.isEmpty()) return null
    return buildJsonObject {
        put("subjectSelector", selectors.distinct().toJsonStringArray())
        put(
            "pingConfig",
            buildJsonObject {
                put("destination", probeUrl?.takeIf(String::isNotBlank) ?: XrayObservatoryProbeUrl)
                put("interval", probeInterval?.takeIf(String::isNotBlank) ?: XrayObservatoryProbeInterval)
                put("sampling", 1)
                put("timeout", probeTimeout?.takeIf(String::isNotBlank) ?: "2s")
            },
        )
    }
}

fun buildSimpleOutbound(tag: String, protocol: String): JsonObject = buildJsonObject {
    put("tag", tag)
    put("protocol", protocol)
}

/**
 * Handles every DNS record type from TUN and transparent clients. The
 * explicit catch-all rule prevents an implicit empty success response for
 * record types such as HTTPS, SVCB, SRV, and TXT.
 */
fun buildXrayDnsOutbound(
    fallback: XrayDnsTcpFallback,
    proxyOutboundTag: String?,
): JsonObject {
    val outbound = buildJsonObject {
        put("tag", XrayTags.DNS_OUT)
        put("protocol", XrayProtocols.DNS)
        put(
            "settings",
            buildJsonObject {
                put("rewriteNetwork", "tcp")
                put("rewriteAddress", fallback.address)
                put("rewritePort", fallback.port)
                put(
                    "rules",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("action", "hijack")
                                put("qType", "1,28")
                            },
                        )
                        add(
                            buildJsonObject {
                                put("action", "direct")
                            },
                        )
                    },
                )
            },
        )
    }
    val proxyTag = proxyOutboundTag?.trim()?.takeIf(String::isNotEmpty) ?: return outbound
    return outbound.withDialerProxyTag(proxyTag)
}

fun buildFreedomOutbound(
    tag: String,
    domainStrategy: String,
    tcpSockopt: XrayTcpSockopt? = null,
): JsonObject {
    val base = buildJsonObject {
        put("tag", tag)
        put("protocol", XrayProtocols.FREEDOM)
        put(
            "settings",
            buildJsonObject {
                put("domainStrategy", domainStrategy)
            },
        )
    }
    return tcpSockopt?.let { sockopt ->
        base.withSockopt {
            put("tcpKeepAliveInterval", sockopt.tcpKeepAliveInterval)
            put("tcpKeepAliveIdle", sockopt.tcpKeepAliveIdle)
            put("tcpUserTimeout", sockopt.tcpUserTimeout)
        }
    } ?: base
}

fun buildXrayFragmentOutbound(options: XrayFragmentOutboundOptions): JsonObject = buildJsonObject {
    put("tag", XrayTags.FRAGMENT)
    put("protocol", XrayProtocols.FREEDOM)
    put(
        "settings",
        buildJsonObject {
            put("domainStrategy", options.domainStrategy)
            put(
                "fragment",
                buildJsonObject {
                    put("packets", options.packets.ifBlank { DefaultFragmentPackets })
                    put("length", options.length.ifBlank { DefaultFragmentLength })
                    put("interval", options.interval.ifBlank { DefaultFragmentInterval })
                },
            )
        },
    )
}

fun buildXrayMuxConfig(
    concurrency: String,
    xudpConcurrency: String,
    xudpProxyUdp443Mode: Int,
): JsonObject = buildJsonObject {
    put("enabled", true)
    put(
        "concurrency",
        concurrency.toIntCoercedInOrDefault(
            -1..MaxMuxConcurrency,
            default = DefaultMuxConcurrency.toInt(),
        ),
    )
    put(
        "xudpConcurrency",
        xudpConcurrency.toIntCoercedInOrDefault(
            -1..MaxMuxXudpConcurrency,
            default = DefaultMuxXudpConcurrency.toInt(),
        ),
    )
    put("xudpProxyUDP443", MuxUdp443Values.getOrElse(xudpProxyUdp443Mode) { MuxUdp443Values.first() })
}

fun xrayDirectOutboundDomainStrategy(
    enableIpv6: Boolean,
    enableIpv6Prefer: Boolean,
): String = when {
    enableIpv6 && enableIpv6Prefer -> "UseIPv6v4"
    enableIpv6 -> "UseIP"
    else -> "UseIPv4"
}

fun xrayWireguardDomainStrategy(
    enableIpv6: Boolean,
    enableIpv6Prefer: Boolean,
): String = when {
    enableIpv6 && enableIpv6Prefer -> "ForceIPv6v4"
    enableIpv6 -> "ForceIP"
    else -> "ForceIPv4"
}

fun buildDefaultRouteOutbound(): JsonObject = buildJsonObject {
    put("tag", XrayTags.DEFAULT_ROUTE_LOOPBACK)
    put("protocol", XrayProtocols.LOOPBACK)
    put(
        "settings",
        buildJsonObject {
            put("inboundTag", XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND)
        },
    )
}

fun buildDnsProxyLoopbackOutbound(): JsonObject = buildJsonObject {
    put("tag", XrayTags.DNS_PROXY_LOOPBACK)
    put("protocol", XrayProtocols.LOOPBACK)
    put(
        "settings",
        buildJsonObject {
            put("inboundTag", XrayTags.DNS_PROXY_LOOPBACK_INBOUND)
        },
    )
}

fun JsonObject.withDialerProxyTag(tag: String): JsonObject = withSockopt {
    put("dialerProxy", tag)
}

fun JsonObject.withSockopt(block: JsonObjectBuilder.() -> Unit): JsonObject =
    updatedNestedObject("streamSettings", "sockopt", block)

private const val XrayObservatoryProbeUrl = NetworkDefaults.CONNECTIVITY_CHECK_URL
private const val XrayObservatoryProbeInterval = "1m"
