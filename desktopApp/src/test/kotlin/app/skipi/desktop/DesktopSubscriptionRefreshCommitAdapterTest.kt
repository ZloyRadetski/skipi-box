// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshCommitScope
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshReconciliationResult
import app.skipi.app.subscription.commitSubscriptionRefresh
import app.skipi.app.subscription.refreshSubscription
import app.skipi.app.subscription.subscriptionRefreshRequestIdentity
import app.ProjectInfo
import features.config.ConfigProfile
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.encodePersistedProxyServer
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.subscription.StoredSubscription
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshCommitAdapterTest {
    @Test
    fun commitReconcilesLatestRowsAndPreservesManualOpaqueAndUnrelatedData() = runBlocking {
        val targetUrl = "https://example.com/provider"
        val targetRow = DesktopStoredSubscription(id = 1, url = targetUrl, name = "Target")
        val otherRow = DesktopStoredSubscription(id = 2, url = "https://other.example/provider", name = "Other")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(targetRow, otherRow))
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated -> subscriptions = updated; Result.success(Unit) },
            publishLibrary = { subscriptions = it },
        )
        val target = subscriptionRepository.catalog.value.subscriptions.single { it.id == 1 }

        val matchedOld = DesktopStoredProxyServer(
            id = 8,
            serverJson = proxy("same.example", "Old name").encodePersistedProxyServer(),
            subscriptionId = 1,
        )
        val opaqueTarget = DesktopStoredProxyServer(
            id = 9,
            serverJson = "{  \"legacy\": [ bytes must remain exact,  \n ] }",
            subscriptionId = 1,
        )
        val manual = DesktopStoredProxyServer(
            id = 10,
            serverJson = proxy("manual.example", "Manual").encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val otherSubscription = DesktopStoredProxyServer(
            id = 12,
            serverJson = proxy("other.example", "Other subscription").encodePersistedProxyServer(),
            subscriptionId = 2,
        )
        val unrelatedOpaque = DesktopStoredProxyServer(
            id = 20,
            serverJson = "opaque bytes: { keep case, spaces, and newline }\n",
            subscriptionId = null,
        )
        var servers = DesktopServerLibrary(
            selectedServerId = manual.id,
            servers = listOf(matchedOld, opaqueTarget, manual, otherSubscription, unrelatedOpaque),
            nextServerId = 100,
        )
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = servers,
            readLibrary = { servers },
            saveLibrary = { updated -> servers = updated; Result.success(Unit) },
            publishLibrary = { servers = it },
        )
        var configs = DesktopConfigLibrary()
        var latencyByServerId: Map<Int, DesktopServerLatencyResult> = mapOf(
            matchedOld.id to DesktopServerLatencyResult.Success(37),
            manual.id to DesktopServerLatencyResult.Success(52),
            otherSubscription.id to DesktopServerLatencyResult.Success(18),
        )
        val adapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = target.id,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyRepository,
            readConfigs = { configs },
            saveConfigs = { updated -> configs = updated; Result.success(Unit) },
            publishConfigs = { configs = it },
            readLatencyByServerId = { latencyByServerId },
            publishLatencyByServerId = { latencyByServerId = it },
        )

        val loaded = loaded(
            target,
            servers = listOf(
                proxy("same.example", "Renamed by provider"),
                proxy("new.example", "New provider server"),
            ),
        )
        val lateManual = DesktopStoredProxyServer(
            id = 30,
            serverJson = proxy("late-manual.example", "Added during fetch").encodePersistedProxyServer(),
            subscriptionId = null,
        )
        servers = DesktopServerLibraries.update(
            servers,
            serverId = matchedOld.id,
            server = proxy("same.example", "Edited during fetch"),
        )
        servers = servers.copy(
            selectedServerId = lateManual.id,
            servers = servers.servers + lateManual,
        )
        latencyByServerId = latencyByServerId + (lateManual.id to DesktopServerLatencyResult.Success(61))

        val result = commitSubscriptionRefresh(
            loaded = loaded,
            refreshedAtMillis = 900L,
            port = adapter,
        )

        assertTrue(result.applicable)
        assertEquals(
            listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
            result.appliedScopes,
        )
        assertNull(result.failure)
        assertEquals(lateManual.id, servers.selectedServerId)
        assertEquals(101, servers.nextServerId)
        val refreshedMatched = servers.servers.single { it.id == matchedOld.id }
        assertEquals(target.id, refreshedMatched.subscriptionId)
        assertEquals(
            "Renamed by provider",
            refreshedMatched.decode().getOrThrow().getInfo().remarks,
        )
        assertEquals(manual, servers.servers.single { it.id == manual.id })
        assertEquals(lateManual, servers.servers.single { it.id == lateManual.id })
        assertEquals(otherSubscription, servers.servers.single { it.id == otherSubscription.id })
        assertEquals(unrelatedOpaque, servers.servers.single { it.id == unrelatedOpaque.id })
        assertFalse(servers.servers.any { it.id == opaqueTarget.id })
        assertEquals(
            listOf(matchedOld.id, 100),
            servers.servers.filter { it.subscriptionId == target.id }.map(DesktopStoredProxyServer::id).sorted(),
        )
        assertEquals(DesktopServerLatencyResult.Success(37), latencyByServerId[matchedOld.id])
        assertEquals(DesktopServerLatencyResult.Success(52), latencyByServerId[manual.id])
        assertEquals(DesktopServerLatencyResult.Success(61), latencyByServerId[lateManual.id])
        assertEquals(DesktopServerLatencyResult.Success(18), latencyByServerId[otherSubscription.id])
        assertEquals(900L, subscriptionRepository.catalog.value.subscriptions.single { it.id == target.id }.lastUpdatedAtMillis)
    }

    @Test
    fun metadataOnlyRefreshDoesNotMarkServerOrProfileLibraryChanged() = runBlocking {
        val fixture = CommitFixture(
            storedSubscriptions = listOf(StoredSubscription(id = 51, url = "https://example.com/provider", name = "Provider")),
        )
        val adapter = fixture.adapter(51)
        val target = fixture.subscriptionRepository.catalog.value.subscriptions.single()

        val result = commitSubscriptionRefresh(
            loaded = loaded(target, servers = emptyList()),
            refreshedAtMillis = 5_100L,
            port = adapter,
        )

        assertTrue(result.applicable)
        assertNull(result.failure)
        assertEquals(listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION), result.appliedScopes)
        assertEquals(0, fixture.serverWriteCount)
        assertEquals(1, fixture.subscriptionWriteCount)
        assertEquals(5_100L, fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
        assertFalse(adapter.serverLibraryChanged)
        assertFalse(adapter.profileLibraryChanged)
    }

    @Test
    fun highWaterOnlyServerLibraryWriteDoesNotMarkServerLibraryChanged() = runBlocking {
        val fixture = CommitFixture(
            storedSubscriptions = listOf(StoredSubscription(id = 52, url = "https://example.com/provider", name = "Provider")),
        )
        val adapter = fixture.adapter(52)

        val result = adapter.updateServers { latest ->
            SubscriptionRefreshReconciliationResult(
                applicable = true,
                snapshot = latest.copy(nextServerId = latest.nextServerId + 12),
                resolvedEmbeddedConfig = null,
            )
        }

        assertTrue(result.applicable)
        assertEquals(1, fixture.serverWriteCount)
        assertEquals(13, fixture.serverLibrary.nextServerId)
        assertFalse(adapter.serverLibraryChanged)
    }

    @Test
    fun serverLibraryChangedFlagRequiresSuccessfulVisibleServerChange() = runBlocking {
        val targetRow = StoredSubscription(id = 53, url = "https://example.com/provider", name = "Provider")
        val initialServers = DesktopServerLibrary(
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 8,
                    serverJson = proxy("old.example", "Old").encodePersistedProxyServer(),
                    subscriptionId = targetRow.id,
                ),
            ),
        )
        val changedFixture = CommitFixture(storedSubscriptions = listOf(targetRow), initialServers = initialServers)
        val changedAdapter = changedFixture.adapter(targetRow.id)
        val target = changedFixture.subscriptionRepository.catalog.value.subscriptions.single()

        val changed = commitSubscriptionRefresh(
            loaded = loaded(target, listOf(proxy("fresh.example", "Fresh"))),
            refreshedAtMillis = 5_300L,
            port = changedAdapter,
        )

        assertTrue(changed.applicable)
        assertNull(changed.failure)
        assertTrue(changedAdapter.serverLibraryChanged)

        val failedFixture = CommitFixture(
            storedSubscriptions = listOf(targetRow),
            initialServers = initialServers,
        ).apply { serverSaveFailure = IllegalStateException("servers.json unavailable") }
        val failedAdapter = failedFixture.adapter(targetRow.id)
        val failedTarget = failedFixture.subscriptionRepository.catalog.value.subscriptions.single()

        val failed = commitSubscriptionRefresh(
            loaded = loaded(failedTarget, listOf(proxy("fresh.example", "Fresh"))),
            refreshedAtMillis = 5_301L,
            port = failedAdapter,
        )

        assertEquals(SubscriptionRefreshCommitScope.SERVERS, failed.failure?.scope)
        assertFalse(failedAdapter.serverLibraryChanged)
    }

    @Test
    fun profileLibraryChangedFlagRequiresSuccessfulActiveProfileChange() = runBlocking {
        val targetRow = StoredSubscription(id = 54, url = "https://example.com/provider", name = "Provider")
        val successFixture = CommitFixture(storedSubscriptions = listOf(targetRow))
        val successAdapter = successFixture.adapter(targetRow.id)
        val target = successFixture.subscriptionRepository.catalog.value.subscriptions.single()

        val success = commitSubscriptionRefresh(
            loaded = loaded(
                target,
                servers = emptyList(),
                resolvedEmbeddedConfig = embeddedConfig().copy(activate = true),
            ),
            refreshedAtMillis = 5_400L,
            port = successAdapter,
        )

        assertNull(success.failure)
        assertTrue(successFixture.configLibrary.selectedConfigId != null)
        assertTrue(successAdapter.profileLibraryChanged)

        val failedFixture = CommitFixture(
            storedSubscriptions = listOf(targetRow),
        ).apply { configSaveFailure = IllegalStateException("configs.json unavailable") }
        val failedAdapter = failedFixture.adapter(targetRow.id)
        val failedTarget = failedFixture.subscriptionRepository.catalog.value.subscriptions.single()

        val failed = commitSubscriptionRefresh(
            loaded = loaded(
                failedTarget,
                servers = emptyList(),
                resolvedEmbeddedConfig = embeddedConfig().copy(activate = true),
            ),
            refreshedAtMillis = 5_401L,
            port = failedAdapter,
        )

        assertEquals(SubscriptionRefreshCommitScope.PROFILE, failed.failure?.scope)
        assertFalse(failedAdapter.profileLibraryChanged)
    }

    @Test
    fun manualNullGroupDoesNotAliasAValidSubscriptionIdOne() = runBlocking {
        val target = StoredSubscription(id = 1, url = "https://example.com/provider", name = "Target")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(target))
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated -> subscriptions = updated; Result.success(Unit) },
            publishLibrary = { subscriptions = it },
        )
        val record = subscriptionRepository.catalog.value.subscriptions.single()
        val manual = DesktopStoredProxyServer(
            id = 7,
            serverJson = proxy("manual.example", "Manual").encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val oldTarget = DesktopStoredProxyServer(
            id = 8,
            serverJson = proxy("old.example", "Old target").encodePersistedProxyServer(),
            subscriptionId = 1,
        )
        val strategy = DesktopStoredProxyServer(
            id = 30,
            serverJson = StrategyGroup(proxyServerIds = listOf(oldTarget.id), selectedMemberId = oldTarget.id)
                .encodePersistedProxyServer(),
            subscriptionId = null,
        )
        var servers = DesktopServerLibrary(
            selectedServerId = oldTarget.id,
            servers = listOf(manual, oldTarget, strategy),
            nextServerId = 31,
        )
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = servers,
            readLibrary = { servers },
            saveLibrary = { updated -> servers = updated; Result.success(Unit) },
            publishLibrary = { servers = it },
        )
        var configs = DesktopConfigLibrary()
        val adapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = 1,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyRepository,
            readConfigs = { configs },
            saveConfigs = { updated -> configs = updated; Result.success(Unit) },
            publishConfigs = { configs = it },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
        )

        val result = commitSubscriptionRefresh(
            loaded = loaded(record, servers = listOf(proxy("fresh.example", "Fresh"))),
            refreshedAtMillis = 50L,
            port = adapter,
        )

        assertTrue(result.applicable)
        assertEquals(manual, servers.servers.single { it.id == manual.id })
        assertNull(servers.servers.single { it.id == manual.id }.subscriptionId)
        assertEquals("Fresh", servers.servers.single { it.subscriptionId == 1 }.decode().getOrThrow().getInfo().remarks)
        val refreshedStrategy = servers.servers.single { it.id == strategy.id }.decode().getOrThrow() as StrategyGroup
        assertEquals(listOf(8), refreshedStrategy.proxyServerIds)
        assertEquals(8, refreshedStrategy.selectedMemberId)
        assertEquals(8, servers.selectedServerId)
    }

    @Test
    fun unrelatedOpaqueSelectionAndCompositeReferencesSurviveSubscriptionRefresh() = runBlocking {
        val target = StoredSubscription(id = 19, url = "https://example.com/provider", name = "Provider")
        val opaque = DesktopStoredProxyServer(
            id = 99,
            serverJson = "future protocol payload; preserve exact bytes\n",
            subscriptionId = null,
        )
        val manual = DesktopStoredProxyServer(
            id = 7,
            serverJson = proxy("manual.example", "Manual").encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val oldTarget = DesktopStoredProxyServer(
            id = 8,
            serverJson = proxy("old.example", "Old target").encodePersistedProxyServer(),
            subscriptionId = target.id,
        )
        val strategy = DesktopStoredProxyServer(
            id = 20,
            serverJson = StrategyGroup(
                remarks = "Keep opaque member",
                proxyServerIds = listOf(opaque.id, oldTarget.id),
                selectedMemberId = opaque.id,
            ).encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val chain = DesktopStoredProxyServer(
            id = 21,
            serverJson = ChainProxy(
                remarks = "Keep opaque hop",
                proxyServerIds = listOf(opaque.id, oldTarget.id),
            ).encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val fixture = CommitFixture(
            storedSubscriptions = listOf(target),
            initialServers = DesktopServerLibrary(
                selectedServerId = opaque.id,
                servers = listOf(opaque, manual, oldTarget, strategy, chain),
                nextServerId = 100,
            ),
        )
        val targetRecord = fixture.subscriptionRepository.catalog.value.subscriptions.single()

        val result = commitSubscriptionRefresh(
            loaded = loaded(targetRecord, listOf(proxy("old.example", "Updated target"))),
            refreshedAtMillis = 6_000L,
            port = fixture.adapter(target.id),
        )

        assertTrue(result.applicable)
        assertNull(result.failure)
        assertEquals(
            "Updated target",
            fixture.serverLibrary.servers.single { it.id == oldTarget.id }.decode().getOrThrow().getInfo().remarks,
        )
        assertEquals(opaque, fixture.serverLibrary.servers.single { it.id == opaque.id })
        assertEquals(opaque.id, fixture.serverLibrary.selectedServerId)
        val refreshedStrategy = fixture.serverLibrary.servers.single { it.id == strategy.id }
            .decode().getOrThrow() as StrategyGroup
        assertEquals(listOf(opaque.id, oldTarget.id), refreshedStrategy.proxyServerIds)
        assertEquals(opaque.id, refreshedStrategy.selectedMemberId)
        val refreshedChain = fixture.serverLibrary.servers.single { it.id == chain.id }
            .decode().getOrThrow() as ChainProxy
        assertEquals(listOf(opaque.id, oldTarget.id), refreshedChain.proxyServerIds)
    }

    @Test
    fun targetOpaqueRowIsRemovedAndItsCompositeReferencesArePruned() = runBlocking {
        val target = StoredSubscription(id = 22, url = "https://example.com/provider", name = "Provider")
        val manual = DesktopStoredProxyServer(
            id = 7,
            serverJson = proxy("manual.example", "Manual").encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val opaqueTarget = DesktopStoredProxyServer(
            id = 9,
            serverJson = "future target protocol payload; remove with its subscription",
            subscriptionId = target.id,
        )
        val strategy = DesktopStoredProxyServer(
            id = 20,
            serverJson = StrategyGroup(
                remarks = "Drop target opaque member",
                proxyServerIds = listOf(opaqueTarget.id, manual.id),
                selectedMemberId = opaqueTarget.id,
            ).encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val chain = DesktopStoredProxyServer(
            id = 21,
            serverJson = ChainProxy(
                remarks = "Drop target opaque hop",
                proxyServerIds = listOf(opaqueTarget.id, manual.id),
            ).encodePersistedProxyServer(),
            subscriptionId = null,
        )
        val fixture = CommitFixture(
            storedSubscriptions = listOf(target),
            initialServers = DesktopServerLibrary(
                selectedServerId = opaqueTarget.id,
                servers = listOf(manual, opaqueTarget, strategy, chain),
                nextServerId = 30,
            ),
        )
        val targetRecord = fixture.subscriptionRepository.catalog.value.subscriptions.single()

        val result = commitSubscriptionRefresh(
            loaded = loaded(targetRecord, listOf(proxy("fresh.example", "Fresh"))),
            refreshedAtMillis = 7_000L,
            port = fixture.adapter(target.id),
        )

        assertTrue(result.applicable)
        assertNull(result.failure)
        assertFalse(fixture.serverLibrary.servers.any { it.id == opaqueTarget.id })
        assertEquals(
            "Fresh",
            fixture.serverLibrary.servers.single { it.subscriptionId == target.id }
                .decode().getOrThrow().getInfo().remarks,
        )
        assertEquals(manual.id, fixture.serverLibrary.selectedServerId)
        val refreshedStrategy = fixture.serverLibrary.servers.single { it.id == strategy.id }
            .decode().getOrThrow() as StrategyGroup
        assertEquals(listOf(manual.id), refreshedStrategy.proxyServerIds)
        assertEquals(manual.id, refreshedStrategy.selectedMemberId)
        val refreshedChain = fixture.serverLibrary.servers.single { it.id == chain.id }
            .decode().getOrThrow() as ChainProxy
        assertEquals(listOf(manual.id), refreshedChain.proxyServerIds)
    }

    @Test
    fun changedOrDeletedTargetIsRejectedBeforeAnyRefreshWrite() = runBlocking {
        for (deleteTarget in listOf(false, true)) {
            val fixture = CommitFixture(
                storedSubscriptions = listOf(
                    StoredSubscription(id = 14, url = "https://example.com/provider", name = "Provider"),
                ),
                initialServers = DesktopServerLibrary(
                    servers = listOf(
                        DesktopStoredProxyServer(
                            id = 6,
                            serverJson = proxy("old.example", "Old").encodePersistedProxyServer(),
                            subscriptionId = 14,
                        ),
                    ),
                ),
            )
            val loaded = loaded(
                fixture.subscriptionRepository.catalog.value.subscriptions.single(),
                servers = listOf(proxy("fresh.example", "Fresh")),
            ).copy(
                resolvedEmbeddedConfig = ResolvedEmbeddedSubscriptionConfig(
                    content = "[Rule]\nFINAL,PROXY\n",
                    sourceUrl = "https://example.com/routing.conf",
                    fallbackName = "Provider",
                    activate = true,
                ),
            )
            fixture.subscriptionRepository.updateCatalog { catalog ->
                catalog.copy(
                    subscriptions = if (deleteTarget) {
                        emptyList()
                    } else {
                        catalog.subscriptions.map { it.copy(url = "https://edited.example/provider") }
                    },
                )
            }
            fixture.resetWriteCounts()
            val serversBefore = fixture.serverLibrary
            val configsBefore = fixture.configLibrary

            val result = commitSubscriptionRefresh(
                loaded = loaded,
                refreshedAtMillis = 1_000L,
                port = fixture.adapter(14),
            )

            assertFalse(result.applicable, "deleteTarget=$deleteTarget")
            assertTrue(result.appliedScopes.isEmpty(), "deleteTarget=$deleteTarget")
            assertNull(result.failure, "deleteTarget=$deleteTarget")
            assertEquals(0, fixture.serverWriteCount, "deleteTarget=$deleteTarget")
            assertEquals(0, fixture.subscriptionWriteCount, "deleteTarget=$deleteTarget")
            assertEquals(0, fixture.configWriteCount, "deleteTarget=$deleteTarget")
            assertEquals(serversBefore, fixture.serverLibrary, "deleteTarget=$deleteTarget")
            assertEquals(configsBefore, fixture.configLibrary, "deleteTarget=$deleteTarget")
        }
    }

    @Test
    fun targetIdentityChangedAfterServerSaveWithholdsMetadataAndProfile() = runBlocking {
        val fixture = CommitFixture(
            storedSubscriptions = listOf(StoredSubscription(id = 18, url = "https://example.com/provider", name = "Provider")),
            initialServers = DesktopServerLibrary(
                servers = listOf(
                    DesktopStoredProxyServer(
                        id = 10,
                        serverJson = proxy("old.example", "Old").encodePersistedProxyServer(),
                        subscriptionId = 18,
                    ),
                ),
            ),
        )
        val target = fixture.subscriptionRepository.catalog.value.subscriptions.single()
        fixture.afterServerPublish = {
            fixture.subscriptionLibrary = fixture.subscriptionLibrary.copy(
                subscriptions = fixture.subscriptionLibrary.subscriptions.map { stored ->
                    if (stored.id == target.id) stored.copy(url = "https://edited.example/provider") else stored
                },
            )
            fixture.subscriptionRepository.refresh(fixture.subscriptionLibrary)
        }

        val result = commitSubscriptionRefresh(
            loaded = loaded(target, listOf(proxy("fresh.example", "Fresh"))).copy(
                resolvedEmbeddedConfig = embeddedConfig(),
            ),
            refreshedAtMillis = 5_000L,
            port = fixture.adapter(target.id),
        )

        assertFalse(result.applicable)
        assertEquals(listOf(SubscriptionRefreshCommitScope.SERVERS), result.appliedScopes)
        assertNull(result.failure)
        assertEquals(1, fixture.serverWriteCount)
        assertEquals(0, fixture.subscriptionWriteCount)
        assertEquals(0, fixture.configWriteCount)
        assertEquals("https://edited.example/provider", fixture.subscriptionRepository.catalog.value.subscriptions.single().url)
        assertNull(fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
        assertEquals("Fresh", fixture.serverLibrary.servers.single { it.subscriptionId == target.id }.decode().getOrThrow().getInfo().remarks)
        assertTrue(fixture.configLibrary.configs.isEmpty())
    }

    @Test
    fun serverPersistenceFailureStopsLaterRefreshScopes() = runBlocking {
        val fixture = CommitFixture(
            storedSubscriptions = listOf(StoredSubscription(id = 15, url = "https://example.com/provider", name = "Provider")),
        ).apply { serverSaveFailure = IllegalStateException("servers.json unavailable") }
        val target = fixture.subscriptionRepository.catalog.value.subscriptions.single()
        val result = commitSubscriptionRefresh(
            loaded = loaded(target, listOf(proxy("fresh.example", "Fresh"))).copy(
                resolvedEmbeddedConfig = embeddedConfig(),
            ),
            refreshedAtMillis = 2_000L,
            port = fixture.adapter(target.id),
        )

        assertTrue(result.applicable)
        assertTrue(result.appliedScopes.isEmpty())
        assertEquals(SubscriptionRefreshCommitScope.SERVERS, result.failure?.scope)
        assertEquals("servers.json unavailable", result.failure?.error?.message)
        assertEquals(1, fixture.serverWriteCount)
        assertEquals(0, fixture.subscriptionWriteCount)
        assertEquals(0, fixture.configWriteCount)
        assertNull(fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
    }

    @Test
    fun subscriptionPersistenceFailureKeepsServerScopeAndSkipsProfile() = runBlocking {
        val fixture = CommitFixture(
            storedSubscriptions = listOf(StoredSubscription(id = 16, url = "https://example.com/provider", name = "Provider")),
            initialServers = DesktopServerLibrary(
                servers = listOf(
                    DesktopStoredProxyServer(
                        id = 8,
                        serverJson = proxy("old.example", "Old").encodePersistedProxyServer(),
                        subscriptionId = 16,
                    ),
                ),
            ),
        ).apply { subscriptionSaveFailure = IllegalStateException("subscriptions.json unavailable") }
        val target = fixture.subscriptionRepository.catalog.value.subscriptions.single()
        val result = commitSubscriptionRefresh(
            loaded = loaded(target, listOf(proxy("fresh.example", "Fresh"))).copy(
                resolvedEmbeddedConfig = embeddedConfig(),
            ),
            refreshedAtMillis = 3_000L,
            port = fixture.adapter(target.id),
        )

        assertTrue(result.applicable)
        assertEquals(listOf(SubscriptionRefreshCommitScope.SERVERS), result.appliedScopes)
        assertEquals(SubscriptionRefreshCommitScope.SUBSCRIPTION, result.failure?.scope)
        assertEquals("subscriptions.json unavailable", result.failure?.error?.message)
        assertEquals(1, fixture.serverWriteCount)
        assertEquals(1, fixture.subscriptionWriteCount)
        assertEquals(0, fixture.configWriteCount)
        assertEquals("Fresh", fixture.serverLibrary.servers.single { it.subscriptionId == 16 }.decode().getOrThrow().getInfo().remarks)
        assertNull(fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
    }

    @Test
    fun profilePersistenceFailureReportsServerAndSubscriptionScopesAsApplied() = runBlocking {
        val fixture = CommitFixture(
            storedSubscriptions = listOf(StoredSubscription(id = 17, url = "https://example.com/provider", name = "Provider")),
            initialServers = DesktopServerLibrary(
                servers = listOf(
                    DesktopStoredProxyServer(
                        id = 9,
                        serverJson = proxy("old.example", "Old").encodePersistedProxyServer(),
                        subscriptionId = 17,
                    ),
                ),
            ),
        ).apply { configSaveFailure = IllegalStateException("configs.json unavailable") }
        val target = fixture.subscriptionRepository.catalog.value.subscriptions.single()
        val result = commitSubscriptionRefresh(
            loaded = loaded(target, listOf(proxy("fresh.example", "Fresh"))).copy(
                resolvedEmbeddedConfig = embeddedConfig(),
            ),
            refreshedAtMillis = 4_000L,
            port = fixture.adapter(target.id),
        )

        assertTrue(result.applicable)
        assertEquals(
            listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
            result.appliedScopes,
        )
        assertEquals(SubscriptionRefreshCommitScope.PROFILE, result.failure?.scope)
        assertEquals("configs.json unavailable", result.failure?.error?.message)
        assertEquals(1, fixture.serverWriteCount)
        assertEquals(1, fixture.subscriptionWriteCount)
        assertEquals(1, fixture.configWriteCount)
        assertEquals(4_000L, fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
        assertTrue(fixture.configLibrary.configs.isEmpty())
    }

    @Test
    fun malformedEmbeddedProfileIsIgnoredAfterServersAndMetadataCommit() = runBlocking {
        val fixture = CommitFixture(
            storedSubscriptions = listOf(StoredSubscription(id = 23, url = "https://example.com/provider", name = "Provider")),
        )
        val target = fixture.subscriptionRepository.catalog.value.subscriptions.single()
        val result = commitSubscriptionRefresh(
            loaded = loaded(target, listOf(proxy("fresh.example", "Fresh"))).copy(
                resolvedEmbeddedConfig = ResolvedEmbeddedSubscriptionConfig(
                    content = "[Rule]\nFINAL,PROXY\nFINAL,DIRECT\n",
                    sourceUrl = "subscription://${target.id}",
                    fallbackName = target.title,
                    activate = true,
                ),
            ),
            refreshedAtMillis = 8_000L,
            port = fixture.adapter(target.id),
        )

        assertTrue(result.applicable)
        assertEquals(
            listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
            result.appliedScopes,
        )
        assertNull(result.failure)
        assertEquals(1, fixture.serverWriteCount)
        assertEquals(1, fixture.subscriptionWriteCount)
        assertEquals(0, fixture.configWriteCount)
        assertEquals(8_000L, fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
        assertTrue(fixture.configLibrary.configs.isEmpty())
    }

    @Test
    fun embeddedImportUsesLatestLibraryAndRefreshesLockedExistingProfileWithoutActivatingIt() = runBlocking {
        val subscription = StoredSubscription(id = 4, url = "https://example.com/provider", name = "Subscription name")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(subscription))
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated -> subscriptions = updated; Result.success(Unit) },
            publishLibrary = { subscriptions = it },
        )
        val lockedProfile = ConfigProfile(
            id = 5,
            name = "Old title",
            content = "[Rule]\nFINAL,DIRECT\n",
            sourceUrl = "https://example.com/routing.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 100L,
        )
        var configs = DesktopConfigLibrary(
            selectedConfigId = 5,
            configs = listOf(lockedProfile),
        )
        var servers = DesktopServerLibrary()
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = servers,
            readLibrary = { servers },
            saveLibrary = { updated -> servers = updated; Result.success(Unit) },
            publishLibrary = { servers = it },
        )
        val adapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = 4,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyRepository,
            readConfigs = { configs },
            saveConfigs = { updated -> configs = updated; Result.success(Unit) },
            publishConfigs = { configs = it },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
        )

        // This profile is added after the adapter is created but before the refresh commits.
        val manualProfile = ConfigProfile(7, "Manual", "[Rule]\nFINAL,DIRECT\n")
        configs = configs.copy(selectedConfigId = manualProfile.id, configs = configs.configs + manualProfile)
        adapter.applyEmbeddedConfig(
            ResolvedEmbeddedSubscriptionConfig(
                content = happRoutingJson,
                sourceUrl = "https://example.com/routing.conf",
                fallbackName = "Subscription name",
                activate = true,
            ),
        )

        val refreshed = configs.configs.single { it.id == lockedProfile.id }
        assertEquals("Happ routing", refreshed.name)
        assertEquals(lockedProfile.id, refreshed.id)
        assertEquals(lockedProfile.sourceUrl, refreshed.sourceUrl)
        assertTrue(refreshed.updateLocked)
        assertEquals(100L, refreshed.lastUpdatedAtMillis)
        assertTrue(refreshed.content.contains("DOMAIN-SUFFIX,example.com,PROXY"))
        val refreshedState = TrafficConfigState(
            id = refreshed.id,
            name = refreshed.name,
            rawConfig = refreshed.content,
        ).withSkipiSettingsReadFromRawConfig()
        assertEquals("", refreshedState.resourceSettings.userAgent)
        assertEquals(manualProfile, configs.configs.single { it.id == manualProfile.id })
        assertEquals(manualProfile.id, configs.selectedConfigId)
    }

    @Test
    fun embeddedImportActivatesOnlyNewProfileWhenRequested() = runBlocking {
        val subscription = StoredSubscription(id = 6, url = "https://example.com/provider", name = "Provider")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(subscription))
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated -> subscriptions = updated; Result.success(Unit) },
            publishLibrary = { subscriptions = it },
        )
        var configs = DesktopConfigLibrary(
            selectedConfigId = 3,
            configs = listOf(ConfigProfile(3, "Active", "[Rule]\nFINAL,DIRECT\n")),
        )
        var servers = DesktopServerLibrary()
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = servers,
            readLibrary = { servers },
            saveLibrary = { updated -> servers = updated; Result.success(Unit) },
            publishLibrary = { servers = it },
        )
        val adapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = 6,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyRepository,
            readConfigs = { configs },
            saveConfigs = { updated -> configs = updated; Result.success(Unit) },
            publishConfigs = { configs = it },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
        )

        adapter.applyEmbeddedConfig(
            ResolvedEmbeddedSubscriptionConfig(
                content = "[Rule]\nFINAL,PROXY\n",
                sourceUrl = "https://example.com/new-routing.conf",
                fallbackName = "Provider config",
                activate = true,
            ),
        )

        val created = configs.configs.single { it.sourceUrl == "https://example.com/new-routing.conf" }
        assertEquals(4, created.id)
        assertEquals("Provider config", created.name)
        assertEquals(created.id, configs.selectedConfigId)
        val createdState = TrafficConfigState(
            id = created.id,
            name = created.name,
            rawConfig = created.content,
        ).withSkipiSettingsReadFromRawConfig()
        assertEquals(
            versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME),
            createdState.resourceSettings.userAgent,
        )
    }

    @Test
    fun emptyRootImportIsRejectedBeforeAnyDesktopStoreWrite() = runBlocking {
        val subscription = StoredSubscription(id = 11, url = "https://example.com/provider", name = "Provider")
        var subscriptionLibrary = DesktopSubscriptionLibrary(subscriptions = listOf(subscription))
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptionLibrary,
            readLibrary = { subscriptionLibrary },
            saveLibrary = { updated -> subscriptionLibrary = updated; Result.success(Unit) },
            publishLibrary = { subscriptionLibrary = it },
        )
        var serverLibrary = DesktopServerLibrary(
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 1,
                    serverJson = proxy("old.example", "Existing").encodePersistedProxyServer(),
                    subscriptionId = 11,
                ),
            ),
        )
        var serverWrites = 0
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = serverLibrary,
            readLibrary = { serverLibrary },
            saveLibrary = { updated -> serverWrites++; serverLibrary = updated; Result.success(Unit) },
            publishLibrary = { serverLibrary = it },
        )
        var configLibrary = DesktopConfigLibrary()
        var configWrites = 0
        val adapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = 11,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyRepository,
            readConfigs = { configLibrary },
            saveConfigs = { updated -> configWrites++; configLibrary = updated; Result.success(Unit) },
            publishConfigs = { configLibrary = it },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
        )
        val beforeServers = serverLibrary
        val beforeSubscription = subscriptionRepository.catalog.value.subscriptions.single()

        val failure = runCatching {
            refreshSubscription(
                request = SubscriptionRefreshLoadRequest(
                    subscription = beforeSubscription,
                    fetchOptions = Unit,
                ),
                fetchResponse = { _, _, _ ->
                    SubscriptionFetchResponse(
                        body = "no supported servers",
                        headers = mapOf("profile-title" to "Must not commit"),
                    )
                },
                parsers = listOf<ProxyServerPayloadParser>({ _, _ -> EmptyProxyServerImportResult }),
                refreshedAtMillis = { 500L },
                port = adapter,
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(beforeServers, serverLibrary)
        assertEquals(0, serverWrites)
        assertEquals(0, configWrites)
        assertEquals(beforeSubscription, subscriptionRepository.catalog.value.subscriptions.single())
    }

    @Test
    fun cancellingDuringRootFetchDoesNotReachDesktopCommitAdapter() = runBlocking {
        val subscription = StoredSubscription(id = 12, url = "https://example.com/provider", name = "Provider")
        var subscriptionLibrary = DesktopSubscriptionLibrary(subscriptions = listOf(subscription))
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptionLibrary,
            readLibrary = { subscriptionLibrary },
            saveLibrary = { updated -> subscriptionLibrary = updated; Result.success(Unit) },
            publishLibrary = { subscriptionLibrary = it },
        )
        var serverLibrary = DesktopServerLibrary()
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = serverLibrary,
            readLibrary = { serverLibrary },
            saveLibrary = { updated -> serverLibrary = updated; Result.success(Unit) },
            publishLibrary = { serverLibrary = it },
        )
        var configLibrary = DesktopConfigLibrary()
        val adapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = 12,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyRepository,
            readConfigs = { configLibrary },
            saveConfigs = { updated -> configLibrary = updated; Result.success(Unit) },
            publishConfigs = { configLibrary = it },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
        )
        val request = SubscriptionRefreshLoadRequest(
            subscription = subscriptionRepository.catalog.value.subscriptions.single(),
            fetchOptions = Unit,
        )
        val fetchStarted = CompletableDeferred<Unit>()
        val job = launch {
            refreshSubscription(
                request = request,
                fetchResponse = { _, _, _ ->
                    fetchStarted.complete(Unit)
                    awaitCancellation()
                },
                parsers = emptyList(),
                refreshedAtMillis = { 700L },
                port = adapter,
            )
        }

        fetchStarted.await()
        job.cancelAndJoin()

        assertTrue(serverLibrary.servers.isEmpty())
        assertTrue(configLibrary.configs.isEmpty())
        assertNull(subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
    }

    private fun loaded(
        subscription: SubscriptionRecord,
        servers: List<ProxyServer<*>>,
        resolvedEmbeddedConfig: ResolvedEmbeddedSubscriptionConfig? = null,
    ) = LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = servers.size,
        servers = servers,
        metadata = SubscriptionMetadata(profileTitle = "Updated target", profileDescription = "fresh metadata"),
        resolvedEmbeddedConfig = resolvedEmbeddedConfig,
    )

    private fun embeddedConfig() = ResolvedEmbeddedSubscriptionConfig(
        content = "[Rule]\nFINAL,PROXY\n",
        sourceUrl = "https://example.com/routing.conf",
        fallbackName = "Provider",
        activate = false,
    )

    private fun proxy(host: String, name: String): ProxyServer<*> = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@$host:443?security=tls#$name",
    )

    private val happRoutingJson = """
        {
          "Name": "Happ routing",
          "RouteOrder": "proxy-direct",
          "GlobalProxy": "true",
          "ProxySites": ["example.com"],
          "DirectSites": [],
          "ProxyIp": [],
          "DirectIp": []
        }
    """.trimIndent()

    private class CommitFixture(
        storedSubscriptions: List<StoredSubscription>,
        initialServers: DesktopServerLibrary = DesktopServerLibrary(),
    ) {
        var subscriptionLibrary = DesktopSubscriptionLibrary(subscriptions = storedSubscriptions)
        var serverLibrary = initialServers
        var configLibrary = DesktopConfigLibrary()
        var latencyByServerId: Map<Int, DesktopServerLatencyResult> = emptyMap()
        var serverSaveFailure: Throwable? = null
        var subscriptionSaveFailure: Throwable? = null
        var configSaveFailure: Throwable? = null
        var serverWriteCount = 0
        var subscriptionWriteCount = 0
        var configWriteCount = 0
        var afterServerPublish: ((DesktopServerLibrary) -> Unit)? = null

        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptionLibrary,
            readLibrary = { subscriptionLibrary },
            saveLibrary = { updated ->
                subscriptionWriteCount += 1
                subscriptionSaveFailure?.let { failure -> Result.failure(failure) }
                    ?: run { subscriptionLibrary = updated; Result.success(Unit) }
            },
            publishLibrary = { subscriptionLibrary = it },
        )
        val proxyServerRepository = DesktopProxyServerRepository(
            initialLibrary = serverLibrary,
            readLibrary = { serverLibrary },
            saveLibrary = { updated ->
                serverWriteCount += 1
                serverSaveFailure?.let { failure -> Result.failure(failure) }
                    ?: run { serverLibrary = updated; Result.success(Unit) }
            },
            publishLibrary = { updated ->
                serverLibrary = updated
                afterServerPublish?.invoke(updated)
            },
        )

        fun adapter(subscriptionId: Int) = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = subscriptionId,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = { configLibrary },
            saveConfigs = { updated ->
                configWriteCount += 1
                configSaveFailure?.let { failure -> Result.failure(failure) }
                    ?: Result.success(Unit)
            },
            publishConfigs = { configLibrary = it },
            readLatencyByServerId = { latencyByServerId },
            publishLatencyByServerId = { latencyByServerId = it },
        )

        fun resetWriteCounts() {
            serverWriteCount = 0
            subscriptionWriteCount = 0
            configWriteCount = 0
        }
    }
}
