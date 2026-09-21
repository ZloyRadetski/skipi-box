// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ShadowrocketStrategyGroupTest {
    @Test
    fun rendersPortableGroupLineFromEditableStrategy() {
        val strategy = StrategyGroup(
            remarks = "Fastest",
            strategy = StrategyGroupConstants.TYPE_FALLBACK,
            proxyServerIds = listOf(10, 20, 30),
            probeUrl = "https://probe.example/204",
            probeInterval = "2m",
            probeTimeout = "7s",
            displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
            enableBurstProbe = true,
            tolerance = "75ms",
        )

        assertEquals(
            "Fastest = fallback, Node A, Node B, url=https://probe.example/204, interval=120, timeout=7, " +
                "skipi-display=active_config, skipi-burst-probe=true, skipi-tolerance=75ms",
            strategy.toShadowrocketLine(
                listOf(
                    ProxyGroupServerChoice(10, "Node A"),
                    ProxyGroupServerChoice(20, "Node B"),
                ),
            ),
        )
    }

    @Test
    fun convertsParsedGroupToEditableStrategyWithoutLosingExtensions() {
        val parsed = ShadowrocketPolicyGroup(
            lineNumber = 4,
            name = "Reliable",
            type = "round-robin",
            members = listOf("Node A", "missing", "node b"),
            url = "https://probe.example/204",
            intervalSeconds = 30,
            timeoutSeconds = 8,
            displayMode = StrategyGroupDisplayMode.ALWAYS,
            enableBurstProbe = false,
            tolerance = "150ms",
        )

        val editable = parsed.toEditableStrategyGroup(
            trafficConfigId = 7,
            serverChoices = listOf(
                ProxyGroupServerChoice(10, "Node A"),
                ProxyGroupServerChoice(20, "Node B"),
            ),
        )

        assertEquals(StrategyGroupConstants.TYPE_ROUND_ROBIN, editable.strategy)
        assertEquals(listOf(10, 20), editable.proxyServerIds)
        assertEquals(7, editable.sourceTrafficConfigId)
        assertEquals("Reliable", editable.sourcePolicyGroupName)
        assertEquals("30s", editable.probeInterval)
        assertEquals("8s", editable.probeTimeout)
        assertEquals("https://probe.example/204", editable.probeUrl)
        assertEquals(StrategyGroupDisplayMode.ALWAYS, editable.displayMode)
        assertEquals(false, editable.enableBurstProbe)
        assertEquals("150ms", editable.tolerance)
    }
}
