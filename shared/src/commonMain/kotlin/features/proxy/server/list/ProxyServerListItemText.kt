// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.proxy_editor_strategy_group_all_groups
import app.shared.res.proxy_editor_strategy_group_least_load
import app.shared.res.proxy_editor_strategy_group_least_ping
import app.shared.res.proxy_editor_strategy_group_random
import app.shared.res.proxy_editor_strategy_group_round_robin
import app.shared.res.proxy_editor_strategy_group_select
import app.shared.res.proxy_server_list_chain_proxy_summary
import app.shared.res.proxy_server_list_strategy_group_summary
import app.shared.res.proxy_server_list_strategy_group_summary_with_filter
import features.proxy.server.model.getTransportDisplay
import ui.text.formatTemplate

data class ProxyServerListItemDisplayText(
    val title: String,
    val summary: String,
    val protocol: String,
    val transport: String? = null,
)

class ProxyServerListItemTextFormatter(
    private val groupNames: Map<Int, String>,
    private val unknownGroupName: String,
    private val allGroupsName: String,
    private val selectName: String,
    private val leastPingName: String,
    private val leastLoadName: String,
    private val randomName: String,
    private val roundRobinName: String,
    private val strategyGroupSummaryTemplate: String,
    private val strategyGroupSummaryWithFilterTemplate: String,
    private val chainProxySummaryTemplate: String,
) {
    fun displayOf(
        serverState: ProxyServerState,
        servers: List<ProxyServerState>,
    ): ProxyServerListItemDisplayText {
        val info = serverState.server.getInfo()
        return ProxyServerListItemDisplayText(
            title = info.remarks.ifBlank { info.protocol },
            summary = summaryOf(serverState, servers),
            protocol = info.protocol,
            transport = serverState.server.getTransportDisplay(),
        )
    }

    private fun summaryOf(
        serverState: ProxyServerState,
        servers: List<ProxyServerState>,
    ): String {
        return when (val proxyServer = serverState.server) {
            is StrategyGroup -> {
                if (proxyServer.strategy == StrategyGroupConstants.TYPE_SELECT) {
                    val serverById = servers.associateBy { it.id }
                    val activeMember = proxyServer.selectedMemberId?.let { serverById[it] }
                        ?: proxyServer.proxyServerIds.firstNotNullOfOrNull { serverById[it] }
                    val memberCount = if (proxyServer.proxyServerIds.isNotEmpty()) {
                        proxyServer.proxyServerIds.size
                    } else {
                        servers.count { s -> s.groupId == proxyServer.subscriptionGroupId || proxyServer.subscriptionGroupId == null }
                    }
                    if (activeMember != null) {
                        val activeName = activeMember.server.getInfo().remarks.ifBlank { activeMember.server.getInfo().protocol }
                        "$selectName: $activeName ($memberCount)"
                    } else {
                        "$selectName ($memberCount)"
                    }
                } else {
                    proxyServer.strategyGroupSummary()
                }
            }
            is ChainProxy -> proxyServer.chainProxySummary(servers)
            else -> proxyServer.getInfo().address
        }
    }

    private fun ChainProxy.chainProxySummary(servers: List<ProxyServerState>): String {
        val serverById = servers.associateBy { server -> server.id }
        val memberNames = proxyServerIds.mapNotNull { memberId ->
            serverById[memberId]?.server?.getInfo()?.let { info ->
                info.remarks.ifBlank { info.protocol }
            }
        }
        return memberNames
            .takeIf { names -> names.isNotEmpty() }
            ?.joinToString(" -> ")
            ?: chainProxySummaryTemplate.formatTemplate("count" to proxyServerIds.size)
    }

    private fun StrategyGroup.strategyGroupSummary(): String {
        val template = if (filter.isBlank()) {
            strategyGroupSummaryTemplate
        } else {
            strategyGroupSummaryWithFilterTemplate
        }
        return template.formatTemplate(
            "strategy" to strategyDisplayName(),
            "group" to sourceGroupName(),
            "filter" to filter,
        )
    }

    private fun StrategyGroup.strategyDisplayName(): String {
        return when (strategy) {
            StrategyGroupConstants.TYPE_SELECT -> selectName
            StrategyGroupConstants.TYPE_LEAST_PING -> leastPingName
            StrategyGroupConstants.TYPE_LEAST_LOAD -> leastLoadName
            StrategyGroupConstants.TYPE_RANDOM -> randomName
            StrategyGroupConstants.TYPE_ROUND_ROBIN -> roundRobinName
            else -> strategy
        }
    }

    private fun StrategyGroup.sourceGroupName(): String {
        return subscriptionGroupId?.let { groupId -> groupNames[groupId] ?: unknownGroupName } ?: allGroupsName
    }
}

@Composable
fun rememberProxyServerListItemTextFormatter(
    groupNames: Map<Int, String>,
    unknownGroupName: String,
): ProxyServerListItemTextFormatter {
    val allGroupsName = stringResource(Res.string.proxy_editor_strategy_group_all_groups)
    val selectName = stringResource(Res.string.proxy_editor_strategy_group_select)
    val leastPingName = stringResource(Res.string.proxy_editor_strategy_group_least_ping)
    val leastLoadName = stringResource(Res.string.proxy_editor_strategy_group_least_load)
    val randomName = stringResource(Res.string.proxy_editor_strategy_group_random)
    val roundRobinName = stringResource(Res.string.proxy_editor_strategy_group_round_robin)
    val strategyGroupSummaryTemplate = stringResource(Res.string.proxy_server_list_strategy_group_summary)
    val strategyGroupSummaryWithFilterTemplate =
        stringResource(Res.string.proxy_server_list_strategy_group_summary_with_filter)
    val chainProxySummaryTemplate = stringResource(Res.string.proxy_server_list_chain_proxy_summary)

    return remember(
        groupNames,
        unknownGroupName,
        allGroupsName,
        selectName,
        leastPingName,
        leastLoadName,
        randomName,
        roundRobinName,
        strategyGroupSummaryTemplate,
        strategyGroupSummaryWithFilterTemplate,
        chainProxySummaryTemplate,
    ) {
        ProxyServerListItemTextFormatter(
            groupNames = groupNames,
            unknownGroupName = unknownGroupName,
            allGroupsName = allGroupsName,
            selectName = selectName,
            leastPingName = leastPingName,
            leastLoadName = leastLoadName,
            randomName = randomName,
            roundRobinName = roundRobinName,
            strategyGroupSummaryTemplate = strategyGroupSummaryTemplate,
            strategyGroupSummaryWithFilterTemplate = strategyGroupSummaryWithFilterTemplate,
            chainProxySummaryTemplate = chainProxySummaryTemplate,
        )
    }
}
