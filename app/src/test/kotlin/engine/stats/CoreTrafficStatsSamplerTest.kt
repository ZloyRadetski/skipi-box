// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import engine.xray.XrayTags
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTrafficStatsSamplerTest {
    @Test
    fun android_traffic_aggregation_still_excludes_xray_loopback_inbounds() {
        val totals = mapOf(
            "socks-in" to XrayTrafficBytes(uplink = 10L, downlink = 20L),
            XrayTags.DEFAULT_ROUTE_LOOPBACK_INBOUND to XrayTrafficBytes(uplink = 999L, downlink = 999L),
        )

        assertEquals(
            XrayTrafficBytes(uplink = 10L, downlink = 20L),
            totals.aggregateInboundTraffic(),
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
    fun notification_refresh_interval_is_limited_to_supported_range() {
        assertEquals(1_000L, trafficStatsNotificationRefreshIntervalMillis(0))
        assertEquals(1_000L, trafficStatsNotificationRefreshIntervalMillis(1))
        assertEquals(10_000L, trafficStatsNotificationRefreshIntervalMillis(10))
        assertEquals(10_000L, trafficStatsNotificationRefreshIntervalMillis(99))
    }
}
