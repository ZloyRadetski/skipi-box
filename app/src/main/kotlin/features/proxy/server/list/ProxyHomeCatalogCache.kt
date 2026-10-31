// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.ProxyServerListState
import app.ProxyServerState
import app.SubscriptionGroupState

/**
 * Remembers the grouping and the Home catalog while their inputs stay the same. Starting the VPN
 * changes AppState several times (running flag, port, routing warnings) without touching a
 * server, and each change used to regroup and re-summarise every server of every subscription
 * on the main thread.
 */
internal class ProxyHomeCatalogCache {
    private var groupsKey: GroupsKey? = null
    private var groups: ProxyServerListGroups? = null
    private var catalogKey: CatalogKey? = null
    private var catalog: ProxyHomeCatalog? = null

    fun homeGroups(
        state: ProxyServerListState,
        allGroupName: String,
        defaultGroupName: String,
        autoBalancerGroupName: String,
    ): ProxyServerListGroups {
        val key = GroupsKey(
            subscriptionGroups = state.subscriptionGroups,
            proxyServers = state.proxyServers,
            enableAllProxyGroup = state.enableAllProxyGroup,
            activeTrafficConfigId = state.activeTrafficConfigId,
            allGroupName = allGroupName,
            defaultGroupName = defaultGroupName,
            autoBalancerGroupName = autoBalancerGroupName,
        )
        val cached = groups
        if (cached != null && groupsKey?.sameAs(key) == true) return cached
        return proxyServerListHomeGroups(
            state = state,
            allGroupName = allGroupName,
            defaultGroupName = defaultGroupName,
            autoBalancerGroupName = autoBalancerGroupName,
        ).also {
            groupsKey = key
            groups = it
        }
    }

    fun catalog(
        appState: AppState,
        groupState: ProxyServerListGroups,
        refreshing: Set<Int>,
        pinging: Set<Int>,
        formatter: ProxyServerListItemTextFormatter?,
    ): ProxyHomeCatalog {
        val key = CatalogKey(
            groupState = groupState,
            selectedProxyServerId = appState.selectedProxyServerId,
            subscriptionGroups = appState.subscriptionGroups,
            refreshing = refreshing,
            pinging = pinging,
            formatter = formatter,
        )
        val cached = catalog
        if (cached != null && catalogKey?.sameAs(key) == true) return cached
        return appState.toProxyHomeCatalog(
            groupState = groupState,
            refreshingSubscriptionGroupIds = refreshing,
            pingingSubscriptionGroupIds = pinging,
            presentationFormatter = formatter,
        ).also {
            catalogKey = key
            catalog = it
        }
    }

    private class GroupsKey(
        val subscriptionGroups: List<SubscriptionGroupState>,
        val proxyServers: List<ProxyServerState>,
        val enableAllProxyGroup: Boolean,
        val activeTrafficConfigId: Int?,
        val allGroupName: String,
        val defaultGroupName: String,
        val autoBalancerGroupName: String,
    ) {
        fun sameAs(other: GroupsKey): Boolean =
            enableAllProxyGroup == other.enableAllProxyGroup &&
                activeTrafficConfigId == other.activeTrafficConfigId &&
                allGroupName == other.allGroupName &&
                defaultGroupName == other.defaultGroupName &&
                autoBalancerGroupName == other.autoBalancerGroupName &&
                (subscriptionGroups === other.subscriptionGroups || subscriptionGroups == other.subscriptionGroups) &&
                (proxyServers === other.proxyServers || proxyServers == other.proxyServers)
    }

    private class CatalogKey(
        val groupState: ProxyServerListGroups,
        val selectedProxyServerId: Int,
        val subscriptionGroups: List<SubscriptionGroupState>,
        val refreshing: Set<Int>,
        val pinging: Set<Int>,
        val formatter: ProxyServerListItemTextFormatter?,
    ) {
        fun sameAs(other: CatalogKey): Boolean =
            groupState === other.groupState &&
                formatter === other.formatter &&
                selectedProxyServerId == other.selectedProxyServerId &&
                refreshing == other.refreshing &&
                pinging == other.pinging &&
                (subscriptionGroups === other.subscriptionGroups || subscriptionGroups == other.subscriptionGroups)
    }
}
