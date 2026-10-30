// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
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
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshBatchCommitPort
import app.skipi.app.subscription.SubscriptionRefreshBatchReconciliationResult
import app.skipi.app.subscription.SubscriptionRefreshCommitPort
import app.skipi.app.subscription.SubscriptionRefreshReconciliationResult
import app.skipi.app.subscription.SubscriptionRefreshRequestIdentity
import app.skipi.app.subscription.SubscriptionRefreshSnapshot
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.commitSubscriptionRefreshBatch
import app.skipi.app.subscription.commitSubscriptionRefresh
import app.skipi.app.subscription.reconcileSubscriptionRefresh
import app.skipi.app.subscription.eligibleSubscriptionRefreshRequests
import app.skipi.app.subscription.withRefreshedMetadata
import data.repository.toAndroidGroup
import data.repository.toSubscriptionRecord
import data.repository.withProxyServerCatalog
import features.config.withImportedTrafficConfig
import features.subscription.SubscriptionMetadata
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.CancellationException

data class ResolvedEmbeddedTrafficConfig(
    val content: String,
    val sourceUrl: String = "",
    val fallbackName: String = "Config",
    val activate: Boolean = false,
)

data class ProxyServerListSubscriptionUpdate(
    val groupId: Int,
    val sourceIdentity: SubscriptionGroupFetchIdentity,
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
    val metadata: SubscriptionMetadata = SubscriptionMetadata(),
    val resolvedConfig: ResolvedEmbeddedTrafficConfig? = null,
)

data class SubscriptionGroupFetchIdentity(
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val ageSecretKey: String,
    val updateViaProxy: Boolean,
    val enabled: Boolean,
)

fun SubscriptionGroupState.subscriptionFetchIdentity(): SubscriptionGroupFetchIdentity {
    return SubscriptionGroupFetchIdentity(
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        enabled = enabled,
    )
}

data class ProxyServerListSubscriptionFailure(
    val groupId: Int,
    val error: Throwable,
)

data class ProxyServerListSubscriptionUpdateResult(
    val updates: List<ProxyServerListSubscriptionUpdate>,
    val failures: List<ProxyServerListSubscriptionFailure>,
    val updatedAtMillis: Long,
) {
    val updatedGroupCount: Int = updates.size
    val failedGroupCount: Int = failures.size
    val importedServerCount: Int = updates.sumOf { update -> update.servers.size }
}

data class ProxyServerListDuplicateDeleteResult(
    val servers: List<ProxyServerState>,
    val removedCount: Int,
)

data class ProxyServerListInvalidDeleteResult(
    val servers: List<ProxyServerState>,
    val removedCount: Int,
    val removedServerIds: Set<Int>,
)

data class ProxyServerEditApplyResult(
    val state: AppState,
    val existingGroupId: Int?,
    val wasExisting: Boolean,
)

fun AppState.withUpdatedSubscriptionServers(
    updates: List<ProxyServerListSubscriptionUpdate>,
    updatedAtMillis: Long,
): AppState {
    if (updates.size == 1) {
        return withUpdatedSingleSubscriptionServers(updates.single(), updatedAtMillis)
    }
    return withUpdatedSubscriptionServersBatch(updates, updatedAtMillis)
}

fun AppState.withUpdatedSingleSubscriptionServers(
    update: ProxyServerListSubscriptionUpdate,
    updatedAtMillis: Long,
): AppState {
    val reconciled = reconcileSubscriptionRefresh(
        snapshot = toSubscriptionRefreshSnapshot(),
        loaded = update.toLoadedSubscriptionRefresh(),
        refreshedAtMillis = updatedAtMillis,
    )
    if (!reconciled.applicable) return this

    var nextState = withSubscriptionRefreshSnapshot(reconciled.snapshot)
    val config = reconciled.resolvedEmbeddedConfig
    if (config != null && config.content.isNotBlank()) {
        nextState = runCatching {
            nextState.withImportedTrafficConfig(
                content = config.content,
                activate = config.activate,
                fallbackName = config.fallbackName,
                sourceUrl = config.sourceUrl,
            )
        }.getOrDefault(nextState)
    }
    return nextState
}

fun AppState.withUpdatedSubscriptionServersBatch(
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
                    userAgent = group.userAgent,
                    updateInterval = group.updateInterval,
                    hwid = group.hwid,
                    ageSecretKey = group.ageSecretKey,
                    updateViaProxy = group.updateViaProxy,
                    autoOverrideRules = group.autoOverrideRules,
                    enabled = group.enabled,
                    builtIn = group.builtIn,
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
                    lastUpdatedAtMillis = group.lastUpdatedAtMillis,
                    notifyOnExpiry = group.notifyOnExpiry,
                    customExpiryReminders = group.customExpiryReminders,
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

fun SubscriptionGroupState.toRefreshSubscriptionRecord(): SubscriptionRecord {
    val record = toSubscriptionRecord()
    return record.copy(
        metadata = record.metadata?.copy(profileUpdateIntervalHours = updateInterval),
    )
}

fun AppState.toSubscriptionRefreshSnapshot(): SubscriptionRefreshSnapshot = SubscriptionRefreshSnapshot(
    subscriptions = subscriptionGroups.map(SubscriptionGroupState::toRefreshSubscriptionRecord),
    servers = proxyServers.map { server ->
        ProxyServerRecord(
            id = server.id,
            groupId = server.groupId,
            server = server.server,
            latency = server.latency,
        )
    },
    nextServerId = nextProxyServerId,
    selectedServerId = selectedProxyServerId,
)

fun AppState.toSubscriptionRefreshSnapshot(
    catalog: ProxyServerCatalog,
): SubscriptionRefreshSnapshot = toSubscriptionRefreshSnapshot().copy(
    servers = catalog.servers.map { server ->
        ProxyServerRecord(
            id = server.id,
            groupId = server.sourceSubscriptionId ?: DefaultSubscriptionGroupId,
            server = server.server,
            latency = proxyServers.firstOrNull { current -> current.id == server.id }?.latency.orEmpty(),
        )
    },
    nextServerId = catalog.nextServerId,
    selectedServerId = catalog.selectedServerId,
    retainedServerIds = catalog.retainedServerIds,
)

fun AppState.withSubscriptionRefreshSnapshot(snapshot: SubscriptionRefreshSnapshot): AppState = copy(
    subscriptionGroups = snapshot.subscriptions.map { record ->
        record.toAndroidGroup(subscriptionGroups.firstOrNull { existing -> existing.id == record.id })
    },
    proxyServers = snapshot.servers.map { server ->
        ProxyServerState(server.id, server.server, server.groupId, server.latency)
    },
    nextProxyServerId = snapshot.nextServerId,
    selectedProxyServerId = snapshot.selectedServerId,
)

fun ProxyServerListSubscriptionUpdate.toLoadedSubscriptionRefresh() = LoadedSubscriptionRefresh(
    sourceIdentity = SubscriptionRefreshRequestIdentity(
        id = groupId,
        url = sourceIdentity.url,
        userAgent = sourceIdentity.userAgent,
        updateInterval = sourceIdentity.updateInterval,
        ageSecretKey = sourceIdentity.ageSecretKey,
        updateViaProxy = sourceIdentity.updateViaProxy,
        enabled = sourceIdentity.enabled,
    ),
    urlCount = urlCount,
    servers = servers,
    metadata = metadata,
    resolvedEmbeddedConfig = resolvedConfig?.let { config ->
        ResolvedEmbeddedSubscriptionConfig(
            content = config.content,
            sourceUrl = config.sourceUrl,
            fallbackName = config.fallbackName,
            activate = config.activate,
        )
    },
)

fun List<SubscriptionGroupState>.updatableSubscriptionGroups(): List<SubscriptionGroupState> {
    val eligibleIds = eligibleSubscriptionRefreshRequests(
        map { group ->
            SubscriptionRefreshLoadRequest(
                subscription = group.toSubscriptionRecord(),
                fetchOptions = Unit,
            )
        },
    ).mapTo(mutableSetOf()) { request -> request.subscription.id }
    return filter { group -> group.id in eligibleIds }
}

fun List<ProxyServerState>.deleteDuplicateServersInGroup(
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

fun List<ProxyServerState>.deleteInvalidServersInGroup(
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
