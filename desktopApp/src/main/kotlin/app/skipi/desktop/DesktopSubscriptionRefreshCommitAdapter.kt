// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord as RefreshServerRecord
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshCommitPort
import app.skipi.app.subscription.SubscriptionRefreshReconciliationResult
import app.skipi.app.subscription.SubscriptionRefreshSnapshot
import app.skipi.app.config.importTrafficConfigDocument
import features.config.ConfigProfile
import features.config.RoscomRoutingResourceSource
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.toRoscomRoutingJsonOrNull
import features.config.toRoscomRoutingShadowrocketConf
import features.config.withSkipiSettingsReadFromRawConfig
import features.proxy.server.model.encodePersistedProxyServer
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

/**
 * Maps the shared single-refresh commit contract onto Desktop's current JSON libraries.
 *
 * The subscription ID scopes opaque target rows that are not representable by the shared
 * server records. All store reads happen at the commit boundary; network loading is owned by
 * the shared refresh operation and the Desktop transport callback.
 */
internal class DesktopSubscriptionRefreshCommitAdapter(
    private val subscriptionId: Int,
    private val subscriptionRepository: DesktopSubscriptionRepository,
    private val proxyServerRepository: DesktopProxyServerRepository,
    private val readConfigs: () -> DesktopConfigLibrary,
    private val saveConfigs: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishConfigs: (DesktopConfigLibrary) -> Unit,
    private val readLatencyByServerId: () -> Map<Int, DesktopServerLatencyResult>,
    private val publishLatencyByServerId: (Map<Int, DesktopServerLatencyResult>) -> Unit,
) : SubscriptionRefreshCommitPort {
    var serverLibraryChanged: Boolean = false
        private set

    var profileLibraryChanged: Boolean = false
        private set

    override suspend fun updateServers(
        reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshReconciliationResult,
    ): SubscriptionRefreshReconciliationResult {
        currentCoroutineContext().ensureActive()
        serverLibraryChanged = false
        profileLibraryChanged = false

        var reconciliation: SubscriptionRefreshReconciliationResult? = null
        var previousServerLibrary: DesktopServerLibrary? = null
        var updatedLatencyByServerId: Map<Int, DesktopServerLatencyResult>? = null
        val updatedServerLibrary = proxyServerRepository.updateLibrary { latestLibrary ->
            previousServerLibrary = latestLibrary
            val latencyByServerId = readLatencyByServerId()
            val result = reconcileLatest(
                latestLibrary.toRefreshSnapshot(
                    subscriptions = subscriptionRepository.catalog.value.subscriptions,
                    latencyByServerId = latencyByServerId,
                ),
            )
            reconciliation = result
            if (!result.applicable) {
                latestLibrary
            } else {
                val updatedLibrary = result.snapshot.toDesktopServerLibrary(
                    currentLibrary = latestLibrary,
                    subscriptionId = subscriptionId,
                )
                val visibleIds = updatedLibrary.servers.mapTo(hashSetOf(), DesktopStoredProxyServer::id)
                updatedLatencyByServerId = latencyByServerId.filterKeys { serverId -> serverId in visibleIds }
                updatedLibrary
            }
        }

        val result = checkNotNull(reconciliation) { "Subscription server reconciliation did not run" }
        if (result.applicable) {
            publishLatencyByServerId(checkNotNull(updatedLatencyByServerId))
            val previous = checkNotNull(previousServerLibrary)
            serverLibraryChanged = previous.servers != updatedServerLibrary.servers ||
                previous.selectedServerId != updatedServerLibrary.selectedServerId
        }
        return result
    }

    override suspend fun updateSubscription(
        subscriptionId: Int,
        transform: (SubscriptionRecord?) -> SubscriptionRecord?,
    ): SubscriptionRecord? {
        currentCoroutineContext().ensureActive()
        var updatedSubscription: SubscriptionRecord? = null
        subscriptionRepository.updateCatalog { latestCatalog ->
            val current = latestCatalog.subscriptions.firstOrNull { it.id == subscriptionId }
            val updated = transform(current)
            updatedSubscription = updated
            if (updated == null) {
                latestCatalog
            } else {
                latestCatalog.copy(
                    subscriptions = latestCatalog.subscriptions.map { existing ->
                        if (existing.id == subscriptionId) updated else existing
                    },
                )
            }
        }
        return updatedSubscription
    }

    override suspend fun applyEmbeddedConfig(config: ResolvedEmbeddedSubscriptionConfig): Boolean {
        currentCoroutineContext().ensureActive()
        profileLibraryChanged = false
        val latestLibrary = readConfigs()
        val imported = try {
            val convertedContent = config.content.toRoscomRoutingJsonOrNull()
                ?.toRoscomRoutingShadowrocketConf(
                    fallbackName = config.fallbackName,
                    resourceSources = AndroidRoscomRoutingResourceSources,
                    customResourceSourceId = ResourceFileSourceCustom,
                )
                ?: config.content

            importTrafficConfigDocument(
                trafficConfigs = latestLibrary.configs.map { profile -> profile.toRefreshTrafficConfigState() },
                nextTrafficConfigId = (latestLibrary.configs.maxOfOrNull(ConfigProfile::id) ?: 0)
                    .let { maxId -> if (maxId >= Int.MAX_VALUE) Int.MAX_VALUE else maxOf(1, maxId + 1) },
                activeTrafficConfigId = latestLibrary.selectedConfigId ?: NoSelectedConfigId,
                content = convertedContent,
                activate = config.activate,
                fallbackName = config.fallbackName,
                sourceUrl = config.sourceUrl,
                newProfileResourceSettings = TrafficConfigResourceSettings(
                    userAgent = versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Android treats an invalid imported profile as best effort after subscription data
            // has been committed. Keep persistence outside this catch so save failures still
            // surface as PROFILE failures from the shared commit operation.
            return false
        }
        currentCoroutineContext().ensureActive()
        val updatedLibrary = DesktopConfigLibrary(
            selectedConfigId = imported.activeTrafficConfigId.takeIf { activeId ->
                imported.trafficConfigs.any { trafficConfig -> trafficConfig.id == activeId }
            },
            configs = imported.trafficConfigs.map { profile -> profile.toDesktopConfigProfile() },
        )
        val activeBefore = latestLibrary.selectedConfigId
            ?.let { selectedId -> latestLibrary.configs.firstOrNull { profile -> profile.id == selectedId } }
        val activeAfter = updatedLibrary.selectedConfigId
            ?.let { selectedId -> updatedLibrary.configs.firstOrNull { profile -> profile.id == selectedId } }
        val activeProfileChanged = activeBefore?.id != activeAfter?.id || activeBefore?.content != activeAfter?.content
        saveConfigs(updatedLibrary).getOrThrow()
        publishConfigs(updatedLibrary)
        profileLibraryChanged = activeProfileChanged
        return true
    }

    private fun DesktopServerLibrary.toRefreshSnapshot(
        subscriptions: List<SubscriptionRecord>,
        latencyByServerId: Map<Int, DesktopServerLatencyResult>,
    ): SubscriptionRefreshSnapshot = SubscriptionRefreshSnapshot(
        subscriptions = subscriptions,
        servers = servers.mapNotNull { stored ->
            val server = stored.decode().getOrNull() ?: return@mapNotNull null
            RefreshServerRecord(
                id = stored.id,
                groupId = stored.subscriptionId ?: ManualSubscriptionGroupId,
                server = server,
                latency = latencyByServerId[stored.id].toRefreshLatency(),
            )
        },
        nextServerId = effectiveNextServerId,
        selectedServerId = selectedServerId ?: NoSelectedServerId,
        retainedServerIds = servers.asSequence()
            .filter { stored -> stored.subscriptionId != subscriptionId && stored.decode().isFailure }
            .map(DesktopStoredProxyServer::id)
            .toSet(),
    )

    private fun SubscriptionRefreshSnapshot.toDesktopServerLibrary(
        currentLibrary: DesktopServerLibrary,
        subscriptionId: Int,
    ): DesktopServerLibrary {
        val oldVisibleById = currentLibrary.servers.mapNotNull { stored ->
            val server = stored.decode().getOrNull() ?: return@mapNotNull null
            stored.id to (stored to server)
        }.toMap()
        val encodedVisible = servers.map { record ->
            val rawSubscriptionId = record.groupId.takeUnless { groupId -> groupId == ManualSubscriptionGroupId }
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
                val visible = stored.decode().isSuccess
                if (!visible) {
                    if (stored.subscriptionId != subscriptionId) add(stored)
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

    private fun DesktopServerLatencyResult?.toRefreshLatency(): String = when (this) {
        is DesktopServerLatencyResult.Success -> "$milliseconds ms"
        DesktopServerLatencyResult.Timeout -> "timeout"
        is DesktopServerLatencyResult.Error -> message
        null -> ""
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

    private companion object {
        const val ManualSubscriptionGroupId = Int.MIN_VALUE
        const val NoSelectedServerId = 0
        const val NoSelectedConfigId = 0

        val AndroidRoscomRoutingResourceSources = listOf(
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
    }
}
