// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

import features.settings.servicecontrol.ServiceControlSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsDraftsTest {
    @Test
    fun openingSettingsPreservesEditableValuesAndAppliesEffectiveFakeDnsRule() {
        val values = SettingsValues(
            tunMtu = "1400", tunVpnDns = "8.8.8.8", tunIpv4Cidr = "172.19.0.1/30",
            tunIpv6Cidr = "fdfe:dcba:9876::1/126", tunTcpKeepAliveInterval = "60", tunTcpUserTimeout = "10000",
            localProxyPort = "10808", dynamicLocalProxyPort = false, localProxyListenAllInterfaces = false,
            localProxyAuth = true, localProxyUsername = "user", localProxyPassword = "pass",
            enableVpnLocalDns = false, enableFakeDns = true, enableResolveProxyServerDomain = false,
            proxyDns = listOf(" 8.8.8.8 ", "8.8.8.8"), directDns = emptyList(), directDnsDomains = emptyList(),
            enableDirectDnsForProxyServerDomains = true, dnsHosts = emptyList(), enableMux = false,
            muxConcurrency = "8", muxXudpConcurrency = "16", muxXudpProxyUdp443 = 1,
            enableFragment = false, fragmentPackets = "tlshello", fragmentLength = "100-200",
            fragmentInterval = "10-20", subscriptionPingUrl = "https://example.com", subscriptionPingTimeoutMillis = "5000",
            serviceControl = ServiceControlSettings(),
        )

        val draft = values.toDnsSettingsDraft()

        assertFalse(draft.enableFakeDns)
        assertEquals(listOf(" 8.8.8.8 ", "8.8.8.8"), draft.proxyDns)
        assertEquals(listOf("8.8.8.8"), draft.copy(enableVpnLocalDns = true, enableFakeDns = true).toSavedSettings().proxyDns)
    }

    @Test
    fun muxSaveNormalizesWhitespaceMalformedValuesAndRetainsPersistedModeOrdinal() {
        assertEquals(
            MuxSettingsDraft(enabled = true, concurrency = "8", xudpConcurrency = "16", xudpProxyUdp443 = 2),
            MuxSettingsDraft(enabled = true, concurrency = " 8 ", xudpConcurrency = "bad", xudpProxyUdp443 = 99).toSavedSettings(),
        )
    }

    @Test
    fun fragmentSaveNormalizesRangesAndFallsBackToExistingDefaults() {
        assertEquals(
            FragmentSettingsDraft(enabled = true, packets = "tlshello", length = "5", interval = "10-20"),
            FragmentSettingsDraft(enabled = true, packets = "TLSHELLO", length = "5-5", interval = "10-20").toSavedSettings(),
        )
        assertEquals("100-200", FragmentSettingsDraft(length = "0-5").toSavedSettings().length)
        assertEquals("100-200", FragmentSettingsDraft(length = "5-bad").toSavedSettings().length)
        assertEquals("5-6", FragmentSettingsDraft(length = "5 - 6").toSavedSettings().length)
        assertEquals("100-200", FragmentSettingsDraft(length = "5-0").toSavedSettings().length)
    }

    @Test
    fun numericValidatorsTrimLikeExistingSettingsValidators() {
        assertTrue(LocalProxySettingsDraft(port = " 65535 ").hasValidPort())
        assertFalse(LocalProxySettingsDraft(port = "65536").hasValidPort())
        assertTrue(isValidTunMtu(" 1400 "))
        assertTrue(isValidTunVpnDns(" 8.8.8.8 "))
        assertFalse(isValidTunVpnDns("2001:4860:4860::8888"))
    }
}
