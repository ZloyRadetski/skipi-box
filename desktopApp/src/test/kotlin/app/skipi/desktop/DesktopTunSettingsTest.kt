// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.AppState
import app.skipi.ui.settings.SkipiSettingsDestination
import engine.network.NetworkCidrAddress
import engine.vpn.toTunInterfacePlan
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.LocalProxyXrayConfigOptions

class DesktopTunSettingsTest {
    @Test
    fun defaultsAreTheAndroidVpnDefaultsWithTunModeOff() {
        val settings = DesktopAppSettings()
        val android = AppState()

        assertFalse(settings.enableTunMode)
        assertEquals(android.tunMtu, settings.tunMtu)
        assertEquals(android.tunIpv4Cidr, settings.tunIpv4Cidr)
        assertEquals(android.tunIpv6Cidr, settings.tunIpv6Cidr)
        assertEquals(android.tunVpnDns, settings.tunVpnDns)
        assertEquals(android.tunTcpKeepAliveInterval, settings.tunTcpKeepAliveInterval)
        assertEquals(android.tunTcpUserTimeout, settings.tunTcpUserTimeout)
        assertEquals(android.enableStrictFullTunnel, settings.enableStrictFullTunnel)
        assertEquals(android.autoConnectOnAppOpen, settings.autoConnectOnAppOpen)
        assertFalse(settings.launchOnLogin)
    }

    @Test
    fun tunSettingsSurviveSaveAndReload() {
        val path = createTempDirectory("skipi-tun-settings").resolve("settings.json")
        val saved = DesktopAppSettings(
            enableTunMode = true,
            tunMtu = "1500",
            tunIpv4Cidr = "10.20.30.1/24",
            tunIpv6Cidr = "fd00::1/64",
            tunVpnDns = "1.1.1.1",
            tunTcpKeepAliveInterval = "30",
            tunTcpUserTimeout = "20000",
            enableStrictFullTunnel = true,
            autoConnectOnAppOpen = true,
            launchOnLogin = true,
        )

        DesktopSettingsLibraries.save(path, saved).getOrThrow()
        val loaded = DesktopSettingsLibraries.load(path).getOrThrow()

        assertEquals(saved.copy(), loaded.copy())
        assertTrue(loaded.enableTunMode)
        assertEquals("1500", loaded.tunMtu)
    }

    @Test
    fun aSettingsFileWithoutTunFieldsLoadsWithDefaults() {
        val path = createTempDirectory("skipi-tun-legacy").resolve("settings.json")
        java.nio.file.Files.writeString(path, """{"localProxyPort":1080}""")

        val loaded = DesktopSettingsLibraries.load(path).getOrThrow()

        assertFalse(loaded.enableTunMode)
        assertEquals("1400", loaded.tunMtu)
    }

    @Test
    fun invalidTunValuesFallBackToTheAndroidDefaultsWhenNormalized() {
        val normalized = DesktopAppSettings(
            tunMtu = "abc",
            tunIpv4Cidr = "fd00::1/64",
            tunIpv6Cidr = "10.0.0.1/24",
            tunVpnDns = "not-an-ip",
            tunTcpKeepAliveInterval = "0",
            tunTcpUserTimeout = "5",
        ).normalized()

        assertEquals("1400", normalized.tunMtu)
        assertEquals("172.19.0.1/30", normalized.tunIpv4Cidr)
        assertEquals("fdfe:dcba:9876::1/126", normalized.tunIpv6Cidr)
        assertEquals("8.8.8.8", normalized.tunVpnDns)
        assertEquals("60", normalized.tunTcpKeepAliveInterval)
        assertEquals("10000", normalized.tunTcpUserTimeout)
    }

    @Test
    fun validTunValuesAreKeptByNormalization() {
        val settings = DesktopAppSettings(tunMtu = "1500", tunIpv4Cidr = "10.20.30.1/24", tunVpnDns = "1.1.1.1")

        assertEquals(settings, settings.normalized())
    }

    @Test
    fun theInterfacePlanIsTheSharedAndroidPlan() {
        assertEquals(AppState().toTunInterfacePlan(), DesktopAppSettings().tunInterfacePlan())
        assertEquals(
            listOf(NetworkCidrAddress("10.20.30.1", 24)),
            DesktopAppSettings(tunIpv4Cidr = "10.20.30.1/24").tunInterfacePlan().addresses,
        )
    }

    @Test
    fun theStartRequestPointsHevAtTheRunningSocksInbound() {
        val request = desktopTunStartRequest(
            settings = DesktopAppSettings(),
            options = LocalProxyXrayConfigOptions(
                socksPort = 12345,
                listenAddress = "0.0.0.0",
                username = "user",
                password = "secret",
            ),
            logPath = "/tmp/hev.log",
        )

        assertEquals("127.0.0.1", request.socksAddress)
        assertEquals(12345, request.socksPort)
        assertEquals("user", request.socksUsername)
        assertEquals("secret", request.socksPassword)
        assertEquals("/tmp/hev.log", request.logPath)
        assertEquals(DesktopAppSettings().tunInterfacePlan(), request.plan)
    }

    @Test
    fun aSpecificListenAddressIsKept() {
        val request = desktopTunStartRequest(
            settings = DesktopAppSettings(),
            options = LocalProxyXrayConfigOptions(socksPort = 1080, listenAddress = "192.168.1.5"),
            logPath = "",
        )

        assertEquals("192.168.1.5", request.socksAddress)
    }

    @Test
    fun vpnResetRestoresTunnelAndGlobalVpnDefaultsAndKeepsEverythingElse() {
        val customized = DesktopAppSettings(
            enableTunMode = true,
            tunMtu = "1500",
            tunIpv4Cidr = "10.20.30.1/24",
            tunIpv6Cidr = "fd00::1/64",
            tunVpnDns = "1.1.1.1",
            tunTcpKeepAliveInterval = "30",
            tunTcpUserTimeout = "20000",
            enableStrictFullTunnel = true,
            localProxyPort = 4040,
            localProxyPortRaw = "4040",
            enableDynamicLocalProxyPort = true,
            localProxyListenAddress = "0.0.0.0",
            enableLocalProxyAuth = true,
            localProxyUsername = "alice",
            localProxyPassword = "secret",
            enableSniffing = false,
            enableSniffingRouteOnly = false,
            enableMux = true,
            muxConcurrency = "9",
            enableFragment = true,
            fragmentLength = "9-9",
            // Not VPN settings:
            themeMode = DesktopThemeMode.Light,
            autoConnectOnAppOpen = true,
            launchOnLogin = true,
            installationUuid = "device",
            onboardingCompleted = true,
        )

        val reset = customized.withVpnSettingsReset()
        val defaults = DesktopAppSettings()

        assertFalse(reset.enableTunMode)
        assertEquals(defaults.tunMtu, reset.tunMtu)
        assertEquals(defaults.tunIpv4Cidr, reset.tunIpv4Cidr)
        assertEquals(defaults.tunIpv6Cidr, reset.tunIpv6Cidr)
        assertEquals(defaults.tunVpnDns, reset.tunVpnDns)
        assertEquals(defaults.tunTcpKeepAliveInterval, reset.tunTcpKeepAliveInterval)
        assertEquals(defaults.tunTcpUserTimeout, reset.tunTcpUserTimeout)
        assertFalse(reset.enableStrictFullTunnel)
        assertEquals(defaults.localProxyPort, reset.localProxyPort)
        assertEquals(null, reset.localProxyPortRaw)
        assertFalse(reset.enableDynamicLocalProxyPort)
        assertEquals(defaults.localProxyListenAddress, reset.localProxyListenAddress)
        assertEquals(defaults.enableLocalProxyAuth, reset.enableLocalProxyAuth)
        assertEquals(defaults.enableSniffing, reset.enableSniffing)
        assertEquals(defaults.enableSniffingRouteOnly, reset.enableSniffingRouteOnly)
        assertEquals(defaults.enableMux, reset.enableMux)
        assertEquals(defaults.muxConcurrency, reset.muxConcurrency)
        assertEquals(defaults.enableFragment, reset.enableFragment)
        assertEquals(defaults.fragmentLength, reset.fragmentLength)
        // Credentials stay so external clients are not silently broken.
        assertEquals("alice", reset.localProxyUsername)
        assertEquals("secret", reset.localProxyPassword)
        assertEquals(DesktopThemeMode.Light, reset.themeMode)
        assertTrue(reset.autoConnectOnAppOpen)
        assertTrue(reset.launchOnLogin)
        assertEquals("device", reset.installationUuid)
        assertTrue(reset.onboardingCompleted)
    }

    @Test
    fun theTunnelKeepAliveSettingsReachTheXrayOptions() {
        val options = DesktopAppSettings(tunTcpKeepAliveInterval = "30", tunTcpUserTimeout = "20000")
            .toLocalProxyXrayConfigOptions()

        assertEquals(engine.xray.XrayTcpSockopt(30, 30, 20_000), options.tcpSockopt)
    }

    @Test
    fun strictFullTunnelSwitchesOffDirectRulesAndTheDirectDefaultInTheGeneratedConfig() {
        val server = features.proxy.server.model.Socks(remarks = "S", server = "proxy.example", port = "1080")
        val library = DesktopServerLibrary(
            selectedServerId = 10,
            servers = listOf(
                DesktopStoredProxyServer(10, server.encodePersistedProxyServer(), subscriptionId = null),
            ),
        )
        val routing = app.skipi.app.model.RoutingConfigRecord(
            defaultOutboundTag = "direct",
            rules = listOf(
                features.routing.model.RouteRule(id = 1, outboundTag = "direct", domain = listOf("domain:direct.example")),
            ),
        )
        fun config(strict: Boolean) = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = DesktopConfigLibrary(),
            routing = routing,
            options = LocalProxyXrayConfigOptions(socksPort = 19_080),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "http://127.0.0.1/ping",
            latencyByServerId = emptyMap(),
            strictFullTunnel = strict,
        )!!.getOrThrow()

        assertTrue("direct.example" in config(strict = false))
        assertFalse("direct.example" in config(strict = true))
        // Every other path of the stored config is untouched: the setting only acts at start.
        assertEquals("direct", routing.defaultOutboundTag)
    }

    @Test
    fun strictFullTunnelRefusesARawCustomConfigLikeAndroid() {
        val custom = features.proxy.server.model.Custom(remarks = "Raw", configJson = "{}")
        val library = DesktopServerLibrary(
            selectedServerId = 10,
            servers = listOf(
                DesktopStoredProxyServer(10, custom.encodePersistedProxyServer(), subscriptionId = null),
            ),
        )

        val result = buildDesktopSelectedServerConfig(
            serverLibrary = library,
            configLibrary = DesktopConfigLibrary(),
            routing = app.skipi.app.model.RoutingConfigRecord(),
            options = LocalProxyXrayConfigOptions(socksPort = 19_080),
            trafficConfigContentById = emptyMap(),
            subscriptionPingUrl = "http://127.0.0.1/ping",
            latencyByServerId = emptyMap(),
            strictFullTunnel = true,
        )!!

        assertEquals(
            "Strict full-tunnel mode cannot safely rewrite a raw Xray configuration",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun theActiveProfilesIpv6SettingDecidesWhetherTheTunnelCarriesIpv6() {
        fun library(content: String) = DesktopConfigLibrary(
            selectedConfigId = 1,
            configs = listOf(DesktopStoredConfig(id = 1, name = "P", content = content)),
        )

        assertTrue(desktopEnableIpv6(library("[General]\nipv6 = true\n[Rule]\nFINAL,PROXY\n")))
        assertFalse(desktopEnableIpv6(library("[General]\nipv6 = false\n[Rule]\nFINAL,PROXY\n")))
        assertFalse(desktopEnableIpv6(library("[Rule]\nFINAL,PROXY\n")))
        assertFalse(desktopEnableIpv6(DesktopConfigLibrary()))
    }

    @Test
    fun withIpv6TheTunnelGetsAnIpv6AddressAndTheHalfRoutes() {
        val request = desktopTunStartRequest(
            settings = DesktopAppSettings(),
            options = LocalProxyXrayConfigOptions(socksPort = 1080),
            logPath = "",
            enableIpv6 = true,
        )

        assertEquals(
            listOf(NetworkCidrAddress("172.19.0.1", 30), NetworkCidrAddress("fdfe:dcba:9876::1", 126)),
            request.plan.addresses,
        )
        assertTrue(NetworkCidrAddress("::", 1) in request.plan.routes)
        assertTrue(NetworkCidrAddress("8000::", 1) in request.plan.routes)
        assertEquals(AppState(enableIpv6 = true).toTunInterfacePlan(), DesktopAppSettings().tunInterfacePlan(enableIpv6 = true))
    }

    @Test
    fun theVpnPageShowsOnlyWhatDesktopCanDo() {
        val capabilities = desktopVpnSettingsCapabilities()

        assertTrue(capabilities.autoConnectOnBoot)
        assertFalse(capabilities.hevTun)
        assertFalse(capabilities.appendHttpProxy)
        assertTrue(capabilities.strictFullTunnel)
        assertFalse(capabilities.killSwitch)
        assertFalse(capabilities.systemVpnSettings)
        assertFalse(capabilities.batteryOptimization)
        assertFalse(capabilities.wakeLock)
        assertFalse(capabilities.seamlessSwitching)
    }

    @Test
    fun theVpnSettingsPageIsOfferedOnlyWhereTheHostCanRunTun() {
        assertFalse(SkipiSettingsDestination.Vpn in desktopSettingsVisibleDestinations())
        assertTrue(SkipiSettingsDestination.Vpn in desktopSettingsVisibleDestinations(vpnPage = true))
    }
}
