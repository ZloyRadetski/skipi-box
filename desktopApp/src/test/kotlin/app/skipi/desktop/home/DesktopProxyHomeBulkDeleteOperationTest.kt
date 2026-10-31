// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.proxy.ProxyServerStopOutcome
import app.skipi.app.store.SharedApplicationActionOutcome
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyHomeBulkDeleteOperationTest {
    @Test
    fun selectedServerIsStoppedBeforeCatalogDeleteAndStopStateIsAppliedFirst() = runBlocking {
        val events = mutableListOf<String>()

        runDesktopProxyHomeBulkDelete(
            serverIds = setOf(7, 8),
            selectedServerId = 7,
            tunnelRunning = true,
            serviceOperationInProgress = false,
            stopService = {
                events += "stop"
                ProxyServerStopOutcome.Stopped
            },
            applyStoppedState = { events += "stop-state" },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Completed
            },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "stop-state", "delete", "deleted"), events)
    }

    @Test
    fun unselectedOrAlreadyStoppedServerDeletesWithoutStopping() = runBlocking {
        val events = mutableListOf<String>()

        suspend fun delete(selectedServerId: Int?, tunnelRunning: Boolean) {
            runDesktopProxyHomeBulkDelete(
                serverIds = setOf(7, 8),
                selectedServerId = selectedServerId,
                tunnelRunning = tunnelRunning,
                serviceOperationInProgress = false,
                stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
                applyStoppedState = { events += "stop-state" },
                dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
                onDeleted = { events += "deleted" },
                onDeleteError = { events += "delete-error" },
                onStopFailed = { events += "stop-error" },
            )
        }

        delete(selectedServerId = 9, tunnelRunning = true)
        delete(selectedServerId = 7, tunnelRunning = false)

        assertEquals(listOf("delete", "deleted", "delete", "deleted"), events)
    }

    @Test
    fun serviceOperationBusySkipsAStopSensitiveDelete() = runBlocking {
        val events = mutableListOf<String>()

        runDesktopProxyHomeBulkDelete(
            serverIds = setOf(7),
            selectedServerId = 7,
            tunnelRunning = true,
            serviceOperationInProgress = true,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            applyStoppedState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(emptyList(), events)
    }

    @Test
    fun failedStopPreservesCatalogAndReportsStopFailure() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("stop failed")

        runDesktopProxyHomeBulkDelete(
            serverIds = setOf(7),
            selectedServerId = 7,
            tunnelRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Failed(failure) },
            applyStoppedState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { reported ->
                events += if (reported === failure) "stop-error" else "wrong-stop-error"
            },
        )

        assertEquals(listOf("stop", "stop-error"), events)
    }

    @Test
    fun missingServiceStillDeletesWithoutApplyingStoppedState() = runBlocking {
        val events = mutableListOf<String>()

        runDesktopProxyHomeBulkDelete(
            serverIds = setOf(7),
            selectedServerId = 7,
            tunnelRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.MissingServer },
            applyStoppedState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "delete", "deleted"), events)
    }
}
