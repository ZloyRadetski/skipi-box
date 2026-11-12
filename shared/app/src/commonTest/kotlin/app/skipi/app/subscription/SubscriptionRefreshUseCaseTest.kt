// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubscriptionRefreshUseCaseTest {
    @Test
    fun refreshLoadsRequestsAndKeepsFailuresIsolated() = runTest {
        val result = refreshSubscriptions(
            requests = listOf("first", "broken", "last"),
            loader = SubscriptionRefreshLoader { request ->
                if (request == "broken") error("fetch failed")
                request.uppercase()
            },
            updatedAtMillis = { 123L },
        )

        assertEquals(listOf("FIRST", "LAST"), result.updates)
        assertEquals(listOf("broken"), result.failures.map { it.request })
        assertEquals("fetch failed", result.failures.single().error.message)
        assertEquals(123L, result.updatedAtMillis)
    }

    @Test
    fun failedBatchLoadProducesNoUpdateForTheHostApplyStep() = runTest {
        val result = refreshSubscriptions(
            requests = listOf("unavailable"),
            loader = SubscriptionRefreshLoader { error("fetch failed") },
            updatedAtMillis = { 321L },
        )

        assertEquals(emptyList(), result.updates)
        assertEquals(listOf("unavailable"), result.failures.map { it.request })
        assertEquals(321L, result.updatedAtMillis)
    }

    @Test
    fun batchStartsEveryLoadBeforeWaitingForResponsesAndKeepsRequestOrder() = runTest {
        val requests = listOf("first", "second", "third")
        val started = mutableListOf<String>()
        val releases = requests.associateWith { CompletableDeferred<Unit>() }
        val batch = async {
            refreshSubscriptions(
                requests = requests,
                loader = SubscriptionRefreshLoader { request ->
                    started += request
                    releases.getValue(request).await()
                    request.uppercase()
                },
                updatedAtMillis = { 456L },
            )
        }

        runCurrent()
        try {
            assertEquals(requests.toSet(), started.toSet())
            assertEquals(requests.size, started.size)
        } finally {
            releases.values.forEach { it.complete(Unit) }
        }

        val result = batch.await()
        assertEquals(listOf("FIRST", "SECOND", "THIRD"), result.updates)
        assertEquals(456L, result.updatedAtMillis)
    }

    @Test
    fun batchRunsAtMostTenLoadsAtOnceAndStartsQueuedOnesAsSlotsFree() = runTest {
        val requests = (1..25).map { "sub$it" }
        val releases = requests.associateWith { CompletableDeferred<Unit>() }
        val started = mutableListOf<String>()
        var running = 0
        var peak = 0
        val batch = async {
            refreshSubscriptions(
                requests = requests,
                loader = SubscriptionRefreshLoader { request ->
                    started += request
                    running++
                    peak = maxOf(peak, running)
                    try {
                        releases.getValue(request).await()
                    } finally {
                        running--
                    }
                    request.uppercase()
                },
                updatedAtMillis = { 1L },
            )
        }

        runCurrent()
        assertEquals(10, started.size)
        assertEquals(10, running)

        releases.getValue(started.first()).complete(Unit)
        runCurrent()
        assertEquals(11, started.size)

        releases.values.forEach { it.complete(Unit) }
        val result = batch.await()
        assertEquals(10, peak)
        assertEquals(requests.map { it.uppercase() }, result.updates)
    }

    @Test
    fun failedLoadFreesItsSlotForQueuedRequests() = runTest {
        val requests = (1..12).map { "sub$it" }
        val result = refreshSubscriptions(
            requests = requests,
            loader = SubscriptionRefreshLoader { request ->
                if (request in setOf("sub1", "sub2")) error("fetch failed")
                request
            },
            updatedAtMillis = { 1L },
        )

        assertEquals(10, result.updates.size)
        assertEquals(listOf("sub1", "sub2"), result.failures.map { it.request })
    }

    @Test
    fun cancellingBatchCancelsEveryOutstandingLoadAndDoesNotSampleCompletionTime() = runTest {
        val requests = listOf("first", "second")
        val started = mutableSetOf<String>()
        val cancelled = mutableSetOf<String>()
        var sampledCompletionTime = false
        val batch = async {
            refreshSubscriptions(
                requests = requests,
                loader = SubscriptionRefreshLoader { request ->
                    started += request
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled += request
                    }
                },
                updatedAtMillis = {
                    sampledCompletionTime = true
                    789L
                },
            )
        }

        runCurrent()
        assertEquals(requests.toSet(), started)
        batch.cancelAndJoin()

        assertEquals(requests.toSet(), cancelled)
        assertFalse(sampledCompletionTime)
    }

    @Test
    fun singleRefreshCapturesBeforeLoadAndCommitsWithThatSnapshot() = runTest {
        val events = mutableListOf<String>()
        val outcome = runSubscriptionRefresh(
            request = "subscription",
            captureSnapshot = { request ->
                events += "snapshot:$request"
                "baseline"
            },
            loader = SubscriptionRefreshLoader { request ->
                events += "load:$request"
                "response"
            },
            commit = { snapshot, request, update ->
                events += "commit:$request"
                "$snapshot:$update"
            },
        )

        assertEquals("baseline:response", outcome)
        assertEquals(listOf("snapshot:subscription", "load:subscription", "commit:subscription"), events)
    }

    @Test
    fun singleRefreshDoesNotCommitAfterLoadFailure() = runTest {
        var committed = false
        assertFailsWith<IllegalStateException> {
            runSubscriptionRefresh(
                request = "subscription",
                captureSnapshot = { "baseline" },
                loader = SubscriptionRefreshLoader { error("fetch failed") },
                commit = { _, _, _ ->
                    committed = true
                    Unit
                },
            )
        }
        assertEquals(false, committed)
    }

    @Test
    fun singleRefreshPropagatesCancellationWithoutCommit() = runTest {
        var committed = false
        assertFailsWith<CancellationException> {
            runSubscriptionRefresh(
                request = "subscription",
                captureSnapshot = { "baseline" },
                loader = SubscriptionRefreshLoader { throw CancellationException("cancelled") },
                commit = { _, _, _ ->
                    committed = true
                    Unit
                },
            )
        }
        assertEquals(false, committed)
    }

    @Test
    fun commitPolicyRejectsProfileEditedDuringFetch() {
        val conflict = subscriptionRefreshConflict(
            baselineTarget = "subscription-v1",
            latestTarget = "subscription-v1",
            baselineProfile = "original profile",
            latestProfile = "manually edited profile",
        )

        assertEquals(SubscriptionRefreshConflict.EMBEDDED_PROFILE_CHANGED, conflict)
    }

    @Test
    fun commitPolicyAllowsUnchangedTargetAndProfile() {
        assertNull(
            subscriptionRefreshConflict(
                baselineTarget = "subscription",
                latestTarget = "subscription",
                baselineProfile = "profile",
                latestProfile = "profile",
            ),
        )
    }
}
