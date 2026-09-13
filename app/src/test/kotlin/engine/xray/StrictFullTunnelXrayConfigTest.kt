// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.Custom
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class StrictFullTunnelXrayConfigTest {

    @Test
    fun generatedConfig_dropsEnabledDirectRulesWhenStrictTunnelIsEnabled() {
        val server = ProxyServerState(
            id = 1,
            groupId = 0,
            server = VLESS(
                remarks = "Test",
                id = "4219d973-8792-462f-8747-df766f70f137",
                server = "test.example.com",
                port = "443",
            ),
        )
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = AppState(
                    enableStrictFullTunnel = true,
                    routeRules = listOf(
                        RouteRule(
                            id = 1,
                            remarks = "must-not-use-direct",
                            outboundTag = "direct",
                            domain = listOf("domain:direct.example"),
                        ),
                    ),
                ),
                selectedServer = server,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths("/tmp/access.log", "/tmp/error.log"),
            ),
        )

        assertFalse(config.contains("direct.example"))
        assertTrue(config.contains("\"outboundTag\":\"proxy\"") || config.contains("\"outboundTag\": \"proxy\""))
    }

    @Test
    fun rawConfig_failsClosedWhenStrictTunnelIsEnabled() {
        val server = ProxyServerState(
            id = 1,
            groupId = 0,
            server = Custom(
                remarks = "Raw",
                configJson = """{"outbounds":[{"tag":"proxy","protocol":"freedom"}]}""",
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            XrayConfigFactory.buildXrayConfig(
                XrayConfigRequest(
                    appState = AppState(enableStrictFullTunnel = true),
                    selectedServer = server,
                    inbounds = emptyList(),
                    coreLogPaths = XrayCoreLogPaths("/tmp/access.log", "/tmp/error.log"),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("Strict full-tunnel"))
    }
}
