// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrategyGroupDefaultsTest {
    @Test
    fun aNewGroupChecksItsMembersOftenEnoughToLeaveADeadServerQuickly() {
        assertEquals("15s", StrategyGroup().probeInterval)
    }

    @Test
    fun onlyTheStrategiesThatSpreadConnectionsAreFlagged() {
        listOf(
            StrategyGroupConstants.TYPE_RANDOM,
            StrategyGroupConstants.TYPE_ROUND_ROBIN,
            StrategyGroupConstants.TYPE_LEAST_LOAD,
        ).forEach { strategy ->
            assertTrue(strategyGroupSpreadsConnections(strategy), strategy)
        }
        listOf(
            StrategyGroupConstants.TYPE_SELECT,
            StrategyGroupConstants.TYPE_LEAST_PING,
            StrategyGroupConstants.TYPE_FALLBACK,
        ).forEach { strategy ->
            assertFalse(strategyGroupSpreadsConnections(strategy), strategy)
        }
    }
}
