// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.SubscriptionWorkerOutcome
import features.subscription.runtime.SubscriptionRefreshTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSubscriptionAutoUpdaterTest {
    @Test
    fun app_lifetime_host_dispatches_a_due_group_without_a_proxy_screen_consumer() = runTest {
        var nowMillis = 1_000L
        val updatedGroupIds = mutableListOf<Int>()
        val updater = updater(
            readTargets = { listOf(target(id = 11)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = { groupId ->
                updatedGroupIds += groupId
                nowMillis = 2_500L
                SubscriptionWorkerOutcome.SUCCESS
            },
        )

        updater.observeTargets(listOf(target(id = 11)))
        assertEquals(1_000L, updater.nextWakeupAtMillis())

        // The app host owns the due pass directly; it does not hand an ID to a visible screen.
        updater.runDue()

        assertEquals(listOf(11), updatedGroupIds)
        assertEquals(2_500L + IntervalMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun disconnected_work_stays_due_until_the_connected_network_constraint_is_met() = runTest {
        var connected = false
        var nowMillis = 5_000L
        val updatedGroupIds = mutableListOf<Int>()
        val updater = updater(
            readTargets = { listOf(target(id = 12)) },
            isNetworkConnected = { connected },
            nowMillis = { nowMillis },
            updateAutomatically = { groupId ->
                updatedGroupIds += groupId
                SubscriptionWorkerOutcome.SUCCESS
            },
        )
        updater.observeTargets(listOf(target(id = 12)))

        updater.runDue()

        assertEquals(emptyList(), updatedGroupIds)
        assertEquals(5_000L, updater.nextWakeupAtMillis())

        connected = true
        nowMillis = 8_000L
        updater.runDue()

        assertEquals(listOf(12), updatedGroupIds)
        assertEquals(8_000L + IntervalMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun losing_connected_constraint_cancels_running_attempt_without_advancing_period() = runTest {
        var connected = true
        var nowMillis = 5_000L
        val firstAttemptStarted = CompletableDeferred<Unit>()
        val firstAttemptCancelled = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = updater(
            readTargets = { listOf(target(id = 19)) },
            isNetworkConnected = { connected },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    firstAttemptStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        firstAttemptCancelled.complete(Unit)
                        throw cancelled
                    }
                }
                nowMillis = 8_000L
                SubscriptionWorkerOutcome.SUCCESS
            },
        )
        updater.observeTargets(listOf(target(id = 19)))

        val firstPass = launch { updater.runDue() }
        runCurrent()
        firstAttemptStarted.await()
        connected = false
        updater.runDue()

        runCurrent()
        assertTrue(firstAttemptCancelled.isCompleted)
        firstAttemptCancelled.await()
        firstPass.join()
        assertEquals(1, attempts)
        assertEquals(5_000L, updater.nextWakeupAtMillis(), "a constraint stop leaves the work due")

        connected = true
        updater.runDue()

        assertEquals(2, attempts, "the due work starts again once CONNECTED is restored")
        assertEquals(8_000L + IntervalMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun permanent_failure_waits_for_the_next_period_instead_of_spinning_on_the_due_item() = runTest {
        var nowMillis = 1_000L
        var attempts = 0
        val updater = updater(
            readTargets = { listOf(target(id = 13)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                nowMillis = 2_000L
                SubscriptionWorkerOutcome.FAILURE
            },
        )
        updater.observeTargets(listOf(target(id = 13)))

        updater.runDue()

        val nextPeriod = 2_000L + IntervalMillis
        assertEquals(nextPeriod, updater.nextWakeupAtMillis())
        nowMillis = nextPeriod - 1L
        updater.runDue()

        assertEquals(1, attempts)
        assertEquals(nextPeriod, updater.nextWakeupAtMillis())
    }

    @Test
    fun retry_outcome_uses_fifteen_then_thirty_minute_deadlines_without_early_runs() = runTest {
        var nowMillis = 1_000L
        var attempts = 0
        val updater = updater(
            readTargets = { listOf(target(id = 16)) },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                when (attempts) {
                    1 -> {
                        nowMillis = 2_500L
                        SubscriptionWorkerOutcome.RETRY
                    }
                    2 -> {
                        nowMillis += 10L
                        SubscriptionWorkerOutcome.RETRY
                    }
                    else -> {
                        nowMillis += 10L
                        SubscriptionWorkerOutcome.SUCCESS
                    }
                }
            },
        )
        updater.observeTargets(listOf(target(id = 16)))

        updater.runDue()

        val firstRetryAtMillis = 2_500L + FifteenMinutesMillis
        assertEquals(1, attempts)
        assertEquals(firstRetryAtMillis, updater.nextWakeupAtMillis())
        nowMillis = firstRetryAtMillis - 1L
        updater.runDue()
        assertEquals(1, attempts, "the first retry must not run before its deadline")

        nowMillis = firstRetryAtMillis
        updater.runDue()

        val secondAttemptCompletedAtMillis = firstRetryAtMillis + 10L
        val secondRetryAtMillis = secondAttemptCompletedAtMillis + ThirtyMinutesMillis
        assertEquals(2, attempts)
        assertEquals(secondRetryAtMillis, updater.nextWakeupAtMillis())
        nowMillis = secondRetryAtMillis - 1L
        updater.runDue()
        assertEquals(2, attempts, "the second retry must not run before its deadline")

        nowMillis = secondRetryAtMillis
        updater.runDue()

        assertEquals(3, attempts)
        assertEquals(nowMillis + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun repeated_due_passes_and_unchanged_observation_do_not_duplicate_or_cancel_running_work() = runTest {
        var nowMillis = 1_000L
        val latestTargets = listOf(target(id = 17))
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
                callbackStarted.complete(Unit)
                try {
                    releaseCallback.await()
                    nowMillis = 2_000L
                    SubscriptionWorkerOutcome.SUCCESS
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    callbackCancelled.complete(Unit)
                    throw cancelled
                }
            },
        )
        updater.observeTargets(latestTargets)

        val firstPass = launch { updater.runDue() }
        runCurrent()
        callbackStarted.await()

        updater.observeTargets(latestTargets)
        updater.runDue()

        assertEquals(1, attempts)
        assertFalse(callbackCancelled.isCompleted)

        releaseCallback.complete(Unit)
        firstPass.join()

        assertEquals(1, attempts)
        assertEquals(2_000L + FifteenMinutesMillis, updater.nextWakeupAtMillis())
    }

    @Test
    fun disabling_an_eligible_group_removes_its_schedule() = runTest {
        var nowMillis = 1_000L
        var latestTargets = listOf(target(id = 14))
        var attempts = 0
        val updater = updater(
            readTargets = { latestTargets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                SubscriptionWorkerOutcome.SUCCESS
            },
        )
        updater.observeTargets(latestTargets)
        assertEquals(nowMillis, updater.nextWakeupAtMillis())

        latestTargets = listOf(target(id = 14, enabled = false))
        updater.observeTargets(latestTargets)
        updater.runDue()

        assertNull(updater.nextWakeupAtMillis())
        assertEquals(0, attempts)
    }

    @Test
    fun disabling_cancels_in_flight_work_and_reenabling_starts_a_fresh_due_entry() = runTest {
        var nowMillis = 100L
        var latestTargets = listOf(target(id = 15))
        val firstCallbackStarted = CompletableDeferred<Unit>()
        val oldCallbackObservedCancellation = CompletableDeferred<Unit>()
        val releaseOldCallback = CompletableDeferred<Unit>()
        var attempts = 0
        val updater = updater(
            readTargets = { latestTargets },
            isNetworkConnected = { true },
            nowMillis = { nowMillis },
            updateAutomatically = {
                attempts += 1
                if (attempts == 1) {
                    firstCallbackStarted.complete(Unit)
                    try {
                        withContext(NonCancellable) { releaseOldCallback.await() }
                        currentCoroutineContext().ensureActive()
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        // Model a callback whose underlying operation returns after its owner
                        // requested cancellation. The host must not record that stale result.
                        oldCallbackObservedCancellation.complete(Unit)
                    }
                }
                SubscriptionWorkerOutcome.SUCCESS
            },
        )
        updater.observeTargets(latestTargets)

        val oldPass = launch { updater.runDue() }
        runCurrent()
        firstCallbackStarted.await()

        latestTargets = listOf(target(id = 15, enabled = false))
        updater.observeTargets(latestTargets)
        assertNull(updater.nextWakeupAtMillis())

        nowMillis = 200L
        latestTargets = listOf(target(id = 15, enabled = true))
        updater.observeTargets(latestTargets)
        assertEquals(200L, updater.nextWakeupAtMillis())

        nowMillis = 300L
        updater.runDue()
        assertEquals(2, attempts)
        val freshDeadline = 300L + IntervalMillis
        assertEquals(freshDeadline, updater.nextWakeupAtMillis())

        // The disabled entry's late completion is stale even though it reports SUCCESS.
        nowMillis = 10_000L
        releaseOldCallback.complete(Unit)
        oldPass.join()

        assertTrue(oldCallbackObservedCancellation.isCompleted)
        assertEquals(2, attempts)
        assertEquals(freshDeadline, updater.nextWakeupAtMillis())
    }

    private fun updater(
        readTargets: () -> List<SubscriptionRefreshTarget>,
        isNetworkConnected: () -> Boolean,
        nowMillis: () -> Long,
        updateAutomatically: suspend (Int) -> SubscriptionWorkerOutcome,
    ) = DesktopSubscriptionAutoUpdater(
        readLatestTargets = readTargets,
        isNetworkConnected = isNetworkConnected,
        updateAutomatically = updateAutomatically,
        nowMillis = nowMillis,
    )

    private fun target(
        id: Int,
        enabled: Boolean = true,
        url: String = "https://example.test/$id",
        updateInterval: String = "0.25",
    ) = SubscriptionRefreshTarget(
        id = id,
        url = url,
        enabled = enabled,
        updateInterval = updateInterval,
    )

    private companion object {
        const val MinuteMillis = 60_000L
        const val FifteenMinutesMillis = 15 * MinuteMillis
        const val ThirtyMinutesMillis = 30 * MinuteMillis
        const val IntervalMillis = FifteenMinutesMillis
    }
}
