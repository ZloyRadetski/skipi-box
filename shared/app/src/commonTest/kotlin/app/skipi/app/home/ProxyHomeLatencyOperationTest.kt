// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyHomeLatencyOperationTest {
    @Test
    fun groupSelectionIntersectsCurrentTargetsInCatalogOrder() {
        val launched = mutableListOf<List<String>>()
        val operation = ProxyHomeLatencyOperation(
            getAllTargets = { listOf("server-9", "server-7", "server-4") },
            targetId = { it },
            getGroupMemberIds = { id -> if (id == "subscription:5") setOf("server-4", "server-7", "missing") else null },
            isHostBusy = { false },
            getModeAndDoneTemplate = { Unit to "" },
            launchTest = { targets, _, _, _ -> launched += targets; Job() },
            onCancel = {},
        )

        assertTrue(operation.testGroup("subscription:5").isSuccess)
        assertEquals(listOf(listOf("server-7", "server-4")), launched)
    }

    @Test
    fun synchronousCompletionBeforeLauncherReturnsDoesNotLeaveTheOperationBusy() {
        val operation = ProxyHomeLatencyOperation(
            getAllTargets = { listOf("server-1") },
            targetId = { it },
            getGroupMemberIds = { null },
            isHostBusy = { false },
            getModeAndDoneTemplate = { Unit to "" },
            launchTest = { _, _, _, onFinished ->
                onFinished()
                Job()
            },
            onCancel = {},
        )

        assertTrue(operation.testVisibleServers(listOf("server-1")).isSuccess)
        assertFalse(operation.isBusy)
    }

    @Test
    fun staleCompletionCannotClearTheNextRunAndCancelInvalidatesItsRunId() {
        val jobs = mutableListOf<Job>()
        val completions = mutableListOf<() -> Unit>()
        var cancelCount = 0
        val operation = ProxyHomeLatencyOperation(
            getAllTargets = { listOf("server-1") },
            targetId = { it },
            getGroupMemberIds = { null },
            isHostBusy = { false },
            getModeAndDoneTemplate = { Unit to "" },
            launchTest = { _, _, _, onFinished ->
                completions += onFinished
                Job().also(jobs::add)
            },
            onCancel = { cancelCount++ },
        )

        assertTrue(operation.testVisibleServers(listOf("server-1")).isSuccess)
        assertTrue(operation.cancel().isSuccess)
        assertTrue(jobs.first().isCancelled)
        assertTrue(operation.testVisibleServers(listOf("server-1")).isSuccess)

        completions.first().invoke()
        assertTrue(operation.isBusy)
        completions.last().invoke()
        assertFalse(operation.isBusy)
        assertEquals(1, cancelCount)
    }
}
