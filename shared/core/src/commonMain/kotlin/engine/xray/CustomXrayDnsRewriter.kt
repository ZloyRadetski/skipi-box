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
    val outbounds = if (skipiRulesNeedBlock) {
        buildJsonArray {
            outboundsRewrite.outbounds.forEach(::add)
            add(buildSimpleOutbound(XrayTags.BLOCK, XrayProtocols.BLACKHOLE))
        }
    } else {
        outboundsRewrite.outbounds
    }

    return request.config.updatedWithout(setOf("fakedns", "fakeDns") + UntrustedCustomTopLevelKeys) {
        put("inbounds", request.inbounds.toJsonObjectArray())
        put("dns", buildXrayDnsConfig(request.dnsPlan))
        putIfNotNull("fakeDns", request.dnsPlan.fakeDns)
        put("outbounds", outbounds)
        put("routing", routing)
    }
}

/**
 * Sections that bind sockets or open channels outside the inbounds SKIPI
 * controls. An imported profile is untrusted input, so they are dropped.
 */
private val UntrustedCustomTopLevelKeys = setOf("api", "metrics", "reverse")

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
