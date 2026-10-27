// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ProxyServerState
import app.SubscriptionGroupState
import app.skipi.app.home.ProxyServerSelectionRuntimeAction
import app.skipi.app.home.decideProxyServerSelectionRuntimeAction
import data.AndroidAppStateStore
import features.proxy.server.list.observeHomeServerSelectionChanges
import features.proxy.server.model.VLESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Android oracle for the real batch commit followed by Home's selection observer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidBatchSubscriptionRefreshRuntimeOracleTest {
    @Test
    fun refreshingInactiveSubscriptionsDoesNotEmitASelectionRuntimeTransition() = runBlocking {
        val stateStore = cleanStateStore()
        val first = subscriptionGroup(id = 4)
        val second = subscriptionGroup(id = 5)
        val initialSelectedId = 33
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(first, second),
                proxyServers = listOf(
                    subscribedServer(id = 7, groupId = first.id, remarks = "Old first"),
                    subscribedServer(id = 8, groupId = second.id, remarks = "Old second"),
                    manualServer(initialSelectedId),
                ),
                nextProxyServerId = 34,
                selectedProxyServerId = initialSelectedId,
                proxyRunning = true,
            )
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val transitions = mutableListOf<Pair<Int, Int>>()
        val runtimeActions = mutableListOf<ProxyServerSelectionRuntimeAction>()
        val observer = scope.observeHomeServerSelectionChanges(stateStore.state) { previousId, selectedId ->
            transitions += previousId to selectedId
            runtimeActions += decideProxyServerSelectionRuntimeAction(
                previousSelectedServerId = previousId,
                selectedServerId = selectedId,
                requestedServerId = selectedId,
                serverExists = stateStore.currentState.proxyServers.any { it.id == selectedId },
                runtimeRunning = true,
                activeServerId = previousId,
            )
        }

        try {
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = listOf(
                    refreshedServers(first, "Refreshed first", "fresh-first", "fresh-first.example.test"),
                    refreshedServers(second, "Refreshed second", "fresh-second", "fresh-second.example.test"),
                ),
                updatedAtMillis = 1_100L,
                updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
            )

            assertEquals(initialSelectedId, stateStore.currentState.selectedProxyServerId)
            assertTrue(stateStore.currentState.proxyServers.any { it.id == initialSelectedId })
            assertEquals(emptyList(), transitions)
            assertEquals(emptyList(), runtimeActions)
        } finally {
            observer.cancel()
            scope.cancel()
            stateStore.resetToStockState()
        }
    }

    @Test
    fun batchRefreshThatRemovesTheSelectedServerEmitsOneRuntimeRestartTransition() = runBlocking {
        val stateStore = cleanStateStore()
        val first = subscriptionGroup(id = 4)
        val second = subscriptionGroup(id = 5)
        val previousSelectedId = 7
        val fallbackSelectedId = 33
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(first, second),
                proxyServers = listOf(
                    subscribedServer(id = previousSelectedId, groupId = first.id, remarks = "Selected first"),
                    subscribedServer(id = 8, groupId = second.id, remarks = "Second"),
                    manualServer(fallbackSelectedId),
                ),
                nextProxyServerId = 34,
                selectedProxyServerId = previousSelectedId,
                proxyRunning = true,
            )
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val transitions = mutableListOf<Pair<Int, Int>>()
        val runtimeActions = mutableListOf<ProxyServerSelectionRuntimeAction>()
        val observer = scope.observeHomeServerSelectionChanges(stateStore.state) { previousId, selectedId ->
            transitions += previousId to selectedId
            runtimeActions += decideProxyServerSelectionRuntimeAction(
                previousSelectedServerId = previousId,
                selectedServerId = selectedId,
                requestedServerId = selectedId,
                serverExists = stateStore.currentState.proxyServers.any { it.id == selectedId },
                runtimeRunning = true,
                activeServerId = previousId,
            )
        }

        try {
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = listOf(
                    refreshedServers(first, emptyList()),
                    refreshedServers(second, emptyList()),
                ),
                updatedAtMillis = 1_200L,
                updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
            )

            assertEquals(fallbackSelectedId, stateStore.currentState.selectedProxyServerId)
            assertTrue(stateStore.currentState.proxyServers.any { it.id == fallbackSelectedId })
            assertEquals(listOf(previousSelectedId to fallbackSelectedId), transitions)
            assertEquals(listOf(ProxyServerSelectionRuntimeAction.Restart), runtimeActions)
        } finally {
            observer.cancel()
            scope.cancel()
            stateStore.resetToStockState()
        }
    }

    private suspend fun cleanStateStore(): AndroidAppStateStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AndroidAppStateStore.get(context).also { it.resetToStockState() }
    }

    private fun subscriptionGroup(id: Int) = SubscriptionGroupState(
        id = id,
        name = "Stored subscription $id",
        url = "https://subscription.example.test/$id",
        userAgent = "Skipi/1 Android",
        updateInterval = "6",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = false,
        enabled = true,
    )

    private fun subscribedServer(id: Int, groupId: Int, remarks: String) = ProxyServerState(
        id = id,
        server = VLESS(remarks = remarks, id = "uuid-$id", server = "$remarks.example.test", port = "443"),
        groupId = groupId,
    )

    private fun manualServer(id: Int) = ProxyServerState(
        id = id,
        server = VLESS(remarks = "Manual", id = "manual-$id", server = "manual.example.test", port = "443"),
        groupId = 90,
    )

    private fun refreshedServers(
        group: SubscriptionGroupState,
        remarks: String,
        uuid: String,
        host: String,
    ) = refreshedServers(
        group = group,
        servers = listOf(VLESS(remarks = remarks, id = uuid, server = host, port = "443")),
    )

    private fun refreshedServers(
        group: SubscriptionGroupState,
        servers: List<VLESS>,
    ) = ProxyServerListSubscriptionUpdate(
        groupId = group.id,
        sourceIdentity = group.subscriptionFetchIdentity(),
        urlCount = servers.size,
        servers = servers,
    )
}
