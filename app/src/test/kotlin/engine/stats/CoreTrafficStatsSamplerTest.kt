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
    fun notification_skips_idle_rebuilds_but_keeps_minute_timer_fresh() {
        val idleDelta = XrayTrafficBytes()
        assertFalse(
            shouldPublishTrafficNotification(
                trafficDelta = idleDelta,
                lastPublishedAtElapsedRealtime = 10_000L,
                nowElapsedRealtime = 20_000L,
                activeTargetChanged = false,
            ),
        )
        assertTrue(
            shouldPublishTrafficNotification(
                trafficDelta = idleDelta,
                lastPublishedAtElapsedRealtime = 10_000L,
                nowElapsedRealtime = 70_000L,
                activeTargetChanged = false,
            ),
        )
        assertTrue(
            shouldPublishTrafficNotification(
                trafficDelta = XrayTrafficBytes(downlink = 1L),
                lastPublishedAtElapsedRealtime = 20_000L,
                nowElapsedRealtime = 20_001L,
                activeTargetChanged = false,
            ),
        )
    }
}
