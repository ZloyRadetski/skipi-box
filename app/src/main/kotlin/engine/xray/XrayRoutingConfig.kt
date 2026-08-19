// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveLocalDnsEnabled
import features.routing.model.RouteRule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toDistinctCsvValues
import utils.toTrimmedNonEmptyDistinctList

internal data class XrayRoutingPlan(
    val domainStrategy: String,
    val rules: JsonArray,
    val balancers: List<JsonObject>,
    val primaryOutboundTag: String?,
)

internal fun AppState.buildXrayRoutingPlan(
    routeTargets: Map<String, XrayRouteTarget>,
    balancers: List<JsonObject>,
    routeProxyDns: Boolean,
    routeDirectDns: Boolean,
    dnsHijackInboundTags: List<String>,
    dataDir: String? = null,
): XrayRoutingPlan {
    val domainStrategy = routeDomainStrategy.toXrayRoutingDomainStrategy()
    val defaultTarget = defaultRouteTarget(routeTargets)
    return XrayRoutingPlan(
        domainStrategy = domainStrategy,
        rules = routingRules(
            routeTargets = routeTargets,
            routeProxyDns = routeProxyDns,
            routeDirectDns = routeDirectDns,
            dnsHijackInboundTags = dnsHijackInboundTags,
            defaultTarget = defaultTarget,
            dataDir = dataDir,
        ),
        balancers = balancers,
        primaryOutboundTag = when (defaultTarget?.kind) {
            XrayRouteTargetKind.Outbound -> defaultTarget.tag
            XrayRouteTargetKind.Balancer -> XrayTags.DEFAULT_ROUTE_LOOPBACK
            null -> null
        },
    )
}

internal fun buildXrayRouting(plan: XrayRoutingPlan): JsonObject {
    return buildJsonObject {
        put("domainStrategy", plan.domainStrategy)
        put("rules", plan.rules)
        if (plan.balancers.isNotEmpty()) {
            put("balancers", plan.balancers.toJsonObjectArray())
        }
    }
}

private fun AppState.routingRules(
    routeTargets: Map<String, XrayRouteTarget>,
    routeProxyDns: Boolean,
    routeDirectDns: Boolean,
    dnsHijackInboundTags: List<String>,
    defaultTarget: XrayRouteTarget?,
    dataDir: String? = null,
): JsonArray {
    return buildJsonArray {
        defaultTarget
            ?.takeIf { target -> target.kind == XrayRouteTargetKind.Balancer }
            ?.let { target -> add(buildDefaultBalancerRoute(target)) }
        if (effectiveLocalDnsEnabled) {
            buildXrayDnsHijackRule(dnsHijackInboundTags)?.let(::add)
        }
        if (routeDirectDns) {
            routeTargets[XrayTags.DIRECT]?.let { target -> add(buildDnsUpstreamRoute(XrayTags.DIRECT_DNS, target)) }
        }
        if (routeProxyDns) {
            routeTargets[XrayTags.PROXY]?.let { target -> add(buildDnsUpstreamRoute(XrayTags.PROXY_DNS, target)) }
        }
        routeRules
            .filter(RouteRule::enabled)
            .mapNotNull { rule -> rule.toXrayRule(routeTargets, dataDir) }
            .forEach(::add)
        // Xray otherwise falls back to the first outbound.  That makes a
        // Shadowrocket FINAL choice look ignored whenever the selected card is
        // still the first proxy outbound.  Keep FINAL as the last rule so
        // every unmatched VPN/local-proxy connection reaches its chosen target.
        defaultTarget?.let(::buildFinalRoute)?.let(::add)
    }
}

private fun buildDefaultBalancerRoute(target: XrayRouteTarget): JsonObject {
    return buildJsonObject {
        target.applyTo(this)
        put("inboundTag", listOf(XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND).toJsonStringArray())
    }
}

private fun buildFinalRoute(target: XrayRouteTarget): JsonObject {
    return buildJsonObject {
        target.applyTo(this)
        // A generated config handles TCP and UDP application traffic.  DNS
        // inbounds have earlier, dedicated rules, and user rules remain above
        // this fallback in their original Shadowrocket order.
        put("network", "tcp,udp")
    }
}

private fun AppState.defaultRouteTarget(routeTargets: Map<String, XrayRouteTarget>): XrayRouteTarget? {
    val defaultOutboundTag = defaultRouteOutboundTag.trim().ifBlank { XrayTags.PROXY }
    val defaultTarget = routeTargets[defaultOutboundTag]?.takeIf {
        defaultOutboundTag !in ReservedDefaultRouteOutboundTags
    }
    return defaultTarget ?: routeTargets[XrayTags.PROXY]
}

internal fun buildXrayDnsHijackRule(inboundTags: List<String>): JsonObject? {
    val tags = inboundTags.toTrimmedNonEmptyDistinctList()
    if (tags.isEmpty()) return null
    return buildJsonObject {
        put("inboundTag", tags.toJsonStringArray())
        put("network", "tcp,udp")
        put("port", "53")
        put("outboundTag", XrayTags.DNS_OUT)
    }
}

private fun buildDnsUpstreamRoute(
    inboundTag: String,
    target: XrayRouteTarget,
): JsonObject {
    return buildJsonObject {
        target.applyTo(this)
        put("inboundTag", listOf(inboundTag).toJsonStringArray())
    }
}

private fun RouteRule.toXrayRule(
    routeTargets: Map<String, XrayRouteTarget>,
    dataDir: String? = null,
): JsonObject? {
    val targetOutboundTag = outboundTag.trim().ifBlank { XrayTags.PROXY }
    val target = routeTargets[targetOutboundTag] ?: return null
    val sanitizedDomains = XrayGeoRuleSanitizer.filterValidDomainRules(domain.toTrimmedNonEmptyDistinctList(), dataDir)
    val sanitizedIps = XrayGeoRuleSanitizer.filterValidIpRules(ip.toTrimmedNonEmptyDistinctList(), dataDir)
    val rule = buildJsonObject {
        target.applyTo(this)
        putJsonStringArrayIfNotEmpty("domain", sanitizedDomains)
        putJsonStringArrayIfNotEmpty("ip", sanitizedIps)
        putJsonStringArrayIfNotEmpty("process", process.toTrimmedNonEmptyDistinctList())
        putIfNotBlank("port", port)
        putIfNotBlank("network", network)
        putJsonStringArrayIfNotEmpty("protocol", protocol.toDistinctCsvValues())
        putIfNotBlank("ruleTag", remarks)
    }
    return if (rule.size > 1) rule else null
}

private fun Int.toXrayRoutingDomainStrategy(): String {
    return when (this) {
        0 -> "AsIs"
        2 -> "IPOnDemand"
        else -> "IPIfNonMatch"
    }
}

private val ReservedDefaultRouteOutboundTags = setOf(
    XrayTags.DNS_OUT,
    XrayTags.FRAGMENT,
    XrayTags.DEFAULT_ROUTE_LOOPBACK,
)
