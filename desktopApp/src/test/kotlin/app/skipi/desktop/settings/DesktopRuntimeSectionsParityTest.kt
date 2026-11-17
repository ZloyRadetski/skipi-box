// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.CoreEnginePreferences
import app.skipi.app.model.LogPreferences
import app.skipi.app.model.NetworkAutomationPreferences
import app.skipi.app.model.VpnPreferences
import features.networkautomation.model.NetworkAutomationRule
import features.networkautomation.model.NetworkRuleType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The same sections Android fills (RuntimeSectionsPersistenceTest) are read from and written to settings.json. */
class DesktopRuntimeSectionsParityTest {
    private val rule = NetworkAutomationRule(id = "r1", type = NetworkRuleType.SPECIFIC_WIFI, ssid = "home", targetServerId = 4)

    private val configured = DesktopAppSettings(
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
        coreLogLevel = "info",
        enableAccessLog = true,
        logRetentionDays = 30,
        autoCheckAppUpdates = false,
        autoConnectOnAppOpen = true,
    )

    @Test
    fun theSnapshotCarriesTheSameSectionsAsAndroid() = runBlocking {
        val persisted = Fixture(configured).repository.state.value

        assertEquals(
            VpnPreferences(true, false, "1380", "9.9.9.9", "10.9.0.1/30", "fd00:9::1/126", "7", "12345"),
            persisted.vpn,
        )
        assertEquals(NetworkAutomationPreferences(true, true, listOf(rule)), persisted.networkAutomation)
        assertEquals(CoreEnginePreferences(false, false, true, "6", "20", 2, true, "tlshello", "5-9", "3-4"), persisted.engine)
        // Android stores the level as an index into debug, info, warning, error, none.
        assertEquals(LogPreferences(coreLogLevel = 1, accessLogEnabled = true, retentionDays = 30), persisted.logs)
        assertEquals(false, persisted.application.autoCheckAppUpdates)
        assertEquals(true, persisted.application.autoConnectOnAppOpen)
    }

    @Test
    fun updatingTheSectionsWritesThemIntoTheSettingsFile() = runBlocking {
        val fixture = Fixture(DesktopAppSettings())
        val shared = Fixture(configured).repository.state.value

        fixture.repository.update { current ->
            current.copy(
                vpn = shared.vpn,
                networkAutomation = shared.networkAutomation,
                engine = shared.engine,
                logs = shared.logs,
                application = shared.application,
            )
        }

        assertEquals(configured.runtimeFields(), fixture.settings.runtimeFields())
    }

    @Test
    fun anUpdateOfAnotherPreferenceLeavesTheSectionsAlone() = runBlocking {
        val fixture = Fixture(configured)

        fixture.repository.update { current ->
            current.copy(application = current.application.copy(hapticsEnabled = !current.application.hapticsEnabled))
        }

        assertEquals(configured.runtimeFields(), fixture.settings.runtimeFields())
    }

    @Test
    fun anUnknownLogLevelIndexKeepsTheCurrentOne() = runBlocking {
        val fixture = Fixture(DesktopAppSettings(coreLogLevel = "warning"))

        fixture.repository.update { current -> current.copy(logs = current.logs?.copy(coreLogLevel = 99)) }

        assertEquals("warning", fixture.settings.coreLogLevel)
    }

    private fun DesktopAppSettings.runtimeFields() = listOf(
        enableStrictFullTunnel, enableVpnLocalDns, tunMtu, tunVpnDns, tunIpv4Cidr, tunIpv6Cidr,
        tunTcpKeepAliveInterval, tunTcpUserTimeout, enableNetworkAutomation, enableOnDemandVpn, networkAutomationRules,
        enableSniffing, enableSniffingRouteOnly, enableMux, muxConcurrency, muxXudpConcurrency, muxXudpProxyUdp443,
        enableFragment, fragmentPackets, fragmentLength, fragmentInterval,
        coreLogLevel, enableAccessLog, logRetentionDays, autoCheckAppUpdates, autoConnectOnAppOpen,
    )

    private class Fixture(settings: DesktopAppSettings) {
        var settings: DesktopAppSettings = settings
        private var servers = DesktopServerLibrary()
        private var configs = DesktopConfigLibrary()

        val repository = DesktopSettingsRepository(
            readSettings = { this.settings },
            saveSettings = { this.settings = it; Result.success(Unit) },
            publishSettings = { this.settings = it },
            readServers = { servers },
            saveServers = { servers = it; Result.success(Unit) },
            publishServers = { servers = it },
            readConfigs = { configs },
            saveConfigs = { configs = it; Result.success(Unit) },
            publishConfigs = { configs = it },
        )
    }
}
