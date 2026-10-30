// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import app.AppState
import app.SubscriptionGroupState
import app.skipi.app.model.ApplicationPreferences
import app.skipi.app.model.AppearanceSettings
import app.skipi.app.model.HomeDisplaySettings
import app.skipi.app.model.LocalProxyPreferences
import app.skipi.app.model.ProxySelectionSettings
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ResourceCatalogRecord
import app.skipi.app.model.ResourceDefinition
import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionPreferences
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.ThemeMode
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.model.TrafficSelectionSettings
import app.skipi.app.repository.AppRepositories
import app.skipi.app.repository.ResourceRepository
import app.skipi.app.repository.RoutingRepository
import app.skipi.app.repository.RuntimeStateRepository
import app.skipi.app.repository.SettingsRepository
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.repository.TrafficConfigRepository
import app.skipi.app.subscription.SubscriptionCatalogOperations
import data.AndroidAppStateStore
import features.config.TrafficConfigState
import features.config.newAndroidTrafficConfig
import features.config.withActiveTrafficConfigProfile
import features.config.withConfigProxyGroupsReflected
import features.config.withUpdatedTrafficConfigProfile
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Builds shared repository contracts over Android's existing Room and SharedPreferences store. */
class AndroidAppRepositories(
    private val stateStore: AndroidAppStateStore,
    scope: CoroutineScope,
    subscriptionRefresh: (suspend (Int) -> Result<SubscriptionRecord>)? = null,
    hostRuntime: RuntimeStateRepository,
) {
    val settings: SettingsRepository = AndroidSettingsRepository(stateStore, scope)
    val proxyServers: AndroidProxyServerRepository = stateStore.proxyServerRepository
    val subscriptions: SubscriptionRepository = AndroidSubscriptionRepository(
        stateStore = stateStore,
        scope = scope,
        subscriptionRefresh = subscriptionRefresh,
    )
    val trafficConfigs: TrafficConfigRepository = AndroidTrafficConfigRepository(stateStore, scope)
    val routing: RoutingRepository = AndroidRoutingRepository(stateStore, scope)
    val resources: ResourceRepository = AndroidResourceRepository(stateStore, scope)
    /** Required process-owned runtime bridge; there is no inert Android fallback. */
    val runtime: RuntimeStateRepository = hostRuntime

    val contracts = AppRepositories(
        settings = settings,
        proxyServers = proxyServers,
        subscriptions = subscriptions,
        trafficConfigs = trafficConfigs,
        runtime = runtime,
        routing = routing,
        resources = resources,
    )
}

/** Rebuilds config-derived proxy groups through the existing atomic catalog repository. */
internal suspend fun AndroidAppStateStore.reconcileTrafficConfigProxyGroups() {
    proxyServerRepository.updateCatalog { catalog ->
        currentState.withConfigProxyGroupsReflected(catalog)
    }
}

private class AndroidSettingsRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : SettingsRepository {
    override val state: StateFlow<app.skipi.app.model.PersistedSettings> = store.state
        .map(AppState::toPersistedSettings)
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.toPersistedSettings())

    override suspend fun update(transform: (app.skipi.app.model.PersistedSettings) -> app.skipi.app.model.PersistedSettings) {
        store.update { current ->
            val previous = current.toPersistedSettings()
            val updated = current.applyPersistedSettings(transform(previous))
            updated.withProxyServerCatalog(
                current.toProxyServerCatalog().copy(selectedServerId = updated.selectedProxyServerId),
            )
        }
    }
}

private class AndroidSubscriptionRepository(
    private val stateStore: AndroidAppStateStore,
    scope: CoroutineScope,
    private val subscriptionRefresh: (suspend (Int) -> Result<SubscriptionRecord>)?,
) : SubscriptionRepository {
    override val catalog: StateFlow<SubscriptionCatalog> = stateStore.state
        .map(AppState::toSubscriptionCatalog)
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            stateStore.currentState.toSubscriptionCatalog(),
        )
    override val subscriptions: StateFlow<List<SubscriptionRecord>> = catalog
        .map { it.subscriptions }
        .stateIn(scope, SharingStarted.Eagerly, catalog.value.subscriptions)

    override suspend fun upsert(subscription: SubscriptionRecord) {
        updateCatalog { current -> SubscriptionCatalogOperations.upsert(current, subscription) }
    }

    override suspend fun updateCatalog(transform: (SubscriptionCatalog) -> SubscriptionCatalog) {
        stateStore.update { state ->
            val current = state.toSubscriptionCatalog()
            val requested = transform(current)
            require(requested.subscriptions.all { it.id > 0 }) { "Subscription IDs must be positive" }
            require(requested.subscriptions.map { it.id }.distinct().size == requested.subscriptions.size) {
                "Subscription IDs must be unique"
            }
            require(requested.nextSubscriptionId > 0) { "Next subscription ID must be positive" }
            val nextSubscriptionGroupId = maxOf(
                current.nextSubscriptionId,
                requested.nextSubscriptionId,
                requested.subscriptions.maxOfOrNull(SubscriptionRecord::id)?.saturatedIncrement() ?: 1,
            )
            state.copy(
                subscriptionGroups = requested.subscriptions.map { record ->
                    record.toAndroidGroup(state.subscriptionGroups.firstOrNull { it.id == record.id })
                },
                nextSubscriptionGroupId = nextSubscriptionGroupId,
            )
        }
    }

    override suspend fun remove(subscriptionId: Int) {
        stateStore.update { state ->
            val currentCatalog = state.toSubscriptionCatalog()
            val result = SubscriptionCatalogOperations.remove(
                catalog = currentCatalog,
                proxyCatalog = state.toProxyServerCatalog(),
                subscriptionId = subscriptionId,
            )
            if (result.catalog == currentCatalog) return@update state

            state.withProxyServerCatalog(result.proxyCatalog).copy(
                subscriptionGroups = result.catalog.subscriptions.map { record ->
                    record.toAndroidGroup(state.subscriptionGroups.firstOrNull { it.id == record.id })
                },
                nextSubscriptionGroupId = maxOf(state.nextSubscriptionGroupId, result.catalog.nextSubscriptionId),
                proxyRunning = if (result.shouldStopProxy) false else state.proxyRunning,
            )
        }
    }

    override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> {
        val refresh = subscriptionRefresh
            ?: return Result.failure(UnsupportedOperationException("Android subscription refresh is not wired"))
        val refreshed = refresh(subscriptionId).getOrElse { return Result.failure(it) }
        if (refreshed.id != subscriptionId) {
            return Result.failure(
                IllegalArgumentException(
                    "Subscription refresh for ID $subscriptionId returned ID ${refreshed.id}",
                ),
            )
        }
        upsert(refreshed)
        return Result.success(refreshed)
    }
}

private class AndroidTrafficConfigRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : TrafficConfigRepository {
    override val configs: StateFlow<List<TrafficConfigRecord>> = store.state
        .map { state -> state.trafficConfigs.map(TrafficConfigState::toTrafficConfigRecord) }
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.trafficConfigs.map(TrafficConfigState::toTrafficConfigRecord))

    override suspend fun upsert(config: TrafficConfigRecord) {
        require(config.enabled) { "Android traffic profiles do not support disabling a profile" }
        require(config.id > 0) { "Traffic config ID must be positive" }
        store.update { state ->
            val old = state.trafficConfigs.firstOrNull { it.id == config.id }
            val next = config.toAndroidTrafficConfig(old)
            val updated = if (old != null) {
                state.withUpdatedTrafficConfigProfile(config.id) { next }
            } else {
                state.copy(trafficConfigs = state.trafficConfigs + next)
            }
            updated.copy(
                nextTrafficConfigId = maxOf(updated.nextTrafficConfigId, config.id.saturatedIncrement()),
            )
        }
        store.proxyServerRepository.updateCatalog { current ->
            store.currentState.withConfigProxyGroupsReflected(current)
        }
    }

    override suspend fun remove(configId: Int) {
        store.update { state ->
            val nextConfigs = state.trafficConfigs.filterNot { it.id == configId }
            if (nextConfigs.size == state.trafficConfigs.size || state.trafficConfigs.size <= 1) return@update state
            val nextActiveConfigId = if (state.activeTrafficConfigId == configId) {
                nextConfigs.first().id
            } else state.activeTrafficConfigId
            state.copy(
                trafficConfigs = nextConfigs,
                activeTrafficConfigId = nextActiveConfigId,
            )
        }
        store.proxyServerRepository.updateCatalog { current ->
            store.currentState.withConfigProxyGroupsReflected(current)
        }
    }
}

private class AndroidRoutingRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : RoutingRepository {
    override val state: StateFlow<RoutingConfigRecord> = store.state
        .map(AppState::toRoutingConfigRecord)
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.toRoutingConfigRecord())

    override suspend fun update(transform: (RoutingConfigRecord) -> RoutingConfigRecord) {
        store.update { current ->
            val updated = transform(current.toRoutingConfigRecord())
            current.copy(
                routeDomainStrategy = updated.domainStrategy,
                defaultRouteOutboundTag = updated.defaultOutboundTag,
                routeRules = updated.rules,
                nextRouteRuleId = maxOf(current.nextRouteRuleId, (updated.rules.maxOfOrNull { it.id } ?: 0) + 1),
            )
        }
    }
}

private class AndroidResourceRepository(
    private val store: AndroidAppStateStore,
    scope: CoroutineScope,
) : ResourceRepository {
    override val state: StateFlow<ResourceCatalogRecord> = store.state
        .map(AppState::toResourceCatalogRecord)
        .stateIn(scope, SharingStarted.Eagerly, store.currentState.toResourceCatalogRecord())

    override suspend fun update(transform: (ResourceCatalogRecord) -> ResourceCatalogRecord) {
        store.update { current ->
            val updated = transform(current.toResourceCatalogRecord())
            val sourceId = updated.sourceId.toIntOrNull()
                ?: throw IllegalArgumentException("Android resource source must be an integer ID")
            current.copy(
                resourceFileSource = sourceId,
                resourceFileUserAgent = updated.userAgent ?: current.resourceFileUserAgent,
                customResourceFiles = updated.customResources.map { it.toAndroidResource() },
                nextCustomResourceFileId = maxOf(
                    current.nextCustomResourceFileId,
                    (updated.customResources.maxOfOrNull(ResourceDefinition::id) ?: 0) + 1,
                ),
            )
        }
    }
}
