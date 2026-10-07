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
import app.skipi.app.proxy.createProxyServerRecord
import app.skipi.app.repository.AppRepositories
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.app.repository.ResourceRepository
import app.skipi.app.repository.RoutingRepository
import app.skipi.app.repository.RuntimeStateRepository
import app.skipi.app.repository.SettingsRepository
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.repository.TrafficConfigRepository
import app.skipi.app.runtime.AppRuntimeState
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import features.routing.model.RouteRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.update
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

    @Test
    fun importingProxyServerBatchUsesRetainedIdHighWaterAndKeepsManualServersGrouped() = runTest {
        val initialCatalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(
                    id = 42,
                    server = HTTP(remarks = "existing", server = "existing.example"),
                    sourceSubscriptionId = 17,
                ),
            ),
            nextServerId = 5,
            selectedServerId = 99,
            retainedServerIds = setOf(99, 500),
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = initialCatalog
        repositories.proxyServers.servers.value = initialCatalog.servers
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val result = store.dispatchAndAwaitInStoreScope(
            SharedApplicationAction.ImportProxyServerBatch(
                servers = listOf(
                    HTTP(remarks = "first import", server = "first-import.example"),
                    HTTP(remarks = "second import", server = "second-import.example"),
                ),
            ),
        )
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
        val updatedCatalog = repositories.proxyServers.catalog
        assertEquals(listOf(501, 502, 42), updatedCatalog.servers.map(ProxyServerRecord::id))
        assertEquals(503, updatedCatalog.nextServerId)
        assertEquals(99, updatedCatalog.selectedServerId)
        assertEquals(
            listOf("first import", "second import", "existing"),
            updatedCatalog.servers.map { (it.server as HTTP).remarks },
        )
        assertEquals(listOf(null, null, 17), updatedCatalog.servers.map(ProxyServerRecord::sourceSubscriptionId))
    }

    @Test
    fun concurrentProxyServerBatchActionsKeepBothBatchesAndAllocateDistinctRanges() = runTest {
        val initialCatalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = 8, server = HTTP(server = "existing.example"))),
            nextServerId = 10,
            selectedServerId = 8,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = initialCatalog
        repositories.proxyServers.servers.value = initialCatalog.servers
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val outcomes = listOf(
            async {
                store.dispatchAndAwaitInStoreScope(
                    SharedApplicationAction.ImportProxyServerBatch(
                        servers = listOf(
                            HTTP(remarks = "batch one first", server = "batch-one-first.example"),
                            HTTP(remarks = "batch one second", server = "batch-one-second.example"),
                        ),
                    ),
                )
            },
            async {
                store.dispatchAndAwaitInStoreScope(
                    SharedApplicationAction.ImportProxyServerBatch(
                        servers = listOf(
                            HTTP(remarks = "batch two first", server = "batch-two-first.example"),
                            HTTP(remarks = "batch two second", server = "batch-two-second.example"),
                        ),
                    ),
                )
            },
        ).awaitAll()
        runCurrent()

        assertTrue(outcomes.all { it.outcome is SharedApplicationActionOutcome.Completed })
        val updatedCatalog = repositories.proxyServers.catalog
        assertEquals(setOf(8, 10, 11, 12, 13), updatedCatalog.servers.map(ProxyServerRecord::id).toSet())
        assertEquals(14, updatedCatalog.nextServerId)
        assertEquals(8, updatedCatalog.selectedServerId)

        val batchOne = updatedCatalog.servers.filter { (it.server as HTTP).remarks.startsWith("batch one") }
        val batchTwo = updatedCatalog.servers.filter { (it.server as HTTP).remarks.startsWith("batch two") }
        assertEquals(listOf("batch one first", "batch one second"), batchOne.map { (it.server as HTTP).remarks })
        assertEquals(listOf("batch two first", "batch two second"), batchTwo.map { (it.server as HTTP).remarks })
        assertEquals(1, batchOne[1].id - batchOne[0].id)
        assertEquals(1, batchTwo[1].id - batchTwo[0].id)
        assertTrue(batchOne.map(ProxyServerRecord::id).toSet().intersect(batchTwo.map(ProxyServerRecord::id).toSet()).isEmpty())
    }

    @Test
    fun failedProxyServerBatchSaveLeavesCatalogUnchanged() = runTest {
        val initialCatalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 8, server = HTTP(server = "existing.example")),
            ),
            nextServerId = 10,
            selectedServerId = 8,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = initialCatalog
        repositories.proxyServers.servers.value = initialCatalog.servers
        repositories.proxyServers.catalogUpdateFailure = IllegalStateException("Catalog save failed")
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val result = store.dispatchAndAwaitInStoreScope(
            SharedApplicationAction.ImportProxyServerBatch(
                servers = listOf(HTTP(remarks = "not saved", server = "not-saved.example")),
            ),
        )
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Failed>(result.outcome)
        assertEquals("Catalog save failed", (result.outcome as SharedApplicationActionOutcome.Failed).reason)
        assertEquals(initialCatalog, repositories.proxyServers.catalog)
        assertEquals(initialCatalog.servers, repositories.proxyServers.servers.value)
        assertEquals(initialCatalog.servers, store.state.value.proxyServers)
    }

    @Test
    fun concurrentCatalogCreatesAllocateDistinctIdsFromTheLatestCatalog() = runTest {
        val initialCatalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = 8, server = HTTP(server = "existing.example"))),
            nextServerId = 10,
            selectedServerId = 8,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = initialCatalog
        repositories.proxyServers.servers.value = initialCatalog.servers
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val outcomes = (1..2).map { index ->
            async {
                store.dispatchAndAwait(
                    SharedApplicationAction.UpdateProxyCatalog { current ->
                        createProxyServerRecord(
                            catalog = current,
                            server = HTTP(server = "created-$index.example"),
                            sourceSubscriptionId = null,
                        )
                    },
                )
            }
        }.awaitAll()

        assertTrue(outcomes.all { it.outcome is SharedApplicationActionOutcome.Completed })
        assertEquals(setOf(10, 11), repositories.proxyServers.catalog.servers
            .filter { (it.server as HTTP).server.startsWith("created-") }
            .map(ProxyServerRecord::id)
            .toSet())
        assertEquals(setOf(8, 10, 11), repositories.proxyServers.catalog.servers.map(ProxyServerRecord::id).toSet())
        assertEquals(12, repositories.proxyServers.catalog.nextServerId)
        assertEquals(8, repositories.proxyServers.catalog.selectedServerId)
    }

    @Test
    fun failedCatalogCreateAtIdExhaustionLeavesThePersistedCatalogUntouched() = runTest {
        val originalCatalog = ProxyServerCatalog(
            servers = listOf(ProxyServerRecord(id = Int.MAX_VALUE - 1, server = HTTP(server = "last.example"))),
            nextServerId = Int.MAX_VALUE,
            selectedServerId = Int.MAX_VALUE - 1,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = originalCatalog
        repositories.proxyServers.servers.value = originalCatalog.servers
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val result = store.dispatchAndAwait(
            SharedApplicationAction.UpdateProxyCatalog { current ->
                createProxyServerRecord(
                    catalog = current,
                    server = HTTP(server = "must-not-be-saved.example"),
                    sourceSubscriptionId = null,
                )
            },
        )

        assertFalse(result.outcome is SharedApplicationActionOutcome.Completed)
        assertEquals(originalCatalog, repositories.proxyServers.catalog)
        assertEquals(originalCatalog.servers, repositories.proxyServers.servers.value)
    }

    @Test
    fun removingSelectedProxyServerPrunesCompositeReferencesAndPreservesCatalogMetadata() = runTest {
        val strategy = StrategyGroup(proxyServerIds = listOf(7, 8), selectedMemberId = 7)
        val chain = ChainProxy(proxyServerIds = listOf(7, 8))
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 30, server = strategy, sourceSubscriptionId = 70, enabled = false),
                ProxyServerRecord(id = 40, server = chain, sourceSubscriptionId = 71),
                ProxyServerRecord(id = 7, server = HTTP(server = "deleted.example"), sourceSubscriptionId = 72),
                ProxyServerRecord(id = 8, server = HTTP(server = "remaining.example"), enabled = false),
                ProxyServerRecord(id = 9, server = HTTP(server = "other.example"), sourceSubscriptionId = 73),
            ),
            nextServerId = 60,
            selectedServerId = 7,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = catalog
        repositories.proxyServers.servers.value = catalog.servers
        repositories.proxyServers.selectedId = catalog.selectedServerId
        val initialRuntime = AppRuntimeState(
            latencyByServerId = mapOf(7 to 37L, 8 to 12L),
            testingServerIds = setOf(7),
        )
        repositories.runtime.state.value = initialRuntime
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val result = store.dispatchAndAwait(SharedApplicationAction.RemoveProxyServer(7))
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
        val updatedCatalog = repositories.proxyServers.catalog
        assertEquals(listOf(30, 40, 8, 9), updatedCatalog.servers.map { it.id })
        val updatedStrategy = updatedCatalog.servers.single { it.id == 30 }.server as StrategyGroup
        val updatedChain = updatedCatalog.servers.single { it.id == 40 }.server as ChainProxy
        assertEquals(listOf(8), updatedStrategy.proxyServerIds)
        assertEquals(8, updatedStrategy.selectedMemberId)
        assertEquals(listOf(8), updatedChain.proxyServerIds)
        assertEquals(listOf(70, 71, null, 73), updatedCatalog.servers.map { it.sourceSubscriptionId })
        assertEquals(listOf(false, true, false, true), updatedCatalog.servers.map { it.enabled })
        assertEquals(60, updatedCatalog.nextServerId)
        assertEquals(30, updatedCatalog.selectedServerId)
        assertEquals(listOf(30, 40, 8, 9), store.state.value.proxyServers.map { it.id })
        assertEquals(initialRuntime, repositories.runtime.state.value)
    }

    @Test
    fun removingUnselectedOrUnknownProxyServerPreservesSelectionAndHighWaterMark() = runTest {
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 12, server = HTTP(server = "remove.example"), sourceSubscriptionId = 90),
                ProxyServerRecord(id = 7, server = HTTP(server = "selected.example"), enabled = false),
            ),
            nextServerId = 41,
            selectedServerId = 7,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = catalog
        repositories.proxyServers.servers.value = catalog.servers
        repositories.proxyServers.selectedId = catalog.selectedServerId
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val removed = store.dispatchAndAwait(SharedApplicationAction.RemoveProxyServer(12))
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Completed>(removed.outcome)
        assertEquals(listOf(7), repositories.proxyServers.catalog.servers.map { it.id })
        assertEquals(41, repositories.proxyServers.catalog.nextServerId)
        assertEquals(7, repositories.proxyServers.catalog.selectedServerId)

        val afterExistingRemoval = repositories.proxyServers.catalog
        val unknown = store.dispatchAndAwait(SharedApplicationAction.RemoveProxyServer(999))
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Completed>(unknown.outcome)
        assertEquals(afterExistingRemoval, repositories.proxyServers.catalog)
        assertEquals(listOf(7), store.state.value.proxyServers.map { it.id })
    }

    @Test
    fun removingMultipleProxyServersPrunesCompositeReferencesAndPreservesCatalogMetadata() = runTest {
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(
                    id = 30,
                    server = StrategyGroup(proxyServerIds = listOf(7, 8, 9), selectedMemberId = 8),
                    sourceSubscriptionId = 70,
                    enabled = false,
                ),
                ProxyServerRecord(
                    id = 40,
                    server = ChainProxy(proxyServerIds = listOf(7, 8, 9)),
                    sourceSubscriptionId = 71,
                ),
                ProxyServerRecord(id = 7, server = HTTP(server = "first-delete.example"), sourceSubscriptionId = 72),
                ProxyServerRecord(id = 8, server = HTTP(server = "second-delete.example"), sourceSubscriptionId = 73),
                ProxyServerRecord(id = 9, server = HTTP(server = "referenced-survivor.example"), enabled = false),
                ProxyServerRecord(id = 10, server = HTTP(server = "unrelated.example"), sourceSubscriptionId = 74),
            ),
            nextServerId = 80,
            selectedServerId = 7,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = catalog
        repositories.proxyServers.servers.value = catalog.servers
        repositories.proxyServers.selectedId = catalog.selectedServerId
        val initialRuntime = AppRuntimeState(
            latencyByServerId = mapOf(7 to 37L, 8 to 18L, 9 to 12L),
            testingServerIds = setOf(7, 8),
        )
        repositories.runtime.state.value = initialRuntime
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val result = store.dispatchAndAwait(
            SharedApplicationAction.RemoveProxyServers(serverIds = setOf(7, 8)),
        )
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
        val updatedCatalog = repositories.proxyServers.catalog
        assertEquals(listOf(30, 40, 9, 10), updatedCatalog.servers.map { it.id })
        val updatedStrategy = updatedCatalog.servers.single { it.id == 30 }.server as StrategyGroup
        val updatedChain = updatedCatalog.servers.single { it.id == 40 }.server as ChainProxy
        assertEquals(listOf(9), updatedStrategy.proxyServerIds)
        assertEquals(9, updatedStrategy.selectedMemberId)
        assertEquals(listOf(9), updatedChain.proxyServerIds)
        assertEquals(listOf(70, 71, null, 74), updatedCatalog.servers.map { it.sourceSubscriptionId })
        assertEquals(listOf(false, true, false, true), updatedCatalog.servers.map { it.enabled })
        assertEquals(80, updatedCatalog.nextServerId)
        assertEquals(30, updatedCatalog.selectedServerId)
        assertEquals(initialRuntime, repositories.runtime.state.value)
    }

    @Test
    fun removingEmptySetOfProxyServersIsANoOp() = runTest {
        val catalog = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 12, server = HTTP(server = "first.example"), sourceSubscriptionId = 90),
                ProxyServerRecord(id = 7, server = HTTP(server = "selected.example"), enabled = false),
            ),
            nextServerId = 41,
            selectedServerId = 7,
        )
        val repositories = FakeRepositories()
        repositories.proxyServers.catalog = catalog
        repositories.proxyServers.servers.value = catalog.servers
        repositories.proxyServers.selectedId = catalog.selectedServerId
        val store = SharedApplicationStore(repositories.bundle, backgroundScope)
        runCurrent()

        val result = store.dispatchAndAwait(
            SharedApplicationAction.RemoveProxyServers(serverIds = emptySet()),
        )
        runCurrent()

        assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
        assertEquals(catalog, repositories.proxyServers.catalog)
        assertEquals(catalog.servers, store.state.value.proxyServers)
    }

    @Test
    fun concurrent_dispatches_receive_unique_action_ids_and_clear_in_flight_actions() = runTest {
        val repositories = FakeRepositories()
        val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = SharedApplicationStore(repositories.bundle, actionScope)
            val actionCount = 512
            val readyCount = MutableStateFlow(0)
            val startTogether = CompletableDeferred<Unit>()

            val actionCallers = (0 until actionCount).map { index ->
                async(Dispatchers.Default) {
                    readyCount.update { it + 1 }
                    startTogether.await()
                    val action = SharedApplicationAction.UpdateRuntime { runtime ->
                        runtime.copy(testingServerIds = setOf(index))
                    }
                    if (index % 2 == 0) {
                        store.dispatch(action)
                    } else {
                        store.dispatchAndAwait(action).actionId
                    }
                }
            }
            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    readyCount.first { ready -> ready == actionCount }
                }
            }
            startTogether.complete(Unit)
            val actionIds = actionCallers.awaitAll()

            assertEquals(actionCount, actionIds.toSet().size)
            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    store.state.first { state -> state.inFlightActionIds.isEmpty() }
                }
            }
            assertTrue(store.state.value.inFlightActionIds.isEmpty())
        } finally {
            actionScope.cancel()
        }
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
        var catalogUpdateFailure: Exception? = null
        override suspend fun updateCatalog(transform: (ProxyServerCatalog) -> ProxyServerCatalog) {
            // Tests sometimes seed the exposed StateFlow directly. Keep the
            // fake's catalog view aligned while retaining explicit metadata.
            val updatedCatalog = transform(catalog.copy(servers = servers.value))
            catalogUpdateFailure?.let { throw it }
            catalog = updatedCatalog
            servers.value = catalog.servers
            selectedId = catalog.selectedServerId.takeIf { id -> catalog.servers.any { it.id == id } }
        }
        override suspend fun select(serverId: Int) {
            selectedId = serverId
            catalog = catalog.copy(selectedServerId = serverId)
        }
        override suspend fun upsert(server: ProxyServerRecord) {
            servers.value = servers.value.filterNot { it.id == server.id } + server
            catalog = catalog.copy(servers = servers.value)
        }
        override suspend fun remove(serverId: Int) {
            servers.value = servers.value.filterNot { it.id == serverId }
            catalog = catalog.copy(servers = servers.value)
        }
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
