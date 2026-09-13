// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.proxy.server.model.Custom
import features.proxy.server.model.VLESS
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayLogConfigTest {

    @Test
    fun debug_mode_enables_dns_diagnostics_and_access_log_uses_its_configured_file() {
        val config = requestFor(
            AppState(coreLogLevel = 0, enableAccessLog = true),
        ).buildXrayLogConfig()

        assertEquals("debug", config["loglevel"]?.jsonPrimitive?.content)
        assertTrue(config["dnsLog"]?.jsonPrimitive?.content?.toBoolean() == true)
        assertEquals("/tmp/access.log", config["access"]?.jsonPrimitive?.content)
        assertEquals("/tmp/error.log", config["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun normal_log_levels_do_not_enable_verbose_dns_diagnostics() {
        val config = requestFor(
            AppState(coreLogLevel = 2, enableAccessLog = false),
        ).buildXrayLogConfig()

        assertEquals("warning", config["loglevel"]?.jsonPrimitive?.content)
        assertFalse(config["dnsLog"]?.jsonPrimitive?.content?.toBoolean() == true)
        assertEquals(XrayLogDisabled, config["access"]?.jsonPrimitive?.content)
    }

    @Test
    fun raw_custom_profile_cannot_suppress_app_owned_logs() {
        val server = ProxyServerState(
            id = 1,
            groupId = 0,
            server = Custom(
                remarks = "Raw",
                overrideInboundAndDns = false,
                configJson = """
                    {
                      "log": {"loglevel": "none", "access": "none", "error": "none"},
                      "outbounds": [{"tag": "proxy", "protocol": "freedom"}]
                    }
                """.trimIndent(),
            ),
        )
        val config = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = AppState(coreLogLevel = 0, enableAccessLog = true),
                selectedServer = server,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(
                    accessLogPath = "/tmp/access.log",
                    errorLogPath = "/tmp/error.log",
                ),
                collectTrafficStats = false,
            ),
        )

        val log = XrayConfigJson.parseToJsonElement(config).jsonObject["log"]?.jsonObject
            ?: error("Log config is missing")
        assertEquals("debug", log["loglevel"]?.jsonPrimitive?.content)
        assertTrue(log["dnsLog"]?.jsonPrimitive?.content?.toBoolean() == true)
        assertEquals("/tmp/access.log", log["access"]?.jsonPrimitive?.content)
        assertEquals("/tmp/error.log", log["error"]?.jsonPrimitive?.content)
    }

    private fun requestFor(appState: AppState): XrayConfigRequest {
        val server = ProxyServerState(
            id = 1,
            groupId = 0,
            server = VLESS(
                remarks = "Test",
                id = "test-id",
                server = "node.example",
                port = "443",
            ),
        )
        return XrayConfigRequest(
            appState = appState,
            selectedServer = server,
            inbounds = emptyList(),
            coreLogPaths = XrayCoreLogPaths(
                accessLogPath = "/tmp/access.log",
                errorLogPath = "/tmp/error.log",
            ),
        )
    }
}
