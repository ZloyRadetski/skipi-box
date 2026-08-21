// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsLeakAnalysisTest {

    @Test
    fun parseIpWhoIs_reads_geo_and_connection() {
        val body = """
            {"ip":"8.8.8.8","success":true,"country":"United States","country_code":"US",
             "connection":{"asn":15169,"org":"Google LLC","isp":"Google LLC"},"extra":1}
        """.trimIndent()
        val geo = DnsLeakAnalysis.parseIpWhoIs(body)
        assertEquals("US", geo?.countryCode)
        assertEquals("Google LLC", geo?.connection?.isp)
        assertEquals(15169, geo?.connection?.asn)
    }

    @Test
    fun parseIpWhoIs_rejects_failures_and_invalid_bodies() {
        assertNull(DnsLeakAnalysis.parseIpWhoIs("""{"success":false}"""))
        assertNull(DnsLeakAnalysis.parseIpWhoIs("not json"))
    }

    @Test
    fun egressIpFromTxt_finds_ipv4_record() {
        val records = listOf("edns0-client-subnet 1.2.3.0/24", "172.217.37.147")
        assertEquals("172.217.37.147", DnsLeakAnalysis.egressIpFromTxt(records))
        assertNull(DnsLeakAnalysis.egressIpFromTxt(listOf("edns0-client-subnet 1.2.3.0/24")))
    }

    @Test
    fun clientSubnetFromTxt_extracts_base_address() {
        val records = listOf("172.217.37.147", "edns0-client-subnet 109.196.66.0/24")
        assertEquals("109.196.66.0", DnsLeakAnalysis.clientSubnetFromTxt(records))
        assertNull(DnsLeakAnalysis.clientSubnetFromTxt(listOf("172.217.37.147")))
    }

    @Test
    fun resolvers_prefer_client_subnet_for_geo() {
        val probes = listOf(
            DnsProbeResult("192.168.31.1", true, "172.217.37.147", "109.196.66.0"),
            DnsProbeResult("1.1.1.1", false, "", null),
        )
        val geo = mapOf(
            "109.196.66.0" to IpWhoIsResponse(
                ip = "109.196.66.0",
                success = true,
                country = "Russia",
                countryCode = "RU",
                connection = IpWhoIsConnection(asn = 12389, isp = "Rostelecom"),
            ),
        )
        val resolvers = DnsLeakAnalysis.resolvers(probes, geo)
        assertEquals(1, resolvers.size)
        assertEquals("RU", resolvers[0].observedCountryCode)
        assertEquals("109.196.66.0", resolvers[0].observedIp)
        assertEquals(true, resolvers[0].isSystemServer)
    }

    @Test
    fun verdict_is_leak_when_any_path_country_differs_from_exit() {
        val resolvers = listOf(
            makeResolver("NL"),
            makeResolver("RU"),
        )
        val verdict = DnsLeakAnalysis.verdict(resolvers, DnsLeakExitInfo("1.1.1.1", "NL", "Netherlands", "", null))
        assertEquals(DnsLeakVerdict.SuspectedLeak, verdict)
    }

    @Test
    fun verdict_is_no_leak_when_all_paths_match_exit() {
        val resolvers = listOf(makeResolver("NL"), makeResolver("nl"))
        val verdict = DnsLeakAnalysis.verdict(resolvers, DnsLeakExitInfo("1.1.1.1", "NL", "Netherlands", "", null))
        assertEquals(DnsLeakVerdict.NoLeak, verdict)
    }

    @Test
    fun verdict_is_unknown_without_data() {
        assertEquals(DnsLeakVerdict.Unknown, DnsLeakAnalysis.verdict(emptyList(), null))
        assertEquals(
            DnsLeakVerdict.Unknown,
            DnsLeakAnalysis.verdict(listOf(makeResolver("")), DnsLeakExitInfo("1.1.1.1", "NL", "Netherlands", "", null)),
        )
    }

    @Test
    fun country_flag_emoji_maps_two_letter_codes() {
        assertEquals("🇩🇪", DnsLeakAnalysis.countryFlagEmoji("de"))
        assertEquals("🇺🇸", DnsLeakAnalysis.countryFlagEmoji(" US "))
        assertNull(DnsLeakAnalysis.countryFlagEmoji(null))
        assertNull(DnsLeakAnalysis.countryFlagEmoji("DEU"))
        assertNull(DnsLeakAnalysis.countryFlagEmoji("D1"))
    }

    @Test
    fun failure_kind_reflects_vpn_presence() {
        assertEquals(DnsLeakFailureKind.TunnelNotPassing, DnsLeakAnalysis.failureKind(true))
        assertEquals(DnsLeakFailureKind.NoInternet, DnsLeakAnalysis.failureKind(false))
    }

    @Test
    fun test_failure_names_dead_tunnel_when_vpn_network_present() {
        val failure = DnsLeakTestFailure(hasVpnNetwork = true)
        assertEquals(DnsLeakFailureKind.TunnelNotPassing, failure.kind)
        assertTrue(failure.message!!.contains("VPN"))
    }

    @Test
    fun test_failure_reports_offline_when_no_vpn_network() {
        val failure = DnsLeakTestFailure(hasVpnNetwork = false)
        assertEquals(DnsLeakFailureKind.NoInternet, failure.kind)
        assertFalse(failure.message!!.contains("VPN"))
    }

    private fun makeResolver(countryCode: String): DnsLeakResolver {
        return DnsLeakResolver(
            server = "1.1.1.1",
            isSystemServer = false,
            egressIp = "172.217.37.147",
            clientSubnetIp = null,
            observedCountryCode = countryCode,
            observedCountryName = "",
            isp = "",
            asn = null,
        )
    }
}
