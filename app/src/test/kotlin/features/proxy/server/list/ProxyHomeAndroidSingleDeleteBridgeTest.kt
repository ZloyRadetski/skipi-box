// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ProxyServerState
import app.skipi.app.proxy.ProxyServerStopOutcome
import app.skipi.app.proxy.runSingleProxyServerDeleteWhenRequired
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import data.AndroidAppStateStore
import features.proxy.server.model.HTTP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Checks Android Home's single-row delete ordering against its Room-backed catalog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProxyHomeAndroidSingleDeleteBridgeTest {
    @Test
    fun selectedRunningServerStopsBeforeRemovalAndSuccessFeedback() = runBlocking {
        val stateStore = prepareStore()
        try {
            val events = mutableListOf<String>()
            var deleteActionId: Long? = null

            runSingleProxyServerDeleteWhenRequired(
                serverId = 7,
                selectedServerId = 7,
                serviceRunning = true,
                serviceOperationInProgress = false,
                stopService = {
                    events += "stop"
                    ProxyServerStopOutcome.Stopped
                },
                dispatchDeleteIfPresent = {
                    if (stateStore.currentState.proxyServers.none { it.id == 7 }) {
                        false
                    } else {
                        events += "delete"
                        deleteActionId = stateStore.sharedApplicationStore.dispatch(
                            SharedApplicationAction.RemoveProxyServer(7),
                        )
                        true
                    }
                },
                applyStopState = {
                    events += "stop-state"
                    stateStore.update(persist = false) { state -> state.copy(proxyRunning = false) }
                },
                onDeleted = { events += "deleted" },
                onMissingServer = { events += "missing" },
                onStopFailed = { events += "stop-error" },
            )

            assertEquals(listOf("stop", "delete", "stop-state", "deleted"), events)
            assertEquals(SharedApplicationActionOutcome.Completed, awaitDeleteOutcome(stateStore, deleteActionId))
            assertEquals(listOf(8), stateStore.currentState.proxyServers.map { it.id })
            assertFalse(stateStore.currentState.proxyRunning)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun unselectedServerIsRemovedDirectlyWhileAnotherServerRuns() = runBlocking {
        val stateStore = prepareStore(selectedServerId = 8)
        try {
            val events = mutableListOf<String>()
            var deleteActionId: Long? = null

            runSingleProxyServerDeleteWhenRequired(
                serverId = 7,
                selectedServerId = 8,
                serviceRunning = true,
                serviceOperationInProgress = false,
                stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
                dispatchDeleteIfPresent = {
                    events += "delete"
                    deleteActionId = stateStore.sharedApplicationStore.dispatch(
                        SharedApplicationAction.RemoveProxyServer(7),
                    )
                    true
                },
                applyStopState = { events += "stop-state" },
                onDeleted = { events += "deleted" },
                onMissingServer = { events += "missing" },
                onStopFailed = { events += "stop-error" },
            )

            assertEquals(listOf("delete", "deleted"), events)
            assertEquals(SharedApplicationActionOutcome.Completed, awaitDeleteOutcome(stateStore, deleteActionId))
            assertEquals(listOf(8), stateStore.currentState.proxyServers.map { it.id })
            assertTrue(stateStore.currentState.proxyRunning)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun stopFailureLeavesTheCatalogAndSuccessFeedbackUntouched() = runBlocking {
        val stateStore = prepareStore()
        try {
            val before = stateStore.currentState
            val failure = IllegalStateException("stop failed")
            val events = mutableListOf<String>()

            runSingleProxyServerDeleteWhenRequired(
                serverId = 7,
                selectedServerId = 7,
                serviceRunning = true,
                serviceOperationInProgress = false,
                stopService = { events += "stop"; ProxyServerStopOutcome.Failed(failure) },
                dispatchDeleteIfPresent = { events += "delete"; true },
                applyStopState = { events += "stop-state" },
                onDeleted = { events += "deleted" },
                onMissingServer = { events += "missing" },
                onStopFailed = { reported ->
                    assertSame(failure, reported)
                    events += "stop-error"
                },
            )

            assertEquals(listOf("stop", "stop-error"), events)
            assertEquals(before.proxyServers, stateStore.currentState.proxyServers)
            assertEquals(before.proxyRunning, stateStore.currentState.proxyRunning)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun cancelledStopLeavesTheCatalogAndSuccessFeedbackUntouched() = runBlocking {
        val stateStore = prepareStore()
        try {
            val before = stateStore.currentState
            val cancellation = CancellationException("stop cancelled")
            val events = mutableListOf<String>()

            val thrown = runCatching {
                runSingleProxyServerDeleteWhenRequired(
                    serverId = 7,
                    selectedServerId = 7,
                    serviceRunning = true,
                    serviceOperationInProgress = false,
                    stopService = { events += "stop"; throw cancellation },
                    dispatchDeleteIfPresent = { events += "delete"; true },
                    applyStopState = { events += "stop-state" },
                    onDeleted = { events += "deleted" },
                    onMissingServer = { events += "missing" },
                    onStopFailed = { events += "stop-error" },
                )
            }.exceptionOrNull()

            assertSame(cancellation, thrown)
            assertEquals(listOf("stop"), events)
            assertEquals(before.proxyServers, stateStore.currentState.proxyServers)
            assertEquals(before.proxyRunning, stateStore.currentState.proxyRunning)
        } finally {
            stateStore.resetToStockState()
        }
    }

    private suspend fun prepareStore(selectedServerId: Int = 7): AndroidAppStateStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AndroidAppStateStore.get(context).also { stateStore ->
            stateStore.resetToStockState()
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(
                        ProxyServerState(7, HTTP(server = "selected.example"), groupId = 72),
                        ProxyServerState(8, HTTP(server = "other.example"), groupId = 73),
                    ),
                    nextProxyServerId = 80,
                    selectedProxyServerId = selectedServerId,
                    proxyRunning = true,
                )
            }
        }
    }

    private suspend fun awaitDeleteOutcome(
        stateStore: AndroidAppStateStore,
        actionId: Long?,
    ): SharedApplicationActionOutcome {
        val expectedActionId = requireNotNull(actionId)
        return withTimeout(10_000) {
            stateStore.sharedApplicationStore.lastActionResult.first { result ->
                result?.actionId == expectedActionId
            }!!.outcome
        }
    }
}
