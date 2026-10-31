// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import features.config.runtime.TrafficConfigRefreshTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopTrafficConfigAutoUpdaterTest {
    @Test
    fun runs_all_eligible_profiles_independently_of_geo_eligibility_or_positive_ids() = runTest {
        val nowMillis = 1_000L
        val targets = listOf(
            target(id = 0, resourceAutoUpdate = false, resourceUpdateInterval = "invalid"),
            target(id = -7, resourceAutoUpdate = false, resourceUpdateInterval = "invalid"),
            target(id = 21, autoUpdate = false, resourceAutoUpdate = true, resourceUpdateInterval = "0.25"),
            target(id = 22, updateLocked = true, resourceAutoUpdate = true, resourceUpdateInterval = "0.25"),
            target(id = 23, sourceUrl = " \t\n", resourceAutoUpdate = true, resourceUpdateInterval = "0.25"),
        )
        val updatedIds = mutableListOf<Int>()
        val updater = updater(
            readTargets = { targets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId ->
                updatedIds += profileId
                TrafficConfigAutoUpdateExecutionResult.Success
            },
        )

        updater.runDue()

        assertEquals(setOf(0, -7), updatedIds.toSet())
        assertEquals(2, updatedIds.size)
    }

    @Test
    fun offline_work_stays_due_and_runs_after_connectivity_returns() = runTest {
        var nowMillis = 500L
        var connected = false
        val updatedIds = mutableListOf<Int>()
        val updater = updater(
            readTargets = { listOf(target(id = 3)) },
            isNetworkConnected = { connected },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId ->
                updatedIds += profileId
                TrafficConfigAutoUpdateExecutionResult.Success
            },
        )

        updater.runDue()
        assertEquals(emptyList(), updatedIds)
        assertEquals(nowMillis, updater.nextWakeupAtMillis())

        connected = true
        updater.runDue()

        assertEquals(listOf(3), updatedIds)
        assertEquals(nowMillis + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun success_anchors_the_next_period_at_callback_completion() = runTest {
        var nowMillis = 100L
        val updater = updater(
            readTargets = { listOf(target(id = 4)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                nowMillis = 250L
                TrafficConfigAutoUpdateExecutionResult.Success
            },
        )

        updater.runDue()

        assertEquals(250L + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun retry_waits_for_backoff_then_success_starts_a_new_period() = runTest {
        var nowMillis = 5_000L
        var attempts = 0
        val updater = updater(
            readTargets = { listOf(target(id = 5)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    TrafficConfigAutoUpdateExecutionResult.Retry
                } else {
                    nowMillis += 25L
                    TrafficConfigAutoUpdateExecutionResult.Success
                }
            },
        )

        updater.runDue()
        val retryAtMillis = 5_000L + FifteenMinutesMillis
        assertEquals(1, attempts)
        assertEquals(retryAtMillis, updater.nextWakeupAtMillis())

        nowMillis = retryAtMillis - 1L
        updater.runDue()
        assertEquals(1, attempts)

        nowMillis = retryAtMillis
        updater.runDue()
        assertEquals(2, attempts)
        assertEquals(retryAtMillis + 25L + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun interval_update_preserves_pending_retry_and_sets_the_following_period() = runTest {
        var nowMillis = 100L
        var latestTargets = listOf(target(id = 6, updateInterval = "0.25"))
        var attempts = 0
        val updater = updater(
            readTargets = { latestTargets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    TrafficConfigAutoUpdateExecutionResult.Retry
                } else {
                    TrafficConfigAutoUpdateExecutionResult.Success
                }
            },
        )

        updater.runDue()
        val retryAtMillis = nowMillis + FifteenMinutesMillis
        nowMillis = 200L
        latestTargets = listOf(target(id = 6, updateInterval = "0.5"))
        updater.observeTargets(latestTargets)
        assertEquals(retryAtMillis, updater.nextWakeupAtMillis())

        nowMillis = retryAtMillis
        updater.runDue()

        assertEquals(2, attempts)
        assertEquals(retryAtMillis + ThirtyMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun updating_interval_preserves_anchor_and_does_not_cancel_in_flight_work() = runTest {
        var nowMillis = 100L
        var latestTargets = listOf(target(id = 7, updateInterval = "0.25"))
        val callbackStarted = CompletableDeferred<Unit>()
        val callbackCancelled = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = updater(
            readTargets = { latestTargets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    nowMillis = 200L
                    TrafficConfigAutoUpdateExecutionResult.Success
                } else {
                    callbackStarted.complete(Unit)
                    try {
                        releaseCallback.await()
                    } catch (cancelled: CancellationException) {
                        callbackCancelled.complete(Unit)
                        throw cancelled
                    }
                    TrafficConfigAutoUpdateExecutionResult.Success
                }
            },
        )

        updater.runDue()
        val firstDeadline = 200L + FifteenMinutesMillis
        assertEquals(firstDeadline, updater.nextWakeupAtMillis())

        nowMillis = firstDeadline
        val run = launch { updater.runDue() }
        runCurrent()
        callbackStarted.await()

        nowMillis = firstDeadline + 50L
        latestTargets = listOf(target(id = 7, updateInterval = "0.5"))
        updater.observeTargets(latestTargets)

        assertEquals(
            200L + ThirtyMinutesMillis,
            updater.nextWakeupAtMillis(),
            "an interval update uses the retained completion anchor",
        )
        assertFalse(callbackCancelled.isCompleted, "an interval update must not cancel the current attempt")

        nowMillis = firstDeadline + 100L
        releaseCallback.complete(Unit)
        run.join()

        assertEquals(2, attempts)
        assertFalse(callbackCancelled.isCompleted)
        assertEquals(nowMillis + ThirtyMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun concurrent_due_passes_do_not_start_a_duplicate_profile_callback() = runTest {
        var nowMillis = 1_000L
        val callbackStarted = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = updater(
            readTargets = { listOf(target(id = 8)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                callbackStarted.complete(Unit)
                releaseCallback.await()
                TrafficConfigAutoUpdateExecutionResult.Success
            },
        )

        val firstPass = launch { updater.runDue() }
        runCurrent()
        callbackStarted.await()
        updater.runDue()
        assertEquals(1, attempts)

        releaseCallback.complete(Unit)
        firstPass.join()

        assertEquals(1, attempts)
        assertEquals(nowMillis + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun stale_due_snapshot_does_not_run_after_a_nested_pass_advances_the_deadline() = runTest {
        val nowMillis = 1_000L
        var latestTargets = listOf(target(id = 31))
        val firstCallbackStarted = CompletableDeferred<Unit>()
        var replacementAttempts = 0
        lateinit var host: DesktopTrafficConfigAutoUpdater
        host = updater(
            readTargets = { latestTargets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { profileId ->
                when (profileId) {
                    31 -> {
                        firstCallbackStarted.complete(Unit)
                        suspendCancellableCoroutine<Nothing> { continuation ->
                            continuation.invokeOnCancellation {
                                runBlocking { host.runDue() }
                            }
                        }
                    }
                    32 -> {
                        replacementAttempts += 1
                        TrafficConfigAutoUpdateExecutionResult.Success
                    }
                    else -> error("Unexpected profile ID: $profileId")
                }
            },
        )

        val firstPass = launch { host.runDue() }
        runCurrent()
        firstCallbackStarted.await()

        latestTargets = listOf(target(id = 32))
        val replacementPass = launch { host.runDue() }
        runCurrent()
        replacementPass.join()
        firstPass.join()

        assertEquals(1, replacementAttempts, "the stale outer due snapshot must not repeat completed work")
        assertEquals(nowMillis + FifteenMinutesMillis, host.nextWakeupAtMillis())
    }

    @Test
    fun removal_cancels_work_and_late_retry_cannot_change_a_reenabled_schedule() = runTest {
        var nowMillis = 100L
        var latestTargets = listOf(target(id = 9))
        val callbackStarted = CompletableDeferred<Unit>()
        val callbackCancelled = CompletableDeferred<Unit>()
        val releaseOldCallback = CompletableDeferred<Unit>()
        val newCallbackStarted = CompletableDeferred<Unit>()
        val releaseNewCallback = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = updater(
            readTargets = { latestTargets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    callbackStarted.complete(Unit)
                    try {
                        releaseOldCallback.await()
                        TrafficConfigAutoUpdateExecutionResult.Success
                    } catch (cancelled: CancellationException) {
                        callbackCancelled.complete(Unit)
                        withContext(NonCancellable) { releaseOldCallback.await() }
                        TrafficConfigAutoUpdateExecutionResult.Retry
                    }
                } else if (attempts == 2) {
                    newCallbackStarted.complete(Unit)
                    releaseNewCallback.await()
                    TrafficConfigAutoUpdateExecutionResult.Success
                } else {
                    TrafficConfigAutoUpdateExecutionResult.Success
                }
            },
        )

        val oldPass = launch { updater.runDue() }
        runCurrent()
        callbackStarted.await()

        latestTargets = emptyList()
        updater.observeTargets(latestTargets)
        callbackCancelled.await()
        assertNull(updater.nextWakeupAtMillis())

        nowMillis = 200L
        latestTargets = listOf(target(id = 9))
        updater.observeTargets(latestTargets)
        assertEquals(200L, updater.nextWakeupAtMillis())

        nowMillis = 2_000L
        val newPass = launch { updater.runDue() }
        runCurrent()
        newCallbackStarted.await()

        releaseOldCallback.complete(Unit)
        oldPass.join()
        assertEquals(200L, updater.nextWakeupAtMillis(), "the old retry must not replace the fresh entry")

        updater.runDue()
        assertEquals(2, attempts, "old job cleanup must not release the new in-flight profile entry")

        nowMillis = 2_500L
        releaseNewCallback.complete(Unit)
        newPass.join()

        assertEquals(2, attempts)
        assertEquals(nowMillis + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun owner_cancellation_does_not_record_retry_when_callback_maps_cancellation_to_retry() = runTest {
        var nowMillis = 300L
        val callbackStarted = CompletableDeferred<Unit>()
        val callbackCaughtCancellation = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = updater(
            readTargets = { listOf(target(id = 10)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    callbackStarted.complete(Unit)
                    try {
                        neverReleased.await()
                        TrafficConfigAutoUpdateExecutionResult.Success
                    } catch (_: CancellationException) {
                        callbackCaughtCancellation.complete(Unit)
                        TrafficConfigAutoUpdateExecutionResult.Retry
                    }
                } else {
                    TrafficConfigAutoUpdateExecutionResult.Success
                }
            },
        )

        val ownerPass = launch { updater.runDue() }
        runCurrent()
        callbackStarted.await()
        ownerPass.cancel()
        ownerPass.join()

        assertTrue(callbackCaughtCancellation.isCompleted)
        assertEquals(300L, updater.nextWakeupAtMillis(), "owner cancellation leaves the immediate attempt due")

        updater.runDue()

        assertEquals(2, attempts)
        assertEquals(nowMillis + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    private fun updater(
        readTargets: () -> List<TrafficConfigRefreshTarget>,
        isNetworkConnected: () -> Boolean,
        nowMillis: () -> Long,
        updateAutomatically: suspend (Int) -> TrafficConfigAutoUpdateExecutionResult,
    ) = DesktopTrafficConfigAutoUpdater(
        readLatestTargets = readTargets,
        isNetworkConnected = isNetworkConnected,
        updateAutomatically = updateAutomatically,
        nowMillis = nowMillis,
    )

    private fun target(
        id: Int,
        autoUpdate: Boolean = true,
        updateLocked: Boolean = false,
        sourceUrl: String = "https://example.test/config.conf",
        updateInterval: String = "0.25",
        resourceAutoUpdate: Boolean = false,
        resourceUpdateInterval: String = "invalid",
    ) = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = sourceUrl,
        autoUpdate = autoUpdate,
        updateLocked = updateLocked,
        updateInterval = updateInterval,
        resourceAutoUpdate = resourceAutoUpdate,
        resourceUpdateInterval = resourceUpdateInterval,
    )

    private companion object {
        const val FifteenMinutesMillis = 15L * 60L * 1_000L
        const val ThirtyMinutesMillis = 30L * 60L * 1_000L
    }
}
