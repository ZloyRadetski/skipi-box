// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import features.proxy.server.model.VLESS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalProxyXrayConfigTest {
    @Test
    fun createsLocalSocksConfigForAValidSelectedServer() {
        val config = Json.parseToJsonElement(
            LocalProxyXrayConfigFactory.build(
                VLESS(
                    remarks = "Desktop",
                    id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6",
                    server = "example.com",
                    port = "443",
                ),
            ),
        ) as JsonObject

        val inbound = (config["inbounds"] as JsonArray).single() as JsonObject
        val outbounds = config["outbounds"] as JsonArray

        assertEquals("socks", inbound["protocol"]?.jsonPrimitive?.content)
        assertEquals(DefaultLocalSocksPort, inbound["port"]?.jsonPrimitive?.content?.toInt())
        assertEquals(LocalProxyXrayConfigFactory.ProxyTag, (outbounds.first() as JsonObject)["tag"]?.jsonPrimitive?.content)
        assertEquals("vless", (outbounds.first() as JsonObject)["protocol"]?.jsonPrimitive?.content)
    }

    @Test
    fun rejectsAnInvalidLocalPortBeforeProducingConfig() {
        assertFailsWith<IllegalArgumentException> {
            LocalProxyXrayConfigFactory.build(
                server = VLESS(remarks = "Desktop", id = "id", server = "example.com", port = "443"),
                options = LocalProxyXrayConfigOptions(socksPort = 0),
            )
        }
    }
}
