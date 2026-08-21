// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import features.proxy.server.list.ProxyServerListAddAction
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.Trojan
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import features.proxy.server.model.getUrlOrNull
import features.proxy.server.model.isCompositeProxyServer
import features.config.withImportedTrafficConfig
import features.subscription.SubscriptionMetadata

internal data class ResolvedEmbeddedTrafficConfig(
    val content: String,
    val sourceUrl: String = "",
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

private data class CandidateIndexEntry(
    val state: ProxyServerState,
    val fingerprint: String,
    val remarks: String,
    val endpointKey: String?,
    val index: Int,
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
        val candidateEntries = candidates.mapIndexed { index, candidate ->
            CandidateIndexEntry(
                state = candidate,
                fingerprint = candidate.server.connectionFingerprint(),
                remarks = candidate.server.getInfo().remarks.trim(),
                endpointKey = candidate.server.endpointKey(),
                index = index,
            )
        }
        val byFingerprint = mutableMapOf<String, MutableList<CandidateIndexEntry>>()
        val byEndpoint = mutableMapOf<String, MutableList<CandidateIndexEntry>>()
        for (entry in candidateEntries) {
            byFingerprint.getOrPut(entry.fingerprint) { mutableListOf() }.add(entry)
            entry.endpointKey?.let { endpoint ->
                byEndpoint.getOrPut(endpoint) { mutableListOf() }.add(entry)
            }
        }

        val consumedIds = mutableSetOf<Int>()

        update.servers.mapIndexed { index, newServer ->
            val newFingerprint = newServer.connectionFingerprint()
            val newRemarks = newServer.getInfo().remarks.trim()
            val newEndpoint = newServer.endpointKey()

            // 1. Primary: match by exact canonical connection fingerprint (ignoring remarks/name)
            val fingerprintMatches = byFingerprint[newFingerprint]?.filter { it.state.id !in consumedIds }.orEmpty()

            var preservedEntry: CandidateIndexEntry? = when {
                fingerprintMatches.isEmpty() -> null
                fingerprintMatches.size == 1 -> fingerprintMatches.first()
                else -> {
                    // Among multiple candidates with identical fingerprints, pick closest remarks
                    fingerprintMatches.firstOrNull { it.remarks == newRemarks }
                        ?: fingerprintMatches.firstOrNull {
                            it.remarks.contains(newRemarks, ignoreCase = true) || newRemarks.contains(it.remarks, ignoreCase = true)
                        }
                        ?: fingerprintMatches.first()
                }
            }

            // 2. Secondary fallback: match by (protocol + host:port) if stream parameters slightly changed
            if (preservedEntry == null && newEndpoint != null) {
                val endpointMatches = byEndpoint[newEndpoint]?.filter { it.state.id !in consumedIds }.orEmpty()
                preservedEntry = when {
                    endpointMatches.isEmpty() -> null
                    endpointMatches.size == 1 -> endpointMatches.first()
                    else -> {
                        endpointMatches.firstOrNull { it.remarks == newRemarks }
                            ?: endpointMatches.first()
                    }
                }
            }

            // 3. Tertiary fallback: match by position index within group if class types match
            if (preservedEntry == null && index < candidateEntries.size) {
                val candidateAtSlot = candidateEntries[index]
                if (candidateAtSlot.state.id !in consumedIds && candidateAtSlot.state.server::class == newServer::class) {
                    preservedEntry = candidateAtSlot
                }
            }

            val preserved = preservedEntry?.state
            val assignedId: Int
            if (preserved != null) {
                consumedIds += preserved.id
                assignedId = preserved.id
                oldIdToNewId[preserved.id] = assignedId
            } else {
                assignedId = nextServerId++
                val fallbackCandidate = candidateEntries.getOrNull(index)?.takeIf { it.state.id !in consumedIds }
                    ?: candidateEntries.firstOrNull { it.state.id !in consumedIds }
                if (fallbackCandidate != null) {
                    consumedIds += fallbackCandidate.state.id
                    oldIdToNewId[fallbackCandidate.state.id] = assignedId
                }
            }

            val group = subscriptionGroups.firstOrNull { it.id == update.groupId }
            if (newServer is Custom && group != null) {
                newServer.overrideInboundAndDns = group.autoOverrideRules
            }

            ProxyServerState(
                id = assignedId,
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
                    sourceUrl = config.sourceUrl,
                )
            }.getOrDefault(finalState)
        }
    }

    return finalState
}

private fun ProxyServer<*>.endpointKey(): String? {
    val info = getInfo()
    val addr = info.address.trim().lowercase()
    if (addr.isBlank() || addr == ":0" || addr == "0") return null
    return "${info.protocol.lowercase()}|$addr"
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
    val keptServerIdsByUrl = mutableMapOf<String, Int>()
    val duplicateServerIds = mutableSetOf<Int>()
    forEach { server ->
        val url = runCatching { server.server.getUrlOrNull() }.getOrNull()
        if (server.id in currentGroupServerIds && url != null) {
            val keptServerId = keptServerIdsByUrl[url]
            if (keptServerId == null) {
                keptServerIdsByUrl[url] = server.id
            } else if (server.id == selectedProxyServerId) {
                duplicateServerIds += keptServerId
                keptServerIdsByUrl[url] = server.id
            } else {
                duplicateServerIds += server.id
            }
        }
    }

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
    val invalidServerIds = asSequence()
        .filter { server -> server.id in currentGroupServerIds }
        .filter { server -> server.server.validateFull().isNotEmpty() }
        .map { server -> server.id }
        .toSet()

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
