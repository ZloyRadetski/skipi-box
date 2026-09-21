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
)

/**
 * Replaces the inbound, DNS, fixed outbounds, and DNS routing of a raw custom
 * profile while retaining its proxy outbounds and original user routing.
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
        ),
    )

    return request.config.updatedWithout(setOf("fakedns", "fakeDns")) {
        put("inbounds", request.inbounds.toJsonObjectArray())
        put("dns", buildXrayDnsConfig(request.dnsPlan))
        putIfNotNull("fakeDns", request.dnsPlan.fakeDns)
        put("outbounds", outboundsRewrite.outbounds)
        put("routing", routing)
    }
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
)

private fun JsonObject.rewriteCustomDnsRouting(plan: CustomDnsRoutingPlan): JsonObject {
    val routing = objectValue("routing") ?: buildJsonObject {}
    val existingRules = routing.arrayValue("rules") ?: buildJsonArray {}
    return routing.updated {
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
