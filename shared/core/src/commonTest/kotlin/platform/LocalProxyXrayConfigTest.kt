// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import features.proxy.server.model.VLESS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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

    @Test
    fun addsSeparateHttpInboundWhenWindowsSystemProxyIsEnabled() {
        val config = Json.parseToJsonElement(
            LocalProxyXrayConfigFactory.build(
                server = VLESS(
                    remarks = "Desktop",
                    id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6",
                    server = "example.com",
                    port = "443",
                ),
                options = LocalProxyXrayConfigOptions(
                    socksPort = 20_480,
                    httpProxyPort = 20_481,
                    listenAddress = "0.0.0.0",
                    enableHttpProxy = true,
                ),
            ),
        ) as JsonObject

        val inbounds = config.getValue("inbounds").jsonArray.map { it as JsonObject }
        val socks = inbounds.first { inbound ->
            inbound.getValue("tag").jsonPrimitive.content == LocalProxyXrayConfigFactory.SocksInboundTag
        }
        val http = inbounds.first { inbound ->
            inbound.getValue("tag").jsonPrimitive.content == LocalProxyXrayConfigFactory.HttpInboundTag
        }

        assertEquals("0.0.0.0", socks.getValue("listen").jsonPrimitive.content)
        assertEquals("http", http.getValue("protocol").jsonPrimitive.content)
        assertEquals(20_481, http.getValue("port").jsonPrimitive.content.toInt())
        assertEquals(DefaultLocalHttpProxyListenAddress, http.getValue("listen").jsonPrimitive.content)
    }

    @Test
    fun rejectsCollidingHttpAndSocksPorts() {
        assertFailsWith<IllegalArgumentException> {
            LocalProxyXrayConfigFactory.build(
                server = VLESS(remarks = "Desktop", id = "id", server = "example.com", port = "443"),
                options = LocalProxyXrayConfigOptions(
                    socksPort = 20_480,
                    httpProxyPort = 20_480,
                    enableHttpProxy = true,
                ),
            )
        }
    }

    @Test
    fun rejectsHttpSystemProxyInboundThatIsNotLoopback() {
        assertFailsWith<IllegalArgumentException> {
            LocalProxyXrayConfigFactory.build(
                server = VLESS(remarks = "Desktop", id = "id", server = "example.com", port = "443"),
                options = LocalProxyXrayConfigOptions(
                    socksPort = 20_480,
                    httpProxyPort = 20_481,
                    httpProxyListenAddress = "0.0.0.0",
                    enableHttpProxy = true,
                ),
            )
        }
    }
}
