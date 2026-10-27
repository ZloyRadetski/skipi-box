// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ProxyServerState
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import data.AndroidAppStateStore
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.deleteDuplicateServersInGroup
import features.proxy.server.usecase.deleteInvalidServersInGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Characterizes Android cleanup and bulk-delete operations against its Room-backed catalog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProxyHomeAndroidDeletionBridgeTest {
    @Test
    fun duplicateAndInvalidCleanupDispatchOnlyGroupRowsAndPruneCompositeReferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            val duplicateUrl = "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@node.example:443#Node"
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(
                        ProxyServerState(30, StrategyGroup(proxyServerIds = listOf(7, 8, 9), selectedMemberId = 7), groupId = -2, latency = "30 ms"),
                        ProxyServerState(40, ChainProxy(proxyServerIds = listOf(7, 8, 9)), groupId = -2, latency = "40 ms"),
                        ProxyServerState(7, features.proxy.server.model.ProxyServer.parse(duplicateUrl), groupId = 72, latency = "7 ms"),
                        ProxyServerState(8, features.proxy.server.model.ProxyServer.parse(duplicateUrl), groupId = 72, latency = "8 ms"),
                        ProxyServerState(9, HTTP(server = "outside.example"), groupId = 99, latency = "9 ms"),
                        ProxyServerState(12, HTTP(port = ""), groupId = 72, latency = "12 ms"),
                        ProxyServerState(13, HTTP(port = ""), groupId = 99, latency = "13 ms"),
                    ),
                    nextProxyServerId = 80,
                    selectedProxyServerId = 8,
                )
            }

            val initial = stateStore.currentState
            val duplicatePreview = initial.proxyServers.deleteDuplicateServersInGroup(
                currentGroupServerIds = setOf(7, 8, 12),
                selectedProxyServerId = initial.selectedProxyServerId,
            )
            val duplicateDeleteIds = initial.proxyServers.map { it.id }.toSet() - duplicatePreview.servers.map { it.id }.toSet()
            assertEquals(setOf(7), duplicateDeleteIds)
            assertEquals(
                SharedApplicationActionOutcome.Completed,
                stateStore.sharedApplicationStore.dispatchAndAwait(
                    SharedApplicationAction.RemoveProxyServers(duplicateDeleteIds),
                ).outcome,
            )

            val afterDuplicates = stateStore.currentState
            val invalidPreview = afterDuplicates.proxyServers.deleteInvalidServersInGroup(setOf(12))
            val invalidDeleteIds = afterDuplicates.proxyServers.map { it.id }.toSet() - invalidPreview.servers.map { it.id }.toSet()
            assertEquals(setOf(12), invalidDeleteIds)
            assertEquals(
                SharedApplicationActionOutcome.Completed,
                stateStore.sharedApplicationStore.dispatchAndAwait(
                    SharedApplicationAction.RemoveProxyServers(invalidDeleteIds),
                ).outcome,
            )

            val result = stateStore.currentState
            assertEquals(listOf(30, 40, 8, 9, 13), result.proxyServers.map { it.id })
            assertEquals(80, result.nextProxyServerId)
            assertEquals(8, result.selectedProxyServerId)
            assertEquals(listOf(8, 9), (result.proxyServers.single { it.id == 30 }.server as StrategyGroup).proxyServerIds)
            assertEquals(8, (result.proxyServers.single { it.id == 30 }.server as StrategyGroup).selectedMemberId)
            assertEquals(listOf(8, 9), (result.proxyServers.single { it.id == 40 }.server as ChainProxy).proxyServerIds)
            assertEquals(listOf("30 ms", "40 ms", "8 ms", "9 ms", "13 ms"), result.proxyServers.map { it.latency })
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun duplicateCleanupKeepsTheActionTimeSelectedRowAfterSelectionChangesBeforeQueuedRemove() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            val duplicate = ProxyServer.parse(
                "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@node.example:443#Node",
            )
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(
                        ProxyServerState(7, duplicate, groupId = 72),
                        ProxyServerState(8, duplicate, groupId = 72),
                        ProxyServerState(9, HTTP(server = "unrelated.example"), groupId = 99),
                    ),
                    nextProxyServerId = 80,
                    selectedProxyServerId = 8,
                )
            }

            // Android plans duplicate IDs before launching the queued RemoveProxyServers action.
            val actionSnapshot = stateStore.currentState
            val currentGroupServerIds = setOf(7, 8)
            val preview = actionSnapshot.proxyServers.deleteDuplicateServersInGroup(
                currentGroupServerIds = currentGroupServerIds,
                selectedProxyServerId = actionSnapshot.selectedProxyServerId,
            )
            val retainedIds = preview.servers.mapTo(hashSetOf()) { it.id }
            val removedIds = actionSnapshot.proxyServers.map { it.id }
                .filterNot { it in retainedIds }
                .toSet()
            assertEquals(setOf(7), removedIds)

            stateStore.update(persist = false) { state -> state.copy(selectedProxyServerId = 9) }
            assertEquals(
                SharedApplicationActionOutcome.Completed,
                stateStore.sharedApplicationStore.dispatchAndAwait(
                    SharedApplicationAction.RemoveProxyServers(removedIds),
                ).outcome,
            )

            val after = stateStore.currentState
            assertEquals(listOf(8, 9), after.proxyServers.map { it.id })
            assertEquals(9, after.selectedProxyServerId)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun duplicateCleanupRemovesOnlyActionTimeDuplicateIdsAfterCatalogChangesBeforeQueuedRemove() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            val duplicate = ProxyServer.parse(
                "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@node.example:443#Node",
            )
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(
                        ProxyServerState(7, duplicate, groupId = 72),
                        ProxyServerState(8, duplicate, groupId = 72),
                        ProxyServerState(9, HTTP(server = "unrelated.example"), groupId = 99),
                    ),
                    nextProxyServerId = 80,
                    selectedProxyServerId = 8,
                )
            }

            val actionSnapshot = stateStore.currentState
            val currentGroupServerIds = setOf(7, 8)
            val preview = actionSnapshot.proxyServers.deleteDuplicateServersInGroup(
                currentGroupServerIds = currentGroupServerIds,
                selectedProxyServerId = actionSnapshot.selectedProxyServerId,
            )
            val retainedIds = preview.servers.mapTo(hashSetOf()) { it.id }
            val removedIds = actionSnapshot.proxyServers.map { it.id }
                .filterNot { it in retainedIds }
                .toSet()
            assertEquals(setOf(7), removedIds)

            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = state.proxyServers.map { server ->
                        if (server.id == 8) {
                            server.copy(
                                server = ProxyServer.parse("vless://other@other.example:443#Changed"),
                            )
                        } else server
                    } + ProxyServerState(11, duplicate, groupId = 72),
                    selectedProxyServerId = 9,
                )
            }
            assertEquals(
                SharedApplicationActionOutcome.Completed,
                stateStore.sharedApplicationStore.dispatchAndAwait(
                    SharedApplicationAction.RemoveProxyServers(removedIds),
                ).outcome,
            )

            val after = stateStore.currentState
            assertEquals(listOf(8, 9, 11), after.proxyServers.map { it.id })
            assertEquals(9, after.selectedProxyServerId)
            assertEquals("Changed", after.proxyServers.single { it.id == 8 }.server.getInfo().remarks)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun bulkStopAppliesRuntimeStateBeforeDeletingAgainstLatestRoomCatalog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(
                        ProxyServerState(30, StrategyGroup(proxyServerIds = listOf(7, 8, 9), selectedMemberId = 7), groupId = -2, latency = "30 ms"),
                        ProxyServerState(40, ChainProxy(proxyServerIds = listOf(7, 8, 9)), groupId = -2, latency = "40 ms"),
                        ProxyServerState(7, HTTP(server = "first.example"), groupId = 72, latency = "7 ms"),
                        ProxyServerState(8, HTTP(server = "second.example"), groupId = 73, latency = "8 ms"),
                        ProxyServerState(9, HTTP(server = "survivor.example"), groupId = 74, latency = "9 ms"),
                        ProxyServerState(10, HTTP(server = "unrelated.example"), groupId = 75, latency = "10 ms"),
                    ),
                    nextProxyServerId = 80,
                    selectedProxyServerId = 7,
                    proxyRunning = true,
                )
            }

            val events = mutableListOf<String>()
            deleteProxyServersAfterStoppingService(
                stopService = {
                    events += "stop"
                    // A concurrent latest-catalog update lands after the UI captured deletion IDs
                    // but before the awaited remove action. The store action must preserve it and
                    // prune composite references from this current snapshot.
                    stateStore.update(persist = false) { state ->
                        state.copy(
                            proxyServers = state.proxyServers.map { row ->
                                when (row.id) {
                                    30 -> row.copy(server = StrategyGroup(proxyServerIds = listOf(7, 8, 9, 11), selectedMemberId = 7))
                                    40 -> row.copy(server = ChainProxy(proxyServerIds = listOf(7, 8, 9, 11)))
                                    else -> row
                                }
                            } + ProxyServerState(11, HTTP(server = "arrived-during-stop.example"), groupId = 76, latency = "11 ms"),
                            selectedProxyServerId = 11,
                            nextProxyServerId = 80,
                            proxyRunning = true,
                        )
                    }
                    ProxyServiceResult.Success(proxyRunning = false)
                },
                dispatchDelete = {
                    events += "delete"
                    stateStore.sharedApplicationStore.dispatchAndAwait(
                        SharedApplicationAction.RemoveProxyServers(setOf(7, 8)),
                    ).outcome
                },
                applyStopState = { result ->
                    events += "stop-state"
                    stateStore.update(persist = false) { state -> state.copy(proxyRunning = result.proxyRunning) }
                },
                onDeleted = { events += "deleted" },
                onDeleteError = { throw it },
                onStopFailed = { throw it },
            )

            assertEquals(listOf("stop", "stop-state", "delete", "deleted"), events)
            val result = stateStore.currentState
            assertEquals(listOf(30, 40, 9, 10, 11), result.proxyServers.map { it.id })
            assertEquals(80, result.nextProxyServerId)
            assertEquals(11, result.selectedProxyServerId)
            assertEquals(listOf(9, 11), (result.proxyServers.single { it.id == 30 }.server as StrategyGroup).proxyServerIds)
            assertEquals(9, (result.proxyServers.single { it.id == 30 }.server as StrategyGroup).selectedMemberId)
            assertEquals(listOf(9, 11), (result.proxyServers.single { it.id == 40 }.server as ChainProxy).proxyServerIds)
            assertEquals(listOf("30 ms", "40 ms", "9 ms", "10 ms", "11 ms"), result.proxyServers.map { it.latency })
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun cancelledBulkStopDoesNotDispatchOrChangeRoomCatalog() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(ProxyServerState(7, HTTP(server = "keep.example"), groupId = 72)),
                    nextProxyServerId = 80,
                    selectedProxyServerId = 7,
                    proxyRunning = true,
                )
            }
            val before = stateStore.currentState
            val cancellation = CancellationException("stop cancelled")
            var dispatched = false

            val thrown = runCatching {
                deleteProxyServersAfterStoppingService(
                    stopService = { throw cancellation },
                    dispatchDelete = {
                        dispatched = true
                        SharedApplicationActionOutcome.Completed
                    },
                    applyStopState = { error("cancelled stop cannot publish a stopped state") },
                    onDeleted = { error("cancelled stop cannot report deletion") },
                    onDeleteError = { error("cancelled stop is not a delete failure") },
                    onStopFailed = { error("cancellation is not a stop failure") },
                )
            }.exceptionOrNull()

            assertSame(cancellation, thrown)
            assertFalse(dispatched)
            assertEquals(before.proxyServers, stateStore.currentState.proxyServers)
            assertEquals(before.nextProxyServerId, stateStore.currentState.nextProxyServerId)
            assertEquals(before.selectedProxyServerId, stateStore.currentState.selectedProxyServerId)
            assertEquals(before.proxyRunning, stateStore.currentState.proxyRunning)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun cancelledBulkDeleteKeepsPublishedStopStateAndDoesNotReportSuccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(
                        ProxyServerState(7, HTTP(server = "selected.example"), groupId = 72),
                        ProxyServerState(8, HTTP(server = "also-selected.example"), groupId = 73),
                    ),
                    nextProxyServerId = 80,
                    selectedProxyServerId = 7,
                    proxyRunning = true,
                )
            }
            val before = stateStore.currentState
            val cancellation = CancellationException("delete cancelled")
            val events = mutableListOf<String>()

            val thrown = runCatching {
                deleteProxyServersAfterStoppingService(
                    stopService = {
                        events += "stop"
                        ProxyServiceResult.Success(proxyRunning = false)
                    },
                    dispatchDelete = {
                        events += "delete"
                        throw cancellation
                    },
                    applyStopState = { result ->
                        events += "stop-state"
                        stateStore.update(persist = false) { state ->
                            state.copy(proxyRunning = result.proxyRunning)
                        }
                    },
                    onDeleted = { events += "deleted" },
                    onDeleteError = { events += "delete-error" },
                    onStopFailed = { events += "stop-error" },
                )
            }.exceptionOrNull()

            assertSame(cancellation, thrown)
            assertEquals(listOf("stop", "stop-state", "delete"), events)
            val after = stateStore.currentState
            assertEquals(before.proxyServers, after.proxyServers)
            assertEquals(before.nextProxyServerId, after.nextProxyServerId)
            assertEquals(before.selectedProxyServerId, after.selectedProxyServerId)
            assertFalse(after.proxyRunning)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun stopFailureLeavesCatalogAloneAndMissingServiceStillRunsBulkDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateStore = AndroidAppStateStore.get(context)
        stateStore.resetToStockState()

        try {
            val strategy = StrategyGroup(proxyServerIds = listOf(7, 8, 9), selectedMemberId = 7)
            val chain = ChainProxy(proxyServerIds = listOf(7, 8, 9))
            stateStore.update(persist = false) { state ->
                state.copy(
                    proxyServers = listOf(
                        ProxyServerState(30, strategy, groupId = -2, latency = "30 ms"),
                        ProxyServerState(40, chain, groupId = -2, latency = "40 ms"),
                        ProxyServerState(7, HTTP(server = "first.example"), groupId = 72, latency = "7 ms"),
                        ProxyServerState(8, HTTP(server = "second.example"), groupId = 73, latency = "8 ms"),
                        ProxyServerState(9, HTTP(server = "survivor.example"), groupId = 74, latency = "9 ms"),
                        ProxyServerState(10, HTTP(server = "unrelated.example"), groupId = 75, latency = "10 ms"),
                    ),
                    nextProxyServerId = 80,
                    selectedProxyServerId = 7,
                    proxyRunning = true,
                )
            }

            val beforeFailedStop = stateStore.currentState
            var dispatchedDeletes = 0
            var stopFailure: Throwable? = null

            deleteProxyServersAfterStoppingService(
                stopService = { ProxyServiceResult.Failed(IllegalStateException("stop failed")) },
                dispatchDelete = {
                    dispatchedDeletes += 1
                    stateStore.sharedApplicationStore.dispatchAndAwait(
                        SharedApplicationAction.RemoveProxyServers(setOf(7, 8)),
                    ).outcome
                },
                applyStopState = { error("failed stop must not publish a success state") },
                onDeleted = { error("failed stop must not report deletion") },
                onDeleteError = { error("failed stop must not attempt catalog deletion") },
                onStopFailed = { stopFailure = it },
            )

            assertEquals(0, dispatchedDeletes)
            assertIs<IllegalStateException>(stopFailure)
            assertEquals(beforeFailedStop.proxyServers, stateStore.currentState.proxyServers)
            assertEquals(beforeFailedStop.nextProxyServerId, stateStore.currentState.nextProxyServerId)
            assertEquals(beforeFailedStop.selectedProxyServerId, stateStore.currentState.selectedProxyServerId)

            var deletedCallbackCount = 0
            deleteProxyServersAfterStoppingService(
                stopService = { ProxyServiceResult.MissingServer },
                dispatchDelete = {
                    dispatchedDeletes += 1
                    stateStore.sharedApplicationStore.dispatchAndAwait(
                        SharedApplicationAction.RemoveProxyServers(setOf(7, 8)),
                    ).outcome
                },
                applyStopState = { error("missing service has no successful runtime result") },
                onDeleted = { deletedCallbackCount += 1 },
                onDeleteError = { throw it },
                onStopFailed = { throw it },
            )

            assertEquals(1, dispatchedDeletes)
            assertEquals(1, deletedCallbackCount)
            val result = stateStore.currentState
            assertEquals(listOf(30, 40, 9, 10), result.proxyServers.map { it.id })
            assertEquals(80, result.nextProxyServerId)
            assertEquals(30, result.selectedProxyServerId)
            assertEquals(listOf(9), (result.proxyServers.single { it.id == 30 }.server as StrategyGroup).proxyServerIds)
            assertEquals(9, (result.proxyServers.single { it.id == 30 }.server as StrategyGroup).selectedMemberId)
            assertEquals(listOf(9), (result.proxyServers.single { it.id == 40 }.server as ChainProxy).proxyServerIds)
            assertEquals(listOf("30 ms", "40 ms", "9 ms", "10 ms"), result.proxyServers.map { it.latency })
            assertFalse(result.proxyServers.any { it.id == 7 || it.id == 8 })
        } finally {
            stateStore.resetToStockState()
        }
    }
}
