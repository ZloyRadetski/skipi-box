// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.navigation

import app.skipi.ui.navigation.SkipiTool
import org.junit.Assert.assertEquals
import org.junit.Test

class SkipiToolRouteTest {
    @Test
    fun everyToolOpensItsOwnFullScreenPage() {
        assertEquals(Route.SpeedTest, SkipiTool.SpeedTest.route(activeTrafficConfigId = 7))
        assertEquals(Route.DnsLeakTest, SkipiTool.DnsLeakTest.route(activeTrafficConfigId = 7))
        assertEquals(Route.IpInfo, SkipiTool.IpInfo.route(activeTrafficConfigId = 7))
        assertEquals(Route.CoreLogs, SkipiTool.CoreLogs.route(activeTrafficConfigId = 7))
    }

    @Test
    fun theRoutingRulesOpenTheRuleEditorOfTheActiveProfile() {
        assertEquals(
            Route.TrafficConfigSection(trafficConfigId = 7, section = TrafficConfigEditorSection.RoutingRules),
            SkipiTool.RoutingRules.route(activeTrafficConfigId = 7),
        )
        assertEquals(
            Route.TrafficConfigSection(trafficConfigId = 3, section = TrafficConfigEditorSection.RoutingRules),
            SkipiTool.RoutingRules.route(activeTrafficConfigId = 3),
        )
    }

    @Test
    fun noRouteIsSharedBetweenTools() {
        assertEquals(SkipiTool.entries.size, SkipiTool.entries.map { it.route(activeTrafficConfigId = 7) }.toSet().size)
    }
}
