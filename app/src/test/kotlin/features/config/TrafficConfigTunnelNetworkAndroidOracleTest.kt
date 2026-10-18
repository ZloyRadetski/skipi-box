// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.skipi.app.config.withNetworkActivation
import app.skipi.app.config.withTunnelOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-derived oracle for Android's current Tunnel and Network editor transforms. */
class TrafficConfigTunnelNetworkAndroidOracleTest {
    @Test
    fun tunnelSaveAppliesTheCapturedFullSettingsToTheLatestRowAndRewritesKnownSkipiFields() {
        val opened = profile(51, "Opened")
        val capturedSettings = opened.androidSettings.copy(
            enableSniffing = false,
            enableSniffingRouteOnly = false,
            enableMux = true,
            enableFragment = true,
        )
        val latest = opened.copy(
            name = "Latest row",
            sourceUrl = "https://latest.example/profile.conf",
            updateLocked = true,
            autoUpdate = false,
            updateInterval = "48",
            androidSettings = opened.androidSettings.copy(
                enableFakeDns = true,
                tunVpnDns = "9.9.9.9",
            ),
            networkActivation = TrafficConfigNetworkActivation(
                enabled = true,
                transport = TrafficConfigNetworkTransportCellular,
            ),
            rawConfig = opened.rawConfig
                .replace("server = proxy.example:443", "server = latest.example:8443")
                .replace("example.net", "latest.example.net")
                .replace("vendor-option = opened", "vendor-option = latest"),
        ).withSkipiSettingsInRawConfig().withUnknownSkipiSetting()
        val sibling = profile(52, "Sibling")
        val before = AppState(
            trafficConfigs = listOf(latest, sibling),
            activeTrafficConfigId = sibling.id,
            nextTrafficConfigId = 70,
        )

        val after = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.withTunnelOptions(capturedSettings, muxConcurrency = " 19 ")
        }

        val saved = after.trafficConfigs.single { it.id == opened.id }
        val skipi = saved.rawConfig.skipiSectionValues()
        assertEquals(listOf(opened.id, sibling.id), after.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(sibling, after.trafficConfigs.single { it.id == sibling.id })
        assertEquals(sibling.id, after.activeTrafficConfigId)
        assertEquals(70, after.nextTrafficConfigId)
        assertEquals("Latest row", saved.name)
        assertEquals("https://latest.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertFalse(saved.autoUpdate)
        assertEquals("48", saved.updateInterval)
        // Android saves the entire settings snapshot opened by the editor, including hidden fields.
        assertEquals(capturedSettings.copy(muxConcurrency = "19"), saved.androidSettings)
        assertEquals(TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportCellular), saved.networkActivation)
        assertEquals("false", skipi[SkipiSniffing]?.last())
        assertEquals("true", skipi[SkipiMux]?.last())
        assertEquals("19", skipi[SkipiMuxConcurrency]?.last())
        assertEquals("false", skipi[SkipiFakeDns]?.last())
        assertEquals("true", skipi[SkipiNetworkActivation]?.last())
        assertEquals("cellular", skipi[SkipiNetworkTransport]?.last())
        assertFalse(skipi.containsKey("future-setting"))
        assertTrue(saved.rawConfig.contains("server = latest.example:8443"))
        assertTrue(saved.rawConfig.contains("DOMAIN-SUFFIX,latest.example.net,DIRECT"))
        assertTrue(saved.rawConfig.contains("vendor-option = latest"))
    }

    @Test
    fun networkScreenClampsItsInitialTransportAndSavesThroughTheAndroidUpdater() {
        val high = profile(61, "High").copy(
            networkActivation = TrafficConfigNetworkActivation(enabled = false, transport = Int.MAX_VALUE),
        ).withSkipiSettingsInRawConfig()
        val low = profile(62, "Low").copy(
            networkActivation = TrafficConfigNetworkActivation(enabled = false, transport = Int.MIN_VALUE),
        ).withSkipiSettingsInRawConfig()
        var state = AppState(
            trafficConfigs = listOf(high, low),
            activeTrafficConfigId = low.id,
            nextTrafficConfigId = 80,
        )
        val transportLabels = listOf("Wi-Fi", "Cellular")

        listOf(high, low).forEach { opened ->
            // TrafficConfigEditorPages initializes its selector with transportLabels.indices.
            val draftTransport = opened.networkActivation.transport.coerceIn(transportLabels.indices)
            state = state.withUpdatedTrafficConfigProfile(opened.id) { current ->
                current.withNetworkActivation(enabled = true, transport = draftTransport)
            }
        }

        val savedHigh = state.trafficConfigs.single { it.id == high.id }
        val savedLow = state.trafficConfigs.single { it.id == low.id }
        assertEquals(TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportCellular), savedHigh.networkActivation)
        assertEquals(TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportWifi), savedLow.networkActivation)
        assertEquals("true", savedHigh.rawConfig.skipiSectionValues()[SkipiNetworkActivation]?.last())
        assertEquals("cellular", savedHigh.rawConfig.skipiSectionValues()[SkipiNetworkTransport]?.last())
        assertEquals("true", savedLow.rawConfig.skipiSectionValues()[SkipiNetworkActivation]?.last())
        assertEquals("wifi", savedLow.rawConfig.skipiSectionValues()[SkipiNetworkTransport]?.last())
        assertEquals(low.id, state.activeTrafficConfigId)
        assertEquals(80, state.nextTrafficConfigId)
    }

    private fun profile(id: Int, name: String): TrafficConfigState = TrafficConfigState(
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
        .withUnknownSkipiSetting()

    private fun TrafficConfigState.withUnknownSkipiSetting(): TrafficConfigState = copy(
        rawConfig = rawConfig.replace("[SKIPI]\n", "[SKIPI]\nfuture-setting = discard-me\n"),
    )
}
