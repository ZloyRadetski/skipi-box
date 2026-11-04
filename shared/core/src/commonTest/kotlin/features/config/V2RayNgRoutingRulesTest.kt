// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V2RayNgRoutingRulesTest {
    private fun rules(json: String, fallback: String = "Config"): List<String> {
        val conf = assertNotNull(json.toV2RayNgRoutingConfOrNull(fallback))
        return conf.lineSequence().dropWhile { it != "[Rule]" }.drop(1).takeWhile { !it.startsWith("[") }
            .filter(String::isNotBlank).toList()
    }

    @Test
    fun ignoresNonRuleJson() {
        assertNull("""["vless://a@b:1"]""".toV2RayNgRoutingConfOrNull("x"))
        assertNull("""{"RouteOrder":"block"}""".toV2RayNgRoutingConfOrNull("x"))
        assertNull("[]".toV2RayNgRoutingConfOrNull("x"))
        assertNull("not json".toV2RayNgRoutingConfOrNull("x"))
    }

    @Test
    fun singleFieldRuleBecomesOneOrRuleAndOneValueStaysPlain() {
        val lines = rules(
            """[{"outboundTag":"block","domain":["geosite:win-spy","domain:a.com","full:b.com","keyword:ads","*.c.net","plain.org","domain:a.com"],"enabled":true}
            ,{"outboundTag":"direct","ip":["10.0.0.0/8","::1","geoip:ru"]}
            ,{"outboundTag":"direct","ip":["10.0.0.0/8"]}]""",
        )
        assertEquals(
            listOf(
                "OR,((DOMAIN-SET,geosite:win-spy),(DOMAIN-SUFFIX,a.com),(DOMAIN,b.com),(DOMAIN-KEYWORD,ads)," +
                    "(DOMAIN-WILDCARD,*.c.net),(DOMAIN-KEYWORD,plain.org)),REJECT",
                "OR,((IP-CIDR,10.0.0.0/8),(IP-CIDR6,::1),(GEOIP,ru)),DIRECT",
                "IP-CIDR,10.0.0.0/8,DIRECT",
                "FINAL,PROXY",
            ),
            lines,
        )
    }

    @Test
    fun aSingleRuleObjectIsAcceptedLikeAnArray() {
        val lines = rules(
            """{"port":"","network":"tcp,udp","outboundTag":"proxy","domain":["steamdb.info","modrinth.com","cdn.modrinth.com","modrinth.com"],
               "enabled":true,"remarks":"proxy","ruleType":0}""",
        )
        assertEquals(
            listOf(
                "OR,((DOMAIN-KEYWORD,steamdb.info),(DOMAIN-KEYWORD,modrinth.com),(DOMAIN-KEYWORD,cdn.modrinth.com)),PROXY // proxy",
                "FINAL,PROXY",
            ),
            lines,
        )
    }

    @Test
    fun geoipInsideDomainListIsAnIpCondition() {
        val lines = rules("""[{"outboundTag":"direct","domain":["geosite:private","geoip:private"]}]""")
        assertEquals(
            listOf(
                "AND,((DOMAIN-SET,geosite:private),(GEOIP,private)),DIRECT",
                "FINAL,PROXY",
            ),
            lines,
        )
    }

    @Test
    fun portAndNetworkAreAndedWithDomains() {
        val lines = rules(
            """[{"outboundTag":"direct","port":"25565","network":"tcp,udp","domain":["hypixel.net","domain:mojang.com"]},
               {"outboundTag":"proxy","port":"80,443","network":"udp","protocol":["tls","http"]}]""",
        )
        assertEquals(
            "AND,((OR,((DOMAIN-KEYWORD,hypixel.net),(DOMAIN-SUFFIX,mojang.com))),(DST-PORT,25565)),DIRECT",
            lines[0],
        )
        assertEquals(
            "AND,((OR,((DST-PORT,80),(DST-PORT,443))),(NETWORK,udp),(OR,((PROTOCOL,tls),(PROTOCOL,http)))),PROXY",
            lines[1],
        )
    }

    @Test
    fun singlePortRuleIsAPlainLine() {
        assertEquals(listOf("DST-PORT,53,DIRECT", "FINAL,PROXY"), rules("""[{"outboundTag":"direct","port":"53"}]"""))
    }

    @Test
    fun ruleWithoutConditionsIsFinalAndNoSecondFinalIsAdded() {
        val lines = rules(
            """[{"outboundTag":"direct","domain":["a.com"]},
               {"outboundTag":"proxy","port":"0-65535","network":"tcp,udp"}]""",
        )
        assertEquals(listOf("DOMAIN-KEYWORD,a.com,DIRECT", "FINAL,PROXY"), lines)
    }

    @Test
    fun skipsDisabledDnsOnlyUnknownTagsAndUnsafeValues() {
        val lines = rules(
            """[{"outboundTag":"block","domain":["off.com"],"enabled":false},
               {"outboundTag":"direct","domain":["dns.com"],"ruleType":2},
               {"outboundTag":"weird","domain":["x.com"]},
               {"outboundTag":"direct","domain":["regexp:^a(b|c),d${'$'}","ok.com"]}]""",
        )
        assertEquals(listOf("DOMAIN-KEYWORD,ok.com,DIRECT", "FINAL,PROXY"), lines)
    }

    @Test
    fun namesTheProfileOnlyWhenAFallbackNameIsGiven() {
        val json = """[{"outboundTag":"direct","domain":["a.com"]}]"""
        val named = assertNotNull(json.toV2RayNgRoutingConfOrNull("My rules"))
        assertTrue(named.contains("# profile-name: My rules"))
        assertTrue(named.contains("profile-name = My rules"))
        val unnamed = assertNotNull(json.toV2RayNgRoutingConfOrNull("Config"))
        assertTrue(!unnamed.contains("profile-name"))
    }

    @Test
    fun remarksBecomeTheNameOfEveryLineOfTheRule() {
        val lines = rules(
            """[{"outboundTag":"direct","domain":["domain:a.com","domain:b.com"],"remarks":"Direct
sites"},
               {"outboundTag":"direct","domain":["domain:c.com"],"port":"443","remarks":"With port"},
               {"outboundTag":"proxy","port":"0-65535","remarks":"Rest"}]""",
        )
        assertEquals(
            listOf(
                "OR,((DOMAIN-SUFFIX,a.com),(DOMAIN-SUFFIX,b.com)),DIRECT // Direct sites",
                "AND,((DOMAIN-SUFFIX,c.com),(DST-PORT,443)),DIRECT // With port",
                "FINAL,PROXY // Rest",
            ),
            lines,
        )
    }

    @Test
    fun theMergedRuleIsOneCardThatXrayCanRoute() {
        val conf = assertNotNull(
            """{"outboundTag":"proxy","remarks":"proxy","domain":["steamdb.info","domain:a.com","geosite:rockstar","full:b.com"]}"""
                .toV2RayNgRoutingConfOrNull("Config"),
        )
        val parsed = conf.analyzeShadowrocketConfig().rules
        assertEquals(2, parsed.size)
        assertEquals("proxy", parsed[0].remarks)
        assertEquals(4, parsed[0].condition?.children?.size)
        assertNull(parsed[0].xrayUnsupportedReason())
    }
}
