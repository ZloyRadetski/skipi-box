// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ProxyServerState
import app.SubscriptionGroupState
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.SubscriptionCatalogOperations
import data.AndroidAppStateStore
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidSubscriptionRepositoryCatalogTest {
    @Test
    fun updateCatalogWritesAndroidGroupsAndCurrentHighWaterCounterTogether() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            val builtIn = stateStore.currentState.subscriptionGroups.single { it.id == DefaultSubscriptionGroupId }
            stateStore.update(persist = false) { state ->
                state.copy(
                    subscriptionGroups = listOf(builtIn),
                    nextSubscriptionGroupId = 14,
                )
            }
            val repository = stateStore.appRepositories.subscriptions

            repository.updateCatalog { catalog ->
                SubscriptionCatalogOperations.createEditor(
                    catalog = catalog,
                    proxyCatalog = ProxyServerCatalog(),
                    draft = record(id = 4, title = "Manual servers", url = ""),
                ).catalog
            }

            val persisted = withTimeout(5_000) {
                repository.catalog.first { catalog -> catalog.subscriptions.any { it.id == 14 } }
            }
            assertEquals(listOf(DefaultSubscriptionGroupId, 14), persisted.subscriptions.map { it.id })
            assertEquals(15, persisted.nextSubscriptionId)
            assertEquals(listOf(DefaultSubscriptionGroupId, 14), stateStore.currentState.subscriptionGroups.map { it.id })
            assertEquals(15, stateStore.currentState.nextSubscriptionGroupId)
            assertEquals("", stateStore.currentState.subscriptionGroups.last().url)
            assertEquals(false, stateStore.currentState.subscriptionGroups.last().builtIn)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun removeUsesSharedCleanupAndPreservesAndroidGroupAndServerState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            val builtIn = stateStore.currentState.subscriptionGroups.single { it.id == DefaultSubscriptionGroupId }
            val target = record(id = 14, title = "Target", url = "https://target.example/sub").toAndroidGroup(null)
            val neighbor = record(id = 20, title = "Neighbor", url = "https://neighbor.example/sub")
                .copy(
                    metadata = features.subscription.SubscriptionMetadata(
                        profileTitle = "Latest profile title",
                        profileDescription = "Preserved metadata",
                    ),
                    lastUpdatedAtMillis = 5678,
                )
                .toAndroidGroup(null)
            val strategy = StrategyGroup(
                remarks = "Strategy",
                proxyServerIds = listOf(31, 32),
                selectedMemberId = 31,
            )
            val chain = ChainProxy(remarks = "Chain", proxyServerIds = listOf(31, 32))
            stateStore.update(persist = false) { state ->
                state.copy(
                    subscriptionGroups = listOf(builtIn, target, neighbor),
                    nextSubscriptionGroupId = 21,
                    proxyServers = listOf(
                        ProxyServerState(31, HTTP(server = "target.example"), 14, "31 ms"),
                        ProxyServerState(32, HTTP(server = "neighbor.example"), 20, "32 ms"),
                        ProxyServerState(40, strategy, groupId = -2, latency = "40 ms"),
                        ProxyServerState(41, chain, groupId = -2, latency = "41 ms"),
                    ),
                    nextProxyServerId = 50,
                    selectedProxyServerId = 31,
                    proxyRunning = true,
                )
            }

            stateStore.appRepositories.subscriptions.remove(14)

            val result = stateStore.currentState
            assertEquals(listOf(DefaultSubscriptionGroupId, 20), result.subscriptionGroups.map { it.id })
            assertEquals(neighbor, result.subscriptionGroups.last())
            assertEquals(21, result.nextSubscriptionGroupId)
            assertEquals(listOf(32, 40, 41), result.proxyServers.map { it.id })
            assertEquals(listOf(20, -2, -2), result.proxyServers.map { it.groupId })
            assertEquals(listOf("32 ms", "40 ms", "41 ms"), result.proxyServers.map { it.latency })
            assertEquals(listOf(32), (result.proxyServers[1].server as StrategyGroup).proxyServerIds)
            assertEquals(32, (result.proxyServers[1].server as StrategyGroup).selectedMemberId)
            assertEquals(listOf(32), (result.proxyServers[2].server as ChainProxy).proxyServerIds)
            assertEquals(50, result.nextProxyServerId)
            assertEquals(32, result.selectedProxyServerId)
            assertFalse(result.proxyRunning)
        } finally {
            stateStore.resetToStockState()
        }
    }

    private fun record(id: Int, title: String, url: String) = SubscriptionRecord(
        id = id,
        title = title,
        url = url,
        userAgent = "Android test agent",
        updateInterval = "",
        hwid = "",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = true,
        enabled = true,
        builtIn = false,
        notifyOnExpiry = true,
        customExpiryReminders = null,
    )
}
