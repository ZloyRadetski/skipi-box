// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.effectiveLocalDnsEnabled
import app.skipi.app.proxy.ProxyServerRow
import app.skipi.app.proxy.XrayOutboundPlanner as SharedXrayOutboundPlanner
import app.skipi.app.proxy.XrayOutboundPlanningInput
import app.skipi.app.proxy.resolveStrategyGroupMembers
import app.skipi.ui.server.display.CountryFlagUtils
import features.proxy.server.model.StrategyGroup

internal fun AppState.buildXrayOutboundPlan(selectedServer: ProxyServerState): XrayOutboundPlan {
    return SharedXrayOutboundPlanner.plan(xrayOutboundPlanningInput(selectedServer))
}

private fun AppState.xrayOutboundPlanningInput(selectedServer: ProxyServerState) = XrayOutboundPlanningInput(
    selectedServer = selectedServer.toProxyServerRecord(),
    servers = proxyServers.map { it.toProxyServerRecord() },
    routeRules = routeRules,
    defaultRouteOutboundTag = defaultRouteOutboundTag,
    shadowrocketPolicyGroups = shadowrocketPolicyGroups,
    trafficConfigContentById = trafficConfigs.associate { it.id to it.rawConfig },
    enableLocalDns = effectiveLocalDnsEnabled,
    enableFragment = enableFragment,
    subscriptionPingUrl = subscriptionPingUrl,
    stripLeadingFlag = CountryFlagUtils::stripLeadingCountryFlag,
)

/**
 * The routing for a raw JSON server that overrides inbound and DNS: the rules the app applies (the active profile's,
 * or the global ones). Servers, groups and balancers the rules name join the raw config. Null, and the JSON keeps
 * its own routing, when there is no enabled rule and the default route is the proxy: the app always carries a
 * profile, and an empty one must not wipe what the JSON says.
 */
internal fun AppState.rawServerSkipiRouting(selectedServer: ProxyServerState, dataDir: String?): CustomSkipiRouting? {
    val defaultsToProxy = defaultRouteOutboundTag.trim().ifBlank { XrayTags.PROXY } == XrayTags.PROXY
    if (routeRules.none { rule -> rule.enabled } && defaultsToProxy) return null
    return app.skipi.app.proxy.rawServerSkipiRouting(
        input = xrayOutboundPlanningInput(selectedServer),
        routeDomainStrategy = routeDomainStrategy,
        transport = xrayTransportOptions(),
        ruleValidator = xrayRoutingRuleValidator(dataDir),
    )
}

internal fun AppState.strategyGroupMembers(
    strategyGroup: StrategyGroup,
    visitingStrategyGroupIds: Set<Int> = emptySet(),
): List<ProxyServerState> {
    val records = proxyServers.map { server ->
        ProxyServerRow(server.id, server.groupId, server.server, server.latency)
    }
    val resolved = resolveStrategyGroupMembers(
        strategyGroup = strategyGroup,
        servers = records,
        trafficConfigContentById = trafficConfigs.associate { it.id to it.rawConfig },
        stripLeadingFlag = CountryFlagUtils::stripLeadingCountryFlag,
        visitingStrategyGroupIds = visitingStrategyGroupIds,
    )
    return resolved.mapNotNull { record -> proxyServers.firstOrNull { it.id == record.id } }
}

private fun ProxyServerState.toProxyServerRecord() = ProxyServerRow(
    id = id,
    groupId = groupId,
    server = server,
    latency = latency,
)
