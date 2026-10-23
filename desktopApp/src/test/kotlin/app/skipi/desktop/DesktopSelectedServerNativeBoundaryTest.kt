// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0
package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import platform.LocalProxyXrayConfigOptions

class DesktopSelectedServerNativeBoundaryTest {
    @Test
    fun selectedOlcRtcFailsWithoutADesktopNativeBridge() {
        assertNativeBridgeUnavailable(
            buildResult(
                library = library(
                    selectedId = 10,
                    stored(10, olcRtc()),
                ),
            ),
        )
    }

    @Test
    fun selectedAmneziaWgFailsWithoutADesktopNativeBridge() {
        assertNativeBridgeUnavailable(
            buildResult(
                library = library(
                    selectedId = 10,
                    stored(10, amneziaWg()),
                ),
            ),
        )
    }

    @Test
    fun chainWithOlcRtcMemberFailsWithoutADesktopNativeBridge() {
        assertNativeBridgeUnavailable(
            buildResult(
                library = library(
                    selectedId = 90,
                    stored(10, Socks(remarks = "Regular hop", server = "hop.example", port = "1080")),
                    stored(20, olcRtc()),
                    stored(90, ChainProxy(remarks = "Native chain", proxyServerIds = listOf(10, 20))),
                ),
            ),
        )
    }

    @Test
    fun automaticGroupWithAmneziaWgMemberFailsWithoutADesktopNativeBridge() {
        assertNativeBridgeUnavailable(
            buildResult(
                library = library(
                    selectedId = 90,
                    stored(10, Socks(remarks = "Regular member", server = "member.example", port = "1080")),
                    stored(20, amneziaWg()),
                    stored(
                        90,
                        StrategyGroup(
                            remarks = "Native automatic group",
                            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                            proxyServerIds = listOf(10, 20),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun buildResult(library: DesktopServerLibrary) = DesktopSelectedServerXrayConfigFactory.build(
        serverLibrary = library,
        routing = RoutingConfigRecord(),
        trafficConfigContentById = emptyMap(),
        options = LocalProxyXrayConfigOptions(socksPort = 19_084),
        subscriptionPingUrl = "http://127.0.0.1:18087/subscription-ping",
        latencyByServerId = emptyMap(),
    )

    private fun assertNativeBridgeUnavailable(result: Result<String>?) {
        val failure = assertNotNull(
            assertNotNull(result).exceptionOrNull(),
            "A Desktop config must not claim unsupported native bridge output is runnable",
        )
        val messages = generateSequence(failure) { it.cause }
            .mapNotNull(Throwable::message)
            .joinToString("\n")
        assertTrue(
            messages.contains("bridge", ignoreCase = true) || messages.contains("runtime", ignoreCase = true),
            "The failure should identify the unavailable native bridge/runtime: $messages",
        )
    }

    private fun library(selectedId: Int, vararg servers: DesktopStoredProxyServer) =
        DesktopServerLibrary(selectedServerId = selectedId, servers = servers.toList())

    private fun stored(id: Int, server: ProxyServer<*>) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
    )

    private fun olcRtc() = OlcRtc(
        remarks = "Native OLCRTC",
        provider = "jitsi",
        transport = "datachannel",
        roomUrl = "https://meet.jit.si/skipi-test-room",
        encryptionKey = "0123456789abcdef".repeat(4),
    ).also { server -> assertTrue(server.validateFull().isEmpty()) }

    private fun amneziaWg() = AmneziaWg(
        remarks = "Native AmneziaWG",
        server = "awg.example.com",
        port = "51820",
        secretKey = "A".repeat(43) + "=",
        publicKey = "A".repeat(43) + "=",
    ).also { server -> assertTrue(server.validateFull().isEmpty()) }
}
