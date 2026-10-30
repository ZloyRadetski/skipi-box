// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import app.skipi.app.tools.IpInfoFailure
import features.tools.ipinfo.IpInfoData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunnelIpResolverTest {
    private fun info(ipv4: String? = null, ipv6: String? = null, flag: String = "") = IpInfoData(
        ipv4 = ipv4, ipv6 = ipv6, country = "", countryCode = "", flagEmoji = flag,
        continent = "", region = "", city = "", postal = "", capital = "", latitude = null,
        longitude = null, isp = "", org = "", asn = null, domain = "", timezoneId = "",
        timezoneAbbr = "", timezoneUtc = "", currentTime = "", isVpnTunnel = true,
    )

    @Test
    fun prefersTheIpv4AddressAndCarriesTheFlag() = runTest {
        val result = resolveTunnelIp(fetch = { info(ipv4 = "203.0.113.7", ipv6 = "2001:db8::1", flag = "F") })
        assertEquals(ProxyHomeTunnelIp("203.0.113.7", "F"), result)
    }

    @Test
    fun fallsBackToIpv6AndDropsABlankFlag() = runTest {
        val result = resolveTunnelIp(fetch = { info(ipv6 = "2001:db8::1", flag = " ") })
        assertEquals(ProxyHomeTunnelIp("2001:db8::1", null), result)
    }

    @Test
    fun retriesWhileTheFreshTunnelDoesNotPassTrafficYet() = runTest {
        var calls = 0
        val result = resolveTunnelIp(retryDelaysMs = listOf(10, 10)) {
            calls++
            if (calls < 3) throw IpInfoFailure("tunnel down", isTunnelIssue = true)
            info(ipv4 = "203.0.113.7")
        }
        assertEquals(3, calls)
        assertEquals("203.0.113.7", result?.value)
    }

    @Test
    fun givesUpWithNullAfterTheLastRetry() = runTest {
        var calls = 0
        val result = resolveTunnelIp(retryDelaysMs = listOf(10, 10)) {
            calls++
            info() // no address at all counts as a failed lookup
        }
        assertEquals(3, calls)
        assertNull(result)
    }
}
