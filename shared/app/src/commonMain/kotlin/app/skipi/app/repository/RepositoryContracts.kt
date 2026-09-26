// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.repository

import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.runtime.AppRuntimeState
import kotlinx.coroutines.flow.StateFlow

/** Storage-independent contract for settings persisted by a client adapter. */
interface SettingsRepository {
    val state: StateFlow<PersistedSettings>

    suspend fun update(transform: (PersistedSettings) -> PersistedSettings)
}

/** Storage-independent proxy/server catalog. */
interface ProxyServerRepository {
    val servers: StateFlow<List<ProxyServerRecord>>

    /** Persist the active server selection using the host's existing settings format. */
    suspend fun select(serverId: Int)

    suspend fun upsert(server: ProxyServerRecord)

    suspend fun remove(serverId: Int)
}

/** Validates a Home selection against the current catalogue before persisting it. */
suspend fun ProxyServerRepository.selectExisting(serverId: Int) {
    require(servers.value.any { it.id == serverId }) { "Unknown proxy server ID: $serverId" }
    select(serverId)
}

/** Storage-independent subscription catalog and refresh boundary. */
interface SubscriptionRepository {
    val subscriptions: StateFlow<List<SubscriptionRecord>>

    suspend fun upsert(subscription: SubscriptionRecord)

    suspend fun remove(subscriptionId: Int)

    suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord>
}

/** Storage-independent traffic configuration catalog. */
interface TrafficConfigRepository {
    val configs: StateFlow<List<TrafficConfigRecord>>

    suspend fun upsert(config: TrafficConfigRecord)

    suspend fun remove(configId: Int)
}

/** Runtime is process/lifecycle state and must not be serialized as settings. */
interface RuntimeStateRepository {
    val state: StateFlow<AppRuntimeState>

    suspend fun update(transform: (AppRuntimeState) -> AppRuntimeState)
}

/** Dependencies needed to assemble feature stores without platform imports. */
data class AppRepositories(
    val settings: SettingsRepository,
    val proxyServers: ProxyServerRepository,
    val subscriptions: SubscriptionRepository,
    val trafficConfigs: TrafficConfigRepository,
    val runtime: RuntimeStateRepository,
)
