// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.withActiveTrafficConfigApplied
import engine.xray.XrayTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficConfigRuleConversionTest {

    @Test
    fun testAllRuleTypesAreParsedAndConvertedCorrectly() {
        val configText = """
            # Test Configuration
            [General]
            dns-server = 8.8.8.8
            
            [Rule]
            DOMAIN,example.com,PROXY
            DOMAIN-SUFFIX,google.com,DIRECT
            DOMAIN-KEYWORD,twitter,PROXY
            DOMAIN-WILDCARD,*.youtube.com,PROXY
            DOMAIN-SET,geosite:google,PROXY
            GEOSITE,category-ads-all,REJECT
            GEOSITE,geosite:netflix,PROXY
            RULE-SET,geosite:openai,PROXY
            RULE-SET,geoip:telegram,PROXY
            RULE-SET,192.168.0.0/16,DIRECT
            IP-CIDR,10.0.0.0/8,DIRECT
            IP-CIDR6,2001:db8::/32,DIRECT
            GEOIP,cn,DIRECT
            GEOIP,geoip:ru,DIRECT
            PROCESS-NAME,com.google.android.youtube,PROXY
            DST-PORT,443,PROXY
            NETWORK,udp,BLOCK
            FINAL,DIRECT
        """.trimIndent()

        val config = TrafficConfigState(
            id = 10,
            name = "Extended Rules Profile",
            rawConfig = configText,
        )

        val appState = AppState(
            trafficConfigs = listOf(config),
            activeTrafficConfigId = 10,
        )

        val appliedState = appState.withActiveTrafficConfigApplied()
        val rules = appliedState.routeRules

        assertEquals(17, rules.size)
        assertEquals("direct", appliedState.defaultRouteOutboundTag)

        // 1. DOMAIN
        assertEquals(listOf("full:example.com"), rules[0].domain)
        assertEquals(XrayTags.PROXY, rules[0].outboundTag)

        // 2. DOMAIN-SUFFIX
        assertEquals(listOf("domain:google.com"), rules[1].domain)
        assertEquals(XrayTags.DIRECT, rules[1].outboundTag)

        // 3. DOMAIN-KEYWORD
        assertEquals(listOf("keyword:twitter"), rules[2].domain)
        assertEquals(XrayTags.PROXY, rules[2].outboundTag)

        // 4. DOMAIN-WILDCARD
        assertTrue(rules[3].domain.first().startsWith("regexp:"))
        assertEquals(XrayTags.PROXY, rules[3].outboundTag)

        // 5. DOMAIN-SET
        assertEquals(listOf("geosite:google"), rules[4].domain)
        assertEquals(XrayTags.PROXY, rules[4].outboundTag)

        // 6. GEOSITE without prefix
        assertEquals(listOf("geosite:category-ads-all"), rules[5].domain)
        assertEquals(XrayTags.BLOCK, rules[5].outboundTag)

        // 7. GEOSITE with prefix
        assertEquals(listOf("geosite:netflix"), rules[6].domain)
        assertEquals(XrayTags.PROXY, rules[6].outboundTag)

        // 8. RULE-SET domain/geosite
        assertEquals(listOf("geosite:openai"), rules[7].domain)
        assertEquals(XrayTags.PROXY, rules[7].outboundTag)

        // 9. RULE-SET geoip
        assertEquals(listOf("geoip:telegram"), rules[8].ip)
        assertEquals(XrayTags.PROXY, rules[8].outboundTag)

        // 10. RULE-SET CIDR
        assertEquals(listOf("192.168.0.0/16"), rules[9].ip)
        assertEquals(XrayTags.DIRECT, rules[9].outboundTag)

        // 11. IP-CIDR
        assertEquals(listOf("10.0.0.0/8"), rules[10].ip)
        assertEquals(XrayTags.DIRECT, rules[10].outboundTag)

        // 12. IP-CIDR6
        assertEquals(listOf("2001:db8::/32"), rules[11].ip)
        assertEquals(XrayTags.DIRECT, rules[11].outboundTag)

        // 13. GEOIP without prefix
        assertEquals(listOf("geoip:cn"), rules[12].ip)
        assertEquals(XrayTags.DIRECT, rules[12].outboundTag)

        // 14. GEOIP with prefix (should not be double prefixed)
        assertEquals(listOf("geoip:ru"), rules[13].ip)
        assertEquals(XrayTags.DIRECT, rules[13].outboundTag)

        // 15. PROCESS-NAME
        assertEquals(listOf("com.google.android.youtube"), rules[14].process)
        assertEquals(XrayTags.PROXY, rules[14].outboundTag)

        // 16. DST-PORT
        assertEquals("443", rules[15].port)
        assertEquals(XrayTags.PROXY, rules[15].outboundTag)

        // 17. NETWORK + BLOCK policy
        assertEquals("udp", rules[16].network)
        assertEquals(XrayTags.BLOCK, rules[16].outboundTag)
    }

    @Test
    fun testBlockAndRejectPolicyVariations() {
        val configText = """
            [Rule]
            DOMAIN,block1.com,BLOCK
            DOMAIN,block2.com,block
            DOMAIN,reject1.com,REJECT
            DOMAIN,reject2.com,reject
            DOMAIN,reject3.com,REJECT-TINYGIF
            DOMAIN,reject4.com,REJECT-DROP
            FINAL,PROXY
        """.trimIndent()

        val config = TrafficConfigState(
            id = 1,
            name = "Block Test",
            rawConfig = configText,
        )

        val appliedState = AppState(
            trafficConfigs = listOf(config),
            activeTrafficConfigId = 1,
        ).withActiveTrafficConfigApplied()

        val rules = appliedState.routeRules
        assertEquals(6, rules.size)
        rules.forEach { rule ->
            assertEquals("Failed for remark: ${rule.remarks}", XrayTags.BLOCK, rule.outboundTag)
        }
    }

    @Test
    fun testRuleSetWithRemoteUrlIsNotTreatedAsIp() {
        val configText = """
            [Rule]
            RULE-SET,https://raw.githubusercontent.com/Loyalsoldier/clash-rules/release/gfw.txt,PROXY
            FINAL,DIRECT
        """.trimIndent()

        val appliedState = AppState(
            trafficConfigs = listOf(TrafficConfigState(id = 1, name = "Test", rawConfig = configText)),
            activeTrafficConfigId = 1,
        ).withActiveTrafficConfigApplied()

        // Remote URL RULE-SET should not be treated as an IP CIDR rule
        assertEquals(0, appliedState.routeRules.size)
    }

    @Test
    fun testIpAsnConversion() {
        val configText = """
            [Rule]
            IP-ASN,15169,PROXY
            IP-ASN,AS13335,DIRECT
            FINAL,DIRECT
        """.trimIndent()

        val appliedState = AppState(
            trafficConfigs = listOf(TrafficConfigState(id = 1, name = "Test", rawConfig = configText)),
            activeTrafficConfigId = 1,
        ).withActiveTrafficConfigApplied()

        assertEquals(2, appliedState.routeRules.size)
        assertEquals(listOf("geoip:as15169"), appliedState.routeRules[0].ip)
        assertEquals(XrayTags.PROXY, appliedState.routeRules[0].outboundTag)
        assertEquals(listOf("geoip:as13335"), appliedState.routeRules[1].ip)
        assertEquals(XrayTags.DIRECT, appliedState.routeRules[1].outboundTag)
    }

    @Test
    fun testUnsupportedRulesIgnoredGracefully() {
        val configText = """
            [Rule]
            USER-AGENT,*Telegram*,PROXY
            URL-REGEX,^https?:\/\/.*,PROXY
            FINAL,DIRECT
        """.trimIndent()

        val appliedState = AppState(
            trafficConfigs = listOf(TrafficConfigState(id = 1, name = "Test", rawConfig = configText)),
            activeTrafficConfigId = 1,
        ).withActiveTrafficConfigApplied()

        assertEquals(0, appliedState.routeRules.size)
    }
}

