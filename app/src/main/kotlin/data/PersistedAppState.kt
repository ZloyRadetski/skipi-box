// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.AppState

internal data class PersistedAppState(
    val subscriptionGroups: List<SubscriptionGroupEntity>,
    val proxyServers: List<ProxyServerEntity>,
    val routingRules: List<RouteRuleEntity>,
    val proxyAppListSelectedApps: List<ProxyAppListSelectedAppEntity>,
) {
    fun hasRoomContent(): Boolean {
        return subscriptionGroups.isNotEmpty() ||
            proxyServers.isNotEmpty() ||
            routingRules.isNotEmpty() ||
            proxyAppListSelectedApps.isNotEmpty()
    }

    fun toAppState(settings: AppState): AppState {
        val restoredSubscriptionGroups = subscriptionGroups.map { group -> group.toState() }
        val restoredProxyServerList = proxyServers.mapNotNull { server -> server.toState() }
        val restoredRouteRules = routingRules.map { rule -> rule.toState() }
        val restoredSelectedProxyServerId = settings.selectedProxyServerId
            .takeIf { serverId -> restoredProxyServerList.any { server -> server.id == serverId } }
            ?: restoredProxyServerList.firstOrNull()?.id
            ?: settings.selectedProxyServerId

        return settings.copy(
            subscriptionGroups = restoredSubscriptionGroups,
            // Reconcile the id counters with the restored lists: the counters
            // live in preferences while these lists live in Room, and the two
            // writes are not atomic.  A stale counter would hand out an id that
            // already exists, and the REPLACE conflict strategy would silently
            // overwrite an existing row.
            nextSubscriptionGroupId = maxOf(
                settings.nextSubscriptionGroupId,
                (restoredSubscriptionGroups.maxOfOrNull { it.id } ?: 0) + 1,
            ),
            proxyServers = restoredProxyServerList,
            nextProxyServerId = maxOf(
                settings.nextProxyServerId,
                (restoredProxyServerList.maxOfOrNull { it.id } ?: 0) + 1,
            ),
            selectedProxyServerId = restoredSelectedProxyServerId,
            proxyRunning = false,
            routeRules = restoredRouteRules,
            nextRouteRuleId = maxOf(
                settings.nextRouteRuleId,
                (restoredRouteRules.maxOfOrNull { it.id } ?: 0) + 1,
            ),
            proxyAppListSelectedApps = proxyAppListSelectedApps.map { app -> app.packageKey },
        )
    }
}
