// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyHomeLatencySettingsTest {
    @Test
    fun persistedTimeoutUsesAndroidDefaultBoundsAndFallback() {
        assertEquals(800, resolveProxyHomeLatencyTimeoutMillis(" 800 "))
        assertEquals(500, resolveProxyHomeLatencyTimeoutMillis("12"))
        assertEquals(60_000, resolveProxyHomeLatencyTimeoutMillis("90000"))
        assertEquals(5_000, resolveProxyHomeLatencyTimeoutMillis("invalid"))
        assertEquals(5_000, resolveProxyHomeLatencyTimeoutMillis(" "))
    }
}
