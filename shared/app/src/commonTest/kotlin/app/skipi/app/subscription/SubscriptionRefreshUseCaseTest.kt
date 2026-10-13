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
