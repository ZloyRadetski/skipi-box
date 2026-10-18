// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.app.config.toTrafficConfigState
import features.config.ConfigProfile
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigNetworkTransportCellular
import features.config.TrafficConfigNetworkTransportWifi
import features.config.TrafficConfigState
import features.config.skipiSectionValues
import features.config.withSkipiSettingsInRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopTrafficConfigTunnelNetworkStoreTest {
    @Test
    fun tunnelSaveUsesTheCapturedSettingsAndPersistsIntoTheLatestLibraryRow() {
        val fallback = "Skipi/9.8.7/Desktop"
        val openedProfile = profile(9, "Opened")
        val openedState = openedProfile.toTrafficConfigState(fallback)
        val capturedSettings = openedState.androidSettings.copy(
            enableSniffing = false,
            enableSniffingRouteOnly = false,
            enableMux = true,
            enableFragment = true,
        )
        val latestState = openedState.copy(
            name = "Latest row",
            sourceUrl = "https://latest.example/profile.conf",
            updateLocked = true,
            autoUpdate = false,
            updateInterval = "48",
            androidSettings = openedState.androidSettings.copy(
                enableFakeDns = true,
                tunVpnDns = "9.9.9.9",
            ),
            networkActivation = TrafficConfigNetworkActivation(
                enabled = true,
                transport = TrafficConfigNetworkTransportCellular,
            ),
            rawConfig = openedState.rawConfig
                .replace("server = proxy.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        ).withSkipiSettingsInRawConfig()
        val latestProfile = openedProfile.copy(
            name = latestState.name,
            content = latestState.rawConfig.withUnknownSkipiSetting(),
            sourceUrl = latestState.sourceUrl,
            updateLocked = latestState.updateLocked,
            lastUpdatedAtMillis = 789L,
        )
        val active = profile(7, "Active")
        val sibling = profile(13, "Sibling")
        val selectedNeighbor = profile(30, "Selected while editing")
        val initial = DesktopConfigLibrary(
            selectedConfigId = selectedNeighbor.id,
            configs = listOf(active, latestProfile, sibling, selectedNeighbor),
            nextConfigId = 91,
        )
        var latest = initial
        var persisted = initial
        val events = mutableListOf<String>()
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { persisted = it; events += "save"; Result.success(Unit) },
            publishLibrary = { latest = it; events += "publish" },
        )
        val store = DesktopTrafficConfigEditorProfileStore(repository, fallback)
        val controller = TrafficConfigEditorController(store)

        val result = controller.saveTunnelThenNavigate(
            profileId = openedProfile.id,
            settings = capturedSettings,
            muxConcurrency = " 19 ",
            onNavigate = {
                assertEquals(latest, persisted)
                events += "navigate"
            },
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        val savedLibrary = latest
        val saved = savedLibrary.configs.single { it.id == openedProfile.id }
        val savedState = saved.toTrafficConfigState(fallback)
        val skipi = saved.content.skipiSectionValues()
        assertEquals(listOf(active, saved, sibling, selectedNeighbor), savedLibrary.configs)
        assertEquals(selectedNeighbor.id, savedLibrary.selectedConfigId)
        assertEquals(91, savedLibrary.nextConfigId)
        assertEquals("Latest row", saved.name)
        assertEquals("https://latest.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertEquals(789L, saved.lastUpdatedAtMillis)
        // The open Tunnel draft is the full AndroidSettings value, so its hidden values win.
        assertEquals(capturedSettings.copy(muxConcurrency = "19"), savedState.androidSettings)
        assertEquals(TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportCellular), savedState.networkActivation)
        assertEquals("false", skipi["sniffing"]?.last())
        assertEquals("true", skipi["mux"]?.last())
        assertEquals("19", skipi["mux-concurrency"]?.last())
        assertEquals("false", skipi["fake-dns"]?.last())
        assertEquals("true", skipi["network-activation"]?.last())
        assertEquals("cellular", skipi["network-transport"]?.last())
        assertFalse(skipi.containsKey("future-setting"))
        assertTrue(saved.content.contains("server = latest.example:8443"))
        assertTrue(saved.content.contains("vendor-option = latest"))
        assertEquals(savedLibrary, persisted)
        assertEquals(listOf("save", "publish", "navigate"), events)
    }

    @Test
    fun networkActivationSaveClampsAndSerializesBothSupportedTransportBounds() {
        val fallback = "Skipi/9.8.7/Desktop"
        val wifi = profile(9, "Wi-Fi")
        val cellular = profile(11, "Cellular")
        val active = profile(7, "Active")
        val initial = DesktopConfigLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, wifi, cellular),
            nextConfigId = 21,
        )
        var latest = initial
        var persisted = initial
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { persisted = it; Result.success(Unit) },
            publishLibrary = { latest = it },
        )
        val store = DesktopTrafficConfigEditorProfileStore(repository, fallback)
        val controller = TrafficConfigEditorController(store)

        val highResult = controller.saveNetworkActivationThenNavigate(
            profileId = wifi.id,
            enabled = true,
            transport = Int.MAX_VALUE,
            onNavigate = { assertEquals(latest, persisted) },
        )
        val lowResult = controller.saveNetworkActivationThenNavigate(
            profileId = cellular.id,
            enabled = true,
            transport = Int.MIN_VALUE,
            onNavigate = { assertEquals(latest, persisted) },
        )

        assertIs<TrafficConfigEditorResult.Saved>(highResult)
        assertIs<TrafficConfigEditorResult.Saved>(lowResult)
        val savedWifi = latest.configs.single { it.id == wifi.id }
        val savedCellular = latest.configs.single { it.id == cellular.id }
        assertEquals(
            TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportCellular),
            savedWifi.toTrafficConfigState(fallback).networkActivation,
        )
        assertEquals(
            TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportWifi),
            savedCellular.toTrafficConfigState(fallback).networkActivation,
        )
        assertEquals("cellular", savedWifi.content.skipiSectionValues()["network-transport"]?.last())
        assertEquals("wifi", savedCellular.content.skipiSectionValues()["network-transport"]?.last())
        assertEquals(listOf(active, savedWifi, savedCellular), latest.configs)
        assertEquals(active.id, latest.selectedConfigId)
        assertEquals(21, latest.nextConfigId)
        assertEquals(latest, persisted)
    }

    private fun profile(id: Int, name: String): ConfigProfile {
        val state = TrafficConfigState(
            id = id,
            name = name,
            sourceUrl = "https://opened.example/profile.conf",
            updateLocked = false,
            autoUpdate = true,
            updateInterval = "24",
            rawConfig = """[General]
loglevel = warning

[Proxy]
server = proxy.example:443

[Rule]
DOMAIN-SUFFIX,example.net,DIRECT

[Vendor]
vendor-option = opened
""",
            androidSettings = TrafficConfigAndroidSettings(muxConcurrency = "4"),
        ).withSkipiSettingsInRawConfig()
        return ConfigProfile(
            id = id,
            name = name,
            content = state.rawConfig.withUnknownSkipiSetting(),
            sourceUrl = state.sourceUrl,
            updateLocked = state.updateLocked,
            lastUpdatedAtMillis = 345L,
        )
    }

    private fun String.withUnknownSkipiSetting(): String = replace(
        "[SKIPI]\n",
        "[SKIPI]\nfuture-setting = discard-me\n",
    )
}
