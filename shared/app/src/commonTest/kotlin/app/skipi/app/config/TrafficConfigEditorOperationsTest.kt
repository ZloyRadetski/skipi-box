// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigNetworkTransportCellular
import features.config.TrafficConfigNetworkTransportWifi
import features.config.TrafficConfigAndroidSettings
import features.config.withSkipiSettingsReadFromRawConfig
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficConfigEditorOperationsTest {
    @Test
    fun generalOptionsUpdateKnownValuesAndPreserveOtherConfiguration() {
        val initial = TrafficConfigState(
            id = 7,
            name = "Office",
            rawConfig = """[General]
ipv6 = false
loglevel = warning

[Proxy]
server = example.org
""",
        )

        val updated = initial.withGeneralOptions(ipv6 = true, preferIpv6 = true)
        val parsed = updated.rawConfig.analyzeShadowrocketConfig()

        assertEquals("true", parsed.general["ipv6"])
        assertEquals("true", parsed.general["prefer-ipv6"])
        assertEquals("warning", parsed.general["loglevel"])
        assertTrue(parsed.sections["proxy"].orEmpty().contains("server = example.org"))
        assertEquals(initial.name, updated.name)
    }

    @Test
    fun networkActivationKeepsBooleanAndBoundsUnsupportedTransport() {
        val initial = TrafficConfigState(id = 2, name = "Mobile", rawConfig = "[General]\n")

        val enabled = initial.withNetworkActivation(enabled = true, transport = TrafficConfigNetworkTransportCellular)
        assertTrue(enabled.networkActivation.enabled)
        assertEquals(TrafficConfigNetworkTransportCellular, enabled.networkActivation.transport)

        val disabled = enabled.withNetworkActivation(enabled = false, transport = Int.MAX_VALUE)
        assertFalse(disabled.networkActivation.enabled)
        assertEquals(TrafficConfigNetworkTransportCellular, disabled.networkActivation.transport)

        val lowerBound = enabled.withNetworkActivation(enabled = true, transport = Int.MIN_VALUE)
        assertEquals(TrafficConfigNetworkTransportWifi, lowerBound.networkActivation.transport)
    }

    @Test
    fun dnsOptionsNormalizeValuesPreserveOrderAndRoundTripThroughProfileDocument() {
        val initial = TrafficConfigState(
            id = 8,
            name = "DNS",
            rawConfig = """[General]
loglevel = warning
dns-server = 9.9.9.9

[Proxy]
server = proxy.example
""",
        )
        val updated = initial.withDnsOptions(
            TrafficConfigAndroidSettings(
                tunVpnDns = " 10.0.0.1 ",
                proxyDns = listOf(" https://1.1.1.1/dns-query ", "", "https://8.8.8.8/dns-query"),
                directDns = listOf(" 77.88.8.8 ", "77.88.8.1"),
                directDnsDomains = listOf(" geosite:cn ", "domain:ru", "geosite:cn"),
                dnsHosts = listOf("example.org: 1.2.3.4"),
            ),
        )

        assertEquals(listOf("77.88.8.8", "77.88.8.1"), updated.androidSettings.directDns)
        assertEquals(listOf("geosite:cn", "domain:ru"), updated.androidSettings.directDnsDomains)
        assertTrue(updated.rawConfig.contains("loglevel = warning"))
        assertTrue(updated.rawConfig.contains("server = proxy.example"))
        assertTrue(updated.rawConfig.contains("dns-server = 77.88.8.8"))
        val restored = TrafficConfigState(
            id = updated.id,
            name = updated.name,
            rawConfig = updated.rawConfig,
        ).withSkipiSettingsReadFromRawConfig()
        assertEquals(updated.androidSettings.proxyDns, restored.androidSettings.proxyDns)
        assertEquals(updated.androidSettings.directDns, restored.androidSettings.directDns)
        assertEquals(updated.androidSettings.dnsHosts, restored.androidSettings.dnsHosts)
    }

    @Test
    fun tunnelOptionsUpdateOnlyPortableTunnelSettings() {
        val initial = TrafficConfigState(id = 3, name = "Tun", rawConfig = "[General]\n")
        val updated = initial.withTunnelOptions(
            initial.androidSettings.copy(enableSniffing = false, enableMux = true),
            " 12 ",
        )
        assertFalse(updated.androidSettings.enableSniffing)
        assertTrue(updated.androidSettings.enableMux)
        assertEquals("12", updated.androidSettings.muxConcurrency)
        assertEquals(initial.rawConfig, updated.rawConfig)
    }

    @Test
    fun profileBasicsUpdateSkipiFieldsAndPreserveUnrelatedConfContent() {
        val initial = TrafficConfigState(
            id = 12,
            name = "Old name",
            rawConfig = """[General]
loglevel = warning

[Proxy]
server = proxy.example:443

[Rule]
DOMAIN-SUFFIX,example.net,DIRECT

[SKIPI]
profile-name = Old name
""",
            sourceUrl = "https://old.example/profile.conf",
            updateLocked = false,
        )

        val updated = initial.withProfileBasics(
            name = "  New name  ",
            sourceUrl = " https://new.example/profile.conf ",
            updateLocked = true,
            autoUpdate = true,
            updateInterval = " 18 ",
            resourceAutoUpdate = false,
            resourceUpdateInterval = " 36 ",
        )
        val parsed = updated.rawConfig.analyzeShadowrocketConfig()

        assertEquals("New name", updated.name)
        assertEquals("https://new.example/profile.conf", updated.sourceUrl)
        assertTrue(updated.updateLocked)
        assertTrue(updated.autoUpdate)
        assertEquals("18", updated.updateInterval)
        assertFalse(updated.resourceSettings.autoUpdate)
        assertEquals("36", updated.resourceSettings.updateInterval)
        assertEquals("warning", parsed.general["loglevel"])
        assertTrue(parsed.sections["proxy"].orEmpty().contains("server = proxy.example:443"))
        assertTrue(parsed.sections["rule"].orEmpty().contains("DOMAIN-SUFFIX,example.net,DIRECT"))
        assertTrue(parsed.sections["skipi"].orEmpty().contains("profile-name = New name"))
        assertTrue(parsed.sections["skipi"].orEmpty().contains("profile-update-url = https://new.example/profile.conf"))
    }
}
