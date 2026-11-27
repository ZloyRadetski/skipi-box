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

    @Test
    fun updatingAGroupCopiesEveryHealthCheckSetting() {
        val target = StrategyGroup(remarks = "Old")

        target.update(StrategyGroup(remarks = "New", probeInterval = "30s", probeTimeout = "9s"))

        assertEquals("30s", target.probeInterval)
        assertEquals("9s", target.probeTimeout)
    }

    @Test
    fun aGroupSavedWithTheRemovedToleranceSettingStillLoadsAndNoLongerStoresIt() {
        val saved = """{"protocol":"strategyGroup","payload":{"remarks":"Old","probeInterval":"30s","tolerance":"150ms"}}"""
            .replace("strategyGroup", ProxyServerConstants.PROTOCOL_STRATEGY_GROUP)

        val loaded = saved.decodePersistedProxyServer() as StrategyGroup

        assertEquals("Old", loaded.remarks)
        assertEquals("30s", loaded.probeInterval)
        assertFalse("tolerance" in loaded.encodePersistedProxyServer())
    }
}
