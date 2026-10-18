// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.routing

import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.proxy.ProxyServerRecord
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.routing.model.DefaultRouteOutboundTag
import features.subscription.DefaultSubscriptionGroupId

/** Portable server or static target displayed by the route-outbound selector. */
sealed interface RouteOutboundItem {
    val key: String

    data class Static(
        val tag: String,
        val title: String,
        val summary: String = "",
    ) : RouteOutboundItem {
        override val key: String get() = "static-$tag"
    }

    data class Server(
        val server: ProxyServerRecord,
        val tag: String,
    ) : RouteOutboundItem {
        override val key: String get() = "server-${server.id}"
    }
}

/** The fields needed from a stored subscription group for selector grouping. */
data class RouteOutboundPickerSubscription(
    val id: Int,
    val name: String,
    val builtIn: Boolean = false,
)

data class RouteOutboundPickerGroup(
    val key: String,
    val title: String,
    val subscriptionGroupId: Int?,
    val items: List<RouteOutboundItem>,
)

/** Localized titles supplied by the host while group and target semantics stay shared. */
data class RouteOutboundPickerLabels(
    val defaultGroupName: String,
    val autoBalancerName: String,
    val systemTargetsName: String,
    val proxyLabel: String,
    val directLabel: String,
    val blockLabel: String,
    val proxyGroupsName: String,
    val configTargetsName: String,
)

data class RouteOutboundPickerProjection(
    val groups: List<RouteOutboundPickerGroup>,
    val initiallyExpandedGroupKey: String?,
)

/**
 * Projects the Android route-outbound selector's stored catalogs into ordered
 * picker groups. The host owns localized group labels and platform resource I/O;
 * shared UI owns portable option presentation and rendering.
 */
fun projectRouteOutboundPicker(
    servers: List<ProxyServerRecord>,
    subscriptionGroups: List<RouteOutboundPickerSubscription>,
    trafficConfigs: List<TrafficConfigState>,
    selectedTag: String,
    trafficConfigId: Int?,
    labels: RouteOutboundPickerLabels,
): RouteOutboundPickerProjection {
    val shadowrocketPolicyMode = trafficConfigId != null
    val trafficConfig = trafficConfigId?.let { id -> trafficConfigs.firstOrNull { it.id == id } }
    val shadowrocketGroups = trafficConfig?.rawConfig?.analyzeShadowrocketConfig()?.proxyGroups.orEmpty()

    val selectableServers = servers.filter { server ->
        (server.server !is Custom || server.server.canBeUsedInGeneratedProxyPlan()) &&
            (!shadowrocketPolicyMode || server.server.getInfo().remarks.isNotBlank())
    }
    val serversByGroup = selectableServers.groupBy(ProxyServerRecord::groupId)

    val systemEntries = mutableListOf(
        RouteOutboundItem.Static(
            tag = if (shadowrocketPolicyMode) "PROXY" else DefaultRouteOutboundTag,
            title = labels.proxyLabel,
        ),
        RouteOutboundItem.Static(
            tag = if (shadowrocketPolicyMode) "DIRECT" else "direct",
            title = labels.directLabel,
        ),
        RouteOutboundItem.Static(
            tag = if (shadowrocketPolicyMode) "REJECT" else "block",
            title = labels.blockLabel,
        ),
    )

    val autoBalancerItems = serversByGroup[DefaultAutoBalancerGroupId].orEmpty()
        .filter { server -> server.server is StrategyGroup && server.server.sourceTrafficConfigId == null }
        .map { server -> RouteOutboundItem.Server(server, resolveTag(server, shadowrocketPolicyMode)) }

    val subscriptionGroupItems = subscriptionGroups
        .filter { it.id != DefaultSubscriptionGroupId }
        .map { group ->
            RouteOutboundPickerGroup(
                key = "routing-subscription-${group.id}",
                title = group.displayName(labels.defaultGroupName),
                subscriptionGroupId = group.id,
                items = serversByGroup[group.id].orEmpty().map { server ->
                    RouteOutboundItem.Server(server, resolveTag(server, shadowrocketPolicyMode))
                },
            )
        }

    val manualGroupItems = serversByGroup[DefaultSubscriptionGroupId].orEmpty().map { server ->
        RouteOutboundItem.Server(server, resolveTag(server, shadowrocketPolicyMode))
    }

    val configProxyGroups = shadowrocketGroups.map { group ->
        RouteOutboundItem.Static(
            tag = group.name,
            title = group.name,
            summary = group.type,
        )
    }

    val otherConfigs = if (shadowrocketPolicyMode) {
        trafficConfigs
            .filter { it.id != trafficConfigId }
            .map { config ->
                RouteOutboundItem.Static(
                    tag = "CONFIG:${config.id}",
                    title = config.name,
                    summary = "CONFIG:${config.id}",
                )
            }
    } else {
        emptyList()
    }

    if (selectedTag.isNotBlank()) {
        val isKnown = systemEntries.any { it.tag.equals(selectedTag, ignoreCase = true) } ||
            configProxyGroups.any { it.tag == selectedTag } ||
            otherConfigs.any { it.tag == selectedTag } ||
            selectableServers.any { server -> resolveTag(server, shadowrocketPolicyMode) == selectedTag }
        if (!isKnown) {
            systemEntries += RouteOutboundItem.Static(
                tag = selectedTag,
                title = selectedTag,
            )
        }
    }

    val groups = buildList {
        add(
            RouteOutboundPickerGroup(
                key = SystemTargetsGroupKey,
                title = labels.systemTargetsName,
                subscriptionGroupId = null,
                items = systemEntries,
            ),
        )
        add(
            RouteOutboundPickerGroup(
                key = AutoBalancersGroupKey,
                title = labels.autoBalancerName,
                subscriptionGroupId = null,
                items = autoBalancerItems,
            ),
        )
        addAll(subscriptionGroupItems)
        add(
            RouteOutboundPickerGroup(
                key = ManualServersGroupKey,
                title = labels.defaultGroupName,
                subscriptionGroupId = subscriptionGroups.firstOrNull { it.id == DefaultSubscriptionGroupId }?.id,
                items = manualGroupItems,
            ),
        )
        if (shadowrocketPolicyMode) {
            add(
                RouteOutboundPickerGroup(
                    key = ConfigProxyGroupsKey,
                    title = labels.proxyGroupsName,
                    subscriptionGroupId = null,
                    items = configProxyGroups,
                ),
            )
            add(
                RouteOutboundPickerGroup(
                    key = ConfigTargetsGroupKey,
                    title = labels.configTargetsName,
                    subscriptionGroupId = null,
                    items = otherConfigs,
                ),
            )
        }
    }

    val initiallyExpandedGroupKey = groups.firstOrNull { group ->
        if (selectedTag.isSystemTargetForInitialExpansion()) {
            group.key == SystemTargetsGroupKey
        } else {
            group.items.any { item -> item.tagForMatching() == selectedTag }
        }
    }?.key ?: groups.firstOrNull()?.key

    return RouteOutboundPickerProjection(
        groups = groups,
        initiallyExpandedGroupKey = initiallyExpandedGroupKey,
    )
}

private fun RouteOutboundPickerSubscription.displayName(defaultGroupName: String): String =
    if (builtIn && id == DefaultSubscriptionGroupId) defaultGroupName else name

private fun resolveTag(server: ProxyServerRecord, shadowrocketPolicyMode: Boolean): String =
    if (shadowrocketPolicyMode) server.server.getInfo().remarks.trim() else server.id.toString()

private fun RouteOutboundItem.tagForMatching(): String = when (this) {
    is RouteOutboundItem.Static -> tag
    is RouteOutboundItem.Server -> tag
}

private fun String.isSystemTargetForInitialExpansion(): Boolean =
    isBlank() || this == "PROXY" || this == DefaultRouteOutboundTag ||
        equals("DIRECT", ignoreCase = true) || equals("REJECT", ignoreCase = true) ||
        equals("block", ignoreCase = true)

private const val SystemTargetsGroupKey = "routing-system-targets"
private const val AutoBalancersGroupKey = "routing-auto-balancers"
private const val ManualServersGroupKey = "routing-manual-servers"
private const val ConfigProxyGroupsKey = "routing-config-proxy-groups"
private const val ConfigTargetsGroupKey = "routing-config-targets"
