// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.routing.model.RouteRule
import kotlinx.serialization.json.JsonObject

/**
 * Everything the shared glue needs to turn already-planned parts into the final Xray JSON of a generated
 * (non-raw) configuration. Hosts build this from their own state; the order of the stages (routing plan,
 * outbound rendering, observatory, assembly) lives only in [buildGeneratedXrayConfig].
 */
data class XrayGeneratedConfigInput(
    val outboundPlan: XrayOutboundPlan,
    /** Supplies DNS, fake DNS and the proxy/direct DNS routing flags. */
    val dnsPlan: XrayDnsPlan,
    val log: JsonObject,
    val inbounds: List<JsonObject>,
    val routeDomainStrategy: Int,
    val routeRules: List<RouteRule>,
    val defaultRouteOutboundTag: String,
    /** The targets rules may name; a host that needs `direct` among them adds it here. */
    val routeTargets: Map<String, XrayRouteTarget> = outboundPlan.routeTargets,
    val enableLocalDns: Boolean,
    val dnsHijackInboundTags: List<String>,
    val ruleValidator: XrayRoutingRuleValidator,
    /** Outbound a DNS query that Core does not answer itself leaves through; null adds none. */
    val dnsProxyOutboundTag: String? = null,
    val transport: XrayOutboundTransportOptions? = null,
    val mux: XrayMuxOutboundOptions? = null,
    val fragment: XrayFragmentOutboundOptions? = null,
    val collectTrafficStats: Boolean = true,
    /**
     * Known divergence: the primary outbound tag used when the routing plan has none (no `proxy` route
     * target). Android passes null (no tag), Desktop passes its `proxy` tag.
     */
    val fallbackPrimaryOutboundTag: String? = null,
    /**
     * Known divergence: a complete `dns` section replacing the one planned from [dnsPlan]. Desktop uses it
     * when the local-proxy options carry an explicit resolver list; Android never sets it.
     */
    val dnsOverride: JsonObject? = null,
)

data class XrayGeneratedConfigResult(
    val json: String,
    val unappliedRules: List<String>,
)

/** The one place that sequences routing plan, outbound rendering, observatory and assembly of a generated config. */
fun buildGeneratedXrayConfig(input: XrayGeneratedConfigInput): XrayGeneratedConfigResult {
    val plan = input.outboundPlan
    val dnsPlan = input.dnsPlan
    val routingPlan = planXrayRouting(
        request = XrayRoutingRequest(
            routeDomainStrategy = input.routeDomainStrategy,
            routeRules = input.routeRules,
            defaultRouteOutboundTag = input.defaultRouteOutboundTag,
            routeTargets = input.routeTargets,
            balancers = buildXrayBalancers(plan.balancers),
            enableLocalDns = input.enableLocalDns,
            routeProxyDns = dnsPlan.routingOptions.routeProxyDns,
            routeDirectDns = dnsPlan.routingOptions.routeDirectDns,
            dnsHijackInboundTags = input.dnsHijackInboundTags,
        ),
        ruleValidator = input.ruleValidator,
    )
    val outbounds = renderXrayOutbounds(
        proxyOutbounds = plan.proxyOutbounds,
        options = XrayOutboundRenderingOptions(
            primaryOutboundTag = routingPlan.primaryOutboundTag ?: input.fallbackPrimaryOutboundTag,
            enableLocalDns = input.enableLocalDns,
            dnsPlan = dnsPlan,
            dnsProxyOutboundTag = input.dnsProxyOutboundTag,
            transport = input.transport,
            mux = input.mux,
            fragment = input.fragment,
        ),
    )
    val generated = GeneratedXrayConfig(
        log = input.log,
        dns = input.dnsOverride ?: buildXrayDnsConfig(dnsPlan),
        inbounds = input.inbounds.toJsonObjectArray(),
        outbounds = outbounds,
        routing = buildXrayRouting(routingPlan),
        fakeDns = dnsPlan.fakeDns,
        observatory = buildXrayObservatory(
            selectors = plan.observatorySelectors,
            probeUrl = plan.observatoryProbeUrl,
            probeInterval = plan.observatoryProbeInterval,
            probeTimeout = plan.observatoryProbeTimeout,
        ),
        burstObservatory = null,
        collectTrafficStats = input.collectTrafficStats,
    )
    return XrayGeneratedConfigResult(
        json = generated.encodeToJsonString(),
        unappliedRules = routingPlan.unappliedRules,
    )
}
