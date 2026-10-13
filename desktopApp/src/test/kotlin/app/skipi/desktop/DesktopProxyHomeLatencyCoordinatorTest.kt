// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProxyHomeLatencyCoordinatorTest {
    @Test
    fun groupTargetsFollowCurrentCatalogOrderAndASecondRunIsRejectedWhileTheFirstIsActive() = runBlocking {
        val events = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val targets = listOf("server-9", "server-7", "server-4")
        var activeJob: Job? = null
        val coordinator = DesktopProxyHomeLatencyCoordinator(
            getAllTargets = { targets },
            targetId = { target -> target.substringAfter("server-") },
            getGroupMemberIds = { groupId ->
                if (groupId == "subscription:5") setOf("4", "7") else null
            },
            isHostBusy = { false },
            launchTest = { selected, onFinished ->
                events += "launch:${selected.joinToString()}"
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    started.complete(Unit)
                    finish.await()
                    onFinished()
                }.also { activeJob = it }
            },
            onCancel = { events += "cancel-host" },
        )

        assertTrue(coordinator.testGroup("subscription:5").isSuccess)
        started.await()
        assertTrue(coordinator.testVisibleServers(listOf("4", "9")).isFailure)
        assertEquals(listOf("launch:server-7, server-4"), events)

        finish.complete(Unit)
        activeJob?.join()
        assertFalse(coordinator.isBusy)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test
    fun cancelCancelsTheActiveHomeRunAndNotifiesTheHostOnce() = runBlocking {
        val events = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        var activeJob: Job? = null
        val coordinator = DesktopProxyHomeLatencyCoordinator(
            getAllTargets = { listOf("server-7") },
            targetId = { target -> target.substringAfter("server-") },
            getGroupMemberIds = { null },
            isHostBusy = { false },
            launchTest = { _, _ ->
                scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                    started.complete(Unit)
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        events += "job-cleanup"
                    }
                }.also { activeJob = it }
            },
            onCancel = { events += "cancel-host" },
        )

        assertTrue(coordinator.testVisibleServers(listOf("7")).isSuccess)
        started.await()
        coordinator.cancel()
        activeJob?.join()
        assertEquals(listOf("job-cleanup", "cancel-host"), events)
        assertFalse(coordinator.isBusy)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
    }
}
