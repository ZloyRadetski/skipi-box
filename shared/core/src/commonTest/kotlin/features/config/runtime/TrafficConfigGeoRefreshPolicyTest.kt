// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrafficConfigGeoRefreshPolicyTest {
    @Test
    fun resource_schedule_is_independent_of_profile_refresh_fields() {
        val target = TrafficConfigRefreshTarget(
            id = 8,
            sourceUrl = "",
            autoUpdate = false,
            updateLocked = true,
            updateInterval = "invalid",
            resourceAutoUpdate = true,
            resourceUpdateInterval = "12",
        )

        assertEquals(
            TrafficConfigWorkSpec(
                id = 8,
                uniqueName = "traffic-config-geo-update-8",
                repeatIntervalMillis = 12 * HourMillis,
                requiresConnectedNetwork = true,
                policy = TrafficConfigExistingWorkPolicy.UPDATE,
                backoffMillis = 15 * MinuteMillis,
                isGeo = true,
            ),
            target.geoRefreshWorkSpecOrNull(),
        )
    }

    @Test
    fun disabled_or_invalid_resource_schedule_has_no_periodic_spec() {
        assertNull(
            target(resourceAutoUpdate = false, resourceUpdateInterval = "12")
                .geoRefreshWorkSpecOrNull(),
        )
        assertNull(
            target(resourceAutoUpdate = true, resourceUpdateInterval = "0.24")
                .geoRefreshWorkSpecOrNull(),
        )
        assertNull(
            target(resourceAutoUpdate = true, resourceUpdateInterval = "invalid")
                .geoRefreshWorkSpecOrNull(),
        )
    }

    @Test
    fun resource_retry_delay_matches_workmanager_exponential_backoff_and_cap() {
        val expectedMinutes = listOf(15L, 30L, 60L, 120L, 240L, 300L, 300L)

        assertEquals(
            expectedMinutes.map { it * MinuteMillis },
            (1..expectedMinutes.size).map(::trafficConfigGeoRetryDelayMillis),
        )
        assertEquals(300 * MinuteMillis, trafficConfigGeoRetryDelayMillis(Int.MAX_VALUE))
    }

    private fun target(
        resourceAutoUpdate: Boolean,
        resourceUpdateInterval: String,
    ) = TrafficConfigRefreshTarget(
        id = 8,
        sourceUrl = "",
        autoUpdate = false,
        updateLocked = true,
        updateInterval = "invalid",
        resourceAutoUpdate = resourceAutoUpdate,
        resourceUpdateInterval = resourceUpdateInterval,
    )

    private companion object {
        const val MinuteMillis = 60_000L
        const val HourMillis = 60 * MinuteMillis
    }
}
