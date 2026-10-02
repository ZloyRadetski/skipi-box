// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.DefaultFragmentInterval
import engine.xray.DefaultFragmentLength
import engine.xray.DefaultFragmentPackets
import engine.xray.DefaultMuxConcurrency
import engine.xray.DefaultMuxUdp443Mode
import engine.xray.DefaultMuxXudpConcurrency
import engine.xray.XrayFakeDnsIpv4OnlyPoolSize
import engine.xray.XrayFakeDnsIpv4Pool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficProfileSettingsTest {
    @Test
    fun sharedDefaultsPreserveProfileValues() {
        val settings = TrafficConfigAndroidSettings()
        assertTrue(settings.enableSniffing)
        assertTrue(settings.enableSniffingRouteOnly)
        assertFalse(settings.enableMux)
        assertEquals(DefaultMuxConcurrency, settings.muxConcurrency)
        assertEquals(DefaultMuxXudpConcurrency, settings.muxXudpConcurrency)
        assertEquals(DefaultMuxUdp443Mode, settings.muxXudpProxyUdp443)
        assertEquals(DefaultFragmentPackets, settings.fragmentPackets)
        assertEquals(DefaultFragmentLength, settings.fragmentLength)
        assertEquals(DefaultFragmentInterval, settings.fragmentInterval)
        assertEquals("8.8.8.8", settings.tunVpnDns)
        assertEquals(listOf("https://8.8.8.8/dns-query"), settings.proxyDns)
        assertEquals(listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"), settings.directDns)
        assertEquals(XrayFakeDnsIpv4Pool, settings.fakeDnsIpPool)
        assertEquals(XrayFakeDnsIpv4OnlyPoolSize, settings.fakeDnsPoolSize)
    }

    @Test
    fun networkActivationDefaultsToDisabledWifi() {
        assertEquals(TrafficConfigNetworkActivation(false, TrafficConfigNetworkTransportWifi), TrafficConfigNetworkActivation())
        assertEquals(1, TrafficConfigNetworkTransportCellular)
    }

    @Test
    fun dnsEditorValidationAcceptsSupportedRulesAndRejectsMalformedValues() {
        assertTrue(isValidTrafficConfigDnsDomainRule("geosite:cn"))
        assertTrue(isValidTrafficConfigDnsDomainRule("domain:example.org"))
        assertFalse(isValidTrafficConfigDnsDomainRule("domain:"))
        assertFalse(isValidTrafficConfigDnsDomainRule("bad domain"))
        assertTrue(isValidTrafficConfigDnsHost("example.org: 1.2.3.4,2001:db8::1"))
        assertFalse(isValidTrafficConfigDnsHost("example.org: not-an-ip"))
        assertFalse(isValidTrafficConfigDnsHost("-invalid.example: 1.2.3.4"))
    }
}
