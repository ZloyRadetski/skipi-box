// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopLocalProxyRuntimeContractTest {
    @Test
    fun trafficProfileCoreConfigUsesTheConfiguredLocalSocksEndpointAndAuth() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@proxy.example:443#Proxy",
        )
        val library = DesktopServerLibraries.add(DesktopServerLibrary(), server)
        val profile = DesktopStoredConfig(id = 1, name = "Profile", content = "[Rule]\nFINAL,PROXY\n")
        val options = LocalProxyXrayConfigOptions(
            socksPort = 20_480,
            listenAddress = "0.0.0.0",
            username = "desktop-user",
            password = "desktop-secret",
        )

        val root = Json.parseToJsonElement(
            DesktopTrafficProfileXrayConfigFactory.build(profile, library, options),
        ).jsonObject
        val socks = root.getValue("inbounds").jsonArray.single().jsonObject
        val settings = socks.getValue("settings").jsonObject
        val user = settings.getValue("users").jsonArray.single().jsonObject

        assertEquals(20_480, socks.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("0.0.0.0", socks.getValue("listen").jsonPrimitive.content)
        assertEquals("password", settings.getValue("auth").jsonPrimitive.content)
        assertEquals("desktop-user", user.getValue("user").jsonPrimitive.content)
        assertEquals("desktop-secret", user.getValue("pass").jsonPrimitive.content)
        assertFalse("ip" in settings)
    }

    @Test
    fun customCoreConfigUsesTheSameLocalSocksCredentialContract() {
        val options = LocalProxyXrayConfigOptions(
            socksPort = 20_481,
            listenAddress = "127.0.0.1",
            username = "desktop-user",
            password = "desktop-secret",
        )
        val root = Json.parseToJsonElement(
            buildDesktopCustomXrayConfig(Custom(remarks = "Custom", configJson = "{}"), options),
        ).jsonObject
        val socks = root.getValue("inbounds").jsonArray.single().jsonObject
        val settings = socks.getValue("settings").jsonObject

        assertEquals(20_481, socks.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("127.0.0.1", socks.getValue("listen").jsonPrimitive.content)
        assertEquals("password", settings.getValue("auth").jsonPrimitive.content)
        assertEquals("127.0.0.1", settings.getValue("ip").jsonPrimitive.content)
        val user = settings.getValue("users").jsonArray.single().jsonObject
        assertEquals("desktop-user", user.getValue("user").jsonPrimitive.content)
        assertEquals("desktop-secret", user.getValue("pass").jsonPrimitive.content)
    }
}
