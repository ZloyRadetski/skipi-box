// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.AppState
import app.skipi.app.model.ApplicationPreferences
import app.skipi.app.model.CoreEnginePreferences
import app.skipi.app.model.LogPreferences
import app.skipi.app.model.NetworkAutomationPreferences
import app.skipi.app.model.PersistedSettings
import app.skipi.app.model.VpnPreferences
import data.repository.applyPersistedSettings
import data.repository.toPersistedSettings
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleType
import kotlin.test.Test
import kotlin.test.assertEquals

/** The VPN, engine, log, network-automation and update settings travel through PersistedSettings on Android. */
class RuntimeSectionsPersistenceTest {
    private val rule = NetworkAutomationRule(id = "r1", type = NetworkRuleType.SPECIFIC_WIFI, ssid = "home", targetServerId = 4)

    private val configured = AppState(
        enableStrictFullTunnel = true,
        enableVpnLocalDns = false,
        tunMtu = "1380",
        tunVpnDns = "9.9.9.9",
        tunIpv4Cidr = "10.9.0.1/30",
        tunIpv6Cidr = "fd00:9::1/126",
        tunTcpKeepAliveInterval = "7",
        tunTcpUserTimeout = "12345",
        enableNetworkAutomation = true,
        enableOnDemandVpn = true,
        networkAutomationRules = listOf(rule),
        enableSniffing = false,
        enableSniffingRouteOnly = false,
        enableMux = true,
        muxConcurrency = "6",
        muxXudpConcurrency = "20",
        muxXudpProxyUdp443 = 2,
        enableFragment = true,
        fragmentPackets = "tlshello",
        fragmentLength = "5-9",
        fragmentInterval = "3-4",
        coreLogLevel = 1,
        enableAccessLog = true,
        logRetentionDays = 30,
        autoCheckAppUpdates = false,
        autoConnectOnAppOpen = true,
    )

    @Test
    fun theSectionsCarryEveryValue() {
        val persisted = configured.toPersistedSettings()

        assertEquals(
            VpnPreferences(
                strictFullTunnel = true,
                localDns = false,
                mtu = "1380",
                dns = "9.9.9.9",
                ipv4Cidr = "10.9.0.1/30",
                ipv6Cidr = "fd00:9::1/126",
                tcpKeepAliveInterval = "7",
                tcpUserTimeout = "12345",
            ),
            persisted.vpn,
        )
        assertEquals(NetworkAutomationPreferences(enabled = true, onDemandVpn = true, rules = listOf(rule)), persisted.networkAutomation)
        assertEquals(
            CoreEnginePreferences(
                sniffing = false,
                sniffingRouteOnly = false,
                mux = true,
                muxConcurrency = "6",
                muxXudpConcurrency = "20",
                muxXudpProxyUdp443 = 2,
                fragment = true,
                fragmentPackets = "tlshello",
                fragmentLength = "5-9",
                fragmentInterval = "3-4",
            ),
            persisted.engine,
        )
        assertEquals(LogPreferences(coreLogLevel = 1, accessLogEnabled = true, retentionDays = 30), persisted.logs)
        assertEquals(false, persisted.application.autoCheckAppUpdates)
        assertEquals(true, persisted.application.autoConnectOnAppOpen)
    }

    @Test
    fun applyingTheSectionsRestoresEveryValue() {
        val restored = AppState().applyPersistedSettings(configured.toPersistedSettings())

        assertEquals(configured.runtimeFields(), restored.runtimeFields())
    }

    @Test
    fun recordsWrittenBeforeTheSectionsExistedKeepTheStateValues() {
        val restored = configured.applyPersistedSettings(
            PersistedSettings(application = ApplicationPreferences(autoCheckAppUpdates = false, autoConnectOnAppOpen = true)),
        )

        assertEquals(configured.runtimeFields(), restored.runtimeFields())
    }

    private fun AppState.runtimeFields() = listOf(
        enableStrictFullTunnel, enableVpnLocalDns, tunMtu, tunVpnDns, tunIpv4Cidr, tunIpv6Cidr,
        tunTcpKeepAliveInterval, tunTcpUserTimeout, enableNetworkAutomation, enableOnDemandVpn, networkAutomationRules,
        enableSniffing, enableSniffingRouteOnly, enableMux, muxConcurrency, muxXudpConcurrency, muxXudpProxyUdp443,
        enableFragment, fragmentPackets, fragmentLength, fragmentInterval,
        coreLogLevel, enableAccessLog, logRetentionDays, autoCheckAppUpdates, autoConnectOnAppOpen,
    )
}
