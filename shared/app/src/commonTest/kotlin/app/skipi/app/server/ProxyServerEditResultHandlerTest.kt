// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.model.TrafficConfigRecord
import app.skipi.app.repository.AppRepositories
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.app.repository.RuntimeStateRepository
import app.skipi.app.repository.SettingsRepository
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.repository.TrafficConfigRepository
import app.skipi.app.runtime.AppRuntimeState
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationStore
import features.proxy.server.model.HTTP
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.Test

private const val TestDefaultGroupId = 1

@OptIn(ExperimentalCoroutinesApi::class)
class ProxyServerEditResultHandlerTest {
    @Test
    fun newDraftSavedAfterImportUsesCurrentIdAndKeepsImportedServer() = runTest {
        // The editor was opened while ID 10 was next. A background import has since
        // claimed 10 and advanced the repository catalog before the editor returns.
        val imported = ProxyServerRecord(
            id = 10,
            server = HTTP(server = "imported.example"),
            sourceSubscriptionId = 70,
            enabled = false,
        )
        val existing = ProxyServerRecord(id = 4, server = HTTP(server = "selected.example"))
        val repository = ControlledProxyServerRepository(
            ProxyServerCatalog(
                servers = listOf(imported, existing),
                nextServerId = 11,
                selectedServerId = 4,
            ),
        )
        val store = createStore(repository, backgroundScope)
        runCurrent()

        val outcome = applyProxyServerEditResult(
            result = ProxyServerEditResult(
                serverId = null,
                server = HTTP(server = "draft.example"),
                groupId = TestDefaultGroupId,
                returnGroupId = TestDefaultGroupId,
            ),
            store = store,
            defaultGroupId = TestDefaultGroupId,
        )
        runCurrent()

        val saved = assertIs<ProxyServerEditApplyOutcome.Saved>(outcome)
        assertEquals(11, saved.serverId)
        assertEquals(false, saved.wasExistingAtCommit)
        assertEquals(TestDefaultGroupId, saved.selectedGroupId)
        assertEquals(listOf(11, 10, 4), repository.catalog.servers.map(ProxyServerRecord::id))
        assertEquals(HTTP(server = "draft.example"), repository.catalog.servers[0].server)
        assertEquals(null, repository.catalog.servers[0].sourceSubscriptionId)
        assertEquals(imported, repository.catalog.servers[1])
        assertEquals(12, repository.catalog.nextServerId)
        assertEquals(4, repository.catalog.selectedServerId)
        assertEquals(listOf(11, 10, 4), store.state.value.proxyServers.map(ProxyServerRecord::id))
    }

    @Test
    fun editingExistingServerKeepsItsPositionGroupAndOtherRecordMetadata() = runTest {
        val before = ProxyServerCatalog(
            servers = listOf(
                ProxyServerRecord(id = 12, server = HTTP(server = "first.example")),
                ProxyServerRecord(
                    id = 8,
                    server = HTTP(server = "before.example"),
                    sourceSubscriptionId = 55,
                    enabled = false,
                ),
                ProxyServerRecord(id = 3, server = HTTP(server = "selected.example")),
            ),
            nextServerId = 40,
            selectedServerId = 3,
        )
        val repository = ControlledProxyServerRepository(before)
        val store = createStore(repository, backgroundScope)
        runCurrent()

        val outcome = applyProxyServerEditResult(
            result = ProxyServerEditResult(
                serverId = 8,
                server = HTTP(server = "after.example"),
                // The operation must use current catalog metadata for a live edit.
                groupId = 99,
            ),
            store = store,
            defaultGroupId = TestDefaultGroupId,
        )
        runCurrent()

        val saved = assertIs<ProxyServerEditApplyOutcome.Saved>(outcome)
        assertEquals(8, saved.serverId)
        assertEquals(true, saved.wasExistingAtCommit)
        assertEquals(55, saved.selectedGroupId)
        assertEquals(listOf(12, 8, 3), repository.catalog.servers.map(ProxyServerRecord::id))
        assertEquals(HTTP(server = "after.example"), repository.catalog.servers[1].server)
        assertEquals(55, repository.catalog.servers[1].sourceSubscriptionId)
        assertEquals(false, repository.catalog.servers[1].enabled)
        assertEquals(40, repository.catalog.nextServerId)
        assertEquals(3, repository.catalog.selectedServerId)
    }

    @Test
    fun editingManualServerUsesDefaultGroupWhenNoReturnGroupWasCaptured() = runTest {
        val repository = ControlledProxyServerRepository(
            ProxyServerCatalog(
                servers = listOf(ProxyServerRecord(id = 8, server = HTTP(server = "manual.example"))),
                nextServerId = 9,
                selectedServerId = 8,
            ),
        )
        val store = createStore(repository, backgroundScope)
        runCurrent()

        val outcome = applyProxyServerEditResult(
            result = ProxyServerEditResult(
                serverId = 8,
                server = HTTP(server = "edited.example"),
            ),
            store = store,
            defaultGroupId = TestDefaultGroupId,
        )

        val saved = assertIs<ProxyServerEditApplyOutcome.Saved>(outcome)
        assertEquals(TestDefaultGroupId, saved.selectedGroupId)
    }

    @Test
    fun editingRemovedServerRestoresCapturedIdAndGroup() = runTest {
        val repository = ControlledProxyServerRepository(
            ProxyServerCatalog(
                servers = listOf(
                    ProxyServerRecord(id = 12, server = HTTP(server = "first.example")),
                    ProxyServerRecord(id = 3, server = HTTP(server = "selected.example")),
                ),
                nextServerId = 50,
                selectedServerId = 3,
            ),
        )
        val store = createStore(repository, backgroundScope)
        runCurrent()

        val outcome = applyProxyServerEditResult(
            result = ProxyServerEditResult(
                serverId = 8,
                server = HTTP(server = "restored.example"),
                groupId = 77,
                returnGroupId = 22,
            ),
            store = store,
            defaultGroupId = TestDefaultGroupId,
        )
        runCurrent()

        val saved = assertIs<ProxyServerEditApplyOutcome.Saved>(outcome)
        assertEquals(8, saved.serverId)
        assertEquals(false, saved.wasExistingAtCommit)
        assertEquals(22, saved.selectedGroupId)
        assertEquals(listOf(8, 12, 3), repository.catalog.servers.map(ProxyServerRecord::id))
        assertEquals(HTTP(server = "restored.example"), repository.catalog.servers[0].server)
        assertEquals(77, repository.catalog.servers[0].sourceSubscriptionId)
        assertEquals(50, repository.catalog.nextServerId)
        assertEquals(3, repository.catalog.selectedServerId)
    }

    @Test
    fun repositoryFailureReturnsFailureWithoutPublishingSuccessOrGroupEffect() = runTest {
        val existing = ProxyServerRecord(id = 4, server = HTTP(server = "selected.example"))
        val repository = ControlledProxyServerRepository(
            ProxyServerCatalog(
                servers = listOf(existing),
                nextServerId = 10,
                selectedServerId = 4,
            ),
        )
        val store = createStore(repository, backgroundScope)
        runCurrent()
        repository.failNextCatalogUpdate = true

        val outcome = applyProxyServerEditResult(
            result = ProxyServerEditResult(
                serverId = null,
                server = HTTP(server = "draft.example"),
                groupId = 77,
                returnGroupId = 77,
            ),
            store = store,
            defaultGroupId = TestDefaultGroupId,
        )
        runCurrent()

        assertIs<ProxyServerEditApplyOutcome.Failed>(outcome)
        assertEquals(listOf(existing), repository.catalog.servers)
        assertEquals(10, repository.catalog.nextServerId)
        assertEquals(4, repository.catalog.selectedServerId)
        assertEquals(listOf(4), store.state.value.proxyServers.map(ProxyServerRecord::id))
    }

    @Test
    fun consumedNewDraftResultRemainsQueuedAfterCollectorCancellation() = runTest {
        val existing = ProxyServerRecord(id = 4, server = HTTP(server = "selected.example"))
        val repository = ControlledProxyServerRepository(
            ProxyServerCatalog(
                servers = listOf(existing),
                nextServerId = 8,
                selectedServerId = 4,
            ),
        )
        val firstUpdateEntered = CompletableDeferred<Unit>()
        val releaseFirstUpdate = CompletableDeferred<Unit>()
        repository.firstCatalogUpdateGate = firstUpdateEntered to releaseFirstUpdate
        val store = createStore(repository, backgroundScope)
        runCurrent()

        val lockOwner = launch {
            store.dispatchAndAwait(SharedApplicationAction.UpdateProxyCatalog { it })
        }
        try {
            runCurrent()
            assertTrue(firstUpdateEntered.isCompleted)

            val resultCollector = launch {
                applyProxyServerEditResult(
                    result = ProxyServerEditResult(
                        serverId = null,
                        server = HTTP(server = "draft.example"),
                        groupId = TestDefaultGroupId,
                        returnGroupId = TestDefaultGroupId,
                    ),
                    store = store,
                    defaultGroupId = TestDefaultGroupId,
                )
            }
            try {
                runCurrent()

                // The new result has reached the store but is waiting behind the action that owns its mutex.
                assertEquals(2, store.state.value.inFlightActionIds.size)
                assertEquals(1, repository.catalogUpdateCount)
                resultCollector.cancelAndJoin()
                assertTrue(resultCollector.isCancelled)
            } finally {
                resultCollector.cancelAndJoin()
            }
        } finally {
            releaseFirstUpdate.complete(Unit)
            lockOwner.join()
        }
        runCurrent()

        assertEquals(2, repository.catalogUpdateCount)
        assertEquals(listOf(8, 4), repository.catalog.servers.map(ProxyServerRecord::id))
        assertEquals(HTTP(server = "draft.example"), repository.catalog.servers.first().server)
        assertEquals(9, repository.catalog.nextServerId)
        assertEquals(4, repository.catalog.selectedServerId)
        assertEquals(listOf(8, 4), store.state.value.proxyServers.map(ProxyServerRecord::id))
    }

    private fun createStore(
        proxyServers: ControlledProxyServerRepository,
        scope: CoroutineScope,
    ): SharedApplicationStore = SharedApplicationStore(
        AppRepositories(
            settings = TestSettingsRepository(),
            proxyServers = proxyServers,
            subscriptions = TestSubscriptionRepository(),
            trafficConfigs = TestTrafficConfigRepository(),
            runtime = TestRuntimeRepository(),
        ),
        scope,
    )

    private class ControlledProxyServerRepository(
        initialCatalog: ProxyServerCatalog,
    ) : ProxyServerRepository {
        var catalog = initialCatalog
            private set
        override val servers = MutableStateFlow(initialCatalog.servers)
        var failNextCatalogUpdate = false
        var catalogUpdateCount = 0
            private set
        var firstCatalogUpdateGate: Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>>? = null

        override suspend fun updateCatalog(transform: (ProxyServerCatalog) -> ProxyServerCatalog) {
            catalogUpdateCount += 1
            if (catalogUpdateCount == 1) {
                firstCatalogUpdateGate?.let { (entered, release) ->
                    entered.complete(Unit)
                    release.await()
                }
            }
            if (failNextCatalogUpdate) {
                failNextCatalogUpdate = false
                throw IllegalStateException("Controlled catalog write failure")
            }
            catalog = transform(catalog)
            servers.value = catalog.servers
        }

        override suspend fun select(serverId: Int) {
            require(catalog.servers.any { it.id == serverId })
            catalog = catalog.copy(selectedServerId = serverId)
        }

        override suspend fun upsert(server: ProxyServerRecord) {
            updateCatalog { current ->
                val index = current.servers.indexOfFirst { it.id == server.id }
                val nextServers = if (index < 0) {
                    current.servers + server
                } else {
                    current.servers.toMutableList().also { it[index] = server }
                }
                current.copy(servers = nextServers)
            }
        }

        override suspend fun remove(serverId: Int) {
            updateCatalog { current -> current.copy(servers = current.servers.filterNot { it.id == serverId }) }
        }
    }

    private class TestSettingsRepository : SettingsRepository {
        override val state = MutableStateFlow(PersistedSettings())
        override suspend fun update(transform: (PersistedSettings) -> PersistedSettings) {
            state.value = transform(state.value)
        }
    }

    private class TestSubscriptionRepository : SubscriptionRepository {
        override val subscriptions = MutableStateFlow(emptyList<SubscriptionRecord>())
        override val catalog = MutableStateFlow(SubscriptionCatalog())
        override suspend fun updateCatalog(transform: (SubscriptionCatalog) -> SubscriptionCatalog) {
            catalog.value = transform(catalog.value)
            subscriptions.value = catalog.value.subscriptions
        }
        override suspend fun upsert(subscription: SubscriptionRecord) = Unit
        override suspend fun remove(subscriptionId: Int) = Unit
        override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> =
            Result.failure(UnsupportedOperationException("Subscription refresh is not part of this test"))
    }

    private class TestTrafficConfigRepository : TrafficConfigRepository {
        override val configs = MutableStateFlow(emptyList<TrafficConfigRecord>())
        override suspend fun upsert(config: TrafficConfigRecord) = Unit
        override suspend fun remove(configId: Int) = Unit
    }

    private class TestRuntimeRepository : RuntimeStateRepository {
        override val state = MutableStateFlow(AppRuntimeState())
        override suspend fun update(transform: (AppRuntimeState) -> AppRuntimeState) {
            state.value = transform(state.value)
        }
    }
}
