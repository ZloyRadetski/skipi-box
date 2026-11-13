// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWifiSsidCacheTest {
    private fun nic(name: String) = DesktopNetworkInterface(name, name, isUp = true, isLoopback = false, hasUsableAddress = true)

    @Test
    fun theSsidIsReadOnceWhileTheAdaptersStayTheSameAndAreFresh() {
        var clock = 0L
        var reads = 0
        val cache = DesktopWifiSsidCache(maxAgeMillis = 30_000, now = { clock }, read = { reads++; "home" })
        val adapters = listOf(nic("wlan0"))

        repeat(5) { clock += 5_000; assertEquals("home", cache.get(adapters)) }

        assertEquals(1, reads)
    }

    @Test
    fun aChangedAdapterListOrAnOldReadingReadsAgain() {
        var clock = 0L
        var reads = 0
        val cache = DesktopWifiSsidCache(maxAgeMillis = 30_000, now = { clock }, read = { "n${reads++}" })

        cache.get(listOf(nic("wlan0")))
        cache.get(listOf(nic("wlan0"), nic("eth0")))
        clock += 31_000
        cache.get(listOf(nic("wlan0"), nic("eth0")))

        assertEquals(3, reads)
    }
}
