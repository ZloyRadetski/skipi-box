// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import features.proxy.server.list.ProxyServerListAddAction
import features.proxy.server.model.ProxyServer
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.server.createProxyServerDraft
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.SubscriptionServerCollectionUpdate
import app.skipi.app.proxy.deleteProxyServerRecords
import app.skipi.app.proxy.importProxyServerRecords
import app.skipi.app.proxy.reconcileSubscriptionServerCollection
import app.skipi.app.proxy.saveProxyServerRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.withRefreshedMetadata
import data.AndroidAppStateStore
import data.repository.reconcileTrafficConfigProxyGroups
import features.config.withImportedTrafficConfig
import features.subscription.SubscriptionMetadata
import features.subscription.DefaultSubscriptionGroupId

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
    if (importResult.servers.isEmpty()) return this
    val result = importProxyServerRecords(
        servers = proxyServers.map { ProxyServerRecord(it.id, it.groupId, it.server, it.latency) },
        imported = importResult.servers,
        groupId = groupId,
        nextServerId = nextProxyServerId,
        selectedServerId = selectedProxyServerId,
    )
    return copy(
        proxyServers = result.servers.map { ProxyServerState(it.id, it.server, it.groupId, it.latency) },
        nextProxyServerId = result.nextServerId,
        selectedProxyServerId = result.selectedServerId,
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
    val result = saveProxyServerRecord(
        servers = proxyServers.map { ProxyServerRecord(it.id, it.groupId, it.server, it.latency) },
        serverId = serverId,
        server = server,
        groupId = groupId,
        nextServerId = nextProxyServerId,
        selectedServerId = selectedProxyServerId,
    )
    val collection = result.collection
    return ProxyServerEditApplyResult(
        state = copy(
            proxyServers = collection.servers.map { ProxyServerState(it.id, it.server, it.groupId, it.latency) },
            nextProxyServerId = collection.nextServerId,
            selectedProxyServerId = collection.selectedServerId,
        ),
        existingGroupId = result.existingGroupId,
        wasExisting = result.wasExisting,
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
    val collection = reconcileSubscriptionServerCollection(
        servers = proxyServers.map { server ->
            ProxyServerRecord(server.id, server.groupId, server.server, server.latency)
        },
        updates = applicableUpdates.map { update ->
            SubscriptionServerCollectionUpdate(
                groupId = update.groupId,
                servers = update.servers,
                autoOverrideRules = subscriptionGroups.firstOrNull { it.id == update.groupId }
                    ?.autoOverrideRules,
            )
        },
        nextServerId = nextProxyServerId,
        selectedServerId = selectedProxyServerId,
    )
    val nextServers = collection.servers.map { server ->
        ProxyServerState(server.id, server.server, server.groupId, server.latency)
    }

    val stateWithUpdatedGroups = copy(
        subscriptionGroups = subscriptionGroups.map { group ->
            val update = applicableUpdatesByGroupId[group.id]
            if (update != null) {
                val refreshed = SubscriptionRecord(
                    id = group.id,
                    title = group.name,
                    url = group.url,
                    metadata = features.subscription.SubscriptionMetadata(
                        profileTitle = group.profileTitle,
                        announce = group.announce,
                        supportUrl = group.supportUrl,
                        supportEmail = group.supportEmail,
                        profileWebPageUrl = group.profileWebPageUrl,
                        announceUrl = group.announceUrl,
                        trafficUploadBytes = group.trafficUploadBytes,
                        trafficDownloadBytes = group.trafficDownloadBytes,
                        trafficTotalBytes = group.trafficTotalBytes,
                        trafficExpireAtSeconds = group.trafficExpireAtSeconds,
                        profileUpdateIntervalHours = group.updateInterval,
                    ),
                ).withRefreshedMetadata(update.metadata, updatedAtMillis)
                group.copy(
                    lastUpdatedAtMillis = refreshed.lastUpdatedAtMillis ?: group.lastUpdatedAtMillis,
                    name = refreshed.title,
                    profileTitle = refreshed.metadata?.profileTitle ?: group.profileTitle,
                    announce = refreshed.metadata?.announce ?: group.announce,
                    supportUrl = refreshed.metadata?.supportUrl ?: group.supportUrl,
                    supportEmail = refreshed.metadata?.supportEmail ?: group.supportEmail,
                    profileWebPageUrl = refreshed.metadata?.profileWebPageUrl ?: group.profileWebPageUrl,
                    announceUrl = refreshed.metadata?.announceUrl ?: group.announceUrl,
                    updateInterval = refreshed.metadata?.profileUpdateIntervalHours ?: group.updateInterval,
                    trafficUploadBytes = refreshed.metadata?.trafficUploadBytes ?: group.trafficUploadBytes,
                    trafficDownloadBytes = refreshed.metadata?.trafficDownloadBytes ?: group.trafficDownloadBytes,
                    trafficTotalBytes = refreshed.metadata?.trafficTotalBytes ?: group.trafficTotalBytes,
                    trafficExpireAtSeconds = refreshed.metadata?.trafficExpireAtSeconds ?: group.trafficExpireAtSeconds,
                )
            } else {
                group
            }
        },
        proxyServers = nextServers,
        nextProxyServerId = collection.nextServerId,
        selectedProxyServerId = collection.selectedServerId,
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

/** Shared-catalog half of subscription refresh; metadata/config state stays in the AppState adapter. */
internal fun reconcileUpdatedSubscriptionProxyRecords(
    servers: List<app.skipi.app.model.ProxyServerRecord>,
    groups: List<SubscriptionGroupState>,
    updates: List<ProxyServerListSubscriptionUpdate>,
    nextServerId: Int,
    selectedServerId: Int,
): ProxyServerCatalog {
    val applicableUpdates = updates.filter { update ->
        groups.any { group -> group.id == update.groupId && group.subscriptionFetchIdentity() == update.sourceIdentity }
    }
    if (applicableUpdates.isEmpty()) return ProxyServerCatalog(servers, nextServerId, selectedServerId)
    val collection = reconcileSubscriptionServerCollection(
        servers = servers.map { record ->
            ProxyServerRecord(
                id = record.id,
                groupId = record.sourceSubscriptionId ?: DefaultSubscriptionGroupId,
                server = record.server,
            )
        },
        updates = applicableUpdates.map { update ->
            SubscriptionServerCollectionUpdate(
                groupId = update.groupId,
                servers = update.servers,
                autoOverrideRules = groups.firstOrNull { it.id == update.groupId }?.autoOverrideRules,
            )
        },
        nextServerId = nextServerId,
        selectedServerId = selectedServerId,
    )
    return ProxyServerCatalog(
        servers = collection.servers.map { record ->
        app.skipi.app.model.ProxyServerRecord(
            id = record.id,
            server = record.server,
            sourceSubscriptionId = record.groupId.takeIf { it != DefaultSubscriptionGroupId },
        )
        },
        nextServerId = collection.nextServerId,
        selectedServerId = collection.selectedServerId,
    )
}

internal suspend fun applyProxySubscriptionUpdates(
    stateStore: AndroidAppStateStore,
    updates: List<ProxyServerListSubscriptionUpdate>,
    updatedAtMillis: Long,
    updateAppState: ((AppState) -> AppState) -> Unit,
) {
    if (updates.isEmpty()) return
    val previous = stateStore.currentState
    stateStore.proxyServerRepository.updateCatalog { current ->
        reconcileUpdatedSubscriptionProxyRecords(
            servers = current.servers,
            groups = stateStore.currentState.subscriptionGroups,
            updates = updates,
            nextServerId = current.nextServerId,
            selectedServerId = current.selectedServerId,
        )
    }
    updateAppState { state ->
        state.withUpdatedSubscriptionServers(updates, updatedAtMillis).copy(
            proxyServers = state.proxyServers,
            nextProxyServerId = state.nextProxyServerId,
            selectedProxyServerId = state.selectedProxyServerId,
        )
    }
    val updated = stateStore.currentState
    if (previous.trafficConfigs != updated.trafficConfigs ||
        previous.activeTrafficConfigId != updated.activeTrafficConfigId
    ) {
        stateStore.reconcileTrafficConfigProxyGroups()
    }
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
    val result = deleteProxyServerRecords(
        servers = proxyServers.map { ProxyServerRecord(it.id, it.groupId, it.server, it.latency) },
        deletedServerIds = deletedServerIds,
        nextServerId = nextProxyServerId,
        selectedServerId = selectedProxyServerId,
        proxyRunning = proxyRunning,
    )
    return copy(
        proxyServers = result.servers.map { ProxyServerState(it.id, it.server, it.groupId, it.latency) },
        selectedProxyServerId = result.selectedServerId,
        proxyRunning = result.proxyRunning,
    )
}

internal fun createProxyServer(action: ProxyServerListAddAction): ProxyServer<*> {
    val kind = when (action) {
        ProxyServerListAddAction.ScanQrCode,
        ProxyServerListAddAction.Clipboard,
        ProxyServerListAddAction.File -> error("Import action cannot create a proxy server")

        ProxyServerListAddAction.Shadowsocks -> ProxyHomeServerKind.Shadowsocks
        ProxyServerListAddAction.ChainProxy -> ProxyHomeServerKind.ChainProxy
        ProxyServerListAddAction.StrategyGroup -> ProxyHomeServerKind.StrategyGroup
        ProxyServerListAddAction.HTTP -> ProxyHomeServerKind.Http
        ProxyServerListAddAction.VMess -> ProxyHomeServerKind.Vmess
        ProxyServerListAddAction.VLESS -> ProxyHomeServerKind.Vless
        ProxyServerListAddAction.Trojan -> ProxyHomeServerKind.Trojan
        ProxyServerListAddAction.Socks -> ProxyHomeServerKind.Socks
        ProxyServerListAddAction.Hysteria2 -> ProxyHomeServerKind.Hysteria2
        ProxyServerListAddAction.Wireguard -> ProxyHomeServerKind.Wireguard
        ProxyServerListAddAction.AmneziaWg -> ProxyHomeServerKind.AmneziaWg
        ProxyServerListAddAction.OlcRtc -> ProxyHomeServerKind.OlcRtc
        ProxyServerListAddAction.Custom -> ProxyHomeServerKind.Custom
    }
    return createProxyServerDraft(kind)
}

private fun AppState.selectedProxyServerIdOrFirstAvailable(nextServers: List<ProxyServerState>): Int {
    return if (nextServers.any { server -> server.id == selectedProxyServerId }) {
        selectedProxyServerId
    } else {
        nextServers.firstOrNull()?.id ?: selectedProxyServerId
    }
}
