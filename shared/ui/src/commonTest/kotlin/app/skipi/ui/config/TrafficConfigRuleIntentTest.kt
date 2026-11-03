package app.skipi.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrafficConfigRuleIntentTest {
    @Test
    fun prefixPicksTypeAndStripsIt() {
        assertEquals(TrafficConfigRuleIntent("DOMAIN-KEYWORD", "google"), detectTrafficConfigRuleIntent("keyword:google"))
        assertEquals(TrafficConfigRuleIntent("DOMAIN-SUFFIX", "google.com"), detectTrafficConfigRuleIntent("domain:google.com"))
        assertEquals(TrafficConfigRuleIntent("DOMAIN", "a.google.com"), detectTrafficConfigRuleIntent("FULL: a.google.com"))
        assertEquals(TrafficConfigRuleIntent("GEOIP", "RU"), detectTrafficConfigRuleIntent("geoip:ru"))
        assertEquals(TrafficConfigRuleIntent("DOMAIN-SET", "geosite:google"), detectTrafficConfigRuleIntent("geosite:google"))
    }

    @Test
    fun shapesAreRecognisedWithoutPrefix() {
        assertEquals(TrafficConfigRuleIntent("IP-CIDR", "10.0.0.0/8"), detectTrafficConfigRuleIntent("10.0.0.0/8"))
        assertEquals(TrafficConfigRuleIntent("DOMAIN-WILDCARD", "*.google.com"), detectTrafficConfigRuleIntent("*.google.com"))
        assertEquals(TrafficConfigRuleIntent("RULE-SET", "https://x.test/a.list"), detectTrafficConfigRuleIntent("https://x.test/a.list"))
    }

    @Test
    fun ambiguousInputKeepsChosenType() {
        assertNull(detectTrafficConfigRuleIntent("google"))
        assertNull(detectTrafficConfigRuleIntent("443"))
        assertNull(detectTrafficConfigRuleIntent("keyword:"))
        assertNull(detectTrafficConfigRuleIntent("unknown:thing"))
        assertNull(detectTrafficConfigRuleIntent(""))
    }
}
