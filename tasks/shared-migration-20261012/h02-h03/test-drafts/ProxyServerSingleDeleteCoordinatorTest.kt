// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0
// Draft only: promote to shared/app commonTest after the coordinator API is agreed.

package app.skipi.app.proxy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Android-source contract for one-row Home deletion; bulk-delete ordering is intentionally different. */
class ProxyServerSingleDeleteCoordinatorTest {
    @Test
    fun unselectedOrStoppedServerDispatchesWithoutStopping() = runBlocking {
        val events = mutableListOf<String>()

        runSingleProxyServerDeleteWhenRequired(
            serverId = 7,
            selectedServerId = 9,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            dispatchDeleteIfPresent = { events += "dispatch"; true },
            applyStopState = { events += "stop-state" },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("dispatch", "deleted"), events)
    }

    @Test
    fun serviceOperationBusyRejectsEvenAnUnselectedDelete() = runBlocking {
        val events = mutableListOf<String>()

        runSingleProxyServerDeleteWhenRequired(
            serverId = 7,
            selectedServerId = 9,
            serviceRunning = false,
            serviceOperationInProgress = true,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            dispatchDeleteIfPresent = { events += "dispatch"; true },
            applyStopState = { events += "stop-state" },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(emptyList(), events)
    }

    @Test
    fun successfulSelectedRunningDeleteDispatchesBeforeApplyingStopState() = runBlocking {
        val events = mutableListOf<String>()

        runSingleProxyServerDeleteWhenRequired(
            serverId = 7,
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            dispatchDeleteIfPresent = { events += "dispatch"; true },
            applyStopState = { events += "stop-state" },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "dispatch", "stop-state", "deleted"), events)
    }

    @Test
    fun concurrentRemovalAfterStopSkipsStopStateAndSuccessFeedback() = runBlocking {
        val events = mutableListOf<String>()

        runSingleProxyServerDeleteWhenRequired(
            serverId = 7,
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            dispatchDeleteIfPresent = { events += "lookup"; false },
            applyStopState = { events += "stop-state" },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "lookup"), events)
    }

    @Test
    fun missingServerAndFailedStopKeepCatalogAndUseTheirDistinctCallbacks() = runBlocking {
        val missingEvents = mutableListOf<String>()
        runSingleProxyServerDeleteWhenRequired(
            serverId = 7,
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { missingEvents += "stop"; ProxyServerStopOutcome.MissingServer },
            dispatchDeleteIfPresent = { missingEvents += "dispatch"; true },
            applyStopState = { missingEvents += "stop-state" },
            onDeleted = { missingEvents += "deleted" },
            onMissingServer = { missingEvents += "missing" },
            onStopFailed = { missingEvents += "stop-error" },
        )
        assertEquals(listOf("stop", "missing"), missingEvents)

        val failure = IllegalStateException("stop failed")
        val failedEvents = mutableListOf<String>()
        runSingleProxyServerDeleteWhenRequired(
            serverId = 7,
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            stopService = { failedEvents += "stop"; ProxyServerStopOutcome.Failed(failure) },
            dispatchDeleteIfPresent = { failedEvents += "dispatch"; true },
            applyStopState = { failedEvents += "stop-state" },
            onDeleted = { failedEvents += "deleted" },
            onMissingServer = { failedEvents += "missing" },
            onStopFailed = { reported ->
                failedEvents += if (reported === failure) "stop-error" else "wrong-error"
            },
        )
        assertEquals(listOf("stop", "stop-error"), failedEvents)
    }

    @Test
    fun cancellationFromStopPropagatesWithoutDeleteOrFeedback() = runBlocking {
        val cancellation = CancellationException("stop cancelled")
        val events = mutableListOf<String>()

        val thrown = runCatching {
            runSingleProxyServerDeleteWhenRequired(
                serverId = 7,
                selectedServerId = 7,
                serviceRunning = true,
                serviceOperationInProgress = false,
                stopService = { events += "stop"; throw cancellation },
                dispatchDeleteIfPresent = { events += "dispatch"; true },
                applyStopState = { events += "stop-state" },
                onDeleted = { events += "deleted" },
                onMissingServer = { events += "missing" },
                onStopFailed = { events += "stop-error" },
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(listOf("stop"), events)
    }
}
