// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.routing.model.RouteRule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import utils.toDistinctCsvValues
import utils.toTrimmedNonEmptyDistinctList

data class XrayRoutingPlan(
    val domainStrategy: String,
    val rules: JsonArray,
    val balancers: List<JsonObject>,
    val primaryOutboundTag: String?,
    val unappliedRules: List<String> = emptyList(),
)

/**
 * Inputs needed to build generated Xray routing without depending on either
 * platform's application-state type.
 */
data class XrayRoutingRequest(
    val routeDomainStrategy: Int,
    val routeRules: List<RouteRule>,
    val defaultRouteOutboundTag: String,
    val routeTargets: Map<String, XrayRouteTarget>,
    val balancers: List<JsonObject>,
    val enableLocalDns: Boolean,
    val routeProxyDns: Boolean,
    val routeDirectDns: Boolean,
    val dnsHijackInboundTags: List<String>,
)

/**
 * Checks rule values that may reference platform-owned resource files.
 * Android supplies its geo-dat validator; desktop can provide its own
 * resource policy without duplicating routing assembly.
 */
interface XrayRoutingRuleValidator {
    fun isDomainRuleValid(rule: String): Boolean
    fun isIpRuleValid(rule: String): Boolean

    /**
     * A platform may report a rule as invalid while retaining it when no
     * resource directory is available. Android uses that compatibility mode
     * for raw IP rules before Xray's data directory has been prepared.
     */
    fun filterValidDomainRules(rules: List<String>): List<String> =
        rules.filter { rule -> isDomainRuleValid(rule) }

    fun filterValidIpRules(rules: List<String>): List<String> =
        rules.filter { rule -> isIpRuleValid(rule) }
}

/** Builds a portable routing plan from [request] and its resource policy. */
fun planXrayRouting(
    request: XrayRoutingRequest,
    ruleValidator: XrayRoutingRuleValidator,
): XrayRoutingPlan {
    val domainStrategy = request.routeDomainStrategy.toXrayRoutingDomainStrategy()
    val defaultTarget = request.defaultRouteTarget()
    val (rules, unappliedRules) = request.routingRules(
        defaultTarget = defaultTarget,
        ruleValidator = ruleValidator,
    )
    return XrayRoutingPlan(
        domainStrategy = domainStrategy,
        rules = rules,
        balancers = request.balancers,
        primaryOutboundTag = when (defaultTarget?.kind) {
            XrayRouteTargetKind.Outbound -> defaultTarget.tag
            XrayRouteTargetKind.Balancer -> XrayTags.DEFAULT_ROUTE_LOOPBACK
            null -> null
        },
        unappliedRules = unappliedRules,
    )
}

fun buildXrayRouting(plan: XrayRoutingPlan): JsonObject = buildJsonObject {
    put("domainStrategy", plan.domainStrategy)
    put("rules", plan.rules)
    if (plan.balancers.isNotEmpty()) {
        put("balancers", plan.balancers.toJsonObjectArray())
    }
}

private fun XrayRoutingRequest.routingRules(
    defaultTarget: XrayRouteTarget?,
    ruleValidator: XrayRoutingRuleValidator,
): Pair<JsonArray, List<String>> {
    val unapplied = mutableListOf<String>()
    val usedRuleTags = mutableSetOf<String>()
    val rulesArray = buildJsonArray {
        defaultTarget
            ?.takeIf { target -> target.kind == XrayRouteTargetKind.Balancer }
            ?.let { target -> add(buildDefaultBalancerRoute(target)) }
        if (enableLocalDns) {
            routeTargets[XrayTags.PROXY]
                ?.takeIf { target -> target.kind == XrayRouteTargetKind.Balancer }
                ?.let { target -> add(buildDnsProxyBalancerRoute(target)) }
            buildXrayDnsHijackRule(dnsHijackInboundTags)?.let(::add)
        }
        if (routeDirectDns) {
            routeTargets[XrayTags.DIRECT]?.let { target ->
                add(buildDnsUpstreamRoute(XrayTags.DIRECT_DNS, target))
            }
        }
        if (routeProxyDns) {
            routeTargets[XrayTags.PROXY]?.let { target ->
                add(buildDnsUpstreamRoute(XrayTags.PROXY_DNS, target))
            }
        }
        routeRules
            .filter(RouteRule::enabled)
            .forEach { rule ->
                unapplied += rule.domain.filterNot { domain -> ruleValidator.isDomainRuleValid(domain) }
                unapplied += rule.ip.filterNot { ip -> ruleValidator.isIpRuleValid(ip) }
                rule.toXrayRule(routeTargets, ruleValidator, usedRuleTags)?.let(::add)
            }
        // Xray otherwise falls back to the first outbound.  That makes a
        // Shadowrocket FINAL choice look ignored whenever the selected card is
        // still the first proxy outbound.  Keep FINAL as the last rule so
        // every unmatched VPN/local-proxy connection reaches its chosen target.
        defaultTarget?.let(::buildFinalRoute)?.let(::add)
    }
    return rulesArray to unapplied.distinct()
}

private fun XrayRoutingRequest.defaultRouteTarget(): XrayRouteTarget? {
    val defaultOutboundTag = defaultRouteOutboundTag.trim().ifBlank { XrayTags.PROXY }
    val defaultTarget = routeTargets[defaultOutboundTag]?.takeIf {
        defaultOutboundTag !in ReservedDefaultRouteOutboundTags
    }
    return defaultTarget ?: routeTargets[XrayTags.PROXY]
}

private fun buildDefaultBalancerRoute(target: XrayRouteTarget): JsonObject = buildJsonObject {
    target.applyTo(this)
    put("inboundTag", listOf(XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND).toJsonStringArray())
}

private fun buildDnsProxyBalancerRoute(target: XrayRouteTarget): JsonObject = buildJsonObject {
    target.applyTo(this)
    put("inboundTag", listOf(XrayTags.DNS_PROXY_LOOPBACK_INBOUND).toJsonStringArray())
}

private fun buildFinalRoute(target: XrayRouteTarget): JsonObject = buildJsonObject {
    target.applyTo(this)
    // A generated config handles TCP and UDP application traffic. DNS
    // inbounds have earlier, dedicated rules, and user rules remain above
    // this fallback in their original Shadowrocket order.
    put("network", "tcp,udp")
}

fun buildXrayDnsHijackRule(inboundTags: List<String>): JsonObject? {
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
): JsonObject = buildJsonObject {
    target.applyTo(this)
    put("inboundTag", listOf(inboundTag).toJsonStringArray())
}

private fun RouteRule.toXrayRule(
    routeTargets: Map<String, XrayRouteTarget>,
    ruleValidator: XrayRoutingRuleValidator,
    usedRuleTags: MutableSet<String>,
): JsonObject? {
    val targetOutboundTag = outboundTag.trim().ifBlank { XrayTags.PROXY }
    val target = routeTargets[targetOutboundTag] ?: return null
    val sanitizedDomains = ruleValidator.filterValidDomainRules(domain.toTrimmedNonEmptyDistinctList())
    val sanitizedIps = ruleValidator.filterValidIpRules(ip.toTrimmedNonEmptyDistinctList())
    val sanitizedProcess = process.toTrimmedNonEmptyDistinctList()
    val sanitizedPort = port.trim()
    val sanitizedNetwork = network.trim()
    val sanitizedProtocol = protocol.toDistinctCsvValues()

    val hasConditions = sanitizedDomains.isNotEmpty() ||
        sanitizedIps.isNotEmpty() ||
        sanitizedProcess.isNotEmpty() ||
        sanitizedPort.isNotEmpty() ||
        sanitizedNetwork.isNotEmpty() ||
        sanitizedProtocol.isNotEmpty()
    if (!hasConditions) return null

    val resolvedTag = resolveUniqueRuleTag(remarks, id, usedRuleTags)
    return buildJsonObject {
        target.applyTo(this)
        putJsonStringArrayIfNotEmpty("domain", sanitizedDomains)
        putJsonStringArrayIfNotEmpty("ip", sanitizedIps)
        putJsonStringArrayIfNotEmpty("process", sanitizedProcess)
        putIfNotBlank("port", sanitizedPort)
        putIfNotBlank("network", sanitizedNetwork)
        putJsonStringArrayIfNotEmpty("protocol", sanitizedProtocol)
        putIfNotBlank("ruleTag", resolvedTag)
    }
}

fun resolveUniqueRuleTag(
    remarks: String,
    ruleId: Int,
    usedTags: MutableSet<String>?,
): String? {
    val trimmed = remarks.trim()
    if (trimmed.isEmpty()) return null
    if (usedTags == null) return trimmed
    if (usedTags.add(trimmed)) {
        return trimmed
    }
    val withId = if (ruleId > 0) "$trimmed #$ruleId" else null
    if (withId != null && usedTags.add(withId)) {
        return withId
    }
    var counter = 2
    while (true) {
        val candidate = "$trimmed #$counter"
        if (usedTags.add(candidate)) {
            return candidate
        }
        counter += 1
    }
}

fun Int.toXrayRoutingDomainStrategy(): String = when (this) {
    0 -> "AsIs"
    2 -> "IPOnDemand"
    else -> "IPIfNonMatch"
}

private val ReservedDefaultRouteOutboundTags = setOf(
    XrayTags.DNS_OUT,
    XrayTags.FRAGMENT,
    XrayTags.DEFAULT_ROUTE_LOOPBACK,
    XrayTags.DNS_PROXY_LOOPBACK,
)
