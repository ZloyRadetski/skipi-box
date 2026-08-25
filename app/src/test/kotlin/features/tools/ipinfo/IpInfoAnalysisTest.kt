// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.ipinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpInfoAnalysisTest {

    private val sampleJson = """
        {
            "ip": "1.2.3.4",
            "success": true,
            "type": "IPv4",
            "continent": "Europe",
            "continent_code": "EU",
            "country": "Finland",
            "country_code": "FI",
            "region": "Uusimaa",
            "region_code": "18",
            "city": "Helsinki",
            "latitude": 60.1698556,
            "longitude": 24.938379,
            "is_eu": true,
            "postal": "00100",
            "calling_code": "358",
            "capital": "Helsinki",
            "borders": "NO,RU,SE",
            "flag": {
                "img": "https://cdn.ipwhois.io/flags/fi.svg",
                "emoji": "🇫🇮",
                "emoji_unicode": "U+1F1EB U+1F1EE"
            },
            "connection": {
                "asn": 213520,
                "org": "Hetzner Online GmbH",
                "isp": "Hetzner Online",
                "domain": "hetzner.com"
            },
            "timezone": {
                "id": "Europe/Helsinki",
                "abbr": "EEST",
                "is_dst": true,
                "offset": 10800,
                "utc": "+03:00",
                "current_time": "2026-08-25T16:00:00+03:00"
            }
        }
    """.trimIndent()

    @Test
    fun parseIpWhoIs_validResponse() {
        val parsed = IpInfoAnalysis.parseIpWhoIs(sampleJson)
        assertNotNull(parsed)
        assertEquals("1.2.3.4", parsed!!.ip)
        assertEquals(true, parsed.success)
        assertEquals("Finland", parsed.country)
        assertEquals("FI", parsed.countryCode)
        assertEquals("Helsinki", parsed.city)
        assertEquals(213520, parsed.connection?.asn)
        assertEquals("Hetzner Online", parsed.connection?.isp)
        assertEquals("Europe/Helsinki", parsed.timezone?.id)
        assertEquals("+03:00", parsed.timezone?.utc)
        assertEquals("🇫🇮", parsed.flag?.emoji)
    }

    @Test
    fun parseIpWhoIs_unsuccessfulOrMalformed() {
        assertNull(IpInfoAnalysis.parseIpWhoIs("""{"success":false,"message":"invalid IP address"}"""))
        assertNull(IpInfoAnalysis.parseIpWhoIs("not a json"))
        assertNull(IpInfoAnalysis.parseIpWhoIs(""))
    }

    @Test
    fun countryFlagEmoji_convertsValid2LetterIsoCodes() {
        assertEquals("🇫🇮", IpInfoAnalysis.countryFlagEmoji("FI"))
        assertEquals("🇩🇪", IpInfoAnalysis.countryFlagEmoji("de"))
        assertEquals("🇺🇸", IpInfoAnalysis.countryFlagEmoji(" US "))
        assertNull(IpInfoAnalysis.countryFlagEmoji(null))
        assertNull(IpInfoAnalysis.countryFlagEmoji("FIN"))
        assertNull(IpInfoAnalysis.countryFlagEmoji("12"))
        assertNull(IpInfoAnalysis.countryFlagEmoji(""))
    }

    @Test
    fun mapToIpInfoData_producesCompleteModel() {
        val parsed = IpInfoAnalysis.parseIpWhoIs(sampleJson)!!
        val data = IpInfoAnalysis.mapToIpInfoData(
            response = parsed,
            explicitIpv4 = "1.2.3.4",
            explicitIpv6 = "2001:db8::1",
            isVpnTunnel = true,
        )

        assertEquals("1.2.3.4", data.ipv4)
        assertEquals("2001:db8::1", data.ipv6)
        assertEquals("1.2.3.4", data.primaryIp)
        assertEquals("Dual Stack", data.ipType)
        assertEquals("Finland", data.country)
        assertEquals("FI", data.countryCode)
        assertEquals("🇫🇮", data.flagEmoji)
        assertEquals("Helsinki", data.city)
        assertEquals("Uusimaa", data.region)
        assertEquals("00100", data.postal)
        assertEquals("Hetzner Online", data.isp)
        assertEquals(213520, data.asn)
        assertEquals("Europe/Helsinki", data.timezoneId)
        assertEquals("EEST", data.timezoneAbbr)
        assertEquals("+03:00", data.timezoneUtc)
        assertEquals(true, data.isVpnTunnel)

        val summary = data.toSummaryText()
        assertTrue(summary.contains("IPv4: 1.2.3.4"))
        assertTrue(summary.contains("IPv6: 2001:db8::1"))
        assertTrue(summary.contains("Country: 🇫🇮 Finland (FI)"))
        assertTrue(summary.contains("Location: Helsinki, Uusimaa, 00100"))
        assertTrue(summary.contains("ISP: Hetzner Online"))
        assertTrue(summary.contains("ASN: AS213520"))
        assertTrue(summary.contains("Route: VPN Tunnel / Proxy"))
    }
}
