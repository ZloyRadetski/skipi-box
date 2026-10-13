// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AndroidLocalProxyInboundParityTest {
    @Test
    fun androidBuilderUsesStoredPortListenAndCredentialFormat() {
        val state = AppState(
            localProxyPort = "20480",
            localProxyListenAllInterfaces = true,
            enableLocalProxyAuth = true,
            localProxyUsername = "proxy-user",
            localProxyPassword = "proxy-secret",
        )

        val inbound = buildLocalSocksInbound(state, "skipi-socks", state.toLocalProxyOptions())
        val settings = inbound.getValue("settings") as JsonObject

        assertEquals("0.0.0.0", inbound.getValue("listen").jsonPrimitive.content)
        assertEquals(20_480, inbound.getValue("port").jsonPrimitive.content.toInt())
        assertEquals("password", settings.getValue("auth").jsonPrimitive.content)
        assertEquals("true", settings.getValue("udp").jsonPrimitive.content)
        assertEquals("0", settings.getValue("userLevel").jsonPrimitive.content)
        val credentials = settings.getValue("users").jsonArray.single().jsonObject
        assertEquals("proxy-user", credentials.getValue("user").jsonPrimitive.content)
        assertEquals("proxy-secret", credentials.getValue("pass").jsonPrimitive.content)
        assertFalse("ip" in settings)
    }

    @Test
    fun androidBuilderKeepsNoAuthContractWhenAuthenticationIsDisabled() {
        val state = AppState(enableLocalProxyAuth = false)
        val inbound = buildLocalSocksInbound(state, "skipi-socks", state.toLocalProxyOptions())
        val settings = inbound.getValue("settings") as JsonObject

        assertEquals("noauth", settings.getValue("auth").jsonPrimitive.content)
        assertTrue("users" !in settings)
    }
}
