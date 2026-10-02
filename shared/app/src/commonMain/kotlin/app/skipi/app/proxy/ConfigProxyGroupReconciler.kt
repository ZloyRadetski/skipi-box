// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.canBeUsedInGeneratedProxyPlan
import features.proxy.server.model.isCompositeProxyServer
import features.proxy.server.model.toStrategyGroupTypeFromShadowrocketPolicy

/** The portable result of materializing profile-owned proxy groups. */
data class ConfigProxyGroupReconciliation(
    val servers: List<ProxyServerRecord>,
    val nextServerId: Int,
    val selectedServerId: Int,
)

/**
 * Reconciles visible `[Proxy Group]` entries into proxy records while keeping
 * profile content authoritative and preserving stable IDs and user selection.
 *
 * [stripLeadingCountryFlag] is supplied by the host so this layer has no
 * dependency on app display utilities.
 */
fun reconcileConfigProxyGroups(
    trafficConfigs: List<TrafficConfigState>,
    activeTrafficConfigId: Int,
    servers: List<ProxyServerRecord>,
    nextServerId: Int,
    selectedServerId: Int,
    autoBalancerGroupId: Int,
    stripLeadingCountryFlag: (String) -> String,
): ConfigProxyGroupReconciliation {
    val desired = trafficConfigs.flatMap { config ->
        val isConfigActive = config.id == activeTrafficConfigId
        config.rawConfig.analyzeShadowrocketConfig().proxyGroups
            .filter { group ->
                when (group.displayMode) {
                    StrategyGroupDisplayMode.ALWAYS -> true
                    StrategyGroupDisplayMode.NEVER -> false
                    StrategyGroupDisplayMode.ACTIVE_CONFIG -> isConfigActive
                    else -> isConfigActive
                }
            }
            .map { ConfigAutoBalancerSource(config.id, it) }
    }
    val existingGenerated = servers.filter { record ->
        (record.server as? StrategyGroup)?.sourceTrafficConfigId != null
    }
    val regularServers = servers.filterNot(existingGenerated::contains)

    val serverIdsByRemark = HashMap<String, MutableList<Int>>(regularServers.size * 2)
    regularServers.forEach { candidate ->
        val remarks = candidate.server.getInfo().remarks.trim().lowercase()
        val cleanRemarks = stripLeadingCountryFlag(remarks).trim().lowercase()
        serverIdsByRemark.getOrPut(remarks) { mutableListOf() }.add(candidate.id)
        if (cleanRemarks != remarks) {
            serverIdsByRemark.getOrPut(cleanRemarks) { mutableListOf() }.add(candidate.id)
        }
    }

    var nextId = nextServerId
    val generatedServers = desired.map { source ->
        val existing = existingGenerated.firstOrNull { record ->
            val strategy = record.server as StrategyGroup
            strategy.sourceTrafficConfigId == source.configId &&
                strategy.sourcePolicyGroupName.equals(source.group.name, ignoreCase = true)
        }
        val id = existing?.id ?: nextId++
        val existingStrategy = existing?.server as? StrategyGroup
        val resolvedMemberIds = source.group.members.flatMap { rawMember ->
            val cleanMember = rawMember.trim().removeSurrounding("\"").removeSurrounding("'").trim().lowercase()
            val cleanWithoutFlag = stripLeadingCountryFlag(cleanMember).trim().lowercase()
            when {
                cleanMember == ".*" -> regularServers.filter { record ->
                    !record.server.isCompositeProxyServer() &&
                        (record.server !is Custom || record.server.canBeUsedInGeneratedProxyPlan())
                }.map { it.id }
                else -> serverIdsByRemark[cleanMember] ?: serverIdsByRemark[cleanWithoutFlag].orEmpty()
            }
        }.distinct()
        val effectiveMemberIds = resolvedMemberIds.ifEmpty {
            existingStrategy?.proxyServerIds.orEmpty()
        }
        val effectiveSelectedMemberId = existingStrategy?.selectedMemberId
            ?.takeIf { memberId -> memberId in effectiveMemberIds || effectiveMemberIds.isEmpty() }
            ?: effectiveMemberIds.firstOrNull()

        ProxyServerRecord(
            id = id,
            groupId = autoBalancerGroupId,
            latency = existing?.latency.orEmpty(),
            server = StrategyGroup(
                remarks = source.group.name,
                strategy = source.group.type.toStrategyGroupTypeFromShadowrocketPolicy(),
                proxyServerIds = effectiveMemberIds,
                selectedMemberId = effectiveSelectedMemberId,
                displayMode = source.group.displayMode,
                showInAutoBalancerList = source.group.displayMode != StrategyGroupDisplayMode.NEVER,
                sourceTrafficConfigId = source.configId,
                sourcePolicyGroupName = source.group.name,
                probeInterval = source.group.intervalSeconds?.let { "${it}s" } ?: "1m",
                probeTimeout = source.group.timeoutSeconds?.let { "${it}s" } ?: existingStrategy?.probeTimeout ?: "5s",
                probeUrl = source.group.url,
                enableBurstProbe = source.group.enableBurstProbe,
                tolerance = source.group.tolerance,
            ),
        )
    }
    val resolvedServers = regularServers + generatedServers
    val resolvedSelectedId = selectedServerId.takeIf { selectedId ->
        resolvedServers.any { record -> record.id == selectedId }
    } ?: resolvedServers.firstOrNull()?.id ?: selectedServerId

    return ConfigProxyGroupReconciliation(
        servers = resolvedServers,
        nextServerId = maxOf(nextServerId, nextId),
        selectedServerId = resolvedSelectedId,
    )
}

private data class ConfigAutoBalancerSource(
    val configId: Int,
    val group: features.config.ShadowrocketPolicyGroup,
)
