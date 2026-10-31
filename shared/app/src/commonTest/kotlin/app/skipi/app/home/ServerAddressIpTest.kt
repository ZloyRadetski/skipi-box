// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import features.tools.ipinfo.IpInfoData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The IP tile of the connection panel shows the tunnel's public address. Until that is known (or when
 * the lookup fails) it shows the address of the server the config leads to, a domain resolved to an IP,
 * instead of a dash.
 */
class ServerAddressIpTest {
    private fun info(ipv4: String? = null, flag: String = "") = IpInfoData(
        ipv4 = ipv4, ipv6 = null, country = "", countryCode = "", flagEmoji = flag,
        continent = "", region = "", city = "", postal = "", capital = "", latitude = null,
        longitude = null, isp = "", org = "", asn = null, domain = "", timezoneId = "",
        timezoneAbbr = "", timezoneUtc = "", currentTime = "", isVpnTunnel = true,
    )

    private val noLookup: suspend (String) -> String? = { error("an IP address needs no lookup") }

    @Test
    fun anIpAddressIsShownAsItIsWithoutAnyLookup() = runTest {
        assertEquals(ProxyHomeTunnelIp("203.0.113.7"), resolveServerAddressIp("203.0.113.7", noLookup))
        assertEquals(ProxyHomeTunnelIp("2001:db8::1"), resolveServerAddressIp("[2001:db8::1]", noLookup))
        assertEquals(ProxyHomeTunnelIp("203.0.113.7"), resolveServerAddressIp("  203.0.113.7 ", noLookup))
    }

    @Test
    fun aDomainIsResolvedToItsAddress() = runTest {
        val asked = mutableListOf<String>()
        val result = resolveServerAddressIp("vpn.example.org") { host -> asked += host; "198.51.100.9" }

        assertEquals(ProxyHomeTunnelIp("198.51.100.9"), result)
        assertEquals(listOf("vpn.example.org"), asked)
    }

    @Test
    fun nothingToShowWhenThereIsNoAddressOrItDoesNotResolve() = runTest {
        assertNull(resolveServerAddressIp(null, noLookup))
        assertNull(resolveServerAddressIp("   ", noLookup))
        assertNull(resolveServerAddressIp("gone.example.org") { null })
        assertNull(resolveServerAddressIp("gone.example.org") { "  " })
        assertNull(resolveServerAddressIp("gone.example.org") { throw IllegalStateException("gone") })
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        assertFailsWith<CancellationException> {
            resolveServerAddressIp("vpn.example.org") { throw CancellationException("stopped") }
        }
    }

    @Test
    fun theServerAddressShowsFirstAndThePublicAddressReplacesIt() = runTest {
        val shown = mutableListOf<ProxyHomeTunnelIp?>()

        resolveTunnelIpWithServerFallback(
            serverHost = "vpn.example.org",
            lookupHost = { "198.51.100.9" },
            publish = { shown += it },
            fetch = { info(ipv4 = "203.0.113.7", flag = "F") },
        )

        assertEquals(
            listOf<ProxyHomeTunnelIp?>(ProxyHomeTunnelIp("198.51.100.9"), ProxyHomeTunnelIp("203.0.113.7", "F")),
            shown,
        )
    }

    @Test
    fun aFailedPublicLookupKeepsTheServerAddressInsteadOfBlankingIt() = runTest {
        val shown = mutableListOf<ProxyHomeTunnelIp?>()

        resolveTunnelIpWithServerFallback(
            serverHost = "vpn.example.org",
            lookupHost = { "198.51.100.9" },
            publish = { shown += it },
            retryDelaysMs = listOf(10),
            fetch = { throw IllegalStateException("tunnel down") },
        )

        assertEquals(listOf<ProxyHomeTunnelIp?>(ProxyHomeTunnelIp("198.51.100.9")), shown)
    }

    @Test
    fun withoutAServerAddressTheBehaviourIsUnchanged() = runTest {
        val shown = mutableListOf<ProxyHomeTunnelIp?>()
        resolveTunnelIpWithServerFallback(
            serverHost = null, lookupHost = noLookup, publish = { shown += it }, fetch = { info(ipv4 = "203.0.113.7") },
        )
        assertEquals(listOf<ProxyHomeTunnelIp?>(ProxyHomeTunnelIp("203.0.113.7")), shown)

        shown.clear()
        resolveTunnelIpWithServerFallback(
            serverHost = null, lookupHost = noLookup, publish = { shown += it }, retryDelaysMs = emptyList(),
            fetch = { info() },
        )
        assertEquals(emptyList<ProxyHomeTunnelIp?>(), shown)
    }
}
