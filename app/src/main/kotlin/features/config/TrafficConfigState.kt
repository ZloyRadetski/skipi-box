// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.ProxyServerState
import app.skipi.app.config.createTrafficConfigProfile
import app.skipi.app.config.deleteTrafficConfigProfile
import app.skipi.app.config.duplicateTrafficConfigProfile
import app.skipi.app.config.selectTrafficConfigProfile
import app.skipi.app.config.updateTrafficConfigProfile
import app.skipi.app.proxy.ProxyServerRecord as ReconciledProxyServerRecord
import app.skipi.app.proxy.reconcileConfigProxyGroups
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord as CatalogProxyServerRecord
import features.subscription.DefaultSubscriptionGroupId
import features.proxy.server.display.CountryFlagUtils
import features.proxy.server.list.AutoBalancerGroupId

internal data class AndroidTrafficConfigCreation(
    val state: AppState,
    val profileId: Int,
)

/** Allocates a profile ID while keeping resource defaults selected by Android. */
internal fun AppState.withCreatedTrafficConfig(
    name: String,
    rawDocument: String,
    sourceUrl: String = "",
    lastUpdatedAtMillis: Long = 0L,
    readSkipiSettingsFromRawDocument: Boolean = false,
): AndroidTrafficConfigCreation {
    val update = createTrafficConfigProfile(
        profiles = trafficConfigs,
        nextId = nextTrafficConfigId,
        name = name,
        rawDocument = rawDocument,
        resourceSettings = androidDefaultTrafficConfigResourceSettings(),
        sourceUrl = sourceUrl,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        readSkipiSettingsFromRawDocument = readSkipiSettingsFromRawDocument,
    )
    return AndroidTrafficConfigCreation(
        state = copy(trafficConfigs = update.profiles, nextTrafficConfigId = update.nextId),
        profileId = update.affectedProfileId,
    )
}

/** Compatibility wrapper; catalog reconciliation is dispatched through the shared store. */
internal fun AppState.withDuplicatedTrafficConfig(
    source: TrafficConfigState,
    name: String,
): AndroidTrafficConfigCreation {
    val creation = withDuplicatedTrafficConfigProfile(source, name)
    return creation
}

/** Duplicates a profile without writing the proxy catalog from the editor path. */
internal fun AppState.withDuplicatedTrafficConfigProfile(
    source: TrafficConfigState,
    name: String,
): AndroidTrafficConfigCreation {
    val update = duplicateTrafficConfigProfile(
        profiles = trafficConfigs,
        nextId = nextTrafficConfigId,
        source = source,
        name = name,
    )
    return AndroidTrafficConfigCreation(
        state = copy(trafficConfigs = update.profiles, nextTrafficConfigId = update.nextId),
        profileId = update.affectedProfileId,
    )
}

/** Compatibility wrapper that changes profiles only; callers dispatch catalog reconciliation. */
internal fun AppState.withDeletedTrafficConfig(profileId: Int): AppState {
    return withDeletedTrafficConfigProfile(profileId)
}

/** Deletes a profile without writing the proxy catalog from the editor path. */
internal fun AppState.withDeletedTrafficConfigProfile(profileId: Int): AppState {
    val update = deleteTrafficConfigProfile(trafficConfigs, activeTrafficConfigId, profileId)
    if (update.profiles === trafficConfigs) return this
    return copy(
        trafficConfigs = update.profiles,
        activeTrafficConfigId = update.activeProfileId,
    )
}

/**
 * Compatibility wrapper for callers that still update the Android profile snapshot.
 * Proxy catalog reconciliation must be dispatched separately through shared actions.
 */
internal fun AppState.withUpdatedTrafficConfig(
    configId: Int,
    transform: (TrafficConfigState) -> TrafficConfigState,
): AppState {
    return withUpdatedTrafficConfigProfile(configId, transform)
}

/** Updates a profile without writing the proxy catalog from the editor path. */
internal fun AppState.withUpdatedTrafficConfigProfile(
    configId: Int,
    transform: (TrafficConfigState) -> TrafficConfigState,
): AppState {
    val updatedConfigs = updateTrafficConfigProfile(trafficConfigs, configId, transform)
    if (updatedConfigs === trafficConfigs) return this
    return copy(trafficConfigs = updatedConfigs)
}

/**
 * Materializes only the `[Proxy Group]` entries explicitly marked for home
 * display. They keep a source reference, so their member set is resolved from
 * the config on every connection instead of becoming stale after a refresh.
 */
internal fun AppState.withConfigProxyGroupsReflected(): AppState {
    val currentCatalog = ProxyServerCatalog(
        servers = proxyServers.map { server ->
            CatalogProxyServerRecord(
                id = server.id,
                server = server.server,
                sourceSubscriptionId = server.groupId.takeIf { it != DefaultSubscriptionGroupId },
            )
        },
        nextServerId = nextProxyServerId,
        selectedServerId = selectedProxyServerId,
    )
    val result = withConfigProxyGroupsReflected(currentCatalog)
    val previousById = proxyServers.associateBy(ProxyServerState::id)
    return copy(
        proxyServers = result.servers.map { server ->
            ProxyServerState(
                id = server.id,
                groupId = server.sourceSubscriptionId ?: DefaultSubscriptionGroupId,
                server = server.server,
                latency = previousById[server.id]?.latency.orEmpty(),
            )
        },
        nextProxyServerId = result.nextServerId,
        selectedProxyServerId = result.selectedServerId,
    )
}

/** Builds the profile-derived proxy catalog as a value for one shared-store update. */
internal fun AppState.withConfigProxyGroupsReflected(catalog: ProxyServerCatalog): ProxyServerCatalog {
    val existingById = catalog.servers.associateBy(CatalogProxyServerRecord::id)
    val result = reconcileConfigProxyGroups(
        trafficConfigs = trafficConfigs,
        activeTrafficConfigId = activeTrafficConfigId,
        servers = catalog.servers.map { server ->
            ReconciledProxyServerRecord(
                id = server.id,
                groupId = server.sourceSubscriptionId ?: DefaultSubscriptionGroupId,
                server = server.server,
            )
        },
        nextServerId = catalog.nextServerId,
        selectedServerId = catalog.selectedServerId,
        autoBalancerGroupId = AutoBalancerGroupId,
        stripLeadingCountryFlag = CountryFlagUtils::stripLeadingCountryFlag,
    )
    return catalog.copy(
        servers = result.servers.map { server ->
            CatalogProxyServerRecord(
                id = server.id,
                server = server.server,
                sourceSubscriptionId = server.groupId.takeIf { it != DefaultSubscriptionGroupId },
                enabled = existingById[server.id]?.enabled ?: true,
            )
        },
        nextServerId = result.nextServerId,
        selectedServerId = result.selectedServerId,
    )
}

/** Restores the caller's catalog after legacy Android profile helpers run. */
internal fun AppState.withProxyCatalogFrom(previous: AppState): AppState = copy(
    proxyServers = previous.proxyServers,
    nextProxyServerId = previous.nextProxyServerId,
    selectedProxyServerId = previous.selectedProxyServerId,
)

/** Compatibility wrapper that selects a profile only; callers dispatch catalog reconciliation. */
internal fun AppState.withActiveTrafficConfig(configId: Int): AppState {
    return withActiveTrafficConfigProfile(configId)
}

/** Selects a profile without writing the proxy catalog from the editor path. */
internal fun AppState.withActiveTrafficConfigProfile(configId: Int): AppState {
    val selectedId = selectTrafficConfigProfile(trafficConfigs, activeTrafficConfigId, configId)
    if (activeTrafficConfigId == selectedId) return this
    return copy(activeTrafficConfigId = selectedId)
}
