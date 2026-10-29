// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IpInfoRunnerTest {
    private class FakeHttp(val bodies: Map<String, String>) : DiagnosticsHttpClient {
        override suspend fun getText(url: String, accept: String): String? = bodies[url]
    }

    private val geoJson = """
        {"ip":"203.0.113.9","success":true,"type":"IPv4","country":"Germany","country_code":"DE",
         "city":"Berlin","connection":{"asn":24940,"isp":"Hetzner","org":"Hetzner Online"}}
    """.trimIndent()

    @Test
    fun combinesExplicitAddressesWithGeoOfTheCurrentConnection() = runTest {
        val http = FakeHttp(
            mapOf(
                "https://api.ipify.org" to " 203.0.113.9 ",
                "https://api64.ipify.org" to "2001:db8::1",
                "https://ipwho.is" to geoJson,
            ),
        )

        val data = IpInfoRunner(http, isVpnTunnel = true).fetch()

        assertEquals("203.0.113.9", data.ipv4)
        assertEquals("2001:db8::1", data.ipv6)
        assertEquals("Berlin", data.city)
        assertEquals("🇩🇪", data.flagEmoji)
        assertTrue(data.isVpnTunnel)
    }

    @Test
    fun fallsBackToGeoLookupByExplicitIpWhenRootGeoFails() = runTest {
        val http = FakeHttp(
            mapOf(
                "https://api.ipify.org" to "203.0.113.9",
                "https://ipwho.is/203.0.113.9" to geoJson,
            ),
        )

        val data = IpInfoRunner(http, isVpnTunnel = false).fetch()

        assertEquals("Berlin", data.city)
        assertNull(data.ipv6)
    }

    @Test
    fun returnsMinimalModelWhenGeoIsUnavailableButAddressKnown() = runTest {
        val http = FakeHttp(mapOf("https://api.ipify.org" to "203.0.113.9"))

        val data = IpInfoRunner(http, isVpnTunnel = false).fetch()

        assertEquals("203.0.113.9", data.ipv4)
        assertEquals("", data.country)
    }

    @Test
    fun noDataFailsWithMessageThatNamesTheTunnelWhenOneIsUsed() = runTest {
        val empty = FakeHttp(emptyMap())

        val tunnel = assertFailsWith<IpInfoFailure> { IpInfoRunner(empty, isVpnTunnel = true).fetch() }
        val offline = assertFailsWith<IpInfoFailure> { IpInfoRunner(empty, isVpnTunnel = false).fetch() }

        assertEquals("VPN tunnel is up but passes no traffic", tunnel.message)
        assertTrue(tunnel.isTunnelIssue)
        assertEquals("No internet connection", offline.message)
    }
}
