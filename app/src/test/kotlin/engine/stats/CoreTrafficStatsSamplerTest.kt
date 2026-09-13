// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTrafficStatsSamplerTest {
    @Test
    fun sampling_policy_is_fast_only_for_visible_meaningful_traffic() {
        assertEquals(
            CoreTrafficStatsActivePollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = true,
                hasMeaningfulTraffic = true,
            ),
        )
        assertEquals(
            CoreTrafficStatsIdlePollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = true,
                hasMeaningfulTraffic = false,
            ),
        )
        assertEquals(
            CoreTrafficStatsScreenOffPollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = false,
                hasMeaningfulTraffic = true,
            ),
        )
    }

    @Test
    fun notification_refreshes_on_the_selected_cadence_even_when_traffic_is_idle() {
        assertFalse(
            shouldPublishTrafficNotification(
                lastPublishedAtElapsedRealtime = 10_000L,
                nowElapsedRealtime = 10_999L,
                activeTargetChanged = false,
                refreshIntervalMillis = 1_000L,
            ),
        )
        assertTrue(
            shouldPublishTrafficNotification(
                lastPublishedAtElapsedRealtime = 10_000L,
                nowElapsedRealtime = 11_000L,
                activeTargetChanged = false,
                refreshIntervalMillis = 1_000L,
            ),
        )
        assertTrue(
            shouldPublishTrafficNotification(
                lastPublishedAtElapsedRealtime = 10_000L,
                nowElapsedRealtime = 10_001L,
                activeTargetChanged = true,
                refreshIntervalMillis = 10_000L,
            ),
        )
    }

    @Test
    fun notification_request_controls_sampling_but_never_slows_a_visible_consumer() {
        assertEquals(
            1_000L,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = false,
                hasMeaningfulTraffic = false,
                requestedRefreshIntervalMillis = 1_000L,
                hasDefaultFrequencyConsumer = false,
            ),
        )
        assertEquals(
            10_000L,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = false,
                hasMeaningfulTraffic = false,
                requestedRefreshIntervalMillis = 10_000L,
                hasDefaultFrequencyConsumer = false,
            ),
        )
        assertEquals(
            CoreTrafficStatsActivePollIntervalMillis,
            coreTrafficStatsPollIntervalMillis(
                isScreenInteractive = true,
                hasMeaningfulTraffic = true,
                requestedRefreshIntervalMillis = 10_000L,
                hasDefaultFrequencyConsumer = true,
            ),
        )
    }

    @Test
    fun notification_refresh_interval_is_limited_to_supported_range() {
        assertEquals(1_000L, trafficStatsNotificationRefreshIntervalMillis(0))
        assertEquals(1_000L, trafficStatsNotificationRefreshIntervalMillis(1))
        assertEquals(10_000L, trafficStatsNotificationRefreshIntervalMillis(10))
        assertEquals(10_000L, trafficStatsNotificationRefreshIntervalMillis(99))
    }
}
