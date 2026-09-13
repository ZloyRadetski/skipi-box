// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.DefaultRouteOutboundTag
import app.withVpnSettingsReset
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import features.routing.model.RouteRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictFullTunnelTest {

    @Test
    fun appState_enableStrictFullTunnel_defaultsToFalseAndVpnResetDisablesIt() {
        assertFalse(AppState().enableStrictFullTunnel)
        assertFalse(AppState(enableStrictFullTunnel = true).withVpnSettingsReset().enableStrictFullTunnel)
    }

    @Test
    fun strictFullTunnel_removesPerAppExceptionsAndEnabledDirectRulesAtRuntime() {
        val directRule = RouteRule(
            id = 1,
            outboundTag = "DIRECT",
            domain = listOf("domain:example.com"),
        )
        val proxyRule = RouteRule(
            id = 2,
            outboundTag = "proxy",
            domain = listOf("domain:proxy.example"),
        )
        val disabledDirectRule = RouteRule(
            id = 3,
            outboundTag = "direct",
            domain = listOf("domain:disabled.example"),
            enabled = false,
        )
        val state = AppState(
            enableStrictFullTunnel = true,
            proxyAppListMode = ProxyAppListModeBlacklist,
            proxyAppListSelectedApps = listOf("com.example.bypass"),
            defaultRouteOutboundTag = "direct",
            routeRules = listOf(directRule, proxyRule, disabledDirectRule),
        )

        val runtimeState = state.withStrictFullTunnelApplied()

        assertEquals(ProxyAppListModeGlobal, runtimeState.proxyAppListMode)
        assertTrue(runtimeState.proxyAppListSelectedApps.isEmpty())
        assertEquals(DefaultRouteOutboundTag, runtimeState.defaultRouteOutboundTag)
        assertFalse(runtimeState.routeRules[0].enabled)
        assertTrue(runtimeState.routeRules[1].enabled)
        assertFalse(runtimeState.routeRules[2].enabled)
        assertEquals(ProxyAppListModeGlobal, state.toVpnApplicationPolicy(currentUserId = 0).mode)
    }

    @Test
    fun strictFullTunnel_doesNotRewriteStoredStateOrNonDirectRules() {
        val directRule = RouteRule(id = 1, outboundTag = "direct", domain = listOf("domain:example.com"))
        val blockRule = RouteRule(id = 2, outboundTag = "block", domain = listOf("domain:ads.example"))
        val state = AppState(
            enableStrictFullTunnel = true,
            routeRules = listOf(directRule, blockRule),
        )

        val runtimeState = state.withStrictFullTunnelApplied()

        assertTrue(state.routeRules[0].enabled)
        assertEquals("direct", state.routeRules[0].outboundTag)
        assertFalse(runtimeState.routeRules[0].enabled)
        assertTrue(runtimeState.routeRules[1].enabled)
        assertEquals("block", runtimeState.routeRules[1].outboundTag)
    }
}
