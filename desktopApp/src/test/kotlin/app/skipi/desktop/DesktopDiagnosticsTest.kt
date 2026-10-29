// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.tools.DiagnosticsHttpClient
import app.skipi.app.tools.DiagnosticsRoute
import app.skipi.ui.settings.SkipiSettingsDestination
import features.tools.ipinfo.IpInfoState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopDiagnosticsTest {
    @Test
    fun routeFollowsTheRunningTunnelListenerLikeAndroid() {
        val running = LocalProxyXrayConfigOptions(
            socksPort = 20_480,
            listenAddress = "0.0.0.0",
            username = "alice",
            password = "secret",
        )

        assertEquals(DiagnosticsRoute.Direct, desktopDiagnosticsRoute(null))
        assertEquals(
            DiagnosticsRoute.Socks("127.0.0.1", 20_480, "alice", "secret"),
            desktopDiagnosticsRoute(running),
        )
    }

    @Test
    fun jndiProviderUrlYieldsDnsServers() {
        assertEquals(
            listOf("192.168.0.1", "2001:db8::1", "8.8.8.8"),
            parseJndiDnsProviderUrl("dns://192.168.0.1 dns://[2001:db8::1] dns://8.8.8.8/"),
        )
        assertEquals(emptyList(), parseJndiDnsProviderUrl(""))
        assertEquals(emptyList(), parseJndiDnsProviderUrl(null))
    }

    @Test
    fun settingsOverviewOffersTheMobileDiagnosticTools() {
        val visible = desktopSettingsVisibleDestinations()

        assertTrue(
            visible.containsAll(
                setOf(
                    SkipiSettingsDestination.SpeedTest,
                    SkipiSettingsDestination.DnsLeakTest,
                    SkipiSettingsDestination.IpInfo,
                ),
            ),
        )
    }

    @Test
    fun ipInfoReportsTunnelUseFromTheActiveRouteAndTracksTheRun() = runBlocking {
        val routes = mutableListOf<DiagnosticsRoute>()
        val gate = CompletableDeferred<Unit>()
        val diagnostics = DesktopDiagnostics(
            activeOptions = { LocalProxyXrayConfigOptions(socksPort = 1080) },
            httpClientFor = { route ->
                routes += route
                DiagnosticsHttpClient { url, _ ->
                    gate.await()
                    when (url) {
                        "https://api.ipify.org" -> "203.0.113.9"
                        "https://ipwho.is" ->
                            """{"ip":"203.0.113.9","success":true,"type":"IPv4","country":"Germany","country_code":"DE","city":"Berlin"}"""
                        else -> null
                    }
                }
            },
            systemDnsServers = { emptyList() },
        )
        try {
            diagnostics.ipInfo.refresh()
            assertEquals(IpInfoState.Loading, diagnostics.ipInfo.state.value)
            gate.complete(Unit)
            val success = withTimeout(5_000) {
                var state = diagnostics.ipInfo.state.value
                while (state !is IpInfoState.Success) {
                    kotlinx.coroutines.delay(10)
                    state = diagnostics.ipInfo.state.value
                }
                state
            }

            assertTrue(success.data.isVpnTunnel)
            assertEquals("Berlin", success.data.city)
            assertEquals(DiagnosticsRoute.Socks("127.0.0.1", 1080), routes.last())
        } finally {
            diagnostics.close()
        }
    }

    @Test
    fun withoutATunnelDirectRouteIsUsedAndReportedAsDirect() = runBlocking {
        val diagnostics = DesktopDiagnostics(
            activeOptions = { null },
            httpClientFor = { route ->
                assertEquals(DiagnosticsRoute.Direct, route)
                DiagnosticsHttpClient { url, _ -> if (url == "https://api.ipify.org") "203.0.113.9" else null }
            },
            systemDnsServers = { emptyList() },
        )
        try {
            diagnostics.ipInfo.refresh()
            val state = withTimeout(5_000) {
                var current = diagnostics.ipInfo.state.value
                while (current is IpInfoState.Loading || current is IpInfoState.Idle) {
                    kotlinx.coroutines.delay(10)
                    current = diagnostics.ipInfo.state.value
                }
                current
            }

            val success = assertIs<IpInfoState.Success>(state)
            assertEquals(false, success.data.isVpnTunnel)
        } finally {
            diagnostics.close()
        }
    }
}
