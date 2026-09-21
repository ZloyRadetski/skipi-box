// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyGroupPolicyMappingTest {
    @Test
    fun shadowrocketAliasesMapToOneStoredStrategy() {
        assertEquals(
            StrategyGroupConstants.TYPE_ROUND_ROBIN,
            "roundrobin".toStrategyGroupTypeFromShadowrocketPolicy(),
        )
        assertEquals(
            StrategyGroupConstants.TYPE_LEAST_LOAD,
            "leastload".toStrategyGroupTypeFromShadowrocketPolicy(),
        )
        assertEquals(
            StrategyGroupConstants.TYPE_LEAST_PING,
            "url-test".toStrategyGroupTypeFromShadowrocketPolicy(),
        )
        assertEquals(
            StrategyGroupConstants.TYPE_SELECT,
            "unknown".toStrategyGroupTypeFromShadowrocketPolicy(),
        )
    }

    @Test
    fun storedStrategiesRoundTripToShadowrocketAndXrayNames() {
        assertEquals("round-robin", StrategyGroupConstants.TYPE_ROUND_ROBIN.toShadowrocketPolicyGroupType())
        assertEquals("url-test", StrategyGroupConstants.TYPE_LEAST_PING.toShadowrocketPolicyGroupType())
        assertEquals(
            StrategyGroupConstants.TYPE_LEAST_PING,
            StrategyGroupConstants.TYPE_FALLBACK.toXrayBalancerStrategy(),
        )
        assertEquals(
            StrategyGroupConstants.TYPE_ROUND_ROBIN,
            "roundrobin".toXrayBalancerStrategy(),
        )
        assertEquals(
            StrategyGroupConstants.TYPE_LEAST_LOAD,
            "least-load".toXrayBalancerStrategy(),
        )
    }
}
