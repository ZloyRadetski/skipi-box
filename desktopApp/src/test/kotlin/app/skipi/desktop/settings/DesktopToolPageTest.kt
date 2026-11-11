// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.navigation.SkipiTool
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopToolPageTest {
    @Test
    fun theDiagnosticsAndLogsOpenTheirOwnSettingsPage() {
        assertEquals(DesktopToolTarget.SettingsPage(DesktopSettingsDestination.SpeedTest), SkipiTool.SpeedTest.desktopTarget())
        assertEquals(DesktopToolTarget.SettingsPage(DesktopSettingsDestination.DnsLeakTest), SkipiTool.DnsLeakTest.desktopTarget())
        assertEquals(DesktopToolTarget.SettingsPage(DesktopSettingsDestination.IpInfo), SkipiTool.IpInfo.desktopTarget())
        assertEquals(DesktopToolTarget.SettingsPage(DesktopSettingsDestination.CoreLogs), SkipiTool.CoreLogs.desktopTarget())
    }

    @Test
    fun theRoutingRulesOpenTheRuleEditorOfTheSelectedProfileNotASettingsPage() {
        assertEquals(DesktopToolTarget.SelectedProfileRules, SkipiTool.RoutingRules.desktopTarget())
    }

    @Test
    fun noTargetIsSharedBetweenTools() {
        assertEquals(SkipiTool.entries.size, SkipiTool.entries.map { it.desktopTarget() }.toSet().size)
    }
}
