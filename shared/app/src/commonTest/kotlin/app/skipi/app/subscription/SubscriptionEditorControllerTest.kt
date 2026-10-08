// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.repository.ProxyServerRepository
import app.skipi.app.repository.SubscriptionRepository
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionEditorControllerTest {
    @Test
    fun saveCreatesFromLatestCounterAndOnlyStartsRefreshForEnabledUrlGroups() = runTest {
        val subscriptions = FakeSubscriptions(
            SubscriptionCatalog(
                subscriptions = listOf(
                    record(id = 3, title = "First", url = "https://first.example/sub"),
                    record(id = 8, title = "Second", url = "https://second.example/sub"),
                ),
                nextSubscriptionId = 14,
            ),
        )
        val proxies = FakeProxyRepository()
        val controller = SubscriptionEditorController(subscriptions, proxies)

        val provider = controller.save(
            draft = record(id = 4, title = "Provider", url = "https://provider.example/sub"),
            isNew = true,
        )
        val manual = controller.save(
            draft = record(id = 4, title = "Manual group", url = ""),
            isNew = true,
        )
        val disabledProvider = controller.save(
            draft = record(id = 4, title = "Disabled", url = "https://disabled.example/sub", enabled = false),
            isNew = true,
        )

        assertEquals(14, provider.savedSubscription?.id)
        assertEquals(15, manual.savedSubscription?.id)
        assertEquals(16, disabledProvider.savedSubscription?.id)
        assertEquals(listOf(3, 8, 14, 15, 16), subscriptions.catalog.value.subscriptions.map { it.id })
        assertEquals(17, subscriptions.catalog.value.nextSubscriptionId)
        assertTrue(provider.shouldStartRefresh)
        assertFalse(manual.shouldStartRefresh)
        assertFalse(disabledProvider.shouldStartRefresh)
        assertEquals(ProxyServerCatalog(), proxies.catalog)
    }

    @Test
    fun editUsesLatestMetadataAndProxyCatalogInsteadOfStaleSnapshots() = runTest {
        val staleEditorSnapshot = record(
            id = 5,
            title = "Manual folder",
            url = "",
            autoOverrideRules = true,
            metadata = SubscriptionMetadata(profileTitle = "Old title", profileDescription = "Old metadata"),
            lastUpdatedAtMillis = 1234,
        )
        val latestRecord = staleEditorSnapshot.copy(
            metadata = SubscriptionMetadata(profileTitle = "Latest title", profileDescription = "Latest metadata"),
            lastUpdatedAtMillis = 5678,
        )
        val neighbor = record(id = 9, title = "Neighbor", url = "https://neighbor.example/sub")
        val linkedCustom = ProxyServerRecord(
            id = 41,
            server = Custom(remarks = "linked", overrideInboundAndDns = true),
            sourceSubscriptionId = 5,
        )
        val linkedHttp = ProxyServerRecord(
            id = 42,
            server = HTTP(server = "linked.example"),
            sourceSubscriptionId = 5,
        )
        val otherCustom = ProxyServerRecord(
            id = 43,
            server = Custom(remarks = "other", overrideInboundAndDns = true),
            sourceSubscriptionId = 9,
        )
        val subscriptions = FakeSubscriptions(
            SubscriptionCatalog(listOf(latestRecord, neighbor), nextSubscriptionId = 10),
        )
        val proxies = FakeProxyRepository(
            ProxyServerCatalog(
                servers = listOf(linkedCustom, linkedHttp, otherCustom),
                nextServerId = 44,
                selectedServerId = 41,
            ),
        )
        val concurrentlyAdded = ProxyServerRecord(
            id = 44,
            server = HTTP(server = "added-during-edit.example"),
            sourceSubscriptionId = 9,
        )
        proxies.beforeNextCatalogUpdate = { current ->
            current.copy(servers = current.servers + concurrentlyAdded, nextServerId = 45)
        }
        val controller = SubscriptionEditorController(subscriptions, proxies)

        val result = controller.save(
            draft = staleEditorSnapshot.copy(
                title = "Renamed folder",
                autoOverrideRules = false,
            ),
            isNew = false,
        )

        val saved = subscriptions.catalog.value.subscriptions.first { it.id == 5 }
        assertEquals("Renamed folder", saved.title)
        assertEquals(latestRecord.metadata, saved.metadata)
        assertEquals(5678, saved.lastUpdatedAtMillis)
        assertEquals(neighbor, subscriptions.catalog.value.subscriptions[1])
        assertEquals(5, result.savedSubscription?.id)
        assertFalse(result.shouldStartRefresh)
        assertEquals(1, proxies.updateCatalogCalls)
        assertFalse((proxies.catalog.servers[0].server as Custom).overrideInboundAndDns)
        assertEquals(linkedHttp, proxies.catalog.servers[1])
        assertEquals(otherCustom, proxies.catalog.servers[2])
        assertEquals(concurrentlyAdded, proxies.catalog.servers[3])
        assertEquals(45, proxies.catalog.nextServerId)
    }

    @Test
    fun homeCanRefreshAnExistingManualGroupWhenItBecomesAProvider() = runTest {
        val manual = record(id = 5, title = "Manual folder", url = "")
        val enabledProvider = record(id = 9, title = "Enabled provider", url = "https://enabled.example/sub")
        val disabledManual = record(id = 12, title = "Disabled folder", url = "", enabled = false)
        val listManual = record(id = 14, title = "List manual", url = "")
        val subscriptions = FakeSubscriptions(
            SubscriptionCatalog(listOf(manual, enabledProvider, disabledManual, listManual), nextSubscriptionId = 15),
        )
        val controller = SubscriptionEditorController(subscriptions, FakeProxyRepository())

        val homeSave = controller.save(
            draft = manual.copy(url = "https://manual.example/sub", updateInterval = "6"),
            isNew = false,
            refreshWhenPreviouslyManual = true,
        )
        val ordinaryEdit = controller.save(
            draft = enabledProvider.copy(url = "https://updated.example/sub", updateInterval = "12"),
            isNew = false,
            refreshWhenPreviouslyManual = true,
        )
        val disabledHomeSave = controller.save(
            draft = disabledManual.copy(url = "https://disabled.example/sub", updateInterval = "6"),
            isNew = false,
            refreshWhenPreviouslyManual = true,
        )
        val subscriptionListSave = controller.save(
            draft = listManual.copy(url = "https://list.example/sub", updateInterval = "6"),
            isNew = false,
        )

        assertTrue(homeSave.shouldStartRefresh)
        assertFalse(ordinaryEdit.shouldStartRefresh)
        assertFalse(disabledHomeSave.shouldStartRefresh)
        assertFalse(subscriptionListSave.shouldStartRefresh)
        assertEquals("https://manual.example/sub", subscriptions.catalog.value.subscriptions.first { it.id == 5 }.url)
    }

    @Test
    fun concurrentSavesForSameSubscriptionKeepLinkedOverridesInFinalSavedState() = runTest {
        val initial = record(id = 5, title = "Manual folder", url = "", autoOverrideRules = true)
        val subscriptions = FakeSubscriptions(SubscriptionCatalog(listOf(initial), nextSubscriptionId = 6))
        val linkedCustom = ProxyServerRecord(
            id = 41,
            server = Custom(remarks = "linked", overrideInboundAndDns = true),
            sourceSubscriptionId = 5,
        )
        val proxies = FakeProxyRepository(
            ProxyServerCatalog(servers = listOf(linkedCustom), nextServerId = 42, selectedServerId = 41),
        ).apply {
            pauseFirstCatalogUpdate = true
        }
        val controller = SubscriptionEditorController(subscriptions, proxies)

        val firstSave = async {
            controller.save(initial.copy(autoOverrideRules = false), isNew = false)
        }
        proxies.firstCatalogUpdateEntered.await()

        val secondSave = async {
            controller.save(initial.copy(autoOverrideRules = true), isNew = false)
        }
        yield()
        proxies.resumeFirstCatalogUpdate.complete(Unit)
        firstSave.await()
        secondSave.await()

        val finalSubscription = subscriptions.catalog.value.subscriptions.single()
        val finalCustom = proxies.catalog.servers.single().server as Custom
        assertTrue(finalSubscription.autoOverrideRules)
        assertEquals(finalSubscription.autoOverrideRules, finalCustom.overrideInboundAndDns)
    }

    @Test
    fun alreadyCancelledSaveDoesNotStartSubscriptionOrProxyWrites() = runTest {
        val initial = record(id = 5, title = "Manual folder", url = "")
        val subscriptions = FakeSubscriptions(SubscriptionCatalog(listOf(initial), nextSubscriptionId = 6))
        val proxies = FakeProxyRepository()
        val controller = SubscriptionEditorController(subscriptions, proxies)
        val callerJob = Job()

        val failure = runCatching {
            withContext(callerJob) {
                callerJob.cancel()
                controller.save(initial.copy(title = "Changed"), isNew = false)
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(0, subscriptions.updateCatalogCalls)
        assertEquals(0, proxies.updateCatalogCalls)
        assertEquals(initial, subscriptions.catalog.value.subscriptions.single())
    }

    @Test
    fun savingAnEditorForADeletedGroupDoesNotRecreateItOrTouchProxies() = runTest {
        val remaining = record(id = 9, title = "Neighbor", url = "https://neighbor.example/sub")
        val subscriptions = FakeSubscriptions(SubscriptionCatalog(listOf(remaining), nextSubscriptionId = 10))
        val proxies = FakeProxyRepository()
        val controller = SubscriptionEditorController(subscriptions, proxies)

        val result = controller.save(
            draft = record(id = 5, title = "Deleted", url = "https://deleted.example/sub"),
            isNew = false,
        )

        assertNull(result.savedSubscription)
        assertFalse(result.shouldStartRefresh)
        assertEquals(listOf(remaining), subscriptions.catalog.value.subscriptions)
        assertEquals(10, subscriptions.catalog.value.nextSubscriptionId)
        assertEquals(0, proxies.updateCatalogCalls)
    }

    @Test
    fun enableAndMoveDelegateToCommonBuiltinAndFixedSlotRules() = runTest {
        val manual = record(id = 1, title = "Default manual", url = "", builtIn = true)
        val first = record(id = 2, title = "First", url = "https://first.example/sub")
        val builtIn = record(id = 3, title = "Fixed built-in", url = "", builtIn = true)
        val second = record(id = 4, title = "Second", url = "https://second.example/sub")
        val subscriptions = FakeSubscriptions(
            SubscriptionCatalog(listOf(manual, first, builtIn, second), nextSubscriptionId = 5),
        )
        val controller = SubscriptionEditorController(subscriptions, FakeProxyRepository())

        controller.setEnabled(subscriptionId = 1, enabled = false)
        controller.setEnabled(subscriptionId = 2, enabled = false)
        controller.move(groupId = 4, offset = -1, fixedGroupId = 1)

        assertEquals(listOf(1, 4, 3, 2), subscriptions.catalog.value.subscriptions.map { it.id })
        assertTrue(subscriptions.catalog.value.subscriptions.first().enabled)
        assertFalse(subscriptions.catalog.value.subscriptions.last().enabled)
    }

    @Test
    fun removeDelegatesToTheHostRepositoryCleanupBoundary() = runTest {
        val subscriptions = FakeSubscriptions(
            SubscriptionCatalog(listOf(record(id = 7, title = "Delete me", url = "https://delete.example/sub")), 8),
        )
        val controller = SubscriptionEditorController(subscriptions, FakeProxyRepository())

        controller.remove(7)

        assertEquals(listOf(7), subscriptions.removedIds)
        assertTrue(subscriptions.catalog.value.subscriptions.isEmpty())
        assertEquals(8, subscriptions.catalog.value.nextSubscriptionId)
    }

    private fun record(
        id: Int,
        title: String,
        url: String,
        autoOverrideRules: Boolean = true,
        enabled: Boolean = true,
        builtIn: Boolean = false,
        metadata: SubscriptionMetadata? = null,
        lastUpdatedAtMillis: Long? = null,
    ) = SubscriptionRecord(
        id = id,
        title = title,
        url = url,
        userAgent = "test-agent",
        updateInterval = if (url.isBlank()) "" else "6",
        hwid = "",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        builtIn = builtIn,
        metadata = metadata,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        notifyOnExpiry = true,
        customExpiryReminders = null,
    )

    private class FakeSubscriptions(initial: SubscriptionCatalog) : SubscriptionRepository {
        override val catalog = MutableStateFlow(initial)
        override val subscriptions = MutableStateFlow(initial.subscriptions)
        val removedIds = mutableListOf<Int>()
        var updateCatalogCalls: Int = 0

        override suspend fun updateCatalog(transform: (SubscriptionCatalog) -> SubscriptionCatalog) {
            updateCatalogCalls++
            val updated = transform(catalog.value)
            catalog.value = updated
            subscriptions.value = updated.subscriptions
        }

        override suspend fun upsert(subscription: SubscriptionRecord) {
            updateCatalog { SubscriptionCatalogOperations.upsert(it, subscription) }
        }

        override suspend fun remove(subscriptionId: Int) {
            removedIds += subscriptionId
            updateCatalog { current ->
                current.copy(subscriptions = current.subscriptions.filterNot { it.id == subscriptionId })
            }
        }

        override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> =
            Result.failure(UnsupportedOperationException("Refresh is not part of this controller test"))
    }

    private class FakeProxyRepository(initial: ProxyServerCatalog = ProxyServerCatalog()) : ProxyServerRepository {
        private var currentCatalog = initial
        private val mutableServers = MutableStateFlow(initial.servers)
        override val servers: StateFlow<List<ProxyServerRecord>> = mutableServers
        var beforeNextCatalogUpdate: ((ProxyServerCatalog) -> ProxyServerCatalog)? = null
        var pauseFirstCatalogUpdate: Boolean = false
        val firstCatalogUpdateEntered = CompletableDeferred<Unit>()
        val resumeFirstCatalogUpdate = CompletableDeferred<Unit>()
        var updateCatalogCalls: Int = 0
            private set
        val catalog: ProxyServerCatalog get() = currentCatalog

        override suspend fun updateCatalog(transform: (ProxyServerCatalog) -> ProxyServerCatalog) {
            val updateIndex = updateCatalogCalls++
            if (updateIndex == 0 && pauseFirstCatalogUpdate) {
                firstCatalogUpdateEntered.complete(Unit)
                resumeFirstCatalogUpdate.await()
            }
            beforeNextCatalogUpdate?.also { beforeNextCatalogUpdate = null }?.let { before ->
                currentCatalog = before(currentCatalog)
            }
            currentCatalog = transform(currentCatalog)
            mutableServers.value = currentCatalog.servers
        }

        override suspend fun select(serverId: Int) {
            currentCatalog = currentCatalog.copy(selectedServerId = serverId)
        }

        override suspend fun upsert(server: ProxyServerRecord) {
            updateCatalog { current ->
                val existingIndex = current.servers.indexOfFirst { it.id == server.id }
                val servers = if (existingIndex < 0) current.servers + server
                else current.servers.toMutableList().also { it[existingIndex] = server }
                current.copy(servers = servers)
            }
        }

        override suspend fun remove(serverId: Int) {
            updateCatalog { current -> current.copy(servers = current.servers.filterNot { it.id == serverId }) }
        }
    }
}
