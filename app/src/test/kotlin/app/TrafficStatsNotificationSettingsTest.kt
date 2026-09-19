// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficStatsNotificationSettingsTest {
    @Test
    fun refreshInterval_defaultsToTwoSeconds_andVpnResetRestoresIt() {
        assertEquals(
            DefaultTrafficStatsNotificationRefreshIntervalSeconds,
            AppState().trafficStatsNotificationRefreshIntervalSeconds,
        )
        assertEquals(
            DefaultTrafficStatsNotificationRefreshIntervalSeconds,
            AppState(trafficStatsNotificationRefreshIntervalSeconds = 10)
                .withVpnSettingsReset()
                .trafficStatsNotificationRefreshIntervalSeconds,
        )
    }
}
