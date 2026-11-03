package app.skipi.ui.config

import features.config.ShadowrocketCondition
import features.config.ShadowrocketLogicalOperator
import features.config.parseShadowrocketConditionBody
import features.config.toStoredBodyText
import features.config.xrayUnsupportedReasonForCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrafficConfigLogicalConditionEditorTest {
    @Test
    fun plainRuleBecomesTheFirstCondition() {
        assertEquals("((DOMAIN-SUFFIX,a.com))", logicalBodyFromPlainRule("DOMAIN-SUFFIX", " a.com "))
        assertEquals("", logicalBodyFromPlainRule("DOMAIN-SUFFIX", ""))
        assertEquals("", logicalBodyFromPlainRule("USER-AGENT", "x"))
    }

    @Test
    fun logicalRuleTurnsBackIntoItsFirstCondition() {
        val body = "((GEOIP,RU),(DOMAIN,a.com))"
        assertEquals("GEOIP" to "RU", plainRuleFromLogicalBody(ShadowrocketLogicalOperator.Or, body))
        assertNull(plainRuleFromLogicalBody(ShadowrocketLogicalOperator.Or, ""))
    }

    @Test
    fun halfTypedConditionsAreLeftOutOfTheStoredBody() {
        val tree = ShadowrocketCondition.Group(
            ShadowrocketLogicalOperator.Or,
            listOf(
                ShadowrocketCondition.Leaf("DOMAIN", "a.com"),
                ShadowrocketCondition.Leaf("DOMAIN", ""),
                ShadowrocketCondition.Group(ShadowrocketLogicalOperator.And, listOf(ShadowrocketCondition.Leaf("GEOIP", " "))),
            ),
        )
        assertEquals("((DOMAIN,a.com))", tree.toStoredBodyText())
        assertEquals("", ShadowrocketCondition.Group(ShadowrocketLogicalOperator.Or, emptyList()).toStoredBodyText())
    }

    @Test
    fun storedBodyRoundTripsThroughTheParser() {
        val body = "((DOMAIN,a.com),(AND,((DOMAIN-SUFFIX,b.com),(DST-PORT,443))))"
        val tree = assertNotNull(parseShadowrocketConditionBody(ShadowrocketLogicalOperator.Or, body))
        assertEquals(body, tree.toStoredBodyText())
    }

    @Test
    fun unsupportedCombinationsAreReported() {
        val twoDomains = assertNotNull(
            parseShadowrocketConditionBody(ShadowrocketLogicalOperator.And, "((DOMAIN,a.com),(DOMAIN-KEYWORD,b))"),
        )
        assertNotNull(xrayUnsupportedReasonForCondition(ShadowrocketLogicalOperator.And, twoDomains))
        assertNull(xrayUnsupportedReasonForCondition(ShadowrocketLogicalOperator.Or, twoDomains))
    }

    @Test
    fun typedPrefixSwitchesTheConditionType() {
        val leaf = ShadowrocketCondition.Leaf("DOMAIN-SUFFIX", "")
        assertEquals(ShadowrocketCondition.Leaf("DOMAIN-KEYWORD", "google"), leaf.withTypedValue("keyword:google"))
        assertEquals(ShadowrocketCondition.Leaf("GEOSITE", "google"), leaf.withTypedValue("geosite:google"))
        assertEquals(ShadowrocketCondition.Leaf("GEOIP", "RU"), leaf.withTypedValue("geoip:ru"))
        assertEquals(ShadowrocketCondition.Leaf("IP-CIDR", "10.0.0.0/8"), leaf.withTypedValue("10.0.0.0/8"))
    }

    @Test
    fun plainTypingKeepsTheTypeAndStripsSyntaxCharacters() {
        val leaf = ShadowrocketCondition.Leaf("DOMAIN-KEYWORD", "")
        assertEquals(ShadowrocketCondition.Leaf("DOMAIN-KEYWORD", "ab"), leaf.withTypedValue("a(b),"))
        assertEquals(ShadowrocketCondition.Leaf("DOMAIN-KEYWORD", "keyword"), leaf.withTypedValue("keyword"))
    }
}
