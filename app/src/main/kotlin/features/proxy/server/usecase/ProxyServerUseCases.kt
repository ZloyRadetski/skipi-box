// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import features.proxy.server.list.ProxyServerListAddAction
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import features.proxy.server.model.isCompositeProxyServer
import features.config.withImportedTrafficConfig
import features.subscription.SubscriptionMetadata
import features.subscription.SubscriptionServerCandidate
import features.subscription.reconcileSubscriptionServers

internal data class ResolvedEmbeddedTrafficConfig(
    val content: String,
    val sourceUrl: String = "",
    val fallbackName: String = "Config",
    val activate: Boolean = false,
)

internal data class ProxyServerListSubscriptionUpdate(
    val groupId: Int,
    val sourceIdentity: SubscriptionGroupFetchIdentity,
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
    val metadata: SubscriptionMetadata = SubscriptionMetadata(),
    val resolvedConfig: ResolvedEmbeddedTrafficConfig? = null,
)

internal data class SubscriptionGroupFetchIdentity(
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val ageSecretKey: String,
    val updateViaProxy: Boolean,
    val enabled: Boolean,
)

internal fun SubscriptionGroupState.subscriptionFetchIdentity(): SubscriptionGroupFetchIdentity {
    return SubscriptionGroupFetchIdentity(
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        enabled = enabled,
    )
}

internal data class ProxyServerListSubscriptionFailure(
    val groupId: Int,
    val error: Throwable,
)

internal data class ProxyServerListSubscriptionUpdateResult(
    val updates: List<ProxyServerListSubscriptionUpdate>,
    val failures: List<ProxyServerListSubscriptionFailure>,
    val updatedAtMillis: Long,
) {
    val updatedGroupCount: Int = updates.size
    val failedGroupCount: Int = failures.size
    val importedServerCount: Int = updates.sumOf { update -> update.servers.size }
}

internal data class ProxyServerListDuplicateDeleteResult(
    val servers: List<ProxyServerState>,
    val removedCount: Int,
)

internal data class ProxyServerListInvalidDeleteResult(
    val servers: List<ProxyServerState>,
    val removedCount: Int,
    val removedServerIds: Set<Int>,
)

internal fun AppState.withImportedProxyServers(
    importResult: ProxyServerImportResult,
    groupId: Int,
): AppState {
    if (importResult.servers.isEmpty()) {
        return this
    }
    var nextServerId = nextProxyServerId
    val importedServers = importResult.servers.map { server ->
        ProxyServerState(
            id = nextServerId++,
            groupId = groupId,
            server = server,
        )
    }
    val nextServers = importedServers + proxyServers
    return copy(
        proxyServers = nextServers,
        nextProxyServerId = maxOf(nextProxyServerId, nextServerId),
        selectedProxyServerId = selectedProxyServerIdOrFirstAvailable(nextServers),
    )
}

internal data class ProxyServerEditApplyResult(
    val state: AppState,
    val existingGroupId: Int?,
    val wasExisting: Boolean,
)

internal fun AppState.withSavedProxyServer(
    serverId: Int,
    server: ProxyServer<*>,
    groupId: Int?,
): ProxyServerEditApplyResult {
    val index = proxyServers.indexOfFirst { it.id == serverId }
    val wasExisting = index >= 0
    var existingGroupId = groupId
    val nextServers = if (index >= 0) {
        proxyServers.toMutableList().also { list ->
            val oldServer = list[index]
            existingGroupId = oldServer.groupId
            list[index] = oldServer.copy(server = server)
        }
    } else if (groupId != null) {
        listOf(
            ProxyServerState(
                id = serverId,
                groupId = groupId,
                server = server,
            ),
        ) + proxyServers
    } else {
        proxyServers
    }
    return ProxyServerEditApplyResult(
        state = copy(
            proxyServers = nextServers,
            nextProxyServerId = maxOf(nextProxyServerId, serverId + 1),
            selectedProxyServerId = selectedProxyServerIdOrFirstAvailable(nextServers),
        ),
        existingGroupId = existingGroupId,
        wasExisting = wasExisting,
    )
}

internal fun AppState.withUpdatedSubscriptionServers(
    updates: List<ProxyServerListSubscriptionUpdate>,
    updatedAtMillis: Long,
): AppState {
    val applicableUpdates = updates.filter { update ->
        subscriptionGroups.any { group ->
            group.id == update.groupId &&
                group.subscriptionFetchIdentity() == update.sourceIdentity
        }
    }
    if (applicableUpdates.isEmpty()) {
        return this
    }
    val applicableUpdatesByGroupId = applicableUpdates.associateBy { update -> update.groupId }
    val updatedGroupIds = applicableUpdates.map { update -> update.groupId }.toSet()
    var nextServerId = nextProxyServerId

    // Keep IDs for equivalent downloaded endpoints using connectionFingerprint().
    // Custom strategy groups use those IDs as references, so this prevents a subscription
    // refresh from silently emptying a user-created balancer even if remarks / names change.
    val existingDownloadedServersByGroup = proxyServers
        .filter { server -> server.groupId in updatedGroupIds && !server.server.isCompositeProxyServer() }
        .groupBy { server -> server.groupId }

    val oldIdToNewId = mutableMapOf<Int, Int>()

    val importedServers = applicableUpdates.flatMap { update ->
        val candidates = existingDownloadedServersByGroup[update.groupId].orEmpty()
        val candidatesById = candidates.associateBy(ProxyServerState::id)
        val reconciliation = reconcileSubscriptionServers(
            previous = candidates.map { candidate ->
                SubscriptionServerCandidate(id = candidate.id, server = candidate.server)
            },
            incoming = update.servers,
            firstNewServerId = nextServerId,
        )
        nextServerId = reconciliation.nextServerId
        oldIdToNewId.putAll(reconciliation.oldIdToNewId)
        val group = subscriptionGroups.firstOrNull { it.id == update.groupId }

        reconciliation.servers.map { reconciled ->
            val preserved = reconciled.matchedPreviousId?.let { id -> candidatesById[id] }
            val newServer = reconciled.server
            if (newServer is Custom && group != null) {
                newServer.overrideInboundAndDns = group.autoOverrideRules
            }

            ProxyServerState(
                id = reconciled.id,
                groupId = update.groupId,
                server = newServer,
                latency = preserved?.latency.orEmpty(),
            )
        }
    }

    // Preserve and sanitize composite proxy servers (strategy groups, chain proxies)
    val existingCompositeServers = proxyServers.filter { server ->
        server.server.isCompositeProxyServer()
    }
    val otherServers = proxyServers.filterNot { server ->
        server.groupId in updatedGroupIds || server.server.isCompositeProxyServer()
    }

    val validServerIds = (importedServers.map { it.id } + otherServers.map { it.id } + existingCompositeServers.map { it.id }).toSet()

    val updatedCompositeServers = existingCompositeServers.map { server ->
        when (val composite = server.server) {
            is StrategyGroup -> {
                val currentIds = composite.proxyServerIds
                if (currentIds.isNotEmpty()) {
                    val remappedIds = currentIds.map { oldIdToNewId[it] ?: it }
                    val filteredIds = remappedIds.filter { it in validServerIds }
                    val finalIds = if (filteredIds.isNotEmpty()) {
                        filteredIds
                    } else if (importedServers.isNotEmpty()) {
                        importedServers.take(currentIds.size).map { it.id }
                    } else {
                        currentIds
                    }
                    if (finalIds != currentIds) {
                        composite.proxyServerIds = finalIds
                    }
                }
                val selectedId = composite.selectedMemberId
                if (selectedId != null) {
                    val remappedSelectedId = oldIdToNewId[selectedId] ?: selectedId
                    if (remappedSelectedId in validServerIds) {
                        composite.selectedMemberId = remappedSelectedId
                    } else if (composite.proxyServerIds.isNotEmpty()) {
                        composite.selectedMemberId = composite.proxyServerIds.first()
                    }
                }
                server
            }
            is ChainProxy -> {
                val currentIds = composite.proxyServerIds
                if (currentIds.isNotEmpty()) {
                    val remappedIds = currentIds.map { oldIdToNewId[it] ?: it }
                    val filteredIds = remappedIds.filter { it in validServerIds }
                    if (filteredIds != currentIds) {
                        composite.proxyServerIds = filteredIds
                    }
                }
                server
            }
            else -> server
        }
    }

    val nextServers = importedServers + otherServers + updatedCompositeServers
    val selectedServerId = when {
        nextServers.any { server -> server.id == selectedProxyServerId } -> selectedProxyServerId
        else -> proxyServers.firstOrNull { server -> server.groupId !in updatedGroupIds }?.id
            ?: nextServers.firstOrNull()?.id
            ?: selectedProxyServerId
    }

    val stateWithUpdatedGroups = copy(
        subscriptionGroups = subscriptionGroups.map { group ->
            val update = applicableUpdatesByGroupId[group.id]
            if (update != null) {
                group.copy(
                    lastUpdatedAtMillis = updatedAtMillis,
                    name = update.metadata.profileTitle
                        ?.takeIf(String::isNotBlank)
                        ?: group.name,
                    profileTitle = update.metadata.profileTitle ?: group.profileTitle,
                    announce = update.metadata.announce ?: group.announce,
                    supportUrl = update.metadata.supportUrl ?: group.supportUrl,
                    supportEmail = update.metadata.supportEmail ?: group.supportEmail,
                    profileWebPageUrl = update.metadata.profileWebPageUrl ?: group.profileWebPageUrl,
                    announceUrl = update.metadata.announceUrl ?: group.announceUrl,
                    updateInterval = update.metadata.profileUpdateIntervalHours ?: group.updateInterval,
                    trafficUploadBytes = if (update.metadata.userInfoReceived) {
                        update.metadata.trafficUploadBytes
                    } else {
                        group.trafficUploadBytes
                    },
                    trafficDownloadBytes = if (update.metadata.userInfoReceived) {
                        update.metadata.trafficDownloadBytes
                    } else {
                        group.trafficDownloadBytes
                    },
                    trafficTotalBytes = if (update.metadata.userInfoReceived) {
                        update.metadata.trafficTotalBytes
                    } else {
                        group.trafficTotalBytes
                    },
                    trafficExpireAtSeconds = if (update.metadata.userInfoReceived) {
                        update.metadata.trafficExpireAtSeconds
                    } else {
                        group.trafficExpireAtSeconds
                    },
                )
            } else {
                group
            }
        },
        proxyServers = nextServers,
        nextProxyServerId = maxOf(nextProxyServerId, nextServerId),
        selectedProxyServerId = selectedServerId,
    )

    var finalState = stateWithUpdatedGroups
    applicableUpdatesByGroupId.values.forEach { update ->
        val config = update.resolvedConfig
        if (config != null && config.content.isNotBlank()) {
            finalState = runCatching {
                finalState.withImportedTrafficConfig(
                    content = config.content,
                    activate = config.activate,
                    fallbackName = config.fallbackName,
                    sourceUrl = config.sourceUrl,
                )
            }.getOrDefault(finalState)
        }
    }

    return finalState
}

internal fun List<SubscriptionGroupState>.updatableSubscriptionGroups(): List<SubscriptionGroupState> {
    return filter { group ->
        group.enabled && group.url.isNotBlank()
    }
}

internal fun List<ProxyServerState>.deleteDuplicateServersInGroup(
    currentGroupServerIds: Set<Int>,
    selectedProxyServerId: Int,
): ProxyServerListDuplicateDeleteResult {
    val duplicateServerIds = ProxyServerGroupCleanup.duplicateServerIds(
        servers = map { server -> ProxyServerGroupCleanupRecord(server.id, server.server) },
        currentGroupServerIds = currentGroupServerIds,
        selectedServerId = selectedProxyServerId,
    )

    return ProxyServerListDuplicateDeleteResult(
        servers = if (duplicateServerIds.isEmpty()) {
            this
        } else {
            filterNot { server -> server.id in duplicateServerIds }
        },
        removedCount = duplicateServerIds.size,
    )
}

internal fun List<ProxyServerState>.deleteInvalidServersInGroup(
    currentGroupServerIds: Set<Int>,
): ProxyServerListInvalidDeleteResult {
    val invalidServerIds = ProxyServerGroupCleanup.invalidServerIds(
        servers = map { server -> ProxyServerGroupCleanupRecord(server.id, server.server) },
        currentGroupServerIds = currentGroupServerIds,
    )

    return ProxyServerListInvalidDeleteResult(
        servers = if (invalidServerIds.isEmpty()) {
            this
        } else {
            filterNot { server -> server.id in invalidServerIds }
        },
        removedCount = invalidServerIds.size,
        removedServerIds = invalidServerIds,
    )
}

internal fun AppState.withDeletedProxyServers(deletedServerIds: Set<Int>): AppState {
    if (deletedServerIds.isEmpty()) return this
    val nextServers = proxyServers
        .filterNot { server -> server.id in deletedServerIds }
        .map { state ->
            when (val server = state.server) {
                is StrategyGroup -> {
                    val remaining = server.proxyServerIds.filterNot { it in deletedServerIds }
                    if (remaining != server.proxyServerIds) {
                        server.proxyServerIds = remaining
                    }
                    if (server.selectedMemberId in deletedServerIds) {
                        server.selectedMemberId = remaining.firstOrNull()
                    }
                    state
                }
                is ChainProxy -> {
                    val remaining = server.proxyServerIds.filterNot { it in deletedServerIds }
                    if (remaining != server.proxyServerIds) {
                        server.proxyServerIds = remaining
                    }
                    state
                }
                else -> state
            }
        }
    val selectedServerDeleted = selectedProxyServerId in deletedServerIds
    return copy(
        proxyServers = nextServers,
        selectedProxyServerId = if (selectedServerDeleted) {
            nextServers.firstOrNull()?.id ?: selectedProxyServerId
        } else {
            selectedProxyServerId
        },
        proxyRunning = proxyRunning && !selectedServerDeleted,
    )
}

internal fun createProxyServer(action: ProxyServerListAddAction): ProxyServer<*> {
    return when (action) {
        ProxyServerListAddAction.ScanQrCode,
        ProxyServerListAddAction.Clipboard,
        ProxyServerListAddAction.File -> error("Import action cannot create a proxy server")

        ProxyServerListAddAction.Shadowsocks -> Shadowsocks(port = "")

        ProxyServerListAddAction.ChainProxy -> ChainProxy()

        ProxyServerListAddAction.StrategyGroup -> StrategyGroup()

        ProxyServerListAddAction.HTTP -> HTTP(port = "")

        ProxyServerListAddAction.VMess -> VMess(port = "")

        ProxyServerListAddAction.VLESS -> VLESS()

        ProxyServerListAddAction.Trojan -> Trojan(port = "")

        ProxyServerListAddAction.Socks -> Socks(port = "")

        ProxyServerListAddAction.Hysteria2 -> Hysteria2(port = "")

        ProxyServerListAddAction.Wireguard -> Wireguard(port = "", reserved = "", address = "", mtu = "")

        ProxyServerListAddAction.AmneziaWg -> AmneziaWg(
            server = "",
            port = "",
            secretKey = "",
            publicKey = "",
            preSharedKey = "",
            reserved = "",
            address = "",
            mtu = "",
        )

        ProxyServerListAddAction.OlcRtc -> OlcRtc()

        ProxyServerListAddAction.Custom -> Custom()
    }
}

private fun AppState.selectedProxyServerIdOrFirstAvailable(nextServers: List<ProxyServerState>): Int {
    return if (nextServers.any { server -> server.id == selectedProxyServerId }) {
        selectedProxyServerId
    } else {
        nextServers.firstOrNull()?.id ?: selectedProxyServerId
    }
}
