// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.clipboard.ClipboardImportMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V2rayRoutingImportTest {
    private fun parse(json: String, resolve: (String) -> String? = ::defaultV2rayOutbound) =
        assertNotNull(json.parseV2rayRoutingImportOrNull(resolve))

    private val export = """
        [
          {"remarks":"BLOCK","outboundTag":"block","domain":["geosite:category-ads-all","geosite:win-spy"],"network":"tcp,udp","enabled":true,"ruleType":0},
          {"remarks":"Hypixel","outboundTag":"direct","port":"25565","domain":["hypixel.net","sessionserver.mojang.com"],"enabled":true},
          {"remarks":"private direct","outboundTag":"direct","domain":["geosite:private"],"ip":["geoip:private"],"enabled":true},
          {"remarks":"zapret","outboundTag":"direct","domain":["geosite:discord"],"enabled":false},
          {"remarks":"dns only","outboundTag":"direct","domain":["b.com"],"ruleType":2},
          {"remarks":"Остальное","outboundTag":"proxy","port":"0-65535","network":"tcp,udp"},
          {"remarks":"after","outboundTag":"direct","domain":["late.com"]}
        ]
    """.trimIndent()

    @Test
    fun otherJsonIsNotAnImport() {
        assertNull("""["vless://a@b:1"]""".parseV2rayRoutingImportOrNull())
        assertNull("""{"RouteOrder":"block"}""".parseV2rayRoutingImportOrNull())
        assertNull("[]".parseV2rayRoutingImportOrNull())
        assertNull("not json".parseV2rayRoutingImportOrNull())
    }

    @Test
    fun everyRuleBecomesARowInOrderWithItsNameAndSwitch() {
        val import = parse(export)
        assertEquals(listOf("BLOCK", "Hypixel", "private direct", "zapret"), import.rows.map { it.name })
        assertEquals(listOf(true, true, true, false), import.rows.map { it.enabled })
        assertEquals(listOf("REJECT", "DIRECT", "DIRECT", "DIRECT"), import.rows.map { it.policy })
    }

    @Test
    fun theFieldsOfARuleFillTheFieldsOfTheRow() {
        val rows = parse(export).rows
        assertEquals(listOf("geosite:category-ads-all", "geosite:win-spy"), rows[0].domains)
        assertEquals("", rows[0].network)
        assertEquals("25565", rows[1].port)
        assertEquals(listOf("geosite:private"), rows[2].domains)
        assertEquals(listOf("geoip:private"), rows[2].ips)
    }

    @Test
    fun theCatchAllRuleIsTheFinalPolicyAndTheRulesAfterItAreDropped() {
        val import = parse(export)
        assertEquals("PROXY", import.finalPolicy)
        assertTrue(import.rows.none { it.name == "after" })
        assertEquals(1, import.droppedAfterFinal)
    }

    @Test
    fun aRuleOnlyForDnsIsSkippedAndSaid() {
        val skipped = parse(export).skipped.single()
        assertEquals("dns only", skipped.name)
        assertEquals(V2rayRoutingSkipReason.DnsOnly, skipped.reason)
    }

    @Test
    fun aRuleForAnUnknownOutboundIsSkippedUnlessItIsResolved() {
        val json = """[{"remarks":"hk","outboundTag":"HK-01","domain":["a.com"]},{"remarks":"ok","outboundTag":"proxy","domain":["b.com"]}]"""
        val plain = parse(json)
        assertEquals(listOf("ok"), plain.rows.map { it.name })
        assertEquals(V2rayRoutingSkipReason.UnknownOutbound, plain.skipped.single().reason)
        assertEquals("HK-01", plain.skipped.single().detail)

        val resolved = parse(json) { tag -> if (tag == "HK-01") "Hong Kong" else defaultV2rayOutbound(tag) }
        assertEquals(listOf("Hong Kong", "PROXY"), resolved.rows.map { it.policy })
        assertTrue(resolved.skipped.isEmpty())
    }

    @Test
    fun aRuleTiedToAnInboundIsSkippedRatherThanWidened() {
        val skipped = parse("""[{"remarks":"tun only","outboundTag":"direct","inboundTag":["tun"],"domain":["a.com"]}]""")
            .skipped.single()
        assertEquals(V2rayRoutingSkipReason.InboundTag, skipped.reason)
    }

    @Test
    fun portsNetworksAndProtocolsAreNormalised() {
        val rows = parse(
            """[
              {"outboundTag":"direct","domain":["a.com"],"port":"0-65535","network":"tcp,udp"},
              {"outboundTag":"direct","domain":["b.com"],"port":"443, 8000-8100","network":"tcp","protocol":["HTTP","tls"]},
              {"outboundTag":"direct","process":["steam.exe"],"network":"udp,tcp"}
            ]""",
        ).rows
        assertEquals("", rows[0].port)
        assertEquals("", rows[0].network)
        assertEquals("443,8000-8100", rows[1].port)
        assertEquals("tcp", rows[1].network)
        assertEquals(listOf("http", "tls"), rows[1].protocols)
        assertEquals(listOf("steam.exe"), rows[2].processes)
        assertEquals("", rows[2].network)
    }

    @Test
    fun domainsThatV2rayNHasSwitchedOffAreLeftOutAndCommaMarkersAreRestored() {
        val row = parse(
            """[{"outboundTag":"direct","domain":["#old.com","new.com","regexp:a<COMMA>b"]}]""",
        ).rows.single()
        assertEquals(listOf("new.com", "regexp:a,b"), row.domains)
    }

    @Test
    fun aRuleWithoutConditionsThatIsSwitchedOffIsIgnored() {
        val import = parse("""[{"outboundTag":"proxy","enabled":false},{"outboundTag":"direct","domain":["a.com"]}]""")
        assertNull(import.finalPolicy)
        assertEquals(1, import.rows.size)
        assertTrue(import.skipped.isEmpty())
    }

    @Test
    fun theCountsMatchWhatTheDialogShows() {
        val import = parse(export)
        assertEquals(7, import.totalRules)
        assertEquals(3, import.rows.count { it.enabled })
        assertEquals(1, import.rows.count { !it.enabled })
    }

    // --- applying it to a profile

    private val profile = "[General]\nipv6 = true\n\n[Rule]\n# mine\nDOMAIN-SUFFIX,old.com,DIRECT // Old\nDST-PORT,22,DIRECT\nFINAL,CONFIG:2\n\n[Host]\na = 1.1.1.1\n"

    private fun rulesOf(document: String) = document.analyzeShadowrocketConfig().rules

    @Test
    fun mergingAddsTheRowsBeforeFinalAndKeepsWhatIsThere() {
        val rows = listOf(
            RoutingTableRow(name = "New", policy = "PROXY", domains = listOf("domain:new.com")),
            RoutingTableRow(name = "Off", policy = "DIRECT", enabled = false, ips = listOf("10.0.0.0/8")),
        )
        val result = profile.withRoutingRowsImported(rows, finalPolicy = "PROXY", mode = ClipboardImportMode.Merge)

        assertEquals(
            listOf("DOMAIN-SUFFIX,old.com,DIRECT", "DST-PORT,22,DIRECT", "DOMAIN-SUFFIX,new.com,PROXY", "IP-CIDR,10.0.0.0/8,DIRECT", "FINAL,CONFIG:2"),
            rulesOf(result.document).map { it.toShadowrocketLine().substringBefore(" //").substringBefore(",skipi-disabled") },
        )
        assertEquals(listOf("Old", "", "New", "Off", ""), rulesOf(result.document).map { it.remarks })
        assertEquals(2, result.added)
        assertTrue(result.document.contains("# mine"))
        assertTrue(result.document.contains("[Host]\na = 1.1.1.1"))
    }

    @Test
    fun mergingSkipsARowThatIsAlreadyThere() {
        val rows = listOf(RoutingTableRow(name = "Old", policy = "DIRECT", domains = listOf("domain:old.com")))
        val result = profile.withRoutingRowsImported(rows, finalPolicy = null, mode = ClipboardImportMode.Merge)
        assertEquals(0, result.added)
        assertEquals(profile, result.document)
    }

    @Test
    fun replacingWritesTheNewRulesAndTheFinalOfTheImport() {
        val rows = listOf(RoutingTableRow(name = "New", policy = "PROXY", domains = listOf("domain:new.com")))
        val result = profile.withRoutingRowsImported(rows, finalPolicy = "DIRECT", mode = ClipboardImportMode.Replace)

        assertEquals(listOf("DOMAIN-SUFFIX", "FINAL"), rulesOf(result.document).map { it.type })
        assertEquals("DIRECT", rulesOf(result.document).last().policy)
        assertTrue(result.document.contains("# mine"))
        assertTrue(result.document.contains("[General]\nipv6 = true"))
        assertTrue(result.document.contains("[Host]\na = 1.1.1.1"))
        assertTrue(!result.document.contains("old.com"))
    }

    @Test
    fun replacingKeepsTheCurrentFinalWhenTheImportHasNone() {
        val rows = listOf(RoutingTableRow(policy = "PROXY", domains = listOf("domain:new.com")))
        val result = profile.withRoutingRowsImported(rows, finalPolicy = null, mode = ClipboardImportMode.Replace)
        assertEquals("CONFIG:2", rulesOf(result.document).last().policy)
    }

    @Test
    fun aProfileWithoutARuleSectionGetsOne() {
        val rows = listOf(RoutingTableRow(policy = "PROXY", domains = listOf("domain:new.com")))
        val result = "[General]\nipv6 = true\n".withRoutingRowsImported(rows, finalPolicy = "DIRECT", mode = ClipboardImportMode.Merge)
        assertEquals(listOf("DOMAIN-SUFFIX"), rulesOf(result.document).map { it.type })
    }

    @Test
    fun valuesThatCannotBeWrittenAreReportedWithTheirRule() {
        val rows = listOf(
            RoutingTableRow(name = "Regex", policy = "DIRECT", domains = listOf("domain:ok.com", "regexp:^(a|b)\\.com$")),
            RoutingTableRow(name = "Nothing", policy = "DIRECT", domains = listOf("x,y")),
        )
        val result = profile.withRoutingRowsImported(rows, finalPolicy = null, mode = ClipboardImportMode.Merge)
        assertEquals(1, result.added)
        assertEquals(listOf("Regex" to "regexp:^(a|b)\\.com$", "Nothing" to "x,y"), result.unwritable.map { it.ruleName to it.value })
    }
}
