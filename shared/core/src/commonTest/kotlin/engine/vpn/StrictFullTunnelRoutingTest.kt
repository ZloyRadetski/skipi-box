// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.DefaultRouteOutboundTag
import features.routing.model.RouteRule
import kotlin.test.Test
import kotlin.test.assertEquals

/** The routing half of the strict full tunnel, shared by every host that stores global routing. */
class StrictFullTunnelRoutingTest {
    private val direct = RouteRule(id = 1, outboundTag = "DIRECT", domain = listOf("domain:example.com"))
    private val proxy = RouteRule(id = 2, outboundTag = "proxy", domain = listOf("domain:proxy.example"))
    private val disabledDirect = RouteRule(id = 3, outboundTag = "direct", domain = listOf("domain:off.example"), enabled = false)

    @Test
    fun enabledDirectRulesAreSwitchedOffAndOthersKept() {
        val strict = strictFullTunnelRouting("proxy", listOf(direct, proxy, disabledDirect))

        assertEquals(listOf(direct.copy(enabled = false), proxy, disabledDirect), strict.rules)
    }

    @Test
    fun aDirectDefaultRouteBecomesTheStandardDefault() {
        assertEquals(DefaultRouteOutboundTag, strictFullTunnelRouting(" Direct ", emptyList()).defaultOutboundTag)
        assertEquals("proxy-2", strictFullTunnelRouting("proxy-2", emptyList()).defaultOutboundTag)
    }
}
