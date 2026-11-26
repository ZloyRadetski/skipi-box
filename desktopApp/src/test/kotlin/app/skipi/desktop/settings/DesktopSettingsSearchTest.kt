// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import app.skipi.ui.settings.search.SettingsSearchPage
import app.skipi.ui.settings.search.settingsSearchEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSettingsSearchTest {
    private fun ids(vpnPage: Boolean = true, systemProxy: Boolean = true) =
        settingsSearchEntries(desktopSettingsSearchHost(vpnPage, systemProxy)).map { it.id }

    @Test
    fun everyResultOpensItsOwnPage() {
        val destinations = SettingsSearchPage.entries.map { it.desktopDestination() }
        assertEquals(destinations.distinct(), destinations)
        assertFalse(DesktopSettingsDestination.Overview in destinations)
    }

    @Test
    fun thePagesTheOverviewListsAreFound() {
        val ids = ids()
        for (destination in desktopSettingsVisibleDestinations(vpnPage = true)) {
            assertTrue("page:${destination.name}" in ids, destination.name)
        }
    }

    @Test
    fun withoutATunHostNothingOfTheVpnPageIsFound() {
        val ids = ids(vpnPage = false)
        assertFalse("page:Vpn" in ids)
        assertFalse("page:NetworkAutomation" in ids)
        assertFalse("settings_tun_mode_title" in ids)
    }

    @Test
    fun systemProxyRowsFollowTheMachine() {
        assertTrue("desktop_settings_use_system_proxy" in ids())
        assertFalse("desktop_settings_use_system_proxy" in ids(systemProxy = false))
    }

    @Test
    fun rowsDesktopHidesAreNotFound() {
        val ids = ids()
        for (id in listOf(
            "settings_kill_switch", "settings_logcat", "settings_app_icon", "settings_enable_haptics",
            "subscription_expiry_notifications_enable", "settings_auto_install_night_title",
        )) assertFalse(id in ids, id)
    }

    @Test
    fun controlOrCommandWithFAsksForTheSearch() {
        assertTrue(isSettingsSearchShortcut(Key.F, KeyEventType.KeyDown, ctrl = true, meta = false))
        assertTrue(isSettingsSearchShortcut(Key.F, KeyEventType.KeyDown, ctrl = false, meta = true))
        assertFalse(isSettingsSearchShortcut(Key.F, KeyEventType.KeyDown, ctrl = false, meta = false))
        assertFalse(isSettingsSearchShortcut(Key.F, KeyEventType.KeyUp, ctrl = true, meta = false))
        assertFalse(isSettingsSearchShortcut(Key.G, KeyEventType.KeyDown, ctrl = true, meta = false))
    }
}
