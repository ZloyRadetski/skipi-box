// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.navigation

import app.skipi.ui.navigation.SkipiTool

/**
 * The full-screen page Android opens for a tool of the navigation. The routing rules belong to a profile,
 * so they open the rule editor of the one that is active ([activeTrafficConfigId]).
 */
fun SkipiTool.route(activeTrafficConfigId: Int): Route = when (this) {
    SkipiTool.SpeedTest -> Route.SpeedTest
    SkipiTool.DnsLeakTest -> Route.DnsLeakTest
    SkipiTool.IpInfo -> Route.IpInfo
    SkipiTool.CoreLogs -> Route.CoreLogs
    SkipiTool.RoutingRules -> Route.TrafficConfigSection(
        trafficConfigId = activeTrafficConfigId,
        section = TrafficConfigEditorSection.RoutingRules,
    )
}
