// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CustomXrayDnsRewriteRequest(
    val config: JsonObject,
    val inbounds: List<JsonObject>,
    val dnsPlan: XrayDnsPlan,
    val enableLocalDns: Boolean,
    val dnsHijackInboundTags: List<String>,
    val directOutbound: JsonObject,
    /** SKIPI's own rules for the raw server; without them its original user routing stays. */
    val skipiRouting: CustomSkipiRouting? = null,
)

/**
 * The routing SKIPI applies to a raw server that overrides inbound and DNS ("automatic rule override"):
 * [rules] get the tag of the server's proxy outbound and replace the rules written in its JSON.
 */
class CustomSkipiRouting(
    val domainStrategy: String?,
    val rules: (proxyOutboundTag: String) -> List<JsonObject>,
    /** The outbounds, balancers and health checks [rules] refer to besides the server's own proxy outbound. */
    val extras: (proxyOutboundTag: String) -> CustomSkipiExtras = { CustomSkipiExtras() },
)

/**
 * What a profile adds to a raw server so that its other servers and groups can be routed to: [outbounds] join the
 * server's own, [balancers] go into its routing, and [observatory] probes the members the balancers pick from.
 */
class CustomSkipiExtras(
    val outbounds: List<JsonObject> = emptyList(),
    val balancers: List<JsonObject> = emptyList(),
    val observatory: JsonObject? = null,
    /** Hosts of the servers behind [outbounds], resolved before the tunnel is up like the server's own. */
    val serverHosts: List<String> = emptyList(),
)

/**
 * Replaces the inbound, DNS, fixed outbounds, and DNS routing of a raw custom
 * profile while retaining its proxy outbounds and, unless [CustomXrayDnsRewriteRequest.skipiRouting]
 * replaces them, its original user routing.
 */
fun rewriteCustomXrayDnsConfig(request: CustomXrayDnsRewriteRequest): JsonObject {
    val outboundsRewrite = request.config.rewriteCustomDnsOutbounds(
        directOutbound = request.directOutbound,
        enableLocalDns = request.enableLocalDns,
        dnsPlan = request.dnsPlan,
    )
    val routing = request.config.rewriteCustomDnsRouting(
        CustomDnsRoutingPlan(
            dnsHijackInboundTags = request.dnsHijackInboundTags,
            enableLocalDns = request.enableLocalDns,
            routeProxyDns = request.dnsPlan.routingOptions.routeProxyDns,
            routeDirectDns = request.dnsPlan.routingOptions.routeDirectDns,
            proxyDnsOutboundTag = outboundsRewrite.proxyOutboundTag,
            skipiRouting = request.skipiRouting,
        ),
    )
    // SKIPI's rules may send traffic to "block"; a raw server does not always carry that outbound.
    val skipiRulesNeedBlock = request.skipiRouting != null && outboundsRewrite.proxyOutboundTag != null &&
        outboundsRewrite.outbounds.none { (it as? JsonObject)?.stringValue("tag") == XrayTags.BLOCK }
    val extras = request.skipiRouting?.takeIf { outboundsRewrite.proxyOutboundTag != null }
        ?.extras?.invoke(outboundsRewrite.proxyOutboundTag!!)
    val outbounds = if (skipiRulesNeedBlock || extras?.outbounds?.isNotEmpty() == true) {
        buildJsonArray {
            outboundsRewrite.outbounds.forEach(::add)
            if (skipiRulesNeedBlock) add(buildSimpleOutbound(XrayTags.BLOCK, XrayProtocols.BLACKHOLE))
            extras?.outbounds?.forEach(::add)
        }
    } else {
        outboundsRewrite.outbounds
    }

    return request.config.updatedWithoutIgnoreCase(
        setOf("fakedns", "inbounds", "dns", "outbounds", "routing") + UntrustedCustomTopLevelKeys,
    ) {
        put("inbounds", request.inbounds.toJsonObjectArray())
        put("dns", buildXrayDnsConfig(request.dnsPlan))
        putIfNotNull("fakeDns", request.dnsPlan.fakeDns)
        put("outbounds", outbounds)
        put("routing", if (extras == null || extras.balancers.isEmpty()) routing else routing.withBalancers(extras.balancers))
        extras?.observatory?.let { probe -> put("observatory", request.config.objectValue("observatory").mergedObservatory(probe)) }
    }
}

/**
 * Sections that bind sockets or open channels outside the inbounds SKIPI
 * controls. An imported profile is untrusted input, so they are dropped.
 */
private val UntrustedCustomTopLevelKeys = setOf("api", "metrics", "reverse")

/** [added] after the balancers the JSON already has, which keep their tags. */
private fun JsonObject.withBalancers(added: List<JsonObject>): JsonObject {
    val own = arrayValue("balancers")?.mapNotNull { it as? JsonObject }.orEmpty()
    val ownTags = own.mapNotNull { it.stringValue("tag") }.toSet()
    return updated {
        put("balancers", buildJsonArray {
            own.forEach(::add)
            added.filter { it.stringValue("tag") !in ownTags }.forEach(::add)
        })
    }
}

/** The JSON's own observatory keeps its settings and also probes [added]'s subjects. */
private fun JsonObject?.mergedObservatory(added: JsonObject): JsonObject {
    if (this == null) return added
    val subjects = (arrayValue("subjectSelector").orEmpty() + added.arrayValue("subjectSelector").orEmpty()).distinct()
    return updated { put("subjectSelector", JsonArray(subjects)) }
}

private data class CustomOutboundsRewrite(
    val outbounds: JsonArray,
    val proxyOutboundTag: String?,
)

private fun JsonObject.rewriteCustomDnsOutbounds(
    directOutbound: JsonObject,
    enableLocalDns: Boolean,
    dnsPlan: XrayDnsPlan,
): CustomOutboundsRewrite {
    val proxyOutbounds = (arrayValue("outbounds") ?: buildJsonArray {}).withProxyOutboundTag()
    val rewrittenOutbounds = buildJsonArray {
        proxyOutbounds.outbounds.forEach { outbound ->
            when (outbound.stringValue("tag")) {
                XrayTags.DNS_OUT -> Unit
                XrayTags.DIRECT -> Unit
                else -> add(outbound)
            }
        }
        add(directOutbound)
        if (enableLocalDns) {
            add(
                buildXrayDnsOutbound(
                    fallback = dnsPlan.nonIpQueryFallback,
                    proxyOutboundTag = proxyOutbounds.proxyOutboundTag,
                ),
            )
        }
    }
    return CustomOutboundsRewrite(
        outbounds = rewrittenOutbounds,
        proxyOutboundTag = proxyOutbounds.proxyOutboundTag,
    )
}

private data class CustomProxyOutbounds(
    val outbounds: List<JsonObject>,
    val proxyOutboundTag: String?,
)

private fun JsonArray.withProxyOutboundTag(): CustomProxyOutbounds {
    val outbounds = mapNotNull { element -> element as? JsonObject }.toMutableList()
    var firstProxyCandidateIndex: Int? = null
    outbounds.forEachIndexed { index, outbound ->
        val tag = outbound.stringValue("tag")
        if (tag == XrayTags.PROXY) {
            return CustomProxyOutbounds(outbounds, tag)
        }
        if (tag !in XrayTags.FIXED_OUTBOUND_TAGS && firstProxyCandidateIndex == null) {
            firstProxyCandidateIndex = index
        }
    }

    val candidateIndex = firstProxyCandidateIndex ?: return CustomProxyOutbounds(outbounds, null)
    val candidate = outbounds[candidateIndex]
    val candidateTag = candidate.stringValue("tag")
    if (!candidateTag.isNullOrBlank()) {
        return CustomProxyOutbounds(outbounds, candidateTag)
    }
    outbounds[candidateIndex] = candidate.updated {
        put("tag", XrayTags.PROXY)
    }
    return CustomProxyOutbounds(outbounds, XrayTags.PROXY)
}

private data class CustomDnsRoutingPlan(
    val dnsHijackInboundTags: List<String>,
    val enableLocalDns: Boolean,
    val routeProxyDns: Boolean,
    val routeDirectDns: Boolean,
    val proxyDnsOutboundTag: String?,
    val skipiRouting: CustomSkipiRouting? = null,
)

private fun JsonObject.rewriteCustomDnsRouting(plan: CustomDnsRoutingPlan): JsonObject {
    val routing = objectValue("routing") ?: buildJsonObject {}
    val skipiRules = plan.skipiRouting?.let { skipi ->
        plan.proxyDnsOutboundTag?.takeIf(String::isNotBlank)?.let { proxyTag -> skipi.rules(proxyTag) }
    }
    val existingRules = skipiRules?.let { rules -> buildJsonArray { rules.forEach(::add) } }
        ?: routing.arrayValue("rules") ?: buildJsonArray {}
    return routing.updated {
        if (skipiRules != null) plan.skipiRouting.domainStrategy?.let { strategy -> put("domainStrategy", strategy) }
        put(
            "rules",
            buildJsonArray {
                if (plan.enableLocalDns) {
                    buildXrayDnsHijackRule(plan.dnsHijackInboundTags)?.let(::add)
                }
                if (plan.routeDirectDns) {
                    add(buildCustomDnsUpstreamRoute(XrayTags.DIRECT_DNS, XrayTags.DIRECT))
                }
                if (plan.routeProxyDns && !plan.proxyDnsOutboundTag.isNullOrBlank()) {
                    add(buildCustomDnsUpstreamRoute(XrayTags.PROXY_DNS, plan.proxyDnsOutboundTag))
                }
                existingRules.forEach(::add)
            },
        )
    }
}

private fun buildCustomDnsUpstreamRoute(
    inboundTag: String,
    outboundTag: String,
): JsonObject = buildJsonObject {
    put("inboundTag", listOf(inboundTag).toJsonStringArray())
    put("outboundTag", outboundTag)
}
