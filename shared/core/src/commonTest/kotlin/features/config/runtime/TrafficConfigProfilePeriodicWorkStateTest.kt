// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers profile WorkSpec eligibility and WorkManager-derived periodic timing only. It does not
 * exercise WorkManager execution, the Desktop timer host, worker callbacks, or host cancellation.
 */
class TrafficConfigProfilePeriodicWorkStateTest {
    @Test
    fun profile_refresh_spec_uses_profile_eligibility_and_stable_workmanager_fields_without_id_filter() {
        val spec = target(
            id = -7,
            resourceAutoUpdate = false,
            resourceUpdateInterval = "invalid",
        ).profileRefreshWorkSpecOrNull()

        assertEquals(
            TrafficConfigWorkSpec(
                id = -7,
                uniqueName = "traffic-config-update--7",
                repeatIntervalMillis = 6 * HourMillis,
                requiresConnectedNetwork = true,
                policy = TrafficConfigExistingWorkPolicy.UPDATE,
                backoffMillis = 15 * MinuteMillis,
                isGeo = false,
            ),
            spec,
        )
    }

    @Test
    fun profile_refresh_spec_is_absent_for_disabled_locked_blank_or_invalid_profiles() {
        val eligible = target(id = 11)
        val ineligible = listOf(
            "automatic refresh disabled" to eligible.copy(autoUpdate = false),
            "profile locked" to eligible.copy(updateLocked = true),
            "blank source URL" to eligible.copy(sourceUrl = " \t\n"),
            "disabled interval" to eligible.copy(updateInterval = "0"),
            "invalid interval" to eligible.copy(updateInterval = "invalid"),
            "interval below WorkManager minimum" to eligible.copy(updateInterval = "0.24"),
        )

        for ((reason, profile) in ineligible) {
            assertNull(profile.profileRefreshWorkSpecOrNull(), reason)
        }
    }

    @Test
    fun new_periodic_state_is_due_at_its_enqueue_anchor_and_success_reanchors_at_completion() {
        val spec = profileSpec(intervalMillis = 6 * HourMillis)
        val initial = TrafficConfigPeriodicWorkState(
            spec = spec,
            periodAnchorMillis = 1_000L,
        )

        assertEquals(spec.id, initial.profileId)
        assertEquals(spec.repeatIntervalMillis, initial.intervalMillis)
        assertEquals(1_000L, initial.nextRunAtMillis)
        assertEquals(0L, initial.completedPeriods)
        assertEquals(0, initial.runAttemptCount)
        assertNull(initial.retryAtMillis)

        val completed = initial.copy(
            completedPeriods = 2L,
            runAttemptCount = 2,
            retryAtMillis = 1_000L + 30 * MinuteMillis,
        ).recordAttemptResult(succeeded = true, atMillis = 9_000L)

        assertEquals(9_000L, completed.periodAnchorMillis)
        assertEquals(3L, completed.completedPeriods)
        assertEquals(0, completed.runAttemptCount)
        assertNull(completed.retryAtMillis)
        assertEquals(9_000L + spec.repeatIntervalMillis, completed.nextRunAtMillis)
    }

    @Test
    fun periodic_retry_delay_doubles_from_fifteen_minutes_and_caps_at_five_hours() {
        val initial = TrafficConfigPeriodicWorkState(
            spec = profileSpec(),
            periodAnchorMillis = 1_000L,
        )

        val firstRetry = initial.recordAttemptResult(succeeded = false, atMillis = 2_000L)
        assertEquals(1, firstRetry.runAttemptCount)
        assertEquals(2_000L, firstRetry.periodAnchorMillis)
        assertEquals(2_000L + 15 * MinuteMillis, firstRetry.retryAtMillis)
        assertEquals(firstRetry.retryAtMillis, firstRetry.nextRunAtMillis)

        val secondRetry = firstRetry.recordAttemptResult(succeeded = false, atMillis = 4_000L)
        assertEquals(2, secondRetry.runAttemptCount)
        assertEquals(4_000L, secondRetry.periodAnchorMillis)
        assertEquals(4_000L + 30 * MinuteMillis, secondRetry.retryAtMillis)
        assertEquals(secondRetry.retryAtMillis, secondRetry.nextRunAtMillis)

        val cappedRetry = initial.copy(runAttemptCount = Int.MAX_VALUE - 1)
            .recordAttemptResult(succeeded = false, atMillis = 6_000L)
        assertEquals(Int.MAX_VALUE, cappedRetry.runAttemptCount)
        assertEquals(6_000L + 5 * HourMillis, cappedRetry.retryAtMillis)
        assertEquals(cappedRetry.retryAtMillis, cappedRetry.nextRunAtMillis)
    }

    @Test
    fun update_preserves_period_and_retry_state_but_new_interval_applies_after_success() {
        val original = TrafficConfigPeriodicWorkState(
            spec = profileSpec(intervalMillis = 24 * HourMillis),
            periodAnchorMillis = 5_000L,
            completedPeriods = 3L,
            runAttemptCount = 2,
            retryAtMillis = 5_000L + 30 * MinuteMillis,
        )
        val updatedSpec = original.spec.copy(repeatIntervalMillis = 48 * HourMillis)

        val updated = original.withUpdatedSpec(updatedSpec)

        assertEquals(original.copy(spec = updatedSpec), updated)
        assertEquals(5_000L + 30 * MinuteMillis, updated.nextRunAtMillis)

        val succeeded = updated.recordAttemptResult(succeeded = true, atMillis = 8_000L)
        assertEquals(8_000L, succeeded.periodAnchorMillis)
        assertEquals(4L, succeeded.completedPeriods)
        assertEquals(0, succeeded.runAttemptCount)
        assertNull(succeeded.retryAtMillis)
        assertEquals(8_000L + 48 * HourMillis, succeeded.nextRunAtMillis)
    }

    @Test
    fun period_and_retry_deadline_addition_preserves_workmanager_long_overflow_semantics() {
        val spec = profileSpec(intervalMillis = 20L)
        val periodAnchor = Long.MAX_VALUE - 10L
        val periodic = TrafficConfigPeriodicWorkState(
            spec = spec,
            periodAnchorMillis = periodAnchor,
            completedPeriods = 1L,
        )
        assertEquals(periodAnchor + 20L, periodic.nextRunAtMillis)

        val retryAnchor = Long.MAX_VALUE - 2L
        val retrying = periodic.recordAttemptResult(succeeded = false, atMillis = retryAnchor)
        assertEquals(retryAnchor + 15 * MinuteMillis, retrying.retryAtMillis)
        assertEquals(retrying.retryAtMillis, retrying.nextRunAtMillis)
    }

    @Test
    fun completed_period_and_attempt_counters_saturate_at_their_maximum_values() {
        val spec = profileSpec()
        val periodSaturated = TrafficConfigPeriodicWorkState(
            spec = spec,
            periodAnchorMillis = 1_000L,
            completedPeriods = Long.MAX_VALUE,
            runAttemptCount = 1,
            retryAtMillis = 1_000L + 15 * MinuteMillis,
        ).recordAttemptResult(succeeded = true, atMillis = 2_000L)
        assertEquals(Long.MAX_VALUE, periodSaturated.completedPeriods)
        assertEquals(0, periodSaturated.runAttemptCount)
        assertNull(periodSaturated.retryAtMillis)

        val attemptsSaturated = TrafficConfigPeriodicWorkState(
            spec = spec,
            periodAnchorMillis = 1_000L,
            runAttemptCount = Int.MAX_VALUE,
        ).recordAttemptResult(succeeded = false, atMillis = 3_000L)
        assertEquals(Int.MAX_VALUE, attemptsSaturated.runAttemptCount)
        assertEquals(3_000L + 5 * HourMillis, attemptsSaturated.retryAtMillis)
    }

    private fun target(
        id: Int = 11,
        autoUpdate: Boolean = true,
        updateLocked: Boolean = false,
        sourceUrl: String = "https://example.test/profile.conf",
        updateInterval: String = "6",
        resourceAutoUpdate: Boolean = true,
        resourceUpdateInterval: String = "24",
    ) = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = sourceUrl,
        autoUpdate = autoUpdate,
        updateLocked = updateLocked,
        updateInterval = updateInterval,
        resourceAutoUpdate = resourceAutoUpdate,
        resourceUpdateInterval = resourceUpdateInterval,
    )

    private fun profileSpec(
        id: Int = 19,
        intervalMillis: Long = 24 * HourMillis,
    ) = TrafficConfigWorkSpec(
        id = id,
        uniqueName = trafficConfigWorkName(id),
        repeatIntervalMillis = intervalMillis,
        requiresConnectedNetwork = true,
        policy = TrafficConfigExistingWorkPolicy.UPDATE,
        backoffMillis = 15 * MinuteMillis,
        isGeo = false,
    )

    private companion object {
        const val MinuteMillis = 60_000L
        const val HourMillis = 60 * MinuteMillis
    }
}
