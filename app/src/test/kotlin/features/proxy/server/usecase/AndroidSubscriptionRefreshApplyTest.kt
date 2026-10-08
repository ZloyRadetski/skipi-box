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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidSubscriptionRefreshApplyTest {
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
