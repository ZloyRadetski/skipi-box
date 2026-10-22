// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.proxy.ProxyServerRecord
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.StrategyGroup

fun projectTrafficConfigSourcedProxyGroups(
    trafficConfigs: List<TrafficConfigState>,
): List<TrafficConfigProxyGroupItem> = trafficConfigs.flatMap { config ->
    config.rawConfig.analyzeShadowrocketConfig().proxyGroups.map { group ->
        TrafficConfigProxyGroupItem(
            key = "source:${config.id}:${group.lineNumber}",
            name = group.name,
            subtitle = config.name,
            badge = group.type,
        )
    }
}

fun projectTrafficConfigGlobalProxyGroups(
    proxyServers: List<ProxyServerRecord>,
): List<TrafficConfigProxyGroupItem> = proxyServers.filter { state ->
    state.groupId == DefaultAutoBalancerGroupId &&
        (state.server as? StrategyGroup)?.sourceTrafficConfigId == null
}.map { state ->
    TrafficConfigProxyGroupItem(
        key = "global:${state.id}",
        name = state.server.getInfo().remarks.ifBlank { state.server.getInfo().protocol },
        subtitle = state.server.getInfo().address.ifBlank { "Auto-Balancer" },
        badge = "StrategyGroup",
    )
}
