// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.store

import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ResourceCatalogRecord
import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.repository.AppRepositories
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.app.repository.ResourceRepository
import app.skipi.app.repository.RoutingRepository
import app.skipi.app.repository.RuntimeStateRepository
import app.skipi.app.repository.SettingsRepository
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.repository.TrafficConfigRepository
import app.skipi.app.runtime.AppRuntimeState
import features.proxy.server.model.HTTP
import features.routing.model.RouteRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharedApplicationStoreTest {
    @Test
    fun aggregatesPersistedCatalogsAndVolatileRuntimeSeparately() = runTest {
        val repositories = FakeRepositories()
        repositories.proxyServers.servers.value = listOf(
            ProxyServerRecord(id = 12, server = HTTP(server = "proxy.example")),
        )
        repositories.subscriptions.subscriptions.value = listOf(
            SubscriptionRecord(id = 3, title = "Primary", url = "https://example.test/sub"),
        )
        repositories.trafficConfigs.configs.value = listOf(
            TrafficConfigRecord(id = 4, name = "Default", rawDocument = "{}"),
        )
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        assertEquals(listOf(12), store.state.value.proxyServers.map { it.id })
        assertEquals(listOf(3), store.state.value.subscriptions.map { it.id })
        assertEquals(listOf(4), store.state.value.trafficConfigs.map { it.id })

        val updateSettingsId = store.dispatch(
            SharedApplicationAction.UpdateSettings { it.copy(application = it.application.copy(languageTag = "ru")) },
        )
        runCurrent()
        assertEquals("ru", store.state.value.settings.application.languageTag)
        assertEquals(AppRuntimeState(), store.state.value.runtime)
        assertEquals(updateSettingsId, store.lastActionResult.value?.actionId)
        assertIs<SharedApplicationActionOutcome.Completed>(store.lastActionResult.value?.outcome)

        store.dispatch(SharedApplicationAction.UpdateRuntime { it.copy(testingServerIds = setOf(12)) })
        runCurrent()
        assertEquals(setOf(12), store.state.value.runtime.testingServerIds)
        assertEquals("ru", store.state.value.settings.application.languageTag)
    }

    @Test
    fun validatesSelectionAndReportsMissingOptionalRepositoryAsRejected() = runTest {
        val repositories = FakeRepositories(includeExtensions = false)
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val invalidSelectionId = store.dispatch(SharedApplicationAction.SelectProxyServer(99))
        runCurrent()
        assertEquals(invalidSelectionId, store.lastActionResult.value?.actionId)
        assertIs<SharedApplicationActionOutcome.Rejected>(store.lastActionResult.value?.outcome)
        assertEquals(null, repositories.proxyServers.selectedId)

        val routingActionId = store.dispatch(SharedApplicationAction.UpdateRouting { it })
        runCurrent()
        assertEquals(routingActionId, store.lastActionResult.value?.actionId)
        assertIs<SharedApplicationActionOutcome.Rejected>(store.lastActionResult.value?.outcome)
        assertFalse(routingActionId in store.state.value.inFlightActionIds)
    }

    @Test
    fun selectingProxyServerUsesRepositoryAndPublishesSelectedRecord() = runTest {
        val repositories = FakeRepositories()
        repositories.proxyServers.servers.value = listOf(
            ProxyServerRecord(id = 12, server = HTTP(server = "twelve.example")),
            ProxyServerRecord(id = 7, server = HTTP(server = "seven.example")),
        )
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val result = store.dispatchAndAwait(SharedApplicationAction.SelectProxyServer(7))
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
        assertEquals(7, repositories.proxyServers.selectedId)
    }

    @Test
    fun routingAndResourceTransformsUpdateTheAggregate() = runTest {
        val repositories = FakeRepositories()
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        store.dispatch(
            SharedApplicationAction.UpdateRouting {
                it.copy(defaultOutboundTag = "direct", rules = listOf(RouteRule(id = 1, remarks = "local")))
            },
        )
        runCurrent()
        store.dispatch(
            SharedApplicationAction.UpdateResources {
                it.copy(customResources = listOf(app.skipi.app.model.ResourceDefinition(7, "Geo", "https://example.test/geo")))
            },
        )
        runCurrent()

        assertEquals("direct", store.state.value.routing.defaultOutboundTag)
        assertEquals(1, store.state.value.routing.rules.single().id)
        assertEquals(7, store.state.value.resources.customResources.single().id)
        assertTrue(store.state.value.inFlightActionIds.isEmpty())
    }

    @Test
    fun proxyCollectionActionPreservesRequestedOrderAndRejectsDuplicateIds() = runTest {
        val repositories = FakeRepositories()
        repositories.proxyServers.servers.value = listOf(
            ProxyServerRecord(id = 8, server = HTTP(server = "eight.example")),
            ProxyServerRecord(id = 3, server = HTTP(server = "three.example")),
        )
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        store.dispatch(SharedApplicationAction.UpdateProxyServers { current -> current.reversed() })
        runCurrent()
        assertEquals(listOf(3, 8), repositories.proxyServers.servers.value.map { it.id })
        assertEquals(listOf(3, 8), store.state.value.proxyServers.map { it.id })

        store.dispatch(
            SharedApplicationAction.UpdateProxyServers { current -> current + current.first() },
        )
        runCurrent()
        assertEquals(listOf(3, 8), repositories.proxyServers.servers.value.map { it.id })
        assertIs<SharedApplicationActionOutcome.Rejected>(store.lastActionResult.value?.outcome)
    }

    @Test
    fun proxyCatalogActionCommitsRecordsNextIdAndSelectionAsOneSnapshot() = runTest {
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 8, server = HTTP(server = "eight.example")),
                ProxyServerRecord(id = 3, server = HTTP(server = "three.example")),
            ),
            nextServerId = 24,
            selectedServerId = 8,
        )
        repositories.proxyServers.servers.value = repositories.proxyServers.catalog.servers
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        store.dispatch(
            SharedApplicationAction.UpdateProxyCatalog { current ->
                current.copy(
                    servers = current.servers.reversed(),
                    nextServerId = 30,
                    selectedServerId = 3,
                )
            },
        )
        runCurrent()

        assertEquals(listOf(3, 8), repositories.proxyServers.catalog.servers.map { it.id })
        assertEquals(30, repositories.proxyServers.catalog.nextServerId)
        assertEquals(3, repositories.proxyServers.catalog.selectedServerId)
        assertIs<SharedApplicationActionOutcome.Completed>(store.lastActionResult.value?.outcome)
    }

    private class FakeRepositories(includeExtensions: Boolean = true) {
        val settings = FakeSettingsRepository()
        val proxyServers = FakeProxyRepository()
        val subscriptions = FakeSubscriptionRepository()
        val trafficConfigs = FakeTrafficRepository()
        val runtime = FakeRuntimeRepository()
        val routing = FakeRoutingRepository()
        val resources = FakeResourceRepository()

        val bundle = AppRepositories(
            settings = settings,
            proxyServers = proxyServers,
            subscriptions = subscriptions,
            trafficConfigs = trafficConfigs,
            runtime = runtime,
            routing = routing.takeIf { includeExtensions },
            resources = resources.takeIf { includeExtensions },
        )
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val state = MutableStateFlow(PersistedSettings())
        override suspend fun update(transform: (PersistedSettings) -> PersistedSettings) {
            state.value = transform(state.value)
        }
    }

    private class FakeProxyRepository : ProxyServerRepository {
        override val servers = MutableStateFlow(emptyList<ProxyServerRecord>())
        var catalog = ProxyServerCatalog()
        var selectedId: Int? = null
        override suspend fun updateCatalog(transform: (ProxyServerCatalog) -> ProxyServerCatalog) {
            catalog = transform(catalog)
            servers.value = catalog.servers
            selectedId = catalog.selectedServerId
        }
        override suspend fun select(serverId: Int) { selectedId = serverId }
        override suspend fun upsert(server: ProxyServerRecord) {
            servers.value = servers.value.filterNot { it.id == server.id } + server
        }
        override suspend fun remove(serverId: Int) { servers.value = servers.value.filterNot { it.id == serverId } }
    }

    private class FakeSubscriptionRepository : SubscriptionRepository {
        override val subscriptions = MutableStateFlow(emptyList<SubscriptionRecord>())
        override suspend fun upsert(subscription: SubscriptionRecord) { }
        override suspend fun remove(subscriptionId: Int) { }
        override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> =
            Result.failure(UnsupportedOperationException("No refresh implementation"))
    }

    private class FakeTrafficRepository : TrafficConfigRepository {
        override val configs = MutableStateFlow(emptyList<TrafficConfigRecord>())
        override suspend fun upsert(config: TrafficConfigRecord) { }
        override suspend fun remove(configId: Int) { }
    }

    private class FakeRuntimeRepository : RuntimeStateRepository {
        override val state = MutableStateFlow(AppRuntimeState())
        override suspend fun update(transform: (AppRuntimeState) -> AppRuntimeState) {
            state.value = transform(state.value)
        }
    }

    private class FakeRoutingRepository : RoutingRepository {
        override val state = MutableStateFlow(RoutingConfigRecord())
        override suspend fun update(transform: (RoutingConfigRecord) -> RoutingConfigRecord) {
            state.value = transform(state.value)
        }
    }

    private class FakeResourceRepository : ResourceRepository {
        override val state = MutableStateFlow(ResourceCatalogRecord())
        override suspend fun update(transform: (ResourceCatalogRecord) -> ResourceCatalogRecord) {
            state.value = transform(state.value)
        }
    }
}
