// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class SkipiToolTest {
    @Test
    fun everyToolHasItsOwnKeyForStorage() {
        assertEquals(SkipiTool.entries.size, SkipiTool.entries.map { it.key }.toSet().size)
    }

    @Test
    fun aStoredKeyFindsItsToolAndAnUnknownOneNone() {
        SkipiTool.entries.forEach { tool -> assertEquals(tool, SkipiTool.fromKey(tool.key)) }
        assertEquals(null, SkipiTool.fromKey("no-such-tool"))
    }

    @Test
    fun theDefaultPinnedToolsAreAllToolsInTheirDeclaredOrder() {
        assertEquals(SkipiTool.entries.toList(), SkipiTool.defaultPinned)
    }
}

class SkipiToolHeaderActionIdTest {
    @Test
    fun aToolSurvivesTheRoundTripThroughItsMenuId() {
        SkipiTool.entries.forEach { tool ->
            assertEquals(tool, skipiToolFromMenuId(skipiToolMenuId(tool)))
        }
    }

    @Test
    fun everyToolHasItsOwnMenuId() {
        assertEquals(SkipiTool.entries.size, SkipiTool.entries.map(::skipiToolMenuId).toSet().size)
    }

    @Test
    fun anIdThatIsNotAToolFindsNone() {
        assertEquals(null, skipiToolFromMenuId("refresh-all-subscriptions"))
        assertEquals(null, skipiToolFromMenuId("pinned-tool:no-such-tool"))
    }
}

class SkipiToolRoutingRulesTest {
    @Test
    fun theRoutingRulesAreAPinnableToolWithTheirOwnKey() {
        assertEquals(SkipiTool.RoutingRules, SkipiTool.fromKey("routing_rules"))
        assertEquals(SkipiTool.RoutingRules, skipiToolFromMenuId(skipiToolMenuId(SkipiTool.RoutingRules)))
        assertEquals(true, SkipiTool.RoutingRules in SkipiTool.defaultPinned)
    }
}
