// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import app.skipi.ui.settings.AppearanceSettingsCapabilities
import app.skipi.ui.settings.GeneralSettingsCapabilities
import app.skipi.ui.settings.LogsSettingsCapabilities
import app.skipi.ui.settings.SubscriptionSettingsCapabilities
import app.skipi.ui.settings.VpnSettingsCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSearchCatalogTest {
    private val android = SettingsSearchHost()

    /** What the desktop application declares, with TUN and the system proxy available. */
    private val desktop = SettingsSearchHost(
        appearance = AppearanceSettingsCapabilities(
            systemTheme = false,
            appIcon = false,
            connectionLog = true,
            navigationStyle = true,
            floatingPowerButton = false,
            subscriptionSwipe = false,
        ),
        vpn = VpnSettingsCapabilities(
            hevTun = false,
            killSwitch = false,
            systemVpnSettings = false,
            batteryOptimization = false,
            wakeLock = false,
            seamlessSwitching = false,
        ),
        general = GeneralSettingsCapabilities.Desktop,
        subscriptions = SubscriptionSettingsCapabilities(expiryNotifications = false),
        logs = LogsSettingsCapabilities(logcat = false),
        nightUpdateInstall = false,
        pages = SettingsSearchPage.entries.toSet(),
        desktopVpnRows = true,
        systemProxy = true,
        desktopDiagnostics = true,
    )

    private fun ids(host: SettingsSearchHost): List<String> = settingsSearchEntries(host).map { it.id }

    @Test
    fun everyPageIsFoundOnAndroid() {
        val pages = settingsSearchEntries(android).filter { it.isPage }.map { it.page }
        assertEquals(SettingsSearchPage.entries.toSet(), pages.toSet())
        assertEquals(pages.size, pages.toSet().size)
    }

    @Test
    fun idsAreUniqueOnEveryHost() {
        for (host in listOf(android, desktop)) {
            val ids = ids(host)
            assertTrue(ids.isNotEmpty())
            assertEquals(ids.distinct(), ids)
        }
    }

    @Test
    fun aHiddenRowHasAParentOnTheSamePageOfTheSameHost() {
        for (host in listOf(android, desktop)) {
            val entries = settingsSearchEntries(host).associateBy { it.id }
            val children = entries.values.filter { it.parent != null }
            assertTrue(children.isNotEmpty())
            for (child in children) {
                var parent = entries[child.parent]
                // The fallback chain ends at a row that is always shown.
                var steps = 0
                while (parent != null) {
                    assertEquals(child.page, parent.page, child.id)
                    assertTrue(++steps < 5, child.id)
                    parent = parent.parent?.let { entries.getValue(it) }
                }
                assertTrue(steps > 0, "${child.id} falls back to the missing ${child.parent}")
            }
        }
    }

    @Test
    fun androidDoesNotOfferDesktopRows() {
        val ids = ids(android)
        for (id in listOf(
            "settings_close_to_background", "settings_graphics_acceleration", "settings_external_control",
            "settings_navigation_style", "settings_tun_mode_title", "configs_local_dns",
            "settings_launch_on_login_title", "desktop_settings_use_system_proxy", "desktop_settings_data_dir",
        )) assertFalse(id in ids, id)
        for (id in listOf(
            "settings_enable_haptics", "settings_kill_switch", "settings_logcat", "settings_app_icon",
            "settings_auto_connect_on_boot_title", "settings_auto_install_night_title",
        )) assertTrue(id in ids, id)
    }

    @Test
    fun desktopDoesNotOfferAndroidRows() {
        val ids = ids(desktop)
        for (id in listOf(
            "settings_enable_haptics", "settings_kill_switch", "settings_logcat", "settings_app_icon",
            "settings_broadcast_control", "settings_wake_lock", "configs_hevtun",
            "subscription_expiry_notifications_enable", "settings_auto_connect_on_boot_title",
            "settings_auto_install_night_title",
        )) assertFalse(id in ids, id)
        for (id in listOf(
            "settings_close_to_background", "settings_navigation_style", "settings_tun_mode_title",
            "configs_local_dns", "settings_launch_on_login_title", "desktop_settings_use_system_proxy",
            "desktop_settings_data_dir",
        )) assertTrue(id in ids, id)
    }

    @Test
    fun aHostWithoutAPageOffersNothingFromIt() {
        val withoutVpn = desktop.copy(
            pages = SettingsSearchPage.entries.toSet() - SettingsSearchPage.Vpn - SettingsSearchPage.NetworkAutomation,
        )
        val pages = settingsSearchEntries(withoutVpn).map { it.page }.toSet()
        assertFalse(SettingsSearchPage.Vpn in pages)
        assertFalse(SettingsSearchPage.NetworkAutomation in pages)
    }

    @Test
    fun toolsAreOrdinaryResults() {
        val ids = ids(desktop)
        for (page in listOf(SettingsSearchPage.SpeedTest, SettingsSearchPage.DnsLeakTest, SettingsSearchPage.IpInfo)) {
            assertTrue("page:${page.name}" in ids)
        }
    }
}

class SettingsSearchMultilinkTest {
    @Test
    fun linkMergingIsFoundOnEveryHostThatListsItsPage() {
        val listed = settingsSearchEntries(SettingsSearchHost())
        val unlisted = settingsSearchEntries(SettingsSearchHost(pages = SettingsSearchPage.entries.toSet() - SettingsSearchPage.Multilink))
        assertTrue(listed.any { it.page == SettingsSearchPage.Multilink && it.isPage })
        assertTrue(listed.any { it.id == "multilink_enabled" })
        assertFalse(unlisted.any { it.page == SettingsSearchPage.Multilink })
    }
}
