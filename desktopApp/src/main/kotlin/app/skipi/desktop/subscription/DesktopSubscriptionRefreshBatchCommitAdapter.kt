// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.app.config.importTrafficConfigDocument
import app.skipi.app.config.TrafficConfigLibraryOperations
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord as RefreshServerRecord
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshBatchCommitPort
import app.skipi.app.subscription.SubscriptionRefreshBatchReconciliationResult
import app.skipi.app.subscription.SubscriptionRefreshSnapshot
import app.skipi.app.subscription.subscriptionRefreshRequestIdentity
import features.config.ConfigProfile
import features.config.RoscomRoutingResourceSource
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.toRoscomRoutingJsonOrNull
import features.config.toRoscomRoutingShadowrocketConf
import features.config.withSkipiSettingsReadFromRawConfig
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.model.stripLeadingCountryFlag
import features.resources.ResourceFileLoyalsoldierGeoIpUrl
import features.resources.ResourceFileLoyalsoldierGeoSiteUrl
import features.resources.ResourceFileRoscomvpnGeoIpUrl
import features.resources.ResourceFileRoscomvpnGeoSiteUrl
import features.resources.ResourceFileSourceChocolate4UGithub
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileSourceRoscomvpnGithub
import features.resources.ResourceFileSourceRunetFreedomGithub
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.ResourceFileV2FlyGeoIpUrl
import features.resources.ResourceFileV2FlyGeoSiteUrl
import features.resources.ResourceFileChocolate4UGeoIpUrl
import features.resources.ResourceFileChocolate4UGeoSiteUrl
import features.resources.ResourceFileRunetFreedomGeoIpUrl
import features.resources.ResourceFileRunetFreedomGeoSiteUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Persists one successful multi-subscription refresh through Desktop's current JSON libraries. */
internal class DesktopSubscriptionRefreshBatchCommitAdapter(
    private val loaded: List<LoadedSubscriptionRefresh>,
    private val subscriptionRepository: DesktopSubscriptionRepository,
    private val proxyServerRepository: DesktopProxyServerRepository,
    private val readConfigs: () -> DesktopConfigLibrary,
    private val saveConfigs: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishConfigs: (DesktopConfigLibrary) -> Unit,
    private val readLatencyByServerId: () -> Map<Int, DesktopServerLatencyResult>,
    private val publishLatencyByServerId: (Map<Int, DesktopServerLatencyResult>) -> Unit,
) : SubscriptionRefreshBatchCommitPort {
    var serverLibraryChanged: Boolean = false
        private set

    var profileLibraryChanged: Boolean = false
        private set

    var previousSelectedServerId: Int? = null
        private set

    var currentSelectedServerId: Int? = null
        private set

    private var serverResult: SubscriptionRefreshBatchReconciliationResult? = null

    override suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult {
        currentCoroutineContext().ensureActive()
        serverLibraryChanged = false
        profileLibraryChanged = false
        previousSelectedServerId = null
        currentSelectedServerId = null
        serverResult = null
        subscriptionRepository.refresh()

        var result: SubscriptionRefreshBatchReconciliationResult? = null
        var previousLibrary: DesktopServerLibrary? = null
        var updatedLatencyByServerId: Map<Int, DesktopServerLatencyResult>? = null
        val updatedLibrary = proxyServerRepository.updateLibrary { latestLibrary ->
            previousLibrary = latestLibrary
            val latencyByServerId = readLatencyByServerId()
            val latestSubscriptions = subscriptionRepository.catalog.value.subscriptions
            val refreshableSourceIds = loaded.asSequence()
                .map(LoadedSubscriptionRefresh::sourceIdentity)
                .filter { identity ->
                    latestSubscriptions.any { current -> subscriptionRefreshRequestIdentity(current) == identity }
                }
                .mapTo(mutableSetOf()) { identity -> identity.id }
            val current = latestLibrary.toRefreshSnapshot(
                subscriptions = latestSubscriptions,
                latencyByServerId = latencyByServerId,
                retainedOpaqueServerIds = latestLibrary.servers.asSequence()
                    .filter { stored ->
                        stored.decode().isFailure && stored.subscriptionId !in refreshableSourceIds
                    }
                    .map(DesktopStoredProxyServer::id)
                    .toSet(),
            )
            val reconciled = reconcileLatest(current)
            result = reconciled
            if (!reconciled.applicable) {
                latestLibrary
            } else {
                updatedLatencyByServerId = latencyByServerId.filterKeys { serverId ->
                    reconciled.snapshot.servers.any { server -> server.id == serverId }
                }
                reconciled.snapshot.toDesktopServerLibrary(
                    currentLibrary = latestLibrary,
                    appliedSubscriptionIds = reconciled.appliedSubscriptionIds.toSet(),
                )
            }
        }

        val reconciled = checkNotNull(result) { "Desktop batch server reconciliation did not run" }
        serverResult = reconciled
        if (reconciled.applicable) {
            publishLatencyByServerId(checkNotNull(updatedLatencyByServerId))
            val previous = checkNotNull(previousLibrary)
            previousSelectedServerId = previous.selectedServerId
            currentSelectedServerId = updatedLibrary.selectedServerId
            serverLibraryChanged = previous.servers != updatedLibrary.servers ||
                previous.selectedServerId != updatedLibrary.selectedServerId
        }
        return reconciled
    }

    override suspend fun updateMetadataAndProfiles(
        applyLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
    ): SubscriptionRefreshBatchReconciliationResult {
        val serverSnapshot = checkNotNull(serverResult) {
            "Desktop batch metadata cannot be applied before its server catalog"
        }.snapshot

        var result: SubscriptionRefreshBatchReconciliationResult? = null
        subscriptionRepository.updateCatalog { latestCatalog ->
            val reconciled = applyLatest(
                serverSnapshot.copy(subscriptions = latestCatalog.subscriptions),
            )
            result = reconciled
            if (!reconciled.applicable) {
                latestCatalog
            } else {
                latestCatalog.copy(subscriptions = reconciled.snapshot.subscriptions)
            }
        }

        val reconciled = checkNotNull(result) { "Desktop batch metadata reconciliation did not run" }
        if (!reconciled.applicable) return reconciled
        importEmbeddedConfigs(reconciled.resolvedEmbeddedConfigs)
        return reconciled
    }

    private suspend fun importEmbeddedConfigs(configs: List<ResolvedEmbeddedSubscriptionConfig>) {
        if (configs.isEmpty()) return
        val original = readConfigs()
        var updated = original
        configs.forEach { config ->
            updated = try {
                updated.withImportedSubscriptionConfig(config)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Android keeps subscription refresh successful when an embedded profile is invalid.
                updated
            }
        }
        if (updated == original) return

        commitRefreshedConfigLibrary(
            original = original,
            updated = updated,
            saveConfigs = saveConfigs,
            publishConfigs = publishConfigs,
            readConfigs = readConfigs,
            proxyServerRepository = proxyServerRepository,
            onActiveProfileChanged = { changed -> profileLibraryChanged = changed },
        )
    }
}

private fun DesktopServerLibrary.toRefreshSnapshot(
    subscriptions: List<SubscriptionRecord>,
    latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    retainedOpaqueServerIds: Set<Int>,
): SubscriptionRefreshSnapshot = SubscriptionRefreshSnapshot(
    subscriptions = subscriptions,
    servers = servers.mapNotNull { stored ->
        val server = stored.decode().getOrNull() ?: return@mapNotNull null
        RefreshServerRecord(
            id = stored.id,
            groupId = stored.subscriptionId ?: DesktopManualSubscriptionGroupId,
            server = server,
            latency = latencyByServerId[stored.id].toRefreshLatency(),
        )
    },
    nextServerId = effectiveNextServerId,
    selectedServerId = selectedServerId ?: DesktopNoSelectedServerId,
    retainedServerIds = retainedOpaqueServerIds,
)

private fun SubscriptionRefreshSnapshot.toDesktopServerLibrary(
    currentLibrary: DesktopServerLibrary,
    appliedSubscriptionIds: Set<Int>,
): DesktopServerLibrary {
    val oldVisibleById = currentLibrary.servers.mapNotNull { stored ->
        val server = stored.decode().getOrNull() ?: return@mapNotNull null
        stored.id to (stored to server)
    }.toMap()
    val encodedVisible = servers.map { record ->
        val rawSubscriptionId = record.groupId.takeUnless { groupId -> groupId == DesktopManualSubscriptionGroupId }
        oldVisibleById[record.id]
            ?.takeIf { (oldRaw, oldServer) ->
                oldRaw.subscriptionId == rawSubscriptionId && oldServer == record.server
            }
            ?.first
            ?: DesktopStoredProxyServer(
                id = record.id,
                serverJson = record.server.encodePersistedProxyServer(),
                subscriptionId = rawSubscriptionId,
            )
    }

    var visibleIndex = 0
    val mergedServers = buildList {
        currentLibrary.servers.forEach { stored ->
            if (stored.decode().isFailure) {
                if (stored.subscriptionId !in appliedSubscriptionIds) add(stored)
            } else if (visibleIndex < encodedVisible.size) {
                add(encodedVisible[visibleIndex++])
            }
        }
        while (visibleIndex < encodedVisible.size) add(encodedVisible[visibleIndex++])
    }
    val resultingIds = mergedServers.mapTo(hashSetOf(), DesktopStoredProxyServer::id)
    val selectedId = selectedServerId
        .takeIf { it in resultingIds }
        ?: currentLibrary.selectedServerId?.takeIf { it in resultingIds }
        ?: mergedServers.firstOrNull()?.id
    val nextIdAfterRows = mergedServers.maxOfOrNull(DesktopStoredProxyServer::id)
        ?.let { id -> if (id >= Int.MAX_VALUE) Int.MAX_VALUE else id + 1 }
        ?: 1

    return currentLibrary.copy(
        selectedServerId = selectedId,
        servers = mergedServers,
        nextServerId = maxOf(currentLibrary.effectiveNextServerId, nextServerId, nextIdAfterRows),
    )
}

private fun DesktopConfigLibrary.withImportedSubscriptionConfig(
    config: ResolvedEmbeddedSubscriptionConfig,
): DesktopConfigLibrary {
    val convertedContent = config.content.toRoscomRoutingJsonOrNull()
        ?.toRoscomRoutingShadowrocketConf(
            fallbackName = config.fallbackName,
            resourceSources = DesktopSubscriptionProfileResourceSources,
            customResourceSourceId = ResourceFileSourceCustom,
        )
        ?: config.content
    val imported = importTrafficConfigDocument(
        trafficConfigs = configs.map { profile -> profile.toRefreshTrafficConfigState() },
        nextTrafficConfigId = TrafficConfigLibraryOperations.normalize(this).nextConfigId,
        activeTrafficConfigId = selectedConfigId ?: DesktopNoSelectedConfigId,
        content = convertedContent,
        activate = config.activate,
        fallbackName = config.fallbackName,
        sourceUrl = config.sourceUrl,
        newProfileResourceSettings = TrafficConfigResourceSettings(
            userAgent = versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME),
        ),
    )
    return DesktopConfigLibrary(
        selectedConfigId = imported.activeTrafficConfigId.takeIf { activeId ->
            imported.trafficConfigs.any { trafficConfig -> trafficConfig.id == activeId }
        },
        configs = imported.trafficConfigs.map(TrafficConfigState::toDesktopConfigProfile),
        nextConfigId = imported.nextTrafficConfigId,
    )
}

private fun ConfigProfile.toRefreshTrafficConfigState(): TrafficConfigState =
    TrafficConfigState(
        id = id,
        name = name,
        rawConfig = content,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    ).withSkipiSettingsReadFromRawConfig().copy(
        id = id,
        name = name,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    )

private fun TrafficConfigState.toDesktopConfigProfile() = ConfigProfile(
    id = id,
    name = name,
    content = rawConfig,
    sourceUrl = sourceUrl,
    updateLocked = updateLocked,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
)

private fun DesktopServerLatencyResult?.toRefreshLatency(): String = when (this) {
    is DesktopServerLatencyResult.Success -> "$milliseconds ms"
    DesktopServerLatencyResult.Timeout -> "timeout"
    is DesktopServerLatencyResult.Error -> message
    null -> ""
}

private const val DesktopManualSubscriptionGroupId = Int.MIN_VALUE
private const val DesktopNoSelectedServerId = 0
private const val DesktopNoSelectedConfigId = 0

private val DesktopSubscriptionProfileResourceSources = listOf(
    RoscomRoutingResourceSource(
        id = ResourceFileSourceLoyalsoldierGithub,
        geoIpUrl = ResourceFileLoyalsoldierGeoIpUrl,
        geoSiteUrl = ResourceFileLoyalsoldierGeoSiteUrl,
    ),
    RoscomRoutingResourceSource(
        id = ResourceFileSourceV2FlyGithub,
        geoIpUrl = ResourceFileV2FlyGeoIpUrl,
        geoSiteUrl = ResourceFileV2FlyGeoSiteUrl,
    ),
    RoscomRoutingResourceSource(
        id = ResourceFileSourceChocolate4UGithub,
        geoIpUrl = ResourceFileChocolate4UGeoIpUrl,
        geoSiteUrl = ResourceFileChocolate4UGeoSiteUrl,
    ),
    RoscomRoutingResourceSource(
        id = ResourceFileSourceRunetFreedomGithub,
        geoIpUrl = ResourceFileRunetFreedomGeoIpUrl,
        geoSiteUrl = ResourceFileRunetFreedomGeoSiteUrl,
    ),
    RoscomRoutingResourceSource(
        id = ResourceFileSourceRoscomvpnGithub,
        geoIpUrl = ResourceFileRoscomvpnGeoIpUrl,
        geoSiteUrl = ResourceFileRoscomvpnGeoSiteUrl,
    ),
)
