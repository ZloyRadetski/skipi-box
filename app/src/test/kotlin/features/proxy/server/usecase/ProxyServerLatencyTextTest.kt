// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import engine.proxy.latency.ProxyServerLatencyTestResult
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxyServerLatencyTextTest {
    private val empty = StrategyGroup(remarks = "Auto", strategy = "leastPing")

    @Test
    fun aGroupWithoutMembersSaysSoInsteadOfTimeout() {
        assertEquals("No servers", proxyServerLatencyText(empty, ProxyServerLatencyTestResult.Failed, "Timeout", "No servers"))
    }

    @Test
    fun everythingElseKeepsItsUsualText() {
        assertEquals("Timeout", proxyServerLatencyText(empty.copy(filter = "x"), ProxyServerLatencyTestResult.Failed, "Timeout", "No servers"))
        assertEquals("Timeout", proxyServerLatencyText(HTTP(remarks = "h"), ProxyServerLatencyTestResult.Failed, "Timeout", "No servers"))
        assertEquals("87 ms", proxyServerLatencyText(HTTP(remarks = "h"), ProxyServerLatencyTestResult(87), "Timeout", "No servers"))
    }
}
