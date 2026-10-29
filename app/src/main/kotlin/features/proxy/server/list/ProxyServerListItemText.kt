// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.ProxyServerState
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.ChainProxy
import app.R
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationLabels
import features.proxy.server.presentation.ProxyServerPresentationNode
import features.proxy.server.presentation.ProxyServerPresentationText

/** Android adapter for the portable proxy-card presentation contract. */
internal typealias ProxyServerListItemDisplayText = ProxyServerPresentationText

internal class ProxyServerListItemTextFormatter(
    private val delegate: ProxyServerPresentationFormatter,
) {
    // Home formats every visible server against the same list; map it once, not once per server.
    private var nodesSource: List<ProxyServerState>? = null
    private var nodes: List<ProxyServerPresentationNode> = emptyList()

    fun displayOf(
        serverState: ProxyServerState,
        servers: List<ProxyServerState>,
    ): ProxyServerListItemDisplayText {
        // Only strategy groups and chains describe other servers; a plain server needs none.
        val needsMembers = serverState.server is StrategyGroup || serverState.server is ChainProxy
        return delegate.displayOf(
            serverNode = serverState.toPresentationNode(),
            servers = if (needsMembers) nodesOf(servers) else emptyList(),
        )
    }

    private fun nodesOf(servers: List<ProxyServerState>): List<ProxyServerPresentationNode> {
        if (servers !== nodesSource) {
            nodes = servers.map(ProxyServerState::toPresentationNode)
            nodesSource = servers
        }
        return nodes
    }
}

@Composable
internal fun rememberProxyServerListItemTextFormatter(
    groupNames: Map<Int, String>,
    unknownGroupName: String,
): ProxyServerListItemTextFormatter {
    val labels = ProxyServerPresentationLabels(
        unknownGroupName = unknownGroupName,
        allGroupsName = stringResource(R.string.proxy_editor_strategy_group_all_groups),
        selectName = stringResource(R.string.proxy_editor_strategy_group_select),
        leastPingName = stringResource(R.string.proxy_editor_strategy_group_least_ping),
        leastLoadName = stringResource(R.string.proxy_editor_strategy_group_least_load),
        randomName = stringResource(R.string.proxy_editor_strategy_group_random),
        roundRobinName = stringResource(R.string.proxy_editor_strategy_group_round_robin),
        strategyGroupSummaryTemplate = stringResource(R.string.proxy_server_list_strategy_group_summary),
        strategyGroupSummaryWithFilterTemplate =
            stringResource(R.string.proxy_server_list_strategy_group_summary_with_filter),
        chainProxySummaryTemplate = stringResource(R.string.proxy_server_list_chain_proxy_summary),
    )
    return remember(groupNames, labels) {
        ProxyServerListItemTextFormatter(
            delegate = ProxyServerPresentationFormatter(
                groupNames = groupNames,
                labels = labels,
            ),
        )
    }
}

private fun ProxyServerState.toPresentationNode(): ProxyServerPresentationNode {
    return ProxyServerPresentationNode(
        id = id,
        groupId = groupId,
        server = server,
    )
}
