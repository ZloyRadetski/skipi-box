// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopProxyDeletionActionTest {
    @Test
    fun removeActionPrunesCompositeReferencesAndPreservesOpaqueRowsAndHighWaterMark() = runBlocking {
        val opaqueJson = """{"type":"future-proxy","raw":"keep \"as-is\""}"""
        val initial = DesktopServerLibrary(
            selectedServerId = 10,
            nextServerId = 200,
            servers = listOf(
                stored(10, HTTP(remarks = "Delete", server = "delete.example"), subscriptionId = 7),
                stored(11, HTTP(remarks = "Keep", server = "keep.example")),
                stored(12, StrategyGroup(proxyServerIds = listOf(10, 11), selectedMemberId = 10)),
                stored(13, ChainProxy(proxyServerIds = listOf(10, 11))),
                DesktopStoredProxyServer(id = 99, serverJson = opaqueJson, subscriptionId = 8),
            ),
        )
        withDesktopApplication(initial) { fixture ->
            val result = fixture.application.store.dispatchAndAwait(SharedApplicationAction.RemoveProxyServer(10))

            assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
            val persisted = fixture.currentServers()
            assertEquals(listOf(11, 12, 13, 99), persisted.servers.map(DesktopStoredProxyServer::id))
            assertEquals(11, persisted.selectedServerId)
            assertEquals(200, persisted.nextServerId)
            assertEquals(opaqueJson, persisted.servers.single { it.id == 99 }.serverJson)
            assertEquals(8, persisted.servers.single { it.id == 99 }.subscriptionId)

            val strategy = persisted.servers.single { it.id == 12 }.decode().getOrThrow() as StrategyGroup
            val chain = persisted.servers.single { it.id == 13 }.decode().getOrThrow() as ChainProxy
            assertEquals(listOf(11), strategy.proxyServerIds)
            assertEquals(11, strategy.selectedMemberId)
            assertEquals(listOf(11), chain.proxyServerIds)
            assertEquals(listOf(persisted), fixture.publicationHistory)
        }
    }

    @Test
    fun bulkRemoveActionCommitsOneOrderedCatalogAndPrunesEveryDeletedReference() = runBlocking {
        val opaqueJson = """{"type":"future-proxy","raw":"bulk keep \"as-is\""}"""
        val initial = DesktopServerLibrary(
            selectedServerId = 14,
            nextServerId = 200,
            servers = listOf(
                stored(10, HTTP(remarks = "Delete A", server = "delete-a.example")),
                stored(11, HTTP(remarks = "Delete B", server = "delete-b.example")),
                stored(12, StrategyGroup(proxyServerIds = listOf(10, 11, 14), selectedMemberId = 10)),
                stored(13, ChainProxy(proxyServerIds = listOf(10, 11, 14))),
                stored(14, HTTP(remarks = "Keep", server = "keep.example")),
                DesktopStoredProxyServer(id = 99, serverJson = opaqueJson, subscriptionId = 8),
            ),
        )

        withDesktopApplication(initial) { fixture ->
            val result = fixture.application.store.dispatchAndAwait(
                SharedApplicationAction.RemoveProxyServers(setOf(10, 11)),
            )

            assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
            val persisted = fixture.currentServers()
            assertEquals(listOf(12, 13, 14, 99), persisted.servers.map(DesktopStoredProxyServer::id))
            assertEquals(14, persisted.selectedServerId)
            assertEquals(200, persisted.nextServerId)
            assertEquals(opaqueJson, persisted.servers.single { it.id == 99 }.serverJson)
            assertEquals(8, persisted.servers.single { it.id == 99 }.subscriptionId)

            val strategy = persisted.servers.single { it.id == 12 }.decode().getOrThrow() as StrategyGroup
            val chain = persisted.servers.single { it.id == 13 }.decode().getOrThrow() as ChainProxy
            assertEquals(listOf(14), strategy.proxyServerIds)
            assertEquals(14, strategy.selectedMemberId)
            assertEquals(listOf(14), chain.proxyServerIds)
            assertEquals(listOf(persisted), fixture.publicationHistory)
        }
    }

    @Test
    fun removingTheLastKnownServerLeavesDesktopSelectionNullAndRetainsHighWaterMark() = runBlocking {
        withDesktopApplication(
            DesktopServerLibrary(
                selectedServerId = 40,
                nextServerId = 90,
                servers = listOf(stored(40, HTTP(remarks = "Only", server = "only.example"))),
            ),
        ) { fixture ->
            val result = fixture.application.store.dispatchAndAwait(SharedApplicationAction.RemoveProxyServer(40))

            assertIs<SharedApplicationActionOutcome.Completed>(result.outcome)
            assertEquals(emptyList(), fixture.currentServers().servers)
            assertEquals(null, fixture.currentServers().selectedServerId)
            assertEquals(90, fixture.currentServers().nextServerId)
            assertEquals(emptyList(), fixture.application.proxyServers.servers.value)
        }
    }

    @Test
    fun failedRemovalSaveDoesNotPublishOrExposeAPartiallyUpdatedCatalog() = runBlocking {
        val initial = DesktopServerLibrary(
            selectedServerId = 10,
            nextServerId = 20,
            servers = listOf(
                stored(10, HTTP(remarks = "Delete", server = "delete.example")),
                stored(11, StrategyGroup(proxyServerIds = listOf(10), selectedMemberId = 10)),
            ),
        )
        withDesktopApplication(initial, failServerSave = true) { fixture ->
            val result = fixture.application.store.dispatchAndAwait(SharedApplicationAction.RemoveProxyServer(10))

            assertIs<SharedApplicationActionOutcome.Failed>(result.outcome)
            assertEquals(initial, fixture.currentServers())
            assertEquals(initial.servers.map(DesktopStoredProxyServer::id), fixture.application.proxyServers.servers.value.map { it.id })
            assertTrue(fixture.publicationHistory.isEmpty())
        }
    }

    private suspend fun withDesktopApplication(
        initialServers: DesktopServerLibrary,
        failServerSave: Boolean = false,
        block: suspend (DesktopFixture) -> Unit,
    ) {
        val fixture = desktopApplication(initialServers, failServerSave)
        try {
            block(fixture)
        } finally {
            fixture.scope.cancel()
        }
    }

    private fun desktopApplication(
        initialServers: DesktopServerLibrary,
        failServerSave: Boolean = false,
    ): DesktopFixture {
        var currentServers = initialServers
        var currentSettings = DesktopAppSettings()
        var currentSubscriptions = DesktopSubscriptionLibrary()
        var currentConfigs = DesktopConfigLibrary()
        val publicationHistory = mutableListOf<DesktopServerLibrary>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val proxyServers = DesktopProxyServerRepository(
            initialLibrary = initialServers,
            readLibrary = { currentServers },
            saveLibrary = { candidate ->
                if (failServerSave) {
                    Result.failure(IllegalStateException("server library save failed"))
                } else {
                    currentServers = candidate
                    Result.success(Unit)
                }
            },
            publishLibrary = { candidate ->
                currentServers = candidate
                publicationHistory += candidate
            },
        )
        val settings = DesktopSettingsRepository(
            readSettings = { currentSettings },
            saveSettings = { currentSettings = it; Result.success(Unit) },
            publishSettings = { currentSettings = it },
            readServers = { currentServers },
            saveServers = { currentServers = it; Result.success(Unit) },
            publishServers = { currentServers = it },
            readConfigs = { currentConfigs },
            saveConfigs = { currentConfigs = it; Result.success(Unit) },
            publishConfigs = { currentConfigs = it },
        )
        val subscriptions = DesktopSubscriptionRepository(
            initialLibrary = currentSubscriptions,
            readLibrary = { currentSubscriptions },
            saveLibrary = { currentSubscriptions = it; Result.success(Unit) },
            publishLibrary = { currentSubscriptions = it },
        )
        val trafficConfigs = DesktopTrafficConfigRepository(
            initialLibrary = currentConfigs,
            readLibrary = { currentConfigs },
            saveLibrary = { currentConfigs = it; Result.success(Unit) },
            publishLibrary = { currentConfigs = it },
        )
        val runtime = DesktopRuntimeStateRepository { app.skipi.app.runtime.AppRuntimeState() }
        val application = createDesktopSharedApplication(
            scope = scope,
            settings = settings,
            proxyServers = proxyServers,
            subscriptions = subscriptions,
            trafficConfigs = trafficConfigs,
            runtime = runtime,
        )
        return DesktopFixture(application, { currentServers }, publicationHistory, scope)
    }

    private fun stored(
        id: Int,
        server: features.proxy.server.model.ProxyServer<*>,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )

    private data class DesktopFixture(
        val application: DesktopSharedApplication,
        val currentServers: () -> DesktopServerLibrary,
        val publicationHistory: List<DesktopServerLibrary>,
        val scope: CoroutineScope,
    )
}
