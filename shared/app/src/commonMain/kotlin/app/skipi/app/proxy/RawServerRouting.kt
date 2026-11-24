// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import engine.xray.CustomSkipiExtras
import features.proxy.server.model.AmneziaWg
import engine.xray.CustomSkipiRouting
import engine.xray.XrayOutboundPlan
import engine.xray.XrayOutboundRenderingOptions
import engine.xray.XrayOutboundTransportOptions
import engine.xray.XrayRouteTarget
import engine.xray.XrayRouteTargetKind
import engine.xray.XrayRoutingRequest
import engine.xray.XrayRoutingRuleValidator
import engine.xray.XrayTags
import engine.xray.buildXrayBalancers
import engine.xray.buildXrayObservatory
import engine.xray.planXrayRouting
import engine.xray.renderXrayOutbounds
import engine.xray.toXrayRoutingDomainStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The routing SKIPI puts in a raw JSON server that overrides inbound and DNS, from the app's route rules (the active
 * profile's, or the global ones). [input] describes the whole server list with the raw server selected; the rules
 * may name other servers, proxy groups and balancers, which join the raw config as real outbounds and balancers
 * next to its own proxy outbound. What names the raw server itself, or `proxy`, goes to that outbound.
 */
fun rawServerSkipiRouting(
    input: XrayOutboundPlanningInput,
    routeDomainStrategy: Int,
    transport: XrayOutboundTransportOptions?,
    ruleValidator: XrayRoutingRuleValidator,
): CustomSkipiRouting {
    // Rules and extras of one proxy tag come from one plan, made once.
    val plans = HashMap<String, XrayOutboundPlan>()
    fun planFor(proxyTag: String) = plans.getOrPut(proxyTag) {
        // The raw config has neither the DNS nor the fragment outbound a generated one carries.
        XrayOutboundPlanner.plan(
            input.copy(
                // AmneziaWG needs the runner the generated path starts; a rule naming one goes to the raw proxy.
                servers = input.servers.filterNot { row -> row.server is AmneziaWg },
                rawProxyTag = proxyTag,
                enableLocalDns = false,
                enableFragment = false,
            ),
        )
    }
    return CustomSkipiRouting(
        domainStrategy = routeDomainStrategy.toXrayRoutingDomainStrategy(),
        rules = { proxyTag ->
            val plan = planFor(proxyTag)
            planXrayRouting(
                request = XrayRoutingRequest(
                    routeDomainStrategy = routeDomainStrategy,
                    routeRules = input.routeRules,
                    defaultRouteOutboundTag = input.defaultRouteOutboundTag,
                    routeTargets = plan.routeTargets +
                        (XrayTags.DIRECT to XrayRouteTarget(XrayTags.DIRECT, XrayRouteTargetKind.Outbound)) +
                        (XrayTags.BLOCK to XrayRouteTarget(XrayTags.BLOCK, XrayRouteTargetKind.Outbound)),
                    balancers = buildXrayBalancers(plan.balancers),
                    enableLocalDns = false,
                    routeProxyDns = false,
                    routeDirectDns = false,
                    dnsHijackInboundTags = emptyList(),
                ),
                ruleValidator = ruleValidator,
            ).rules.map { rule -> rule as JsonObject }
        },
        extras = { proxyTag ->
            val plan = planFor(proxyTag)
            val added = plan.proxyOutbounds.map { outbound -> outbound.tag }.toSet()
            CustomSkipiExtras(
                outbounds = renderXrayOutbounds(
                    proxyOutbounds = plan.proxyOutbounds,
                    options = XrayOutboundRenderingOptions(
                        primaryOutboundTag = null,
                        enableLocalDns = false,
                        dnsPlan = null,
                        dnsProxyOutboundTag = null,
                        transport = transport,
                        mux = null,
                        fragment = null,
                    ),
                ).map { outbound -> outbound.jsonObject }
                    // The renderer also writes direct and block; the raw config has its own.
                    .filter { outbound -> outbound.getValue("tag").jsonPrimitive.content in added },
                balancers = buildXrayBalancers(plan.balancers),
                observatory = buildXrayObservatory(
                    selectors = plan.observatorySelectors,
                    probeUrl = plan.observatoryProbeUrl,
                    probeInterval = plan.observatoryProbeInterval,
                    probeTimeout = plan.observatoryProbeTimeout,
                ),
                serverHosts = plan.dnsHostServers,
            )
        },
    )
}
