// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeServerHeadlineTest {
    @Test
    fun matchesAndroidHeadlineFallbackAndCountryFlagRules() {
        val englandFlag = "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"

        assertEquals(
            ProxyHomeServerHeadline(title = "VLESS", flag = null),
            proxyHomeServerHeadline(remarks = "", protocol = "VLESS", serverId = 30, isStrategyGroup = false),
        )
        assertEquals(
            ProxyHomeServerHeadline(title = "Strategy", flag = "⚡"),
            proxyHomeServerHeadline(remarks = "", protocol = "Strategy", serverId = 31, isStrategyGroup = true),
        )
        assertEquals(
            ProxyHomeServerHeadline(title = "  😀 Plain label  ", flag = null),
            proxyHomeServerHeadline(
                remarks = "  😀 Plain label  ",
                protocol = "VLESS",
                serverId = 32,
                isStrategyGroup = false,
            ),
        )
        assertEquals(
            ProxyHomeServerHeadline(title = "🇫🇮", flag = "🇫🇮"),
            proxyHomeServerHeadline(remarks = "🇫🇮", protocol = "VLESS", serverId = 33, isStrategyGroup = false),
        )
        assertEquals(
            ProxyHomeServerHeadline(title = "England", flag = englandFlag),
            proxyHomeServerHeadline(
                remarks = "  ${englandFlag} England",
                protocol = "VLESS",
                serverId = 34,
                isStrategyGroup = false,
            ),
        )
    }
}
