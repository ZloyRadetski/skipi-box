// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerListState
import app.ProxyServerState
import app.SubscriptionGroupState
import features.proxy.server.display.displayName
import features.proxy.server.display.displayNameById
import features.subscription.DefaultSubscriptionGroupId
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode

const val AutoBalancerGroupId = -2

const val AllProxyGroupId = 0

fun ProxyServerState.isVisibleOnProxyServerList(activeTrafficConfigId: Int? = null): Boolean {
    if (groupId != AutoBalancerGroupId) return true
    val strategy = server as? StrategyGroup ?: return false
    if (strategy.sourceTrafficConfigId == null) {
        return true
    }
    return when (strategy.displayMode) {
        StrategyGroupDisplayMode.ALWAYS -> true
        StrategyGroupDisplayMode.NEVER -> false
        StrategyGroupDisplayMode.ACTIVE_CONFIG -> {
            strategy.sourceTrafficConfigId == activeTrafficConfigId
        }
        else -> strategy.sourceTrafficConfigId == activeTrafficConfigId
    }
}

data class ProxyServerListGroups(
    val visibleGroups: List<SubscriptionGroupState>,
    val showGroupTabs: Boolean,
    val selectedGroup: SubscriptionGroupState,
    val selectedTabId: Int,
    val isAllGroupsSelected: Boolean,
    val groupNames: Map<Int, String>,
    val visibleGroupIds: Set<Int>,
    val visibleServers: List<ProxyServerState>,
    val groupTabs: List<ProxyServerListGroupTabUi>,
    val selectedTabIndex: Int,
    val currentGroupServers: List<ProxyServerState>,
    val currentFilteredServers: List<ProxyServerState>,
)

fun proxyServerListGroups(
    state: ProxyServerListState,
    selectedGroupId: Int,
    searchValue: String,
    allGroupName: String,
    defaultGroupName: String,
    autoBalancerGroupName: String,
): ProxyServerListGroups {
    val servers = state.proxyServers
    val displayedServers = servers.filter { it.isVisibleOnProxyServerList(state.activeTrafficConfigId) }
    val manualGroup = state.subscriptionGroups.firstOrNull { it.id == DefaultSubscriptionGroupId }
    val autoBalancerGroup = AutoBalancerGroup.takeIf {
        displayedServers.any { server -> server.groupId == AutoBalancerGroupId && server.server is StrategyGroup }
    }?.copy(name = autoBalancerGroupName)
    val visibleSubscriptionGroups = state.subscriptionGroups.filter { group ->
        group.id != DefaultSubscriptionGroupId && group.enabled
    }
    val visibleManualGroup = manualGroup?.takeIf { group -> servers.any { it.groupId == group.id } }
    val visibleGroups = listOfNotNull(autoBalancerGroup) + visibleSubscriptionGroups + listOfNotNull(visibleManualGroup)
    val showGroupTabs = visibleGroups.size > 1
    val showAllProxyGroup = state.enableAllProxyGroup && showGroupTabs
    val selectedGroup = visibleGroups.firstOrNull { it.id == selectedGroupId }
        ?: visibleGroups.firstOrNull()
        ?: manualGroup
        ?: error("Manual proxy-server group is missing")
    val selectedTabId = if (showAllProxyGroup && selectedGroupId == AllProxyGroupId) {
        AllProxyGroupId
    } else {
        selectedGroup.id
    }
    val isAllGroupsSelected = selectedTabId == AllProxyGroupId
    val groupNames = (state.subscriptionGroups + listOfNotNull(autoBalancerGroup)).displayNameById(defaultGroupName)
    val visibleGroupIds = visibleGroups.map { it.id }.toSet()
    val visibleServers = displayedServers.filter { it.groupId in visibleGroupIds }
    val serverCountByGroup = visibleServers.groupingBy(ProxyServerState::groupId).eachCount()
    val groupTabs = proxyServerListGroupTabs(
        visibleGroups = visibleGroups,
        showAllProxyGroup = showAllProxyGroup,
        allGroupName = allGroupName,
        defaultGroupName = defaultGroupName,
        serverCountByGroup = serverCountByGroup,
        visibleServers = visibleServers,
    )
    val selectedTabIndex = groupTabs.indexOfFirst { group -> group.id == selectedTabId }
        .coerceAtLeast(0)
    val currentGroupServers = if (isAllGroupsSelected) {
        visibleServers.filter { server ->
            (server.server as? StrategyGroup)?.sourceTrafficConfigId == null
        }
    } else {
        visibleServers.filter { server -> server.groupId == selectedTabId }
    }
    val currentFilterKeyword = searchValue.trim()
    val currentFilteredServers = currentGroupServers.filter { server ->
        val info = server.server.getInfo()
        currentFilterKeyword.isEmpty() || info.remarks.contains(currentFilterKeyword, ignoreCase = true)
    }

    return ProxyServerListGroups(
        visibleGroups = visibleGroups,
        showGroupTabs = showGroupTabs,
        selectedGroup = selectedGroup,
        selectedTabId = selectedTabId,
        isAllGroupsSelected = isAllGroupsSelected,
        groupNames = groupNames,
        visibleGroupIds = visibleGroupIds,
        visibleServers = visibleServers,
        groupTabs = groupTabs,
        selectedTabIndex = selectedTabIndex,
        currentGroupServers = currentGroupServers,
        currentFilteredServers = currentFilteredServers,
    )
}

private val AutoBalancerGroup = SubscriptionGroupState(
    id = AutoBalancerGroupId,
    name = "Auto balancers",
    url = "",
    userAgent = "",
    updateInterval = "",
    enabled = true,
    builtIn = true,
)

private fun proxyServerListGroupTabs(
    visibleGroups: List<SubscriptionGroupState>,
    showAllProxyGroup: Boolean,
    allGroupName: String,
    defaultGroupName: String,
    serverCountByGroup: Map<Int, Int>,
    visibleServers: List<ProxyServerState>,
): List<ProxyServerListGroupTabUi> {
    val groupTabs = visibleGroups.map { group ->
        ProxyServerListGroupTabUi(
            id = group.id,
            name = group.displayName(defaultGroupName),
            serverCount = serverCountByGroup[group.id] ?: 0,
        )
    }
    return if (showAllProxyGroup) {
        listOf(
            ProxyServerListGroupTabUi(
                id = AllProxyGroupId,
                name = allGroupName,
                serverCount = visibleServers.size,
            ),
        ) + groupTabs
    } else {
        groupTabs
    }
}
