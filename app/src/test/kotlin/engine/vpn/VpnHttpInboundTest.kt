// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.withLocalProxyHttpAuthMode
import app.withLocalProxySocksAuthMode
import engine.proxy.toLocalProxyOptions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import platform.LocalProxyAuthMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VpnHttpInboundTest {
    private fun inbounds(state: AppState): List<JsonObject> {
        val local = state.toLocalProxyOptions()
        val http = state.toVpnAppendHttpProxyOptions(local)
        return buildVpnXrayInbounds(state, state.toTunOptions(), local, http)
    }

    private fun List<JsonObject>.protocol(name: String): JsonObject? =
        firstOrNull { it.getValue("protocol").jsonPrimitive.content == name }

    @Test
    fun theHttpInboundIsEnabledForTheVpnToo() {
        val state = AppState(localProxyPort = "20480", localHttpProxyPort = "20481", enableLocalHttpProxy = true)

        val http = assertNotNullInbound(inbounds(state).protocol("http"))
        assertEquals(20_481, http.getValue("port").jsonPrimitive.content.toInt())
        assertTrue(state.toVpnAppendHttpProxyOptions(state.toLocalProxyOptions()).enabled)
    }

    @Test
    fun noHttpInboundMeansNoHttpListener() {
        assertNull(inbounds(AppState()).protocol("http"))
    }

    @Test
    fun theVpnProxyStillBringsItsInboundAlong() {
        val state = AppState(enableVpnAppendHttpProxy = true)

        assertNotNullInbound(inbounds(state).protocol("http"))
        assertTrue(state.toVpnAppendHttpProxyOptions(state.toLocalProxyOptions()).enabled)
    }

    @Test
    fun theHttpInboundListensLikeSocksAndKeepsItsOwnLogin() {
        val state = AppState(
            localProxyListenAllInterfaces = true,
            enableLocalHttpProxy = true,
            localProxyUsername = "s-user",
            localProxyPassword = "s-pass",
            localHttpProxyUsername = "h-user",
            localHttpProxyPassword = "h-pass",
        ).withLocalProxySocksAuthMode(LocalProxyAuthMode.Manual).withLocalProxyHttpAuthMode(LocalProxyAuthMode.Manual)

        val all = inbounds(state)
        val http = assertNotNullInbound(all.protocol("http"))
        val socks = assertNotNullInbound(all.protocol("socks"))

        assertEquals("0.0.0.0", http.getValue("listen").jsonPrimitive.content)
        assertEquals("0.0.0.0", socks.getValue("listen").jsonPrimitive.content)
        val account = http.getValue("settings").jsonObject.getValue("accounts").jsonArray.single().jsonObject
        assertEquals("h-user", account.getValue("user").jsonPrimitive.content)
        val user = socks.getValue("settings").jsonObject.getValue("users").jsonArray.single().jsonObject
        assertEquals("s-user", user.getValue("user").jsonPrimitive.content)
    }

    @Test
    fun httpWithoutAnAuthModeStaysOpenWhileSocksIsProtected() {
        val state = AppState(enableLocalHttpProxy = true)

        val http = assertNotNullInbound(inbounds(state).protocol("http"))

        assertFalse(http.getValue("settings").jsonObject.containsKey("accounts"))
    }

    private fun assertNotNullInbound(inbound: JsonObject?): JsonObject = inbound ?: error("expected the inbound to exist")
}
