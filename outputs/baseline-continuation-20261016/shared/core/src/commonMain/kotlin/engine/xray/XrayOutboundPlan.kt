// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import engine.network.NetworkDefaults
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A generated or raw outbound that can be rendered by either SKIPI runtime. */
data class XrayProxyOutboundServer(
    val tag: String,
    val server: ProxyServer<*>? = null,
    /** A primary outbound extracted from a simple raw Xray/JSON server. */
    val customOutbound: JsonObject? = null,
    val dialerProxyTag: String? = null,
    val allowFragment: Boolean = true,
)

enum class XrayRouteTargetKind {
    Outbound,
    Balancer,
}

/** A routing destination, represented in Xray as an outbound or a balancer tag. */
data class XrayRouteTarget(
    val tag: String,
    val kind: XrayRouteTargetKind,
) {
    fun applyTo(builder: JsonObjectBuilder) {
        when (kind) {
            XrayRouteTargetKind.Outbound -> builder.put("outboundTag", tag)
            XrayRouteTargetKind.Balancer -> builder.put("balancerTag", tag)
        }
    }
}

data class XrayBalancerPlan(
    val tag: String,
    val selector: String,
    val strategy: String,
    /**
     * A real member used while observatory data is not available or all
     * members are temporarily unhealthy. It must never resolve to a generated
     * loopback default outbound, or a balancer can route to itself.
     */
    val fallbackTag: String? = null,
)

/** Portable output of proxy, balancer, and route planning. */
data class XrayOutboundPlan(
    val proxyOutbounds: List<XrayProxyOutboundServer>,
    val balancers: List<XrayBalancerPlan>,
    val observatorySelectors: List<String>,
    val routeTargets: Map<String, XrayRouteTarget>,
    val dnsHostServers: List<String>,
    val observatoryProbeUrl: String? = null,
    val observatoryProbeInterval: String? = null,
    val observatoryProbeTimeout: String? = null,
)

/** Shared JSON renderer for Xray's routing balancer entries. */
fun buildXrayBalancers(plans: List<XrayBalancerPlan>): List<JsonObject> = plans.map { plan ->
    buildJsonObject {
        put("tag", plan.tag)
        put("selector", buildJsonArray { add(plan.selector) })
        put(
            "strategy",
            buildJsonObject {
                put("type", plan.strategy)
            },
        )
        plan.fallbackTag?.let { fallbackTag ->
            put("fallbackTag", fallbackTag)
        }
    }
}

/** Shared JSON renderer for an Xray observatory block. */
fun buildXrayObservatory(
    selectors: List<String>,
    probeUrl: String? = null,
    probeInterval: String? = null,
    @Suppress("UNUSED_PARAMETER") probeTimeout: String? = null,
    fallbackProbeUrl: String = NetworkDefaults.CONNECTIVITY_CHECK_URL,
    fallbackProbeInterval: String = "1m",
): JsonObject? {
    if (selectors.isEmpty()) return null
    return buildJsonObject {
        put("subjectSelector", buildJsonArray { selectors.distinct().forEach(::add) })
        put("probeURL", probeUrl?.takeIf(String::isNotBlank) ?: fallbackProbeUrl)
        put("probeInterval", probeInterval?.takeIf(String::isNotBlank) ?: fallbackProbeInterval)
        put("enableConcurrency", true)
    }
}
