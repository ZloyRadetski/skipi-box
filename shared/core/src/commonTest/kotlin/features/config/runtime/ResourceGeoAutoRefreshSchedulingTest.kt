// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceGeoAutoRefreshSchedulingTest {
    @Test
    fun reconcile_starts_new_resource_schedules_and_ignores_profile_refresh_fields() {
        val result = GeoResourcePeriodicScheduleState().reconcile(
            targets = listOf(
                target(
                    id = 0,
                    resourceAutoUpdate = true,
                    resourceUpdateInterval = "24",
                    sourceUrl = "",
                    autoUpdate = false,
                    updateLocked = true,
                ),
                target(id = 4, resourceAutoUpdate = true, resourceUpdateInterval = "12"),
                target(id = 5, resourceAutoUpdate = false, resourceUpdateInterval = "12"),
                target(id = 6, resourceAutoUpdate = true, resourceUpdateInterval = "0.24"),
            ),
            nowMillis = 1_000L,
        )

        assertEquals(setOf(0, 4), result.newlyEligibleProfileIds)
        assertEquals(emptySet(), result.removedProfileIds)
        assertEquals(setOf(0, 4), result.state.schedulesByProfileId.keys)
        assertEquals(1_000L, result.state.schedulesByProfileId.getValue(0).periodAnchorMillis)
        assertEquals(1_000L, result.state.schedulesByProfileId.getValue(0).nextRunAtMillis)
    }

    @Test
    fun interval_update_keeps_period_anchor_and_removal_then_reenable_starts_a_new_period() {
        val first = GeoResourcePeriodicScheduleState()
            .reconcile(listOf(target(id = 7, resourceUpdateInterval = "24")), nowMillis = 100L)
            .state
            .recordAttemptResult(profileId = 7, succeeded = true, atMillis = 500L)

        val changed = first.reconcile(
            targets = listOf(target(id = 7, resourceUpdateInterval = "48")),
            nowMillis = 600L,
        )

        val changedSchedule = changed.state.schedulesByProfileId.getValue(7)
        assertEquals(emptySet(), changed.newlyEligibleProfileIds)
        assertEquals(500L, changedSchedule.periodAnchorMillis)
        assertEquals(1L, changedSchedule.completedPeriods)
        assertEquals(48 * HourMillis, changedSchedule.intervalMillis)
        assertEquals(500L + 48 * HourMillis, changedSchedule.nextRunAtMillis)

        val removed = changed.state.reconcile(emptyList(), nowMillis = 700L)
        assertEquals(setOf(7), removed.removedProfileIds)
        assertTrue(removed.state.schedulesByProfileId.isEmpty())

        val reenabled = removed.state.reconcile(
            targets = listOf(target(id = 7, resourceUpdateInterval = "48")),
            nowMillis = 900L,
        )
        val newSchedule = reenabled.state.schedulesByProfileId.getValue(7)
        assertEquals(setOf(7), reenabled.newlyEligibleProfileIds)
        assertEquals(900L, newSchedule.periodAnchorMillis)
        assertEquals(0L, newSchedule.completedPeriods)
        assertEquals(900L, newSchedule.nextRunAtMillis)
    }

    @Test
    fun periodic_and_one_time_retries_follow_their_distinct_workmanager_bases() {
        val initial = GeoResourcePeriodicScheduleState()
            .reconcile(listOf(target(id = 3, resourceUpdateInterval = "24")), nowMillis = 1_000L)
            .state

        val failed = initial.recordAttemptResult(profileId = 3, succeeded = false, atMillis = 2_000L)
        val retry = failed.schedulesByProfileId.getValue(3)
        assertEquals(1, retry.runAttemptCount)
        assertEquals(2_000L + 15 * MinuteMillis, retry.nextRunAtMillis)
        assertEquals(2_000L, retry.periodAnchorMillis)

        val succeeded = failed.recordAttemptResult(profileId = 3, succeeded = true, atMillis = 3_000L)
        val nextPeriod = succeeded.schedulesByProfileId.getValue(3)
        assertEquals(0, nextPeriod.runAttemptCount)
        assertNull(nextPeriod.retryAtMillis)
        assertEquals(1L, nextPeriod.completedPeriods)
        assertEquals(3_000L, nextPeriod.periodAnchorMillis)
        assertEquals(3_000L + 24 * HourMillis, nextPeriod.nextRunAtMillis)

        assertEquals(15 * MinuteMillis, trafficConfigGeoRetryDelayMillis(1))
        assertEquals(30 * MinuteMillis, trafficConfigGeoRetryDelayMillis(2))
        assertEquals(5 * HourMillis, trafficConfigGeoRetryDelayMillis(Int.MAX_VALUE))
        assertEquals(30_000L, trafficConfigGeoOnceRetryDelayMillis(1))
        assertEquals(60_000L, trafficConfigGeoOnceRetryDelayMillis(2))
        assertEquals(5 * HourMillis, trafficConfigGeoOnceRetryDelayMillis(Int.MAX_VALUE))
    }

    @Test
    fun active_profile_trigger_drops_initial_state_and_tracks_only_id_and_two_geo_urls() {
        val initial = GeoResourceRefreshTriggerState().observe(
            ActiveGeoResourceRefreshTrigger(profileId = 2, customGeoIpUrl = "ip-a", customGeoSiteUrl = "site-a"),
        )
        assertNull(initial.oneTimeProfileId)

        val unchanged = initial.state.observe(
            ActiveGeoResourceRefreshTrigger(profileId = 2, customGeoIpUrl = "ip-a", customGeoSiteUrl = "site-a"),
        )
        assertNull(unchanged.oneTimeProfileId)

        val ipChanged = unchanged.state.observe(
            ActiveGeoResourceRefreshTrigger(profileId = 2, customGeoIpUrl = "ip-b", customGeoSiteUrl = "site-a"),
        )
        assertEquals(2, ipChanged.oneTimeProfileId)

        val siteChanged = ipChanged.state.observe(
            ActiveGeoResourceRefreshTrigger(profileId = 2, customGeoIpUrl = "ip-b", customGeoSiteUrl = "site-b"),
        )
        assertEquals(2, siteChanged.oneTimeProfileId)

        val profileChanged = siteChanged.state.observe(
            ActiveGeoResourceRefreshTrigger(profileId = 3, customGeoIpUrl = "ip-b", customGeoSiteUrl = "site-b"),
        )
        assertEquals(3, profileChanged.oneTimeProfileId)
    }

    @Test
    fun one_time_trigger_is_separate_from_periodic_work_and_matches_android_positive_id_gate() {
        val periodic = GeoResourcePeriodicScheduleState()
            .reconcile(listOf(target(id = 8, resourceUpdateInterval = "24")), nowMillis = 4_000L)
            .state
        val seen = GeoResourceRefreshTriggerState().observe(
            ActiveGeoResourceRefreshTrigger(profileId = 8, customGeoIpUrl = "", customGeoSiteUrl = ""),
        )
        val changed = seen.state.observe(
            ActiveGeoResourceRefreshTrigger(profileId = 8, customGeoIpUrl = "custom-ip", customGeoSiteUrl = ""),
        )

        assertEquals(setOf(8), periodic.schedulesByProfileId.keys)
        assertEquals(8, changed.oneTimeProfileId)

        val zeroId = changed.state.observe(
            ActiveGeoResourceRefreshTrigger(profileId = 0, customGeoIpUrl = "", customGeoSiteUrl = ""),
        )
        assertNull(zeroId.oneTimeProfileId)
    }

    @Test
    fun latest_worker_admission_checks_only_matching_profile_and_resource_auto_update() {
        val latest = listOf(
            target(
                id = 10,
                resourceAutoUpdate = true,
                resourceUpdateInterval = "invalid",
                sourceUrl = "",
                autoUpdate = false,
                updateLocked = true,
            ),
            target(id = 11, resourceAutoUpdate = false, resourceUpdateInterval = "24"),
            target(id = -1, resourceAutoUpdate = true, resourceUpdateInterval = "24"),
        )

        assertTrue(shouldRunGeoResourceAutoUpdate(requestedProfileId = 10, latestTargets = latest))
        assertFalse(shouldRunGeoResourceAutoUpdate(requestedProfileId = 11, latestTargets = latest))
        assertFalse(shouldRunGeoResourceAutoUpdate(requestedProfileId = -1, latestTargets = latest))
        assertFalse(shouldRunGeoResourceAutoUpdate(requestedProfileId = 12, latestTargets = latest))
    }

    @Test
    fun periodic_and_retry_deadlines_keep_workmanager_long_addition_at_the_boundary() {
        val periodAnchor = Long.MAX_VALUE - 10L
        val periodic = TrafficConfigPeriodicWorkState(
            spec = TrafficConfigWorkSpec(
                id = 14,
                uniqueName = trafficConfigGeoWorkName(14),
                repeatIntervalMillis = 20L,
                requiresConnectedNetwork = true,
                policy = TrafficConfigExistingWorkPolicy.UPDATE,
                backoffMillis = 15 * MinuteMillis,
                isGeo = true,
            ),
            periodAnchorMillis = periodAnchor,
            completedPeriods = 1L,
        )
        assertEquals(periodAnchor + 20L, periodic.nextRunAtMillis)

        val state = GeoResourcePeriodicScheduleState(
            schedulesByProfileId = mapOf(14 to periodic),
        )
        val retryAnchor = Long.MAX_VALUE - 2L
        val retrying = state.recordAttemptResult(
            profileId = 14,
            succeeded = false,
            atMillis = retryAnchor,
        ).schedulesByProfileId.getValue(14)
        assertEquals(retryAnchor + 15 * MinuteMillis, retrying.nextRunAtMillis)
    }

    private fun target(
        id: Int,
        resourceAutoUpdate: Boolean = true,
        resourceUpdateInterval: String = "24",
        sourceUrl: String = "https://example.test/subscription",
        autoUpdate: Boolean = true,
        updateLocked: Boolean = false,
    ) = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = sourceUrl,
        autoUpdate = autoUpdate,
        updateLocked = updateLocked,
        updateInterval = "6",
        resourceAutoUpdate = resourceAutoUpdate,
        resourceUpdateInterval = resourceUpdateInterval,
    )

    private companion object {
        const val MinuteMillis = 60_000L
        const val HourMillis = 60 * MinuteMillis
    }
}
