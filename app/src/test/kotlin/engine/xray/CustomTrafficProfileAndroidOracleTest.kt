// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import app.withActiveTrafficConfigApplied
import features.config.TrafficConfigState
import features.proxy.server.model.Custom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Source oracle: Android keeps a selected raw Custom usable with an active profile. */
class CustomTrafficProfileAndroidOracleTest {
    @Test
    fun selectedCustomStillStartsWithAnActiveTrafficProfile() {
        val custom = ProxyServerState(
            id = 41,
            groupId = 0,
            server = Custom(
                remarks = "Raw custom",
                overrideInboundAndDns = false,
                configJson = """
                    {
                      "outbounds": [
                        { "tag": "raw-custom-out", "protocol": "freedom" }
                      ],
                      "routing": {
                        "rules": [
                          {
                            "type": "field",
                            "domain": ["raw-only.example"],
                            "outboundTag": "raw-custom-out"
                          }
                        ]
                      }
                    }
                """.trimIndent(),
            ),
        )
        val profile = TrafficConfigState(
            id = 73,
            name = "Active profile",
            rawConfig = """
                [Rule]
                DOMAIN,profile-only.example,DIRECT
                FINAL,missing-from-local-profile
                [SKIPI]
                route-domain-strategy = IPOnDemand
            """.trimIndent(),
        )
        val state = AppState(
            proxyServers = listOf(custom),
            selectedProxyServerId = custom.id,
            trafficConfigs = listOf(profile),
            activeTrafficConfigId = profile.id,
        ).withActiveTrafficConfigApplied()

        val built = XrayConfigFactory.buildXrayConfigResult(
            XrayConfigRequest(
                appState = state,
                selectedServer = custom,
                inbounds = emptyList(),
                coreLogPaths = XrayCoreLogPaths(
                    accessLogPath = "/tmp/access.log",
                    errorLogPath = "/tmp/error.log",
                ),
                collectTrafficStats = false,
            ),
        )

        assertEquals(profile.id, state.activeTrafficConfigId)
        val root = Json.parseToJsonElement(built.json).jsonObject
        assertEquals(
            "raw-custom-out",
            root.getValue("outbounds").jsonArray.single().jsonObject
                .getValue("tag").jsonPrimitive.content,
        )
        assertEquals(
            "raw-only.example",
            root.getValue("routing").jsonObject.getValue("rules").jsonArray.single().jsonObject
                .getValue("domain").jsonArray.single().jsonPrimitive.content,
        )
        assertFalse(built.json.contains("profile-only.example"))
        assertFalse(built.json.contains("missing-from-local-profile"))
    }
}
