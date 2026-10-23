// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionPeriodicWorkStateTest {
    @Test
    fun new_periodic_work_is_due_at_its_enqueue_anchor() {
        val state = SubscriptionPeriodicWorkState(
            spec = workSpec(),
            periodAnchorMillis = 1_000L,
        )

        assertEquals(1_000L, state.nextRunAtMillis)
        assertEquals(0L, state.completedPeriods)
        assertEquals(0, state.runAttemptCount)
        assertNull(state.retryAtMillis)
    }

    @Test
    fun successful_refresh_starts_the_next_period_at_completion() {
        val completedAtMillis = 9_000L
        val state = SubscriptionPeriodicWorkState(
            spec = workSpec(),
            periodAnchorMillis = 1_000L,
        ).recordAttemptResult(
            retryable = false,
            atMillis = completedAtMillis,
        )

        assertEquals(completedAtMillis, state.periodAnchorMillis)
        assertEquals(1L, state.completedPeriods)
        assertEquals(0, state.runAttemptCount)
        assertNull(state.retryAtMillis)
        assertEquals(completedAtMillis + IntervalMillis, state.nextRunAtMillis)
    }

    @Test
    fun retry_uses_fifteen_minute_base_and_doubles_for_the_next_attempt() {
        val initial = SubscriptionPeriodicWorkState(
            spec = workSpec(),
            periodAnchorMillis = 1_000L,
        )

        val firstRetry = initial.recordAttemptResult(retryable = true, atMillis = 2_000L)
        assertEquals(1, firstRetry.runAttemptCount)
        assertEquals(2_000L + FifteenMinutesMillis, firstRetry.retryAtMillis)
        assertEquals(firstRetry.retryAtMillis, firstRetry.nextRunAtMillis)

        val secondAttemptCompletedAtMillis = 2_000L + FifteenMinutesMillis + 4_000L
        val secondRetry = firstRetry.recordAttemptResult(
            retryable = true,
            atMillis = secondAttemptCompletedAtMillis,
        )
        assertEquals(2, secondRetry.runAttemptCount)
        assertEquals(secondAttemptCompletedAtMillis + ThirtyMinutesMillis, secondRetry.retryAtMillis)
        assertEquals(secondRetry.retryAtMillis, secondRetry.nextRunAtMillis)
    }

    @Test
    fun retry_delay_uses_workmanager_five_hour_maximum() {
        val state = SubscriptionPeriodicWorkState(
            spec = workSpec(),
            periodAnchorMillis = 1_000L,
            runAttemptCount = Int.MAX_VALUE - 1,
        ).recordAttemptResult(retryable = true, atMillis = 2_000L)

        assertEquals(Int.MAX_VALUE, state.runAttemptCount)
        assertEquals(2_000L + FiveHoursMillis, state.retryAtMillis)
        assertEquals(state.retryAtMillis, state.nextRunAtMillis)
    }

    @Test
    fun permanent_failure_completes_the_period_and_clears_a_pending_retry() {
        val failureCompletedAtMillis = 90_000L
        val retriedState = SubscriptionPeriodicWorkState(
            spec = workSpec(),
            periodAnchorMillis = 1_000L,
        ).recordAttemptResult(retryable = true, atMillis = 2_000L)

        // Android maps a permanent worker failure to WorkManager Result.failure().
        // For periodic requests WorkManager resets the period at completion, just as it does
        // for success, instead of ending the periodic request.
        val afterPermanentFailure = retriedState.recordAttemptResult(
            retryable = false,
            atMillis = failureCompletedAtMillis,
        )

        assertEquals(failureCompletedAtMillis, afterPermanentFailure.periodAnchorMillis)
        assertEquals(1L, afterPermanentFailure.completedPeriods)
        assertEquals(0, afterPermanentFailure.runAttemptCount)
        assertNull(afterPermanentFailure.retryAtMillis)
        assertEquals(failureCompletedAtMillis + IntervalMillis, afterPermanentFailure.nextRunAtMillis)
    }

    private fun workSpec(intervalMillis: Long = IntervalMillis) = SubscriptionWorkSpec(
        groupId = 5,
        uniqueName = subscriptionWorkName(5),
        repeatIntervalMillis = intervalMillis,
        requiresConnectedNetwork = true,
        policy = SubscriptionExistingWorkPolicy.UPDATE,
        backoffMillis = FifteenMinutesMillis,
    )

    private companion object {
        const val MinuteMillis = 60_000L
        const val FifteenMinutesMillis = 15 * MinuteMillis
        const val ThirtyMinutesMillis = 30 * MinuteMillis
        const val FiveHoursMillis = 5 * 60 * MinuteMillis
        const val IntervalMillis = 6 * 60 * MinuteMillis
    }
}
