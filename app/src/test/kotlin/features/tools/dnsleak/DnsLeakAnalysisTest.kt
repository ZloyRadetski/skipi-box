// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsLeakAnalysisTest {

    @Test
    fun parse_reads_dns_records_and_ignores_unknown_fields() {
        val body = """
            [
              {"ip":"1.1.1.1","country_id":"US","country_name":"United States","asn":13335,"isp":"Cloudflare","type":"dns","extra":true},
              {"ip":"","type":"conclusion","text":"ok"}
            ]
        """.trimIndent()
        val records = DnsLeakAnalysis.parse(body)
        assertEquals(2, records.size)
        assertEquals("1.1.1.1", records[0].ip)
        assertEquals("Cloudflare", records[0].isp)
    }

    @Test
    fun parse_returns_empty_list_for_invalid_body() {
        assertEquals(emptyList<DnsLeakRecord>(), DnsLeakAnalysis.parse("not json"))
    }

    @Test
    fun resolvers_keeps_only_dns_type_and_deduplicates_by_ip() {
        val records = listOf(
            DnsLeakRecord(ip = "1.1.1.1", type = "dns", asn = 13335),
            DnsLeakRecord(ip = "1.1.1.1", type = "dns", asn = 13335),
            DnsLeakRecord(ip = "9.9.9.9", countryId = "de", type = "dns", asn = 3356),
            DnsLeakRecord(ip = "8.8.8.8", type = "ip"),
            DnsLeakRecord(ip = "", type = "dns"),
        )
        val resolvers = DnsLeakAnalysis.resolvers(records)
        assertEquals(2, resolvers.size)
        assertEquals("DE", resolvers[1].countryCode)
    }

    @Test
    fun verdict_is_no_leak_when_at_most_two_distinct_asn() {
        val resolvers = listOf(
            DnsLeakResolver("1.1.1.1", "US", "United States", 13335, "Cloudflare"),
            DnsLeakResolver("8.8.8.8", "US", "United States", 13335, "Google"),
            DnsLeakResolver("9.9.9.9", "", "", null, ""),
        )
        assertEquals(DnsLeakVerdict.NoLeak, DnsLeakAnalysis.verdict(resolvers))
    }

    @Test
    fun verdict_is_suspected_leak_when_three_or_more_distinct_asn() {
        val resolvers = listOf(
            DnsLeakResolver("1.1.1.1", "US", "United States", 13335, "Cloudflare"),
            DnsLeakResolver("8.8.8.8", "US", "United States", 15169, "Google"),
            DnsLeakResolver("9.9.9.9", "", "", 3356, "Quad9"),
        )
        assertEquals(DnsLeakVerdict.SuspectedLeak, DnsLeakAnalysis.verdict(resolvers))
    }

    @Test
    fun verdict_is_unknown_without_resolvers() {
        assertEquals(DnsLeakVerdict.Unknown, DnsLeakAnalysis.verdict(emptyList()))
    }

    @Test
    fun country_flag_emoji_maps_two_letter_codes() {
        assertEquals("🇩🇪", DnsLeakAnalysis.countryFlagEmoji("de"))
        assertEquals("🇺🇸", DnsLeakAnalysis.countryFlagEmoji(" US "))
        assertNull(DnsLeakAnalysis.countryFlagEmoji(null))
        assertNull(DnsLeakAnalysis.countryFlagEmoji("DEU"))
        assertNull(DnsLeakAnalysis.countryFlagEmoji("D1"))
    }
}
