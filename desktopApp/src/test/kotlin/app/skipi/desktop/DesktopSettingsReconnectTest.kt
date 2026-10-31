// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A running tunnel was built from the settings it started with; a change to any of them must reconnect it. */
class DesktopSettingsReconnectTest {
    private val base = DesktopAppSettings()

    @Test
    fun everySettingThatShapesTheCoreConfigOrTheTunnelRequestsAReconnect() {
        val changes = mapOf(
            "localProxyPort" to base.copy(localProxyPort = 4040),
            "localProxyPortRaw" to base.copy(localProxyPortRaw = "4040"),
            "localHttpProxyPort" to base.copy(localHttpProxyPort = 4041),
            "useSystemProxy" to base.copy(useSystemProxy = !base.useSystemProxy),
            "localProxyListenAddress" to base.copy(localProxyListenAddress = "0.0.0.0"),
            "enableDynamicLocalProxyPort" to base.copy(enableDynamicLocalProxyPort = true),
            "enableLocalProxyAuth" to base.copy(enableLocalProxyAuth = true),
            "localProxyUsername" to base.copy(localProxyUsername = "alice"),
            "localProxyPassword" to base.copy(localProxyPassword = "secret"),
            "coreLogLevel" to base.copy(coreLogLevel = "debug"),
            "enableAccessLog" to base.copy(enableAccessLog = true),
            "subscriptionPingUrl" to base.copy(subscriptionPingUrl = "https://ping.example/204"),
            "enableTunMode" to base.copy(enableTunMode = true),
            "enableStrictFullTunnel" to base.copy(enableStrictFullTunnel = true),
            "tunMtu" to base.copy(tunMtu = "1280"),
            "tunIpv4Cidr" to base.copy(tunIpv4Cidr = "10.20.30.1/24"),
            "tunIpv6Cidr" to base.copy(tunIpv6Cidr = "fd00::1/64"),
            "tunVpnDns" to base.copy(tunVpnDns = "1.1.1.1"),
            "tunTcpKeepAliveInterval" to base.copy(tunTcpKeepAliveInterval = "30"),
            "tunTcpUserTimeout" to base.copy(tunTcpUserTimeout = "20000"),
            "enableSniffing" to base.copy(enableSniffing = !base.enableSniffing),
            "enableSniffingRouteOnly" to base.copy(enableSniffingRouteOnly = !base.enableSniffingRouteOnly),
            "enableMux" to base.copy(enableMux = true),
            "muxConcurrency" to base.copy(muxConcurrency = "9"),
            "muxXudpConcurrency" to base.copy(muxXudpConcurrency = "9"),
            "muxXudpProxyUdp443" to base.copy(muxXudpProxyUdp443 = base.muxXudpProxyUdp443 + 1),
            "enableFragment" to base.copy(enableFragment = true),
            "fragmentPackets" to base.copy(fragmentPackets = base.fragmentPackets + "x"),
            "fragmentLength" to base.copy(fragmentLength = "9-9"),
            "fragmentInterval" to base.copy(fragmentInterval = "9-9"),
            "routeDomainStrategy" to base.copy(routeDomainStrategy = 2),
            "defaultRouteOutboundTag" to base.copy(defaultRouteOutboundTag = "direct"),
        )

        val ignored = changes.filter { (_, updated) -> !desktopSettingsNeedReconnect(base, updated) }.keys

        assertEquals(emptySet(), ignored, "These settings change a running tunnel but did not request a reconnect")
    }

    @Test
    fun appearanceAndOtherSettingsTheCoreNeverSeesDoNotReconnect() {
        assertFalse(desktopSettingsNeedReconnect(base, base.copy(themeMode = DesktopThemeMode.Light)))
        assertFalse(desktopSettingsNeedReconnect(base, base.copy(compactHome = true, proxyServerListColumns = 2)))
        assertFalse(desktopSettingsNeedReconnect(base, base.copy(confirmDeletion = false, languageTag = "ru")))
        assertFalse(desktopSettingsNeedReconnect(base, base.copy(autoConnectOnAppOpen = true, launchOnLogin = true)))
        assertFalse(desktopSettingsNeedReconnect(base, base))
    }

    @Test
    fun aRouteRuleChangeReconnects() {
        val rule = features.routing.model.RouteRule(id = 1, outboundTag = "direct", domain = listOf("domain:a.example"))

        assertTrue(desktopSettingsNeedReconnect(base, base.copy(routeRules = listOf(rule))))
    }
}
