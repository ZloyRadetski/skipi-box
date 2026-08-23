// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

internal enum class XrayRouteTargetKind {
    Outbound,
    Balancer,
}

internal data class XrayRouteTarget(
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

internal data class XrayBalancerPlan(
    val tag: String,
    val selector: String,
    val strategy: String,
    /**
     * A real member used while observatory data is not available or all
     * members are temporarily unhealthy.  It must never resolve to the
     * generated loopback default outbound, or a balancer can route to itself.
     */
    val fallbackTag: String? = null,
)

internal data class XrayOutboundPlan(
    val proxyOutbounds: List<XrayProxyOutboundServer>,
    val balancers: List<XrayBalancerPlan>,
    val observatorySelectors: List<String>,
    val routeTargets: Map<String, XrayRouteTarget>,
    val dnsHostServers: List<String>,
    val observatoryProbeUrl: String? = null,
    val observatoryProbeInterval: String? = null,
)
