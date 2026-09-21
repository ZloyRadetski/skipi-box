// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.effectiveLocalDnsEnabled
import kotlinx.serialization.json.JsonObject

internal fun AppState.buildXrayRoutingPlan(
    routeTargets: Map<String, XrayRouteTarget>,
    balancers: List<JsonObject>,
    routeProxyDns: Boolean,
    routeDirectDns: Boolean,
    dnsHijackInboundTags: List<String>,
    dataDir: String? = null,
): XrayRoutingPlan = planXrayRouting(
    request = XrayRoutingRequest(
        routeDomainStrategy = routeDomainStrategy,
        routeRules = routeRules,
        defaultRouteOutboundTag = defaultRouteOutboundTag,
        routeTargets = routeTargets,
        balancers = balancers,
        enableLocalDns = effectiveLocalDnsEnabled,
        routeProxyDns = routeProxyDns,
        routeDirectDns = routeDirectDns,
        dnsHijackInboundTags = dnsHijackInboundTags,
    ),
    ruleValidator = object : XrayRoutingRuleValidator {
        override fun isDomainRuleValid(rule: String): Boolean =
            XrayGeoRuleSanitizer.isDomainRuleValid(rule, dataDir)

        override fun isIpRuleValid(rule: String): Boolean =
            XrayGeoRuleSanitizer.isIpRuleValid(rule, dataDir)

        override fun filterValidDomainRules(rules: List<String>): List<String> =
            XrayGeoRuleSanitizer.filterValidDomainRules(rules, dataDir)

        override fun filterValidIpRules(rules: List<String>): List<String> =
            XrayGeoRuleSanitizer.filterValidIpRules(rules, dataDir)
    },
)
