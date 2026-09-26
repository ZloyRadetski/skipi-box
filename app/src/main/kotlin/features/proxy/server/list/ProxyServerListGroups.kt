// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerListState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.skipi.app.proxy.ProxyServerGroupInput
import app.skipi.app.proxy.ProxyServerGroupServerInput
import app.skipi.app.proxy.ProxyServerGroupingRequest
import app.skipi.app.proxy.groupProxyServersForHome
import app.skipi.app.proxy.isProxyServerGroupEntryVisible
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.subscription.DefaultSubscriptionGroupId

internal const val AutoBalancerGroupId = -2
internal const val AllProxyGroupId = 0

internal fun ProxyServerState.isVisibleOnProxyServerList(activeTrafficConfigId: Int? = null): Boolean =
    isProxyServerGroupEntryVisible(
        server = toGroupingInput(),
        activeTrafficConfigId = activeTrafficConfigId,
        autoBalancerGroupId = AutoBalancerGroupId,
    )

internal data class ProxyServerListGroups(
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

internal fun proxyServerListGroups(
    state: ProxyServerListState,
    selectedGroupId: Int,
    searchValue: String,
    allGroupName: String,
    defaultGroupName: String,
    autoBalancerGroupName: String,
): ProxyServerListGroups {
    val result = groupProxyServersForHome(
        ProxyServerGroupingRequest(
            groups = state.subscriptionGroups.map(SubscriptionGroupState::toGroupingInput),
            servers = state.proxyServers.map(ProxyServerState::toGroupingInput),
            activeTrafficConfigId = state.activeTrafficConfigId,
            selectedGroupId = selectedGroupId,
            searchQuery = searchValue,
            enableAllGroup = state.enableAllProxyGroup,
            allGroupName = allGroupName,
            defaultGroupName = defaultGroupName,
            autoBalancerGroupName = autoBalancerGroupName,
            defaultGroupId = DefaultSubscriptionGroupId,
            autoBalancerGroupId = AutoBalancerGroupId,
            allGroupId = AllProxyGroupId,
        ),
    )
    val manualGroup = state.subscriptionGroups.firstOrNull { it.id == DefaultSubscriptionGroupId }
    val visibleGroups = result.visibleGroups.map { group ->
        if (group.id == AutoBalancerGroupId) {
            AutoBalancerGroup.copy(name = group.name)
        } else {
            state.subscriptionGroups.first { it.id == group.id }
        }
    }
    val selectedGroup = visibleGroups.firstOrNull { it.id == result.selectedGroupId }
        ?: visibleGroups.firstOrNull()
        ?: manualGroup
        ?: error("Manual proxy-server group is missing")
    val serversById = state.proxyServers.associateBy(ProxyServerState::id)

    return ProxyServerListGroups(
        visibleGroups = visibleGroups,
        showGroupTabs = result.showGroupTabs,
        selectedGroup = selectedGroup,
        selectedTabId = result.selectedTabId,
        isAllGroupsSelected = result.isAllGroupsSelected,
        groupNames = result.groupNames,
        visibleGroupIds = result.visibleGroupIds,
        visibleServers = result.visibleServerIds.mapNotNull(serversById::get),
        groupTabs = result.groupTabs.map { tab ->
            ProxyServerListGroupTabUi(tab.id, tab.name, tab.serverCount)
        },
        selectedTabIndex = result.selectedTabIndex,
        currentGroupServers = result.currentGroupServerIds.mapNotNull(serversById::get),
        currentFilteredServers = result.currentFilteredServerIds.mapNotNull(serversById::get),
    )
}

private fun SubscriptionGroupState.toGroupingInput() = ProxyServerGroupInput(
    id = id,
    name = name,
    enabled = enabled,
    builtIn = builtIn,
)

private fun ProxyServerState.toGroupingInput(): ProxyServerGroupServerInput {
    val strategy = server as? StrategyGroup
    return ProxyServerGroupServerInput(
        id = id,
        groupId = groupId,
        remarks = server.getInfo().remarks,
        isStrategyGroup = strategy != null,
        sourceTrafficConfigId = strategy?.sourceTrafficConfigId,
        displayMode = strategy?.displayMode ?: StrategyGroupDisplayMode.ALWAYS,
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
