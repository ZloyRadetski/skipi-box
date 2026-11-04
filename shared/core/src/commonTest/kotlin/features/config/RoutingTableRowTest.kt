// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingTableRowTest {
    private fun rule(line: String) = "[Rule]\n$line\nFINAL,PROXY\n".analyzeShadowrocketConfig().rules.first()

    private fun row(line: String) = rule(line).toRoutingTableRow()

    private fun written(row: RoutingTableRow) = assertNotNull(row.toShadowrocketLineOrNull()).line

    // --- reading a rule as a row

    @Test
    fun aPlainRuleIsAOneValueRow() {
        assertEquals(
            RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com")),
            row("DOMAIN-SUFFIX,a.com,DIRECT"),
        )
    }

    @Test
    fun domainRulesAreShownInXrayNotation() {
        assertEquals(listOf("domain:a.com"), row("DOMAIN-SUFFIX,a.com,DIRECT")?.domains)
        assertEquals(listOf("full:a.com"), row("DOMAIN,a.com,DIRECT")?.domains)
        assertEquals(listOf("hypixel.net"), row("DOMAIN-KEYWORD,hypixel.net,DIRECT")?.domains)
        assertEquals(listOf("*.es-dis.net"), row("DOMAIN-WILDCARD,*.es-dis.net,DIRECT")?.domains)
        assertEquals(listOf("geosite:steam"), row("DOMAIN-SET,geosite:steam,DIRECT")?.domains)
        assertEquals(listOf("geosite:steam"), row("GEOSITE,steam,DIRECT")?.domains)
        assertEquals(listOf("regexp:^ads\\d+\\.example\\.com$"), row("DOMAIN-SET,regexp:^ads\\d+\\.example\\.com$,DIRECT")?.domains)
    }

    @Test
    fun addressAndProcessRulesGoToTheirOwnLists() {
        assertEquals(listOf("10.0.0.0/8"), row("IP-CIDR,10.0.0.0/8,DIRECT")?.ips)
        assertEquals(listOf("fd00::/8"), row("IP-CIDR6,fd00::/8,DIRECT")?.ips)
        assertEquals(listOf("geoip:ru"), row("GEOIP,ru,DIRECT")?.ips)
        assertEquals(listOf("steam"), row("PROCESS-NAME,steam,DIRECT")?.processes)
    }

    @Test
    fun portNetworkAndProtocolRulesFillTheirFields() {
        assertEquals("25565", row("DST-PORT,25565,DIRECT")?.port)
        assertEquals("udp", row("NETWORK,udp,DIRECT")?.network)
        assertEquals(listOf("bittorrent"), row("PROTOCOL,bittorrent,DIRECT")?.protocols)
    }

    @Test
    fun anAndOfFieldsFillsOneRow() {
        val line = "AND,((OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SET,geosite:steam),(IP-CIDR,10.0.0.0/8))),(OR,((DST-PORT,443),(DST-PORT,8000-8100))),(NETWORK,tcp),(OR,((PROTOCOL,http),(PROTOCOL,tls)))),PROXY"
        assertEquals(
            RoutingTableRow(
                policy = "PROXY",
                domains = listOf("domain:a.com", "geosite:steam"),
                ips = listOf("10.0.0.0/8"),
                port = "443,8000-8100",
                network = "tcp",
                protocols = listOf("http", "tls"),
            ),
            row(line),
        )
    }

    @Test
    fun anOrOfAddressesIsOneRow() {
        assertEquals(
            RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com"), ips = listOf("10.0.0.0/8"), processes = listOf("steam")),
            row("OR,((DOMAIN-SUFFIX,a.com),(IP-CIDR,10.0.0.0/8),(PROCESS-NAME,steam)),DIRECT"),
        )
    }

    @Test
    fun bothNetworksMeanAnyNetwork() {
        assertEquals("", row("AND,((DOMAIN,a.com),(OR,((NETWORK,tcp),(NETWORK,udp)))),DIRECT")?.network)
    }

    @Test
    fun theNameTheSwitchAndOtherOptionsAreKept() {
        val read = assertNotNull(row("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve, skipi-disabled // Local net"))
        assertEquals("Local net", read.name)
        assertFalse(read.enabled)
        assertEquals("no-resolve", read.extraOption)
    }

    @Test
    fun rulesThatDoNotFitTheTableAreNotRows() {
        listOf(
            "RULE-SET,https://example.com/list.txt,DIRECT",
            "IP-ASN,13335,DIRECT",
            "USER-AGENT,foo,DIRECT",
            "URL-REGEX,^https://a,DIRECT",
            "FINAL,PROXY",
            // A domain ANDed with another domain cannot be one row.
            "AND,((DOMAIN,a.com),(DOMAIN-KEYWORD,b)),DIRECT",
            // An OR of a domain and a port mixes two fields.
            "OR,((DOMAIN,a.com),(DST-PORT,443)),DIRECT",
            // An AND nested in an OR is a different shape from the table.
            "OR,((DOMAIN,a.com),(AND,((DOMAIN,b.com),(DST-PORT,443)))),DIRECT",
            // Two ports groups ANDed.
            "AND,((DST-PORT,443),(DST-PORT,80)),DIRECT",
        ).forEach { line -> assertNull(row(line), line) }
    }

    // --- writing a row as a rule line

    @Test
    fun oneValueIsAPlainLine() {
        assertEquals("DOMAIN-SUFFIX,a.com,DIRECT", written(RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com"))))
        assertEquals("DST-PORT,443,PROXY", written(RoutingTableRow(policy = "PROXY", port = "443")))
        assertEquals("NETWORK,udp,PROXY", written(RoutingTableRow(policy = "PROXY", network = "udp")))
    }

    @Test
    fun domainNotationBecomesRuleTypes() {
        fun type(token: String) = written(RoutingTableRow(policy = "D", domains = listOf(token)))
        assertEquals("DOMAIN-SUFFIX,a.com,D", type("domain:a.com"))
        assertEquals("DOMAIN,a.com,D", type("full:a.com"))
        assertEquals("DOMAIN-KEYWORD,a.com,D", type("keyword:a.com"))
        assertEquals("DOMAIN-KEYWORD,a.com,D", type("a.com"))
        assertEquals("DOMAIN-WILDCARD,*.a.com,D", type("*.a.com"))
        assertEquals("DOMAIN-SET,geosite:steam,D", type("geosite:steam"))
        assertEquals("DOMAIN-SET,ext:geo.dat:ads,D", type("ext:geo.dat:ads"))
        assertEquals("DOMAIN-SET,regexp:^a\\.com$,D", type("regexp:^a\\.com\$"))
    }

    @Test
    fun addressNotationBecomesRuleTypes() {
        fun type(token: String) = written(RoutingTableRow(policy = "D", ips = listOf(token)))
        assertEquals("IP-CIDR,10.0.0.0/8,D", type("10.0.0.0/8"))
        assertEquals("IP-CIDR,1.1.1.1,D", type("1.1.1.1"))
        assertEquals("IP-CIDR6,fd00::/8,D", type("fd00::/8"))
        assertEquals("GEOIP,private,D", type("geoip:private"))
        assertEquals("GEOIP,ext:geo.dat:ru,D", type("ext:geo.dat:ru"))
    }

    @Test
    fun severalValuesOfOneFieldAreOneOr() {
        assertEquals(
            "OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SUFFIX,b.com)),DIRECT",
            written(RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com", "domain:b.com"))),
        )
    }

    @Test
    fun domainsAddressesAndProcessesShareOneOr() {
        assertEquals(
            "OR,((DOMAIN-SUFFIX,a.com),(IP-CIDR,10.0.0.0/8),(PROCESS-NAME,steam)),DIRECT",
            written(RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com"), ips = listOf("10.0.0.0/8"), processes = listOf("steam"))),
        )
    }

    @Test
    fun severalFieldsAreAnAndOfTheirGroups() {
        assertEquals(
            "AND,((OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SUFFIX,b.com))),(OR,((DST-PORT,443),(DST-PORT,8000-8100))),(NETWORK,tcp),(OR,((PROTOCOL,http),(PROTOCOL,tls)))),PROXY",
            written(
                RoutingTableRow(
                    policy = "PROXY",
                    domains = listOf("domain:a.com", "domain:b.com"),
                    port = "443, 8000-8100",
                    network = "tcp",
                    protocols = listOf("http", "tls"),
                ),
            ),
        )
    }

    @Test
    fun theNameTheSwitchAndOtherOptionsAreWritten() {
        val line = written(
            RoutingTableRow(name = "Local net", enabled = false, policy = "DIRECT", ips = listOf("10.0.0.0/8"), extraOption = "no-resolve"),
        )
        assertEquals("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve, skipi-disabled // Local net", line)
    }

    @Test
    fun valuesThatCannotBeWrittenAreLeftOutAndReported() {
        val result = assertNotNull(
            RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com", "regexp:^(a|b)\\.com$", "x,y"), ips = listOf("not an ip"))
                .toShadowrocketLineOrNull(),
        )
        assertEquals("DOMAIN-SUFFIX,a.com,DIRECT", result.line)
        assertEquals(listOf("regexp:^(a|b)\\.com$", "x,y", "not an ip"), result.unwritable)
    }

    @Test
    fun blankValuesAndRepeatsAreIgnored() {
        assertEquals(
            "DOMAIN-SUFFIX,a.com,DIRECT",
            written(RoutingTableRow(policy = "DIRECT", domains = listOf(" domain:a.com ", "", "domain:a.com"))),
        )
    }

    @Test
    fun aRowWithoutAnyConditionIsNotWritten() {
        assertNull(RoutingTableRow(policy = "DIRECT").toShadowrocketLineOrNull())
        assertNull(RoutingTableRow(policy = "DIRECT", domains = listOf("", " ")).toShadowrocketLineOrNull())
        assertNull(RoutingTableRow(policy = "DIRECT", ips = listOf("not an ip")).toShadowrocketLineOrNull())
        assertFalse(RoutingTableRow(policy = "DIRECT").hasConditions)
        assertTrue(RoutingTableRow(policy = "DIRECT", port = "443").hasConditions)
    }

    // --- both directions together

    @Test
    fun aRowSurvivesBeingWrittenAndReadBack() {
        listOf(
            RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com")),
            RoutingTableRow(name = "Games", policy = "PROXY", domains = listOf("geosite:steam", "hypixel.net", "*.es-dis.net"), port = "25565"),
            RoutingTableRow(policy = "DIRECT", ips = listOf("geoip:private", "10.0.0.0/8"), enabled = false),
            RoutingTableRow(policy = "block", processes = listOf("steam"), network = "udp"),
            RoutingTableRow(policy = "PROXY", domains = listOf("full:a.com"), ips = listOf("1.1.1.1"), port = "443,80", protocols = listOf("tls", "http")),
            RoutingTableRow(policy = "DIRECT", protocols = listOf("bittorrent"), extraOption = "no-resolve"),
        ).forEach { original ->
            val line = written(original)
            assertEquals(original, row(line), line)
        }
    }

    // --- what Xray gets

    private fun xray(row: RoutingTableRow) = assertNotNull(
        rule(written(row)).convertToXrayRouteRules(firstId = 1) { it.lowercase() }.also { assertNull(it.unsupportedReason) },
    ).rules

    @Test
    fun domainAndAddressOfOneRowAreAlternativesLikeInV2rayN() {
        val rules = xray(RoutingTableRow(policy = "DIRECT", domains = listOf("domain:a.com"), ips = listOf("10.0.0.0/8"), port = "443"))
        assertEquals(2, rules.size)
        assertEquals(listOf("domain:a.com"), rules[0].domain)
        assertEquals(listOf("10.0.0.0/8"), rules[1].ip)
        assertTrue(rules.all { it.port == "443" })
    }

    @Test
    fun aHugeRowIsStillAFewXrayRules() {
        val domains = (1..400).map { "domain:site$it.example" }
        val rules = xray(RoutingTableRow(policy = "DIRECT", domains = domains, ips = listOf("10.0.0.0/8", "geoip:private"), port = "443, 80", network = "tcp"))
        assertEquals(2, rules.size)
        assertEquals(400, rules[0].domain.size)
        assertEquals("443,80", rules[0].port)
        assertEquals("tcp", rules[0].network)
        assertEquals(listOf("10.0.0.0/8", "geoip:private"), rules[1].ip)
    }

    @Test
    fun aDisabledRowGivesNoXrayRule() {
        assertTrue(xray(RoutingTableRow(policy = "DIRECT", enabled = false, domains = listOf("domain:a.com"))).isEmpty())
    }
}