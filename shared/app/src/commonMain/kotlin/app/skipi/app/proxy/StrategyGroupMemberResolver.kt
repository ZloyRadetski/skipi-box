// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.config.ShadowrocketPolicyGroup
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.effectiveFilterGroupIds
import features.proxy.server.model.effectiveWholeGroupIds
import features.proxy.server.model.isCompositeProxyServer

/** Resolves the currently selectable concrete members of a strategy group. */
fun resolveStrategyGroupMembers(
    strategyGroup: StrategyGroup,
    servers: List<ProxyServerRow>,
    trafficConfigContentById: Map<Int, String> = emptyMap(),
    stripLeadingFlag: (String) -> String = { it },
    visitingStrategyGroupIds: Set<Int> = emptySet(),
): List<ProxyServerRow> {
    val explicitMembers = strategyGroup.proxyServerIds.flatMap { memberId ->
        val member = servers.firstOrNull { it.id == memberId }
        when (val server = member?.server) {
            is StrategyGroup -> if (member.id in visitingStrategyGroupIds) emptyList()
                else resolveStrategyGroupMembers(
                    server,
                    servers,
                    trafficConfigContentById,
                    stripLeadingFlag,
                    visitingStrategyGroupIds + member.id,
                )
            null, is ChainProxy -> emptyList()
            is Custom -> member.takeIf { server.canBeUsedInGeneratedProxyPlan() }?.let(::listOf).orEmpty()
            else -> listOf(member)
        }
    }.distinctBy(ProxyServerRow::id)

    if (strategyGroup.sourceTrafficConfigId != null) {
        if (explicitMembers.isNotEmpty()) return explicitMembers
        val configId = strategyGroup.sourceTrafficConfigId
        val sourceGroups = trafficConfigContentById[configId]
            ?.analyzeShadowrocketConfig()
            ?.proxyGroups
            .orEmpty()
        sourceGroups.firstOrNull { it.name.equals(strategyGroup.sourcePolicyGroupName, ignoreCase = true) }
            ?.let { source ->
                return resolveShadowrocketPolicyGroupMembers(
                    source,
                    sourceGroups,
                    servers,
                    trafficConfigContentById,
                    stripLeadingFlag,
                    emptySet(),
                )
            }
    }

    // Picked servers, whole subscriptions and regex matches add up. The last two are looked up here, on every
    // resolution, so a subscription refresh that brings in servers puts them in the group without any edit.
    fun ProxyServerRow.isSelectable(): Boolean =
        !server.isCompositeProxyServer() && (server !is Custom || server.canBeUsedInGeneratedProxyPlan())

    val wholeGroupIds = strategyGroup.effectiveWholeGroupIds().toSet()
    val wholeGroupMembers = if (wholeGroupIds.isEmpty()) emptyList() else {
        servers.filter { row -> row.isSelectable() && row.groupId in wholeGroupIds }
    }

    val filter = strategyGroup.filter
    val filterGroupIds = strategyGroup.effectiveFilterGroupIds().toSet()
    val regex = filter.takeIf(String::isNotBlank)?.let { runCatching { Regex(it) }.getOrNull() }
    val filteredMembers = if (filter.isBlank()) emptyList() else {
        servers.filter { row ->
            val remarks = row.server.getInfo().remarks
            row.isSelectable() &&
                (filterGroupIds.isEmpty() || row.groupId in filterGroupIds) &&
                (regex?.containsMatchIn(remarks) ?: remarks.contains(filter))
        }
    }
    return (explicitMembers + wholeGroupMembers + filteredMembers).distinctBy(ProxyServerRow::id)
}

fun resolveShadowrocketPolicyGroupMembers(
    group: ShadowrocketPolicyGroup,
    policyGroups: List<ShadowrocketPolicyGroup>,
    servers: List<ProxyServerRow>,
    trafficConfigContentById: Map<Int, String>,
    stripLeadingFlag: (String) -> String,
    visitingPolicyGroupNames: Set<String> = emptySet(),
): List<ProxyServerRow> {
    if (group.name in visitingPolicyGroupNames) return emptyList()
    val matchingStrategy = servers.firstOrNull { row ->
        (row.server as? StrategyGroup)?.sourcePolicyGroupName?.equals(group.name, ignoreCase = true) == true
    }?.let { it.server as? StrategyGroup }
    if (matchingStrategy != null && matchingStrategy.proxyServerIds.isNotEmpty()) {
        return resolveStrategyGroupMembers(
            matchingStrategy,
            servers,
            trafficConfigContentById,
            stripLeadingFlag,
        )
    }

    return group.members.flatMap { rawMember ->
        val member = rawMember.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (member == ".*") {
            servers.filter { row ->
                !row.server.isCompositeProxyServer() &&
                    (row.server !is Custom || row.server.canBeUsedInGeneratedProxyPlan())
            }
        } else {
            val matchingServers = servers.filter { row ->
                val remarks = row.server.getInfo().remarks.trim()
                val cleanRemarks = stripLeadingFlag(remarks).trim()
                val cleanMember = stripLeadingFlag(member).trim()
                (remarks.equals(member, ignoreCase = true) ||
                    cleanRemarks.equals(member, ignoreCase = true) ||
                    cleanRemarks.equals(cleanMember, ignoreCase = true) ||
                    remarks.equals(cleanMember, ignoreCase = true)) &&
                    row.server !is ChainProxy &&
                    (row.server !is Custom || row.server.canBeUsedInGeneratedProxyPlan())
            }
            matchingServers.flatMap { matching ->
                when (val proxy = matching.server) {
                    is StrategyGroup -> resolveStrategyGroupMembers(
                        proxy,
                        servers,
                        trafficConfigContentById,
                        stripLeadingFlag,
                        emptySet(),
                    )
                    else -> matching.takeUnless { it.server.isCompositeProxyServer() }?.let(::listOf).orEmpty()
                }
            }.ifEmpty {
                policyGroups.firstOrNull { it.name.equals(member, ignoreCase = true) }?.let { nested ->
                    resolveShadowrocketPolicyGroupMembers(
                        nested,
                        policyGroups,
                        servers,
                        trafficConfigContentById,
                        stripLeadingFlag,
                        visitingPolicyGroupNames + group.name,
                    )
                }.orEmpty()
            }
        }
    }.distinctBy(ProxyServerRow::id)
}
