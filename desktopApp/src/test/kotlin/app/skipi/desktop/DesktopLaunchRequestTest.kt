// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.SkipiDeepLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLaunchRequestTest {
    @Test
    fun aPlainLaunchCarriesNoLinks() {
        val request = parseDesktopLaunchArgs(emptyList())

        assertFalse(request.hidden)
        assertTrue(request.links.isEmpty())
    }

    @Test
    fun skipiLinksArePassedThroughAsTheyAre() {
        val request = parseDesktopLaunchArgs(listOf("--hidden", "skipi://toggle", "SKIPI://connect"))

        assertTrue(request.hidden)
        assertEquals(listOf("skipi://toggle", "SKIPI://connect"), request.links)
    }

    @Test
    fun shortOptionsAreTheSameControlLinks() {
        assertEquals(listOf("skipi://toggle"), parseDesktopLaunchArgs(listOf("--toggle")).links)
        assertEquals(listOf("skipi://connect"), parseDesktopLaunchArgs(listOf("--connect")).links)
        assertEquals(listOf("skipi://disconnect"), parseDesktopLaunchArgs(listOf("--disconnect")).links)
    }

    @Test
    fun unrelatedArgumentsAreIgnored() {
        assertTrue(parseDesktopLaunchArgs(listOf("--unknown", "https://example.com", "file.txt")).links.isEmpty())
    }

    @Test
    fun controlLinksFollowAndroid() {
        assertEquals(DesktopTunnelCommand.Start, desktopTunnelCommandFor(SkipiDeepLink.Connect, running = false))
        assertEquals(DesktopTunnelCommand.None, desktopTunnelCommandFor(SkipiDeepLink.Connect, running = true))
        assertEquals(DesktopTunnelCommand.Stop, desktopTunnelCommandFor(SkipiDeepLink.Disconnect, running = true))
        assertEquals(DesktopTunnelCommand.None, desktopTunnelCommandFor(SkipiDeepLink.Disconnect, running = false))
        assertEquals(DesktopTunnelCommand.Start, desktopTunnelCommandFor(SkipiDeepLink.Toggle, running = false))
        assertEquals(DesktopTunnelCommand.Stop, desktopTunnelCommandFor(SkipiDeepLink.Toggle, running = true))
        assertEquals(DesktopTunnelCommand.None, desktopTunnelCommandFor(SkipiDeepLink.Open, running = true))
    }

    @Test
    fun theNotificationReportsWhatTheLinkActuallyAchieved() {
        assertEquals(DesktopLinkOutcome.Connected, desktopLinkOutcome(DesktopTunnelCommand.Start, runningAfter = true))
        assertEquals(DesktopLinkOutcome.ConnectFailed, desktopLinkOutcome(DesktopTunnelCommand.Start, runningAfter = false))
        assertEquals(DesktopLinkOutcome.Disconnected, desktopLinkOutcome(DesktopTunnelCommand.Stop, runningAfter = false))
        assertEquals(DesktopLinkOutcome.DisconnectFailed, desktopLinkOutcome(DesktopTunnelCommand.Stop, runningAfter = true))
    }

    @Test
    fun aLinkThatChangedNothingStillReportsTheCurrentState() {
        assertEquals(DesktopLinkOutcome.Connected, desktopLinkOutcome(DesktopTunnelCommand.None, runningAfter = true))
        assertEquals(DesktopLinkOutcome.Disconnected, desktopLinkOutcome(DesktopTunnelCommand.None, runningAfter = false))
    }

    @Test
    fun theNotificationIsTitledSkipiAndCarriesTheStatusWithAnyFailureReason() {
        assertEquals(DesktopNotificationText("SKIPI", "VPN включён"), desktopLinkNotificationText("VPN включён", ""))
        assertEquals(
            DesktopNotificationText("SKIPI", "Не удалось включить VPN: нет сети"),
            desktopLinkNotificationText("Не удалось включить VPN", "нет сети"),
        )
    }

    @Test
    fun theSavedLanguageIsAppliedWhenTheApplicationStarts() {
        var applied: java.util.Locale? = null

        applyDesktopStartupLanguage(DesktopAppSettings(languageTag = "ru"), java.util.Locale.ENGLISH) { applied = it }
        assertEquals("ru", applied?.language)

        applyDesktopStartupLanguage(DesktopAppSettings(languageTag = null), java.util.Locale.ENGLISH) { applied = it }
        assertEquals(java.util.Locale.ENGLISH, applied)
    }
}
