// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.effectiveLocalDnsEnabled
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.XrayOutboundPlanner as SharedXrayOutboundPlanner
import app.skipi.app.proxy.XrayOutboundPlanningInput
import app.skipi.app.proxy.resolveStrategyGroupMembers
import app.skipi.ui.server.display.CountryFlagUtils
import features.proxy.server.model.StrategyGroup

internal fun AppState.buildXrayOutboundPlan(selectedServer: ProxyServerState): XrayOutboundPlan {
    return SharedXrayOutboundPlanner.plan(
        XrayOutboundPlanningInput(
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
        ),
    )
}

internal fun AppState.strategyGroupMembers(
    strategyGroup: StrategyGroup,
    visitingStrategyGroupIds: Set<Int> = emptySet(),
): List<ProxyServerState> {
    val records = proxyServers.map { server ->
        ProxyServerRecord(server.id, server.groupId, server.server, server.latency)
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

private fun ProxyServerState.toProxyServerRecord() = ProxyServerRecord(
    id = id,
    groupId = groupId,
    server = server,
    latency = latency,
)
