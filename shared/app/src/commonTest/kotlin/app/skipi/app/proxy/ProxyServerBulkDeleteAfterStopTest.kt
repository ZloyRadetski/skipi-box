// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import app.skipi.app.store.SharedApplicationActionOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Mirrors only Android's bulk selected-server delete path, not single/group delete ordering. */
class ProxyServerBulkDeleteAfterStopTest {
    @Test
    fun successfulStopAppliesStopStateBeforeAwaitedDeleteAndFeedback() = runBlocking {
        val events = mutableListOf<String>()

        runBulkDeleteAfterStoppingProxyService(
            stopService = {
                events += "stop"
                ProxyServerStopOutcome.Stopped
            },
            applyStopState = { events += "stop-state" },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Completed
            },
            onDeleted = { events += "deleted" },
            onDeleteError = { error("successful delete must not report an error: $it") },
            onStopFailed = { error("successful stop must not report a stop failure: $it") },
        )

        assertEquals(listOf("stop", "stop-state", "delete", "deleted"), events)
    }

    @Test
    fun missingServiceStillDispatchesBulkDeleteWithoutApplyingStopState() = runBlocking {
        val events = mutableListOf<String>()

        runBulkDeleteAfterStoppingProxyService(
            stopService = {
                events += "stop"
                ProxyServerStopOutcome.MissingServer
            },
            applyStopState = { events += "stop-state" },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Completed
            },
            onDeleted = { events += "deleted" },
            onDeleteError = { error("successful delete must not report an error: $it") },
            onStopFailed = { error("missing service is not a stop failure: $it") },
        )

        assertEquals(listOf("stop", "delete", "deleted"), events)
    }

    @Test
    fun stopFailureLeavesDeleteAndSuccessFeedbackUntouched() = runBlocking {
        val failure = IllegalStateException("stop failed")
        var dispatched = false
        var deleted = false
        var reported: Throwable? = null
        var stopStateApplied = false

        runBulkDeleteAfterStoppingProxyService(
            stopService = { ProxyServerStopOutcome.Failed(failure) },
            applyStopState = { stopStateApplied = true },
            dispatchDelete = {
                dispatched = true
                SharedApplicationActionOutcome.Completed
            },
            onDeleted = { deleted = true },
            onDeleteError = { error("stop failure is not a delete failure: $it") },
            onStopFailed = { reported = it },
        )

        assertSame(failure, reported)
        assertTrue(!dispatched)
        assertTrue(!deleted)
        assertTrue(!stopStateApplied)
    }

    @Test
    fun rejectedAndFailedStoreResultsKeepAndroidFeedbackExceptionTypes() = runBlocking {
        val errors = mutableListOf<Throwable>()

        listOf(
            SharedApplicationActionOutcome.Rejected("invalid delete"),
            SharedApplicationActionOutcome.Failed("storage failed"),
        ).forEach { outcome ->
            runBulkDeleteAfterStoppingProxyService(
                stopService = { ProxyServerStopOutcome.Stopped },
                applyStopState = {},
                dispatchDelete = { outcome },
                onDeleted = { throw AssertionError("failed dispatch must not report deletion") },
                onDeleteError = { errors += it },
                onStopFailed = { error("successful stop must not report a stop failure: $it") },
            )
        }

        assertEquals(2, errors.size)
        assertIs<IllegalArgumentException>(errors[0])
        assertEquals("invalid delete", errors[0].message)
        assertIs<IllegalStateException>(errors[1])
        assertEquals("storage failed", errors[1].message)
    }

    @Test
    fun cancellationFromStopOrAwaitedDeletePropagatesWithoutSyntheticFeedback() = runBlocking {
        val cancelledStop = CancellationException("stop cancelled")
        var dispatched = false
        var stopFailureFeedback = false

        val stopThrown = runCatching {
            runBulkDeleteAfterStoppingProxyService(
                stopService = { throw cancelledStop },
                applyStopState = {},
                dispatchDelete = {
                    dispatched = true
                    SharedApplicationActionOutcome.Completed
                },
                onDeleted = {},
                onDeleteError = { throw AssertionError("cancellation is not a delete error", it) },
                onStopFailed = { stopFailureFeedback = true },
            )
        }.exceptionOrNull()

        assertSame(cancelledStop, stopThrown)
        assertTrue(!dispatched)
        assertTrue(!stopFailureFeedback)

        val cancelledDelete = CancellationException("delete cancelled")
        var deleteErrorFeedback = false
        val deleteThrown = runCatching {
            runBulkDeleteAfterStoppingProxyService(
                stopService = { ProxyServerStopOutcome.Stopped },
                applyStopState = {},
                dispatchDelete = { throw cancelledDelete },
                onDeleted = {},
                onDeleteError = { deleteErrorFeedback = true },
                onStopFailed = { error("successful stop must not report a stop failure: $it") },
            )
        }.exceptionOrNull()

        assertSame(cancelledDelete, deleteThrown)
        assertTrue(!deleteErrorFeedback)
    }
}
