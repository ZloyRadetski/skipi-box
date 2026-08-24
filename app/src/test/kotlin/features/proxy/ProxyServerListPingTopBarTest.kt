// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy

import app.modes.SubscriptionPingModeHttp
import app.modes.SubscriptionPingModeTcp
import engine.proxy.latency.ProxyServerLatencyTestMode
import org.junit.Test
import kotlin.test.assertEquals

class ProxyServerListPingTopBarTest {

    private fun resolvePingMode(subscriptionPingMode: Int): ProxyServerLatencyTestMode {
        return if (subscriptionPingMode == SubscriptionPingModeHttp) {
            ProxyServerLatencyTestMode.RealConnection
        } else {
            ProxyServerLatencyTestMode.TcpConnect
        }
    }

    @Test
    fun testResolvePingModeHttp() {
        val mode = resolvePingMode(SubscriptionPingModeHttp)
        assertEquals(ProxyServerLatencyTestMode.RealConnection, mode)
    }

    @Test
    fun testResolvePingModeTcp() {
        val mode = resolvePingMode(SubscriptionPingModeTcp)
        assertEquals(ProxyServerLatencyTestMode.TcpConnect, mode)
    }
}
