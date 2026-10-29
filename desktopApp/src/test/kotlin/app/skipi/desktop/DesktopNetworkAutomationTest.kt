// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.settings.SkipiSettingsDestination
import app.skipi.ui.settings.SkipiSettingsHomeState
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleAction
import features.networkautomation.model.NetworkRuleType
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Android's network-automation rules stored by Desktop and edited with the shared screen. */
class DesktopNetworkAutomationTest {
    @Test
    fun defaultsMatchAndroid() {
        val settings = DesktopAppSettings()

        assertFalse(settings.enableNetworkAutomation)
        assertFalse(settings.enableOnDemandVpn)
        assertTrue(settings.networkAutomationRules.isEmpty())
    }

    @Test
    fun rulesKeepOrderAndFieldsAcrossSaveAndReload() {
        val directory = Files.createTempDirectory("skipi-automation-")
        try {
            val path = directory.resolve("settings.json")
            val rules = listOf(
                NetworkAutomationRule(id = "r1", type = NetworkRuleType.SPECIFIC_WIFI, ssid = "Home", targetServerId = 3),
                NetworkAutomationRule(
                    id = "r2", type = NetworkRuleType.CELLULAR, action = NetworkRuleAction.DISCONNECT_VPN, enabled = false,
                ),
            )

            DesktopSettingsLibraries.save(
                path,
                DesktopAppSettings(enableNetworkAutomation = true, enableOnDemandVpn = true, networkAutomationRules = rules),
            ).getOrThrow()
            val loaded = DesktopSettingsLibraries.load(path).getOrThrow()

            assertTrue(loaded.enableNetworkAutomation)
            assertTrue(loaded.enableOnDemandVpn)
            assertEquals(rules, loaded.networkAutomationRules)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun settingsOverviewOffersNetworkAutomationButAndroidsDefaultDoesNot() {
        assertTrue(SkipiSettingsDestination.NetworkAutomation in desktopSettingsVisibleDestinations())
        // Android reaches the page from its VPN settings, so its home screen must not list it.
        assertFalse(
            SkipiSettingsDestination.NetworkAutomation in
                SkipiSettingsHomeState("v", "1500", "1080", null, androidx.compose.ui.graphics.Color.Unspecified)
                    .visibleDestinations,
        )
    }

    @Test
    fun rulesOfferTheCatalogServersByRemarks() {
        val library = DesktopServerLibrary(
            servers = listOf(
                DesktopStoredProxyServer(1, Socks(remarks = "Berlin", server = "a.example", port = "1080").encodePersistedProxyServer()),
                DesktopStoredProxyServer(2, "garbage"),
            ),
        )

        val options = desktopNetworkAutomationServers(library)

        assertEquals(listOf(1, 2), options.map { it.id })
        assertEquals("Berlin", options[0].name)
        assertEquals("#2", options[1].name)
    }
}
