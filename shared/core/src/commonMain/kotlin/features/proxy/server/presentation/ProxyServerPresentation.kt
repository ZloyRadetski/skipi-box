// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.presentation

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.extractLeadingCountryFlagOrNull
import features.proxy.server.model.getTransportDisplay
import features.proxy.server.model.stripLeadingCountryFlag

/** A host-neutral proxy record used to build Home list text. */
data class ProxyServerPresentationNode(
    val id: Int,
    val groupId: Int,
    val server: ProxyServer<*>,
)

/** Text rendered by a proxy-server list item on Android and desktop. */
data class ProxyServerPresentationText(
    val title: String,
    val summary: String,
    val protocol: String,
    val transport: String? = null,
)

/** Localized terms supplied by the UI host. */
data class ProxyServerPresentationLabels(
    val unknownGroupName: String,
    val allGroupsName: String,
    val selectName: String,
    val leastPingName: String,
    val leastLoadName: String,
    val randomName: String,
    val roundRobinName: String,
    val strategyGroupSummaryTemplate: String,
    val strategyGroupSummaryWithFilterTemplate: String,
    val chainProxySummaryTemplate: String,
)

data class ProxyServerPresentationTitle(
    val flag: String?,
    val title: String,
)

/**
 * Portable presentation logic for proxy Home cards.
 *
 * This deliberately knows only the SKIPI Core proxy model. Platform code owns
 * localization, persistence, latency probing and the actual UI actions.
 */
class ProxyServerPresentationFormatter(
    private val groupNames: Map<Int, String>,
    private val labels: ProxyServerPresentationLabels,
) {
    fun displayOf(
        serverNode: ProxyServerPresentationNode,
        servers: List<ProxyServerPresentationNode>,
    ): ProxyServerPresentationText {
        val info = serverNode.server.getInfo()
        return ProxyServerPresentationText(
            title = info.remarks.ifBlank { info.protocol },
            summary = summaryOf(serverNode, servers),
            protocol = info.protocol,
            transport = serverNode.server.getTransportDisplay(),
        )
    }

    private fun summaryOf(
        serverNode: ProxyServerPresentationNode,
        servers: List<ProxyServerPresentationNode>,
    ): String = when (val proxyServer = serverNode.server) {
        is StrategyGroup -> proxyServer.strategyGroupSummary(servers)
        is ChainProxy -> proxyServer.chainProxySummary(servers)
        else -> proxyServer.getInfo().address
    }

    private fun ChainProxy.chainProxySummary(servers: List<ProxyServerPresentationNode>): String {
        val serverById = servers.associateBy(ProxyServerPresentationNode::id)
        val memberNames = proxyServerIds.mapNotNull { memberId ->
            serverById[memberId]?.server?.getInfo()?.let { info ->
                info.remarks.ifBlank { info.protocol }
            }
        }
        return memberNames
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(" -> ")
            ?: labels.chainProxySummaryTemplate.formatPresentationTemplate("count" to proxyServerIds.size)
    }

    private fun StrategyGroup.strategyGroupSummary(servers: List<ProxyServerPresentationNode>): String {
        if (strategy == StrategyGroupConstants.TYPE_SELECT) {
            val serverById = servers.associateBy(ProxyServerPresentationNode::id)
            val activeMember = selectedMemberId?.let(serverById::get)
                ?: proxyServerIds.firstNotNullOfOrNull(serverById::get)
            val memberCount = if (proxyServerIds.isNotEmpty()) {
                proxyServerIds.size
            } else {
                servers.count { server ->
                    server.groupId == subscriptionGroupId || subscriptionGroupId == null
                }
            }
            return activeMember?.server?.getInfo()?.let { info ->
                val activeName = info.remarks.ifBlank { info.protocol }
                "${labels.selectName}: $activeName ($memberCount)"
            } ?: "${labels.selectName} ($memberCount)"
        }

        val template = if (filter.isBlank()) {
            labels.strategyGroupSummaryTemplate
        } else {
            labels.strategyGroupSummaryWithFilterTemplate
        }
        return template.formatPresentationTemplate(
            "strategy" to strategyDisplayName(),
            "group" to sourceGroupName(),
            "filter" to filter,
        )
    }

    private fun StrategyGroup.strategyDisplayName(): String = when (strategy) {
        StrategyGroupConstants.TYPE_SELECT -> labels.selectName
        StrategyGroupConstants.TYPE_LEAST_PING -> labels.leastPingName
        StrategyGroupConstants.TYPE_LEAST_LOAD -> labels.leastLoadName
        StrategyGroupConstants.TYPE_RANDOM -> labels.randomName
        StrategyGroupConstants.TYPE_ROUND_ROBIN -> labels.roundRobinName
        else -> strategy
    }

    private fun StrategyGroup.sourceGroupName(): String {
        return subscriptionGroupId?.let { groupId ->
            groupNames[groupId] ?: labels.unknownGroupName
        } ?: labels.allGroupsName
    }
}

/**
 * Splits a leading country flag or emoji badge from a title.
 *
 * [emptyTitle] is supplied by the localized host; a title consisting only of
 * the badge remains intact instead of becoming blank.
 */
fun String.toProxyServerPresentationTitle(
    emptyTitle: String = "",
): ProxyServerPresentationTitle {
    val trimmed = trim()
    if (trimmed.isEmpty()) {
        return ProxyServerPresentationTitle(flag = null, title = emptyTitle)
    }
    val prefix = trimmed.substringBefore(' ')
    val flag = extractLeadingCountryFlagOrNull()
        ?: prefix.takeIf { candidate ->
            candidate.any(Char::isSurrogate) || candidate == "⚡"
        }
    return ProxyServerPresentationTitle(
        flag = flag,
        title = if (flag == null) {
            trimmed
        } else if (flag == extractLeadingCountryFlagOrNull()) {
            trimmed.stripLeadingCountryFlag()
        } else {
            trimmed.removePrefix(flag).trim().ifBlank { trimmed }
        },
    )
}

private fun String.formatPresentationTemplate(vararg values: Pair<String, Any?>): String {
    return values.fold(this) { formatted, (key, value) ->
        formatted.replace("{$key}", value.toString())
    }
}
