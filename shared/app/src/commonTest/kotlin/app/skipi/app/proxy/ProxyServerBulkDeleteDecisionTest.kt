// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import app.skipi.app.store.SharedApplicationActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProxyServerBulkDeleteDecisionTest {
    @Test
    fun emptyDeleteSetDoesNotStopDispatchOrReport() = runBlocking {
        val events = mutableListOf<String>()

        runProxyServerBulkDeleteWhenRequired(
            serverIds = emptySet(),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            applyStopState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(emptyList(), events)
    }

    @Test
    fun unselectedServerDeletesDirectlyEvenWhileAnotherServiceOperationIsBusy() = runBlocking {
        val events = mutableListOf<String>()

        runProxyServerBulkDeleteWhenRequired(
            serverIds = setOf(7, 8),
            selectedServerId = 9,
            serviceRunning = true,
            serviceOperationInProgress = true,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            applyStopState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("delete", "deleted"), events)
    }

    @Test
    fun noSelectedServerUsesTheDirectDeletePathEvenWhileBusy() = runBlocking {
        val events = mutableListOf<String>()

        runProxyServerBulkDeleteWhenRequired(
            serverIds = setOf(7),
            selectedServerId = null,
            serviceRunning = true,
            serviceOperationInProgress = true,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            applyStopState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("delete", "deleted"), events)
    }

    @Test
    fun stoppedServiceDeletesDirectlyWithoutApplyingRuntimeState() = runBlocking {
        val events = mutableListOf<String>()

        runProxyServerBulkDeleteWhenRequired(
            serverIds = setOf(7),
            selectedServerId = 7,
            serviceRunning = false,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            applyStopState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("delete", "deleted"), events)
    }

    @Test
    fun selectedRunningServerWaitsForBusyGuardBeforeStoppingOrDeleting() = runBlocking {
        val events = mutableListOf<String>()

        runProxyServerBulkDeleteWhenRequired(
            serverIds = setOf(7, 8),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = true,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            applyStopState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(emptyList(), events)
    }

    @Test
    fun selectedRunningServerStopsThenPublishesStopStateBeforeDeleteAndFeedback() = runBlocking {
        val events = mutableListOf<String>()

        runProxyServerBulkDeleteWhenRequired(
            serverIds = setOf(7, 8),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            applyStopState = { events += "stop-state" },
            dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { events += "deleted" },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "stop-state", "delete", "deleted"), events)
    }

    @Test
    fun failedStopDoesNotDeleteButMissingServiceDoes() = runBlocking {
        val failure = IllegalStateException("stop failed")
        val failureEvents = mutableListOf<String>()
        runProxyServerBulkDeleteWhenRequired(
            serverIds = setOf(7),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { failureEvents += "stop"; ProxyServerStopOutcome.Failed(failure) },
            applyStopState = { failureEvents += "stop-state" },
            dispatchDelete = { failureEvents += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { failureEvents += "deleted" },
            onDeleteError = { failureEvents += "delete-error" },
            onStopFailed = { reported ->
                failureEvents += if (reported === failure) "stop-error" else "wrong-stop-error"
            },
        )
        assertEquals(listOf("stop", "stop-error"), failureEvents)

        val missingEvents = mutableListOf<String>()
        runProxyServerBulkDeleteWhenRequired(
            serverIds = setOf(7),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { missingEvents += "stop"; ProxyServerStopOutcome.MissingServer },
            applyStopState = { missingEvents += "stop-state" },
            dispatchDelete = { missingEvents += "delete"; SharedApplicationActionOutcome.Completed },
            onDeleted = { missingEvents += "deleted" },
            onDeleteError = { missingEvents += "delete-error" },
            onStopFailed = { missingEvents += "stop-error" },
        )
        assertEquals(listOf("stop", "delete", "deleted"), missingEvents)
    }

    @Test
    fun cancellationFromStopPropagatesWithoutDeleteOrFeedback() = runBlocking {
        val cancellation = CancellationException("stop cancelled")
        val events = mutableListOf<String>()

        val thrown = runCatching {
            runProxyServerBulkDeleteWhenRequired(
                serverIds = setOf(7),
                selectedServerId = 7,
                serviceRunning = true,
                serviceOperationInProgress = false,
                stopService = { events += "stop"; throw cancellation },
                applyStopState = { events += "stop-state" },
                dispatchDelete = { events += "delete"; SharedApplicationActionOutcome.Completed },
                onDeleted = { events += "deleted" },
                onDeleteError = { events += "delete-error" },
                onStopFailed = { events += "stop-error" },
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(listOf("stop"), events)
        assertFalse(events.contains("delete"))
        assertTrue(events.none { it.endsWith("error") })
    }
}
