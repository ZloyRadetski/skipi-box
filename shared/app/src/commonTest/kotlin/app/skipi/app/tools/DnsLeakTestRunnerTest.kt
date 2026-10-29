// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.dnsleak.DnsLeakFailureKind
import features.tools.dnsleak.DnsLeakTestFailure
import features.tools.dnsleak.DnsLeakVerdict
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DnsLeakTestRunnerTest {
    private fun geo(ip: String, code: String, country: String, isp: String) =
        """{"ip":"$ip","success":true,"country":"$country","country_code":"$code","connection":{"asn":1,"isp":"$isp"}}"""

    private class FakeHttp(val bodies: Map<String, String>) : DiagnosticsHttpClient {
        val requested = mutableListOf<String>()
        override suspend fun getText(url: String, accept: String): String? {
            requested += url
            return bodies[url]
        }
    }

    private class FakeDns(val answers: Map<String, List<String>?>) : DnsTxtResolver {
        val queried = mutableListOf<Pair<String, String>>()
        override suspend fun queryTxt(server: String, domain: String): List<String>? {
            queried += server to domain
            return answers[server]
        }
    }

    private val fastRetry = DnsLeakTestConfig(retryDelayMillis = 0L)

    @Test
    fun probesSystemThenPublicResolversWithGoogleEchoRecord() = runTest {
        val dns = FakeDns(mapOf("192.168.1.1" to listOf("203.0.113.9"), "1.1.1.1" to listOf("203.0.113.9")))
        val http = FakeHttp(
            mapOf(
                "https://api.ipify.org" to "203.0.113.9\n",
                "https://ipwho.is/203.0.113.9" to geo("203.0.113.9", "DE", "Germany", "Hetzner"),
            ),
        )

        DnsLeakTestRunner(listOf("192.168.1.1", "1.1.1.1", ""), dns, http, true, fastRetry).run()

        val servers = dns.queried.map { it.first }.distinct()
        assertEquals(listOf("192.168.1.1", "1.1.1.1", "8.8.8.8", "9.9.9.9"), servers)
        assertTrue(dns.queried.all { it.second == "o-o.myaddr.l.google.com" })
    }

    @Test
    fun matchingCountriesAreNoLeakAndMismatchIsSuspected() = runTest {
        val http = FakeHttp(
            mapOf(
                "https://api.ipify.org" to "203.0.113.9",
                "https://ipwho.is/203.0.113.9" to geo("203.0.113.9", "DE", "Germany", "Hetzner"),
                "https://ipwho.is/198.51.100.7" to geo("198.51.100.7", "RU", "Russia", "Home ISP"),
            ),
        )
        val clean = FakeDns(mapOf("8.8.8.8" to listOf("203.0.113.9")))
        val leaking = FakeDns(
            mapOf("8.8.8.8" to listOf("203.0.113.9"), "9.9.9.9" to listOf("198.51.100.7")),
        )

        val cleanOutcome = DnsLeakTestRunner(emptyList(), clean, http, true, fastRetry).run()
        val leakOutcome = DnsLeakTestRunner(emptyList(), leaking, http, true, fastRetry).run()

        assertEquals(DnsLeakVerdict.NoLeak, cleanOutcome.verdict)
        assertEquals("203.0.113.9", cleanOutcome.exit?.ip)
        assertEquals(DnsLeakVerdict.SuspectedLeak, leakOutcome.verdict)
        assertEquals(2, leakOutcome.resolvers.size)
    }

    @Test
    fun silentEverythingFailsWithKindFromTunnelPresence() = runTest {
        val silentHttp = FakeHttp(emptyMap())
        val silentDns = FakeDns(emptyMap())

        val tunnel = assertFailsWith<DnsLeakTestFailure> {
            DnsLeakTestRunner(emptyList(), silentDns, silentHttp, true, fastRetry).run()
        }
        val offline = assertFailsWith<DnsLeakTestFailure> {
            DnsLeakTestRunner(emptyList(), silentDns, silentHttp, false, fastRetry).run()
        }

        assertEquals(DnsLeakFailureKind.TunnelNotPassing, tunnel.kind)
        assertEquals(DnsLeakFailureKind.NoInternet, offline.kind)
    }

    @Test
    fun silentEndpointsAreRetriedOnce() = runTest {
        val dns = FakeDns(emptyMap())
        val http = FakeHttp(emptyMap())

        runCatching { DnsLeakTestRunner(emptyList(), dns, http, true, fastRetry).run() }

        assertEquals(2, http.requested.count { it == "https://api.ipify.org" })
    }
}
