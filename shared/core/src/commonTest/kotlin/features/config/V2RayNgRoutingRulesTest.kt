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
    fun singleFieldRuleBecomesOneLinePerValue() {
        val lines = rules(
            """[{"outboundTag":"block","domain":["geosite:win-spy","domain:a.com","full:b.com","keyword:ads","*.c.net","plain.org"],"enabled":true}
            ,{"outboundTag":"direct","ip":["10.0.0.0/8","::1","geoip:ru"]}]""",
        )
        assertEquals(
            listOf(
                "DOMAIN-SET,geosite:win-spy,REJECT",
                "DOMAIN-SUFFIX,a.com,REJECT",
                "DOMAIN,b.com,REJECT",
                "DOMAIN-KEYWORD,ads,REJECT",
                "DOMAIN-WILDCARD,*.c.net,REJECT",
                "DOMAIN-KEYWORD,plain.org,REJECT",
                "IP-CIDR,10.0.0.0/8,DIRECT",
                "IP-CIDR6,::1,DIRECT",
                "GEOIP,ru,DIRECT",
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
}
