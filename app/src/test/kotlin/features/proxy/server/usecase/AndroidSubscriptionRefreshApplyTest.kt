// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import data.AndroidAppStateStore
import features.config.TrafficConfigState
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidSubscriptionRefreshApplyTest {
    @Test
    fun batchEligibilityMatchesEnabledNonblankUrlRuleAndKeepsGroupOrder() {
        val eligibleFirst = subscriptionGroup().copy(id = 1, url = "https://first.example.test/list")
        val disabled = subscriptionGroup().copy(id = 2, enabled = false)
        val blankUrl = subscriptionGroup().copy(id = 3, url = "  ")
        val eligibleLast = subscriptionGroup().copy(id = 5, url = "https://last.example.test/list")

        assertEquals(
            listOf(eligibleFirst.id, eligibleLast.id),
            listOf(eligibleFirst, disabled, blankUrl, eligibleLast).updatableSubscriptionGroups().map { it.id },
        )
    }

    @Test
    fun applyProxySubscriptionUpdatesRebasesCurrentCatalogAndSubscriptionMetadata() = runBlocking {
        val stateStore = cleanStateStore()
        val group = subscriptionGroup()
        val latestManual = ProxyServerState(
            id = 33,
            server = VLESS(remarks = "Manual", id = "manual-uuid", server = "manual.example.test", port = "443"),
            groupId = 90,
            latency = "9 ms",
        )
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(group),
                proxyServers = listOf(latestManual),
                nextProxyServerId = 34,
                selectedProxyServerId = 33,
            )
        }

        try {
            var appStateUpdates = 0
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = listOf(refreshUpdate(group)),
                updatedAtMillis = 900L,
                updateAppState = { transform ->
                    appStateUpdates += 1
                    stateStore.update(persist = false, transform = transform)
                },
            )

            val applied = stateStore.currentState
            assertEquals(1, appStateUpdates)
            assertEquals("Updated profile", applied.subscriptionGroups.single().name)
            assertEquals("18", applied.subscriptionGroups.single().updateInterval)
            assertEquals(900L, applied.subscriptionGroups.single().lastUpdatedAtMillis)
            assertEquals(latestManual, applied.proxyServers.single { it.id == latestManual.id })
            assertEquals("New subscription server", applied.proxyServers.single { it.groupId == group.id }.server.getInfo().remarks)
            assertEquals(35, applied.nextProxyServerId)
            assertEquals(33, applied.selectedProxyServerId)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun cancelledCallerDoesNotUpdateAndroidCatalogOrSubscriptionState() = runBlocking {
        val stateStore = cleanStateStore()
        val group = subscriptionGroup()
        val originalServer = ProxyServerState(
            id = 7,
            server = VLESS(remarks = "Existing", id = "existing-uuid", server = "existing.example.test", port = "443"),
            groupId = group.id,
            latency = "22 ms",
        )
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(group),
                proxyServers = listOf(originalServer),
                nextProxyServerId = 8,
                selectedProxyServerId = 7,
            )
        }
        val before = stateStore.currentState
        val cancelledJob = Job().apply { cancel() }
        var completion: Result<Unit>? = null

        try {
            suspend {
                applyProxySubscriptionUpdates(
                    stateStore = stateStore,
                    updates = listOf(refreshUpdate(group)),
                    updatedAtMillis = 901L,
                    updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
                )
            }.startCoroutine(object : Continuation<Unit> {
                override val context = cancelledJob

                override fun resumeWith(result: Result<Unit>) {
                    completion = result
                }
            })

            assertTrue(requireNotNull(completion).exceptionOrNull() is CancellationException)
            assertEquals(before.proxyServers, stateStore.currentState.proxyServers)
            assertEquals(before.subscriptionGroups, stateStore.currentState.subscriptionGroups)
            assertEquals(before.nextProxyServerId, stateStore.currentState.nextProxyServerId)
            assertEquals(before.selectedProxyServerId, stateStore.currentState.selectedProxyServerId)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun multiGroupBatchKeepsSharedReferencesSelectionAndBothEmbeddedProfiles() = runBlocking {
        val stateStore = cleanStateStore()
        val firstGroup = subscriptionGroup()
        val secondGroup = subscriptionGroup().copy(
            id = 5,
            name = "Second stored subscription",
            url = "https://subscription.example.test/second",
        )
        val initialServers = listOf(
            ProxyServerState(
                id = 7,
                server = HTTP(remarks = "Old first", server = "old-first.example.test", port = "443"),
                groupId = firstGroup.id,
                latency = "20 ms",
            ),
            ProxyServerState(
                id = 8,
                server = VLESS(remarks = "Old second", id = "old-second", server = "old-second.example.test", port = "443"),
                groupId = secondGroup.id,
                latency = "30 ms",
            ),
            ProxyServerState(
                id = 33,
                server = VLESS(remarks = "Manual", id = "manual", server = "manual.example.test", port = "443"),
                groupId = 90,
                latency = "9 ms",
            ),
            ProxyServerState(
                id = 100,
                server = StrategyGroup(remarks = "Balancer", proxyServerIds = listOf(7, 8), selectedMemberId = 8),
                groupId = -2,
            ),
            ProxyServerState(
                id = 101,
                server = ChainProxy(remarks = "Chain", proxyServerIds = listOf(7, 8)),
                groupId = -2,
            ),
        )
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(firstGroup, secondGroup),
                proxyServers = initialServers,
                // Keep the catalog high-water mark above the composite rows.
                nextProxyServerId = 102,
                selectedProxyServerId = 7,
                trafficConfigs = emptyList<TrafficConfigState>(),
                nextTrafficConfigId = 1,
                activeTrafficConfigId = 0,
            )
        }

        try {
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = listOf(
                    batchRefreshUpdate(
                        group = firstGroup,
                        serverName = "First refreshed",
                        serverId = "fresh-first",
                        host = "fresh-first.example.test",
                        title = "Updated first profile",
                        configUrl = "subscription://${firstGroup.id}",
                        configContent = "[General]\ndns-server = 1.1.1.1\n[Rule]\nFINAL,DIRECT",
                    ),
                    batchRefreshUpdate(
                        group = secondGroup,
                        serverName = "Second refreshed",
                        serverId = "fresh-second",
                        host = "fresh-second.example.test",
                        title = "Updated second profile",
                        configUrl = "subscription://${secondGroup.id}",
                        configContent = "[General]\ndns-server = 8.8.8.8\n[Rule]\nFINAL,DIRECT",
                    ),
                ),
                updatedAtMillis = 1_100L,
                updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
            )

            val applied = stateStore.currentState
            assertEquals(listOf("Updated first profile", "Updated second profile"), applied.subscriptionGroups.map { it.name })
            assertEquals(1_100L, applied.subscriptionGroups.first().lastUpdatedAtMillis)
            assertEquals(1_100L, applied.subscriptionGroups.last().lastUpdatedAtMillis)
            assertEquals(33, applied.selectedProxyServerId)
            assertEquals(103, applied.nextProxyServerId)

            assertEquals(
                listOf(
                    Triple(102, firstGroup.id, "First refreshed"),
                    Triple(8, secondGroup.id, "Second refreshed"),
                    Triple(33, 90, "Manual"),
                    Triple(100, -2, "Balancer"),
                    Triple(101, -2, "Chain"),
                ),
                applied.proxyServers.map { server ->
                    Triple(server.id, server.groupId, server.server.getInfo().remarks)
                },
            )

            val importedIds = applied.proxyServers
                .filter { it.groupId == firstGroup.id || it.groupId == secondGroup.id }
                .associate { it.server.getInfo().remarks to it.id }
            assertEquals(mapOf("First refreshed" to 102, "Second refreshed" to 8), importedIds)
            assertEquals(
                listOf(importedIds.getValue("First refreshed"), importedIds.getValue("Second refreshed")),
                (applied.proxyServers.single { it.id == 100 }.server as StrategyGroup).proxyServerIds,
            )
            assertEquals(
                importedIds.getValue("Second refreshed"),
                (applied.proxyServers.single { it.id == 100 }.server as StrategyGroup).selectedMemberId,
            )
            assertEquals(
                listOf(importedIds.getValue("First refreshed"), importedIds.getValue("Second refreshed")),
                (applied.proxyServers.single { it.id == 101 }.server as ChainProxy).proxyServerIds,
            )

            assertEquals(
                setOf("subscription://${firstGroup.id}", "subscription://${secondGroup.id}"),
                applied.trafficConfigs.mapNotNull { config -> config.sourceUrl }.toSet(),
            )
            assertEquals(
                applied.trafficConfigs.single { it.sourceUrl == "subscription://${secondGroup.id}" }.id,
                applied.activeTrafficConfigId,
            )
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun androidOriginalSameProtocolBatchReusesRowsAndKeepsSelectionAndCounter() {
        val firstGroup = subscriptionGroup()
        val secondGroup = subscriptionGroup().copy(
            id = 5,
            name = "Second stored subscription",
            url = "https://subscription.example.test/second",
        )
        val original = AppState().copy(
            subscriptionGroups = listOf(firstGroup, secondGroup),
            proxyServers = listOf(
                ProxyServerState(
                    id = 11,
                    server = VLESS(remarks = "Old first", id = "old-first", server = "old-first.example.test", port = "443"),
                    groupId = firstGroup.id,
                ),
                ProxyServerState(
                    id = 12,
                    server = VLESS(remarks = "Old second", id = "old-second", server = "old-second.example.test", port = "443"),
                    groupId = secondGroup.id,
                ),
                ProxyServerState(
                    id = 33,
                    server = VLESS(remarks = "Manual", id = "manual", server = "manual.example.test", port = "443"),
                    groupId = 90,
                ),
            ),
            nextProxyServerId = 100,
            selectedProxyServerId = 11,
        )
        val updates = listOf(
            refreshUpdate(firstGroup).copy(
                servers = listOf(
                    VLESS(remarks = "Refresh first", id = "new-first", server = "new-first.example.test", port = "443"),
                ),
            ),
            refreshUpdate(secondGroup).copy(
                servers = listOf(
                    VLESS(remarks = "Refresh second", id = "new-second", server = "new-second.example.test", port = "443"),
                ),
            ),
        )

        val updated = original.withUpdatedSubscriptionServers(updates, updatedAtMillis = 1_400L)

        assertEquals(11, updated.selectedProxyServerId)
        assertEquals(100, updated.nextProxyServerId)
        assertEquals(
            listOf(
                Triple(11, firstGroup.id, "Refresh first"),
                Triple(12, secondGroup.id, "Refresh second"),
                Triple(33, 90, "Manual"),
            ),
            updated.proxyServers.map { server ->
                Triple(server.id, server.groupId, server.server.getInfo().remarks)
            },
        )
    }

    @Test
    fun batchApplyRebasesLatestRowsAndSkipsChangedOrDeletedSubscriptionIdentities() = runBlocking {
        val stateStore = cleanStateStore()
        val firstGroup = subscriptionGroup()
        val changedGroup = subscriptionGroup().copy(
            id = 5,
            name = "Latest edited second",
            url = "https://subscription.example.test/second-edited",
        )
        val removedGroup = subscriptionGroup().copy(
            id = 6,
            name = "Removed third",
            url = "https://subscription.example.test/third",
        )
        val latestManual = ProxyServerState(
            id = 33,
            server = VLESS(remarks = "Latest manual", id = "manual-latest", server = "manual.example.test", port = "443"),
            groupId = 90,
            latency = "9 ms",
        )
        val latestServers = listOf(
            ProxyServerState(
                id = 7,
                server = HTTP(remarks = "Old first", server = "old-first.example.test", port = "443"),
                groupId = firstGroup.id,
                latency = "20 ms",
            ),
            ProxyServerState(
                id = 8,
                server = VLESS(remarks = "Old second", id = "old-second", server = "old-second.example.test", port = "443"),
                groupId = changedGroup.id,
                latency = "30 ms",
            ),
            ProxyServerState(
                id = 9,
                server = VLESS(remarks = "Old removed", id = "old-removed", server = "old-removed.example.test", port = "443"),
                groupId = removedGroup.id,
                latency = "40 ms",
            ),
            latestManual,
        )
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(firstGroup, changedGroup),
                proxyServers = latestServers,
                nextProxyServerId = 40,
                selectedProxyServerId = latestManual.id,
                trafficConfigs = emptyList<TrafficConfigState>(),
                nextTrafficConfigId = 1,
                activeTrafficConfigId = 0,
            )
        }

        try {
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = listOf(
                    batchRefreshUpdate(
                        group = firstGroup,
                        serverName = "Fresh first",
                        serverId = "fresh-first",
                        host = "fresh-first.example.test",
                        title = "Fresh first profile",
                        configUrl = "subscription://${firstGroup.id}",
                        configContent = "[General]\ndns-server = 1.1.1.1\n[Rule]\nFINAL,DIRECT",
                    ),
                    batchRefreshUpdate(
                        group = subscriptionGroup().copy(id = changedGroup.id, url = "https://subscription.example.test/second"),
                        serverName = "Stale second response",
                        serverId = "stale-second",
                        host = "stale-second.example.test",
                        title = "Stale second profile",
                        configUrl = "subscription://${changedGroup.id}",
                        configContent = "[General]\ndns-server = 8.8.8.8\n[Rule]\nFINAL,DIRECT",
                    ),
                    batchRefreshUpdate(
                        group = removedGroup,
                        serverName = "Removed third response",
                        serverId = "removed-third",
                        host = "removed-third.example.test",
                        title = "Removed third profile",
                        configUrl = "subscription://${removedGroup.id}",
                        configContent = "[General]\ndns-server = 9.9.9.9\n[Rule]\nFINAL,DIRECT",
                    ),
                ),
                updatedAtMillis = 1_200L,
                updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
            )

            val applied = stateStore.currentState
            assertEquals(listOf("Fresh first profile", "Latest edited second"), applied.subscriptionGroups.map { it.name })
            assertEquals(1_200L, applied.subscriptionGroups.first().lastUpdatedAtMillis)
            assertEquals(changedGroup, applied.subscriptionGroups.last())
            assertEquals(33, applied.selectedProxyServerId)
            assertEquals(41, applied.nextProxyServerId)
            assertEquals(
                listOf(
                    Triple(40, firstGroup.id, "Fresh first"),
                    Triple(8, changedGroup.id, "Old second"),
                    Triple(9, removedGroup.id, "Old removed"),
                    Triple(33, 90, "Latest manual"),
                ),
                applied.proxyServers.map { server ->
                    Triple(server.id, server.groupId, server.server.getInfo().remarks)
                },
            )
            assertEquals(
                setOf("subscription://${firstGroup.id}"),
                applied.trafficConfigs.mapNotNull { config -> config.sourceUrl }.toSet(),
            )
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun batchApplyFailureAfterCatalogWriteLeavesTheCurrentPartialResultVisible() = runBlocking {
        val stateStore = cleanStateStore()
        val firstGroup = subscriptionGroup()
        val secondGroup = subscriptionGroup().copy(
            id = 5,
            name = "Second stored subscription",
            url = "https://subscription.example.test/second",
        )
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(firstGroup, secondGroup),
                proxyServers = listOf(
                    ProxyServerState(
                        id = 7,
                        server = HTTP(remarks = "Old first", server = "old-first.example.test", port = "443"),
                        groupId = firstGroup.id,
                    ),
                    ProxyServerState(
                        id = 8,
                        server = VLESS(remarks = "Old second", id = "old-second", server = "old-second.example.test", port = "443"),
                        groupId = secondGroup.id,
                    ),
                ),
                nextProxyServerId = 9,
                selectedProxyServerId = 7,
                trafficConfigs = emptyList<TrafficConfigState>(),
                nextTrafficConfigId = 1,
                activeTrafficConfigId = 0,
            )
        }

        try {
            val failure = assertFailsWith<IllegalStateException> {
                applyProxySubscriptionUpdates(
                    stateStore = stateStore,
                    updates = listOf(
                        batchRefreshUpdate(
                            group = firstGroup,
                            serverName = "First catalog write",
                            serverId = "first-written",
                            host = "first-written.example.test",
                            title = "First metadata that will not save",
                            configUrl = "subscription://${firstGroup.id}",
                            configContent = "[General]\ndns-server = 1.1.1.1\n[Rule]\nFINAL,DIRECT",
                        ),
                        batchRefreshUpdate(
                            group = secondGroup,
                            serverName = "Second catalog write",
                            serverId = "second-written",
                            host = "second-written.example.test",
                            title = "Second metadata that will not save",
                            configUrl = "subscription://${secondGroup.id}",
                            configContent = "[General]\ndns-server = 8.8.8.8\n[Rule]\nFINAL,DIRECT",
                        ),
                    ),
                    updatedAtMillis = 1_300L,
                    updateAppState = { error("injected subscription metadata write failure") },
                )
            }

            val partiallyApplied = stateStore.currentState
            assertEquals("injected subscription metadata write failure", failure.message)
            assertEquals(listOf("Stored subscription", "Second stored subscription"), partiallyApplied.subscriptionGroups.map { it.name })
            assertEquals(0L, partiallyApplied.subscriptionGroups.first().lastUpdatedAtMillis)
            assertEquals(
                listOf("First catalog write", "Second catalog write"),
                partiallyApplied.proxyServers.map { it.server.getInfo().remarks },
            )
            assertEquals(emptyList(), partiallyApplied.trafficConfigs)
        } finally {
            stateStore.resetToStockState()
        }
    }
}

private suspend fun cleanStateStore(): AndroidAppStateStore {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AndroidAppStateStore.get(context).also { it.resetToStockState() }
}

private fun subscriptionGroup() = SubscriptionGroupState(
    id = 4,
    name = "Stored subscription",
    url = "https://subscription.example.test/list",
    userAgent = "Skipi/1 Android",
    updateInterval = "6",
    ageSecretKey = "",
    updateViaProxy = false,
    autoOverrideRules = false,
    enabled = true,
)

private fun refreshUpdate(group: SubscriptionGroupState) = ProxyServerListSubscriptionUpdate(
    groupId = group.id,
    sourceIdentity = group.subscriptionFetchIdentity(),
    urlCount = 1,
    servers = listOf(
        VLESS(
            remarks = "New subscription server",
            id = "new-uuid",
            server = "new.example.test",
            port = "443",
        ),
    ),
    metadata = SubscriptionMetadata(
        profileTitle = "Updated profile",
        profileUpdateIntervalHours = "18",
    ),
)

private fun batchRefreshUpdate(
    group: SubscriptionGroupState,
    serverName: String,
    serverId: String,
    host: String,
    title: String,
    configUrl: String,
    configContent: String,
) = ProxyServerListSubscriptionUpdate(
    groupId = group.id,
    sourceIdentity = group.subscriptionFetchIdentity(),
    urlCount = 1,
    servers = listOf(VLESS(remarks = serverName, id = serverId, server = host, port = "443")),
    metadata = SubscriptionMetadata(profileTitle = title),
    resolvedConfig = ResolvedEmbeddedTrafficConfig(
        content = configContent,
        sourceUrl = configUrl,
        fallbackName = title,
        activate = true,
    ),
)
