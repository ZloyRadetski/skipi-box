// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Android-source contract for deleting a subscription group from Home. */
class ProxySubscriptionGroupDeleteCoordinatorTest {
    @Test
    fun runningSelectedMemberIsStoppedAndGroupRemovalCompletesBeforeStatePublication() = runBlocking {
        val events = mutableListOf<String>()
        val allowRemovalToFinish = CompletableDeferred<Unit>()
        val removalStarted = CompletableDeferred<Unit>()

        val delete = async {
            runSubscriptionGroupDeleteWhenRequired(
                groupServerIds = setOf(4, 7),
                selectedServerId = 7,
                serviceRunning = true,
                serviceOperationInProgress = false,
                groupIsBuiltIn = false,
                stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
                deleteGroupIfPresent = {
                    events += "remove-start"
                    removalStarted.complete(Unit)
                    allowRemovalToFinish.await()
                    events += "remove-finished"
                    true
                },
                applyStopState = { events += "stop-state" },
                onDeleted = { events += "deleted" },
                onMissingServer = { events += "missing-server" },
                onStopFailed = { events += "stop-error" },
                onDeleteError = { events += "delete-error" },
            )
        }

        removalStarted.await()
        assertEquals(listOf("stop", "remove-start"), events)
        allowRemovalToFinish.complete(Unit)
        delete.await()

        assertEquals(listOf("stop", "remove-start", "remove-finished", "stop-state", "deleted"), events)
    }

    @Test
    fun stoppedOrUnselectedGroupRemovalDoesNotStopAndAwaitsDelete() = runBlocking {
        val events = mutableListOf<String>()

        runSubscriptionGroupDeleteWhenRequired(
            groupServerIds = setOf(4, 7),
            selectedServerId = 9,
            serviceRunning = true,
            serviceOperationInProgress = false,
            groupIsBuiltIn = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            deleteGroupIfPresent = { events += "remove"; true },
            applyStopState = { events += "stop-state" },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing-server" },
            onStopFailed = { events += "stop-error" },
            onDeleteError = { events += "delete-error" },
        )

        assertEquals(listOf("remove", "deleted"), events)
    }

    @Test
    fun builtInAndBusyGroupDeletesDoNothing() = runBlocking {
        val events = mutableListOf<String>()
        val delete = suspend {
            runSubscriptionGroupDeleteWhenRequired(
                groupServerIds = setOf(7),
                selectedServerId = 7,
                serviceRunning = true,
                serviceOperationInProgress = false,
                groupIsBuiltIn = true,
                stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
                deleteGroupIfPresent = { events += "remove"; true },
                applyStopState = { events += "stop-state" },
                onDeleted = { events += "deleted" },
                onMissingServer = { events += "missing-server" },
                onStopFailed = { events += "stop-error" },
                onDeleteError = { events += "delete-error" },
            )
        }
        delete()
        runSubscriptionGroupDeleteWhenRequired(
            groupServerIds = setOf(7),
            selectedServerId = 9,
            serviceRunning = false,
            serviceOperationInProgress = true,
            groupIsBuiltIn = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            deleteGroupIfPresent = { events += "remove"; true },
            applyStopState = { events += "stop-state" },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing-server" },
            onStopFailed = { events += "stop-error" },
            onDeleteError = { events += "delete-error" },
        )

        assertEquals(emptyList(), events)
    }

    @Test
    fun disappearedGroupAndDeleteFailureDoNotPublishStopStateOrSuccess() = runBlocking {
        val disappearedEvents = mutableListOf<String>()
        runSubscriptionGroupDeleteWhenRequired(
            groupServerIds = setOf(7),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            groupIsBuiltIn = false,
            stopService = { disappearedEvents += "stop"; ProxyServerStopOutcome.Stopped },
            deleteGroupIfPresent = { disappearedEvents += "lookup"; false },
            applyStopState = { disappearedEvents += "stop-state" },
            onDeleted = { disappearedEvents += "deleted" },
            onMissingServer = { disappearedEvents += "missing-server" },
            onStopFailed = { disappearedEvents += "stop-error" },
            onDeleteError = { disappearedEvents += "delete-error" },
        )
        assertEquals(listOf("stop", "lookup"), disappearedEvents)

        val failure = IllegalStateException("remove failed")
        val failedEvents = mutableListOf<String>()
        runSubscriptionGroupDeleteWhenRequired(
            groupServerIds = setOf(7),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            groupIsBuiltIn = false,
            stopService = { failedEvents += "stop"; ProxyServerStopOutcome.Stopped },
            deleteGroupIfPresent = { failedEvents += "remove"; throw failure },
            applyStopState = { failedEvents += "stop-state" },
            onDeleted = { failedEvents += "deleted" },
            onMissingServer = { failedEvents += "missing-server" },
            onStopFailed = { failedEvents += "stop-error" },
            onDeleteError = { reported -> failedEvents += if (reported === failure) "delete-error" else "wrong-error" },
        )
        assertEquals(listOf("stop", "remove", "delete-error"), failedEvents)
    }

    @Test
    fun stopMissingFailureAndCancellationKeepGroupAndStopStateContract() = runBlocking {
        val missingEvents = mutableListOf<String>()
        runSubscriptionGroupDeleteWhenRequired(
            groupServerIds = setOf(7),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            groupIsBuiltIn = false,
            stopService = { missingEvents += "stop"; ProxyServerStopOutcome.MissingServer },
            deleteGroupIfPresent = { missingEvents += "remove"; true },
            applyStopState = { missingEvents += "stop-state" },
            onDeleted = { missingEvents += "deleted" },
            onMissingServer = { missingEvents += "missing-server" },
            onStopFailed = { missingEvents += "stop-error" },
            onDeleteError = { missingEvents += "delete-error" },
        )
        assertEquals(listOf("stop", "missing-server"), missingEvents)

        val stopFailure = IllegalStateException("stop failed")
        val failedEvents = mutableListOf<String>()
        runSubscriptionGroupDeleteWhenRequired(
            groupServerIds = setOf(7),
            selectedServerId = 7,
            serviceRunning = true,
            serviceOperationInProgress = false,
            groupIsBuiltIn = false,
            stopService = { failedEvents += "stop"; ProxyServerStopOutcome.Failed(stopFailure) },
            deleteGroupIfPresent = { failedEvents += "remove"; true },
            applyStopState = { failedEvents += "stop-state" },
            onDeleted = { failedEvents += "deleted" },
            onMissingServer = { failedEvents += "missing-server" },
            onStopFailed = { reported -> failedEvents += if (reported === stopFailure) "stop-error" else "wrong-error" },
            onDeleteError = { failedEvents += "delete-error" },
        )
        assertEquals(listOf("stop", "stop-error"), failedEvents)

        val cancellation = CancellationException("remove cancelled")
        val cancelledEvents = mutableListOf<String>()
        val thrown = runCatching {
            runSubscriptionGroupDeleteWhenRequired(
                groupServerIds = setOf(7),
                selectedServerId = 7,
                serviceRunning = true,
                serviceOperationInProgress = false,
                groupIsBuiltIn = false,
                stopService = { cancelledEvents += "stop"; ProxyServerStopOutcome.Stopped },
                deleteGroupIfPresent = { cancelledEvents += "remove"; throw cancellation },
                applyStopState = { cancelledEvents += "stop-state" },
                onDeleted = { cancelledEvents += "deleted" },
                onMissingServer = { cancelledEvents += "missing-server" },
                onStopFailed = { cancelledEvents += "stop-error" },
                onDeleteError = { cancelledEvents += "delete-error" },
            )
        }.exceptionOrNull()
        assertSame(cancellation, thrown)
        assertEquals(listOf("stop", "remove"), cancelledEvents)
    }
}
