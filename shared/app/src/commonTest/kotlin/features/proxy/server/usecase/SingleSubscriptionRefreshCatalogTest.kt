// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.SubscriptionGroupState
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.app.store.InMemoryAppStateStore
import data.repository.withProxyServerCatalog
import features.proxy.server.model.HTTP
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleSubscriptionRefreshCatalogTest {
    private class CatalogRepository(private val store: InMemoryAppStateStore) : ProxyServerRepository {
        var catalog = ProxyServerCatalog(retainedServerIds = setOf(99))
        var catalogUpdates = 0
        override val servers = MutableStateFlow(emptyList<ProxyServerRecord>())

        override suspend fun updateCatalog(transform: (ProxyServerCatalog) -> ProxyServerCatalog) {
            catalogUpdates += 1
            catalog = transform(catalog.copy(servers = servers.value))
            servers.value = catalog.servers
            store.update { state -> state.withProxyServerCatalog(catalog) }
        }

        override suspend fun select(serverId: Int) = Unit
        override suspend fun upsert(server: ProxyServerRecord) = Unit
        override suspend fun remove(serverId: Int) = Unit
    }

    @Test
    fun singleSubscriptionRefreshCommitsServersThroughTheCatalogAndKeepsRetainedIds() = runTest {
        val group = SubscriptionGroupState(
            id = 3,
            name = "Sub",
            url = "https://example.test/sub",
            userAgent = "ua",
            updateInterval = "24",
            enabled = true,
        )
        val store = InMemoryAppStateStore(AppState(subscriptionGroups = listOf(group)))
        val repository = CatalogRepository(store)

        applyProxySubscriptionUpdates(
            stateStore = store,
            proxyServers = repository,
            reconcileTrafficConfigProxyGroups = {},
            updates = listOf(
                ProxyServerListSubscriptionUpdate(
                    groupId = 3,
                    sourceIdentity = group.subscriptionFetchIdentity(),
                    urlCount = 1,
                    servers = listOf(HTTP(server = "one.example")),
                ),
            ),
            updatedAtMillis = 1L,
            updateAppState = { transform -> store.update(transform) },
        )

        assertTrue(repository.catalogUpdates > 0, "servers must go through the repository catalog transaction")
        assertEquals(setOf(99), repository.catalog.retainedServerIds)
        assertEquals(1, repository.catalog.servers.size)
        assertEquals(3, repository.catalog.servers.single().sourceSubscriptionId)
    }
}
