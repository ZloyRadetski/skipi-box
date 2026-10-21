// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.subscription.DefaultSubscriptionGroupId

/** The subscription fields needed to label one member-picker group. */
data class StrategyMemberSelectorSubscription(
    val id: Int,
    val name: String,
    val profileTitle: String = "",
)

/** One ordered member-picker group projected from the stored proxy catalog. */
data class StrategyMemberSelectorGroupProjection(
    val key: String,
    val title: String,
    val subscriptionGroupId: Int?,
    val servers: List<ProxyServerRecord>,
)

/**
 * Projects the Android strategy-group member picker while leaving localized labels and
 * platform-specific summary data with the host.
 */
fun projectStrategyGroupMemberGroups(
    servers: List<ProxyServerRecord>,
    subscriptionGroups: List<StrategyMemberSelectorSubscription>,
    defaultGroupName: String,
    autoBalancerName: String,
    excludedServerId: Int?,
    requireServerRemarks: Boolean = false,
    defaultGroupId: Int = DefaultSubscriptionGroupId,
): List<StrategyMemberSelectorGroupProjection> {
    val selectableServers = servers.filter { server ->
        server.id != excludedServerId &&
            server.server !is ChainProxy &&
            (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan()) &&
            (!requireServerRemarks || server.server.getInfo().remarks.isNotBlank()) &&
            (server.server !is StrategyGroup || server.server.sourceTrafficConfigId == null)
    }
    val serversByGroup = selectableServers.groupBy(ProxyServerRecord::groupId)

    return buildList {
        val autoBalancerServers = serversByGroup[DefaultAutoBalancerGroupId].orEmpty()
            .filter { it.server is StrategyGroup }
        if (autoBalancerServers.isNotEmpty()) {
            add(
                StrategyMemberSelectorGroupProjection(
                    key = "auto-balancers",
                    title = autoBalancerName,
                    subscriptionGroupId = null,
                    servers = autoBalancerServers,
                ),
            )
        }

        subscriptionGroups.forEach { group ->
            if (group.id != defaultGroupId) {
                add(
                    StrategyMemberSelectorGroupProjection(
                        key = "subscription-${group.id}",
                        title = group.profileTitle.ifBlank { group.name },
                        subscriptionGroupId = group.id,
                        servers = serversByGroup[group.id].orEmpty(),
                    ),
                )
            }
        }

        val manualServers = serversByGroup[defaultGroupId].orEmpty()
        if (manualServers.isNotEmpty() || subscriptionGroups.none { it.id != defaultGroupId }) {
            val manualGroupId = subscriptionGroups.firstOrNull { it.id == defaultGroupId }?.id
            add(
                StrategyMemberSelectorGroupProjection(
                    key = "manual-servers",
                    title = defaultGroupName,
                    subscriptionGroupId = manualGroupId,
                    servers = manualServers,
                ),
            )
        }
    }
}
