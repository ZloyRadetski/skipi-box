// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package platform

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocalProxySocksInboundContractTest {
    @Test
    fun unauthenticatedInboundKeepsAndroidListenerFields() {
        val inbound = LocalProxyXrayConfigFactory.buildSocksInbound(
            LocalProxyXrayConfigOptions(
                socksPort = 20_480,
                listenAddress = "127.0.0.1",
            ),
        )

        assertEquals("skipi-socks", inbound.getValue("tag").jsonPrimitive.content)
        assertEquals("127.0.0.1", inbound.getValue("listen").jsonPrimitive.content)
        assertEquals(20_480, inbound.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("socks", inbound.getValue("protocol").jsonPrimitive.content)
        val settings = inbound.getValue("settings").jsonObject
        assertEquals("noauth", settings.getValue("auth").jsonPrimitive.content)
        assertEquals("true", settings.getValue("udp").jsonPrimitive.content)
        assertEquals("0", settings.getValue("userLevel").jsonPrimitive.content)
        assertEquals("127.0.0.1", settings.getValue("ip").jsonPrimitive.content)
        assertFalse("users" in settings)
    }

    @Test
    fun authenticatedInboundCarriesExactCredentialsAndLanListenAddress() {
        val inbound = LocalProxyXrayConfigFactory.buildSocksInbound(
            LocalProxyXrayConfigOptions(
                socksPort = 20_480,
                listenAddress = "0.0.0.0",
                username = " proxy-user ",
                password = "proxy-secret",
            ),
        )

        assertEquals("0.0.0.0", inbound.getValue("listen").jsonPrimitive.content)
        assertEquals(20_480, inbound.getValue("port").jsonPrimitive.content.toInt())
        val settings = inbound.getValue("settings").jsonObject
        assertEquals("password", settings.getValue("auth").jsonPrimitive.content)
        val user = settings.getValue("users").jsonArray.single().jsonObject
        assertEquals(" proxy-user ", user.getValue("user").jsonPrimitive.content)
        assertEquals("proxy-secret", user.getValue("pass").jsonPrimitive.content)
        assertFalse("ip" in settings)
    }
}
