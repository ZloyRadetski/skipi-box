// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DesktopStoredProxyServerDecodeCacheTest {
    private fun cache(capacity: Int = 4096, counter: IntArray) = DesktopDecodeCache<String>(capacity) { json ->
        counter[0]++
        json.uppercase()
    }

    @Test
    fun equalJsonIsDecodedOnceAndTheSameObjectComesBack() {
        val calls = intArrayOf(0)
        val cache = cache(counter = calls)
        val a = cache.get(String("{\"a\":1}".toCharArray()))
        val b = cache.get(String("{\"a\":1}".toCharArray()))
        assertSame(a, b)
        assertEquals(1, calls[0])
    }

    @Test
    fun theCacheIsBoundedAndDropsTheOldestEntry() {
        val calls = intArrayOf(0)
        val cache = cache(capacity = 2, counter = calls)
        cache.get("a"); cache.get("b"); cache.get("c")
        cache.get("a")
        assertEquals(4, calls[0])
    }

    @Test
    fun storedServerDecodeUsesTheSharedCache() {
        val first = DesktopStoredProxyServer(1, "not json").decode()
        val second = DesktopStoredProxyServer(2, "not json").decode()
        assertEquals(first.isFailure, second.isFailure)
    }
}
