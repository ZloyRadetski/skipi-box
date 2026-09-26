// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.StrategyGroupDisplayMode

/** Only the fields Home needs to decide whether a subscription group is displayed. */
data class ProxyServerGroupInput(
    val id: Int,
    val name: String,
    val enabled: Boolean,
    val builtIn: Boolean = false,
)

/** Platform-neutral projection of a server used by Home grouping and search. */
data class ProxyServerGroupServerInput(
    val id: Int,
    val groupId: Int,
    val remarks: String,
    val isStrategyGroup: Boolean = false,
    val sourceTrafficConfigId: Int? = null,
    val displayMode: String = StrategyGroupDisplayMode.ALWAYS,
)

data class ProxyServerGroupTab(
    val id: Int,
    val name: String,
    val serverCount: Int,
)

data class ProxyServerGroupingRequest(
    val groups: List<ProxyServerGroupInput>,
    val servers: List<ProxyServerGroupServerInput>,
    val activeTrafficConfigId: Int?,
    val selectedGroupId: Int,
    val searchQuery: String,
    val enableAllGroup: Boolean,
    val allGroupName: String,
    val defaultGroupName: String,
    val autoBalancerGroupName: String,
    val defaultGroupId: Int,
    val autoBalancerGroupId: Int = DefaultAutoBalancerGroupId,
    val allGroupId: Int = DefaultAllProxyGroupId,
)

data class ProxyServerGroupingResult(
    val visibleGroups: List<ProxyServerGroupInput>,
    val showGroupTabs: Boolean,
    /** The resolved group backing the selected state, or null if no manual group exists. */
    val selectedGroupId: Int?,
    val selectedTabId: Int,
    val isAllGroupsSelected: Boolean,
    val groupNames: Map<Int, String>,
    val visibleGroupIds: Set<Int>,
    val visibleServerIds: List<Int>,
    val groupTabs: List<ProxyServerGroupTab>,
    val selectedTabIndex: Int,
    val currentGroupServerIds: List<Int>,
    val currentFilteredServerIds: List<Int>,
)

const val DefaultAutoBalancerGroupId: Int = -2
const val DefaultAllProxyGroupId: Int = 0
const val DefaultManualProxyGroupName: String = "Default"

/** Mirrors the current display policy for config-owned strategy groups. */
fun isProxyServerGroupEntryVisible(
    server: ProxyServerGroupServerInput,
    activeTrafficConfigId: Int?,
    autoBalancerGroupId: Int = DefaultAutoBalancerGroupId,
): Boolean {
    if (server.groupId != autoBalancerGroupId) return true
    if (!server.isStrategyGroup) return false
    if (server.sourceTrafficConfigId == null) return true
    return when (server.displayMode) {
        StrategyGroupDisplayMode.ALWAYS -> true
        StrategyGroupDisplayMode.NEVER -> false
        StrategyGroupDisplayMode.ACTIVE_CONFIG -> server.sourceTrafficConfigId == activeTrafficConfigId
        else -> server.sourceTrafficConfigId == activeTrafficConfigId
    }
}

/** Pure ordering, visibility, selection fallback, counting, and search for the Home groups. */
fun groupProxyServersForHome(request: ProxyServerGroupingRequest): ProxyServerGroupingResult {
    val displayedServers = request.servers.filter { server ->
        isProxyServerGroupEntryVisible(server, request.activeTrafficConfigId, request.autoBalancerGroupId)
    }
    val manualGroup = request.groups.firstOrNull { it.id == request.defaultGroupId }
    val hasVisibleAutoBalancer = displayedServers.any { server ->
        server.groupId == request.autoBalancerGroupId && server.isStrategyGroup
    }
    val autoBalancerGroup = if (hasVisibleAutoBalancer) {
        ProxyServerGroupInput(
            id = request.autoBalancerGroupId,
            name = request.autoBalancerGroupName,
            enabled = true,
            builtIn = true,
        )
    } else {
        null
    }
    val visibleSubscriptionGroups = request.groups.filter { group ->
        group.id != request.defaultGroupId && group.enabled
    }
    val visibleManualGroup = manualGroup?.takeIf { group ->
        request.servers.any { server -> server.groupId == group.id }
    }
    val visibleGroups = listOfNotNull(autoBalancerGroup) + visibleSubscriptionGroups + listOfNotNull(visibleManualGroup)
    val showGroupTabs = visibleGroups.size > 1
    val showAllProxyGroup = request.enableAllGroup && showGroupTabs
    val selectedGroup = visibleGroups.firstOrNull { it.id == request.selectedGroupId }
        ?: visibleGroups.firstOrNull()
        ?: manualGroup
    val selectedTabId = if (showAllProxyGroup && request.selectedGroupId == request.allGroupId) {
        request.allGroupId
    } else {
        selectedGroup?.id ?: request.defaultGroupId
    }
    val isAllGroupsSelected = selectedTabId == request.allGroupId
    val groupNames = (request.groups + listOfNotNull(autoBalancerGroup)).associate { group ->
        group.id to if (group.builtIn && group.id == request.defaultGroupId) {
            request.defaultGroupName
        } else {
            group.name
        }
    }
    val visibleGroupIds = visibleGroups.mapTo(linkedSetOf(), ProxyServerGroupInput::id)
    val visibleServers = displayedServers.filter { server -> server.groupId in visibleGroupIds }
    val serverCountByGroup = visibleServers.groupingBy(ProxyServerGroupServerInput::groupId).eachCount()
    val groupTabs = visibleGroups.map { group ->
        ProxyServerGroupTab(
            id = group.id,
            name = if (group.builtIn && group.id == request.defaultGroupId) request.defaultGroupName else group.name,
            serverCount = serverCountByGroup[group.id] ?: 0,
        )
    }.let { tabs ->
        if (showAllProxyGroup) {
            listOf(ProxyServerGroupTab(request.allGroupId, request.allGroupName, visibleServers.size)) + tabs
        } else {
            tabs
        }
    }
    val selectedTabIndex = groupTabs.indexOfFirst { tab -> tab.id == selectedTabId }.coerceAtLeast(0)
    val currentGroupServers = if (isAllGroupsSelected) {
        visibleServers.filter { server -> !server.isStrategyGroup || server.sourceTrafficConfigId == null }
    } else {
        visibleServers.filter { server -> server.groupId == selectedTabId }
    }
    val search = request.searchQuery.trim()
    val currentFilteredServers = currentGroupServers.filter { server ->
        search.isEmpty() || server.remarks.contains(search, ignoreCase = true)
    }

    return ProxyServerGroupingResult(
        visibleGroups = visibleGroups,
        showGroupTabs = showGroupTabs,
        selectedGroupId = selectedGroup?.id,
        selectedTabId = selectedTabId,
        isAllGroupsSelected = isAllGroupsSelected,
        groupNames = groupNames,
        visibleGroupIds = visibleGroupIds,
        visibleServerIds = visibleServers.map(ProxyServerGroupServerInput::id),
        groupTabs = groupTabs,
        selectedTabIndex = selectedTabIndex,
        currentGroupServerIds = currentGroupServers.map(ProxyServerGroupServerInput::id),
        currentFilteredServerIds = currentFilteredServers.map(ProxyServerGroupServerInput::id),
    )
}
