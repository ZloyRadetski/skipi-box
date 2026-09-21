// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XrayDnsServerTest {
    @Test
    fun acceptsOnlyDnsTransportsSupportedByBundledXray() {
        assertTrue(isSupportedXrayDnsServer("https://dns.example/dns-query"))
        assertTrue(isSupportedXrayDnsServer("h2c://dns.example/dns-query"))
        assertTrue(isSupportedXrayDnsServer("quic+local://dns.example"))
        assertTrue(isSupportedXrayDnsServer("tcp://1.1.1.1:53"))
        assertTrue(isSupportedXrayDnsServer("localhost"))

        assertFalse(isSupportedXrayDnsServer("tls://dns.example:853"))
        assertFalse(isSupportedXrayDnsServer("tls+local://dns.example:853"))
        assertFalse(isSupportedXrayDnsServer("udp://1.1.1.1:53"))
        assertFalse(isSupportedXrayDnsServer("quic://dns.example"))
        assertFalse(isSupportedXrayDnsServer("fakedns"))
    }

    @Test
    fun normalizesAndFiltersPortableDnsServerLists() {
        assertEquals(
            listOf("1.1.1.1", "https://dns.example/dns-query"),
            listOf(
                " 1.1.1.1 ",
                "tls://dns.example:853",
                "https://dns.example/dns-query",
                "https://dns.example/dns-query",
                "",
            ).toSupportedXrayDnsServers(),
        )
    }

    @Test
    fun extractsOnlyRemoteDnsHostsAndSafeTcpFallbacks() {
        assertEquals("dns.example", "https://DNS.Example./dns-query".remoteXrayDnsHostOrNull())
        assertEquals("dns.example", "dns.example".remoteXrayDnsHostOrNull())
        assertNull("tcp+local://dns.example:53".remoteXrayDnsHostOrNull())

        assertEquals(
            XrayDnsTcpFallback(address = "dns.nullsproxy.com"),
            "https://dns.nullsproxy.com/dns-query".toXrayTcpDnsFallbackOrNull(),
        )
        assertEquals(
            XrayDnsTcpFallback(address = "1.1.1.1", port = 5353),
            "tcp://1.1.1.1:5353".toXrayTcpDnsFallbackOrNull(),
        )
        assertNull("https://dns.example/dns-query".toXrayTcpDnsFallbackOrNull())
    }
}
