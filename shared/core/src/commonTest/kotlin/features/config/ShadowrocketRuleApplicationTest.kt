// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.RuleApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ShadowrocketRuleApplicationTest {
    private fun rule(line: String): ShadowrocketRule =
        assertNotNull("[Rule]\n$line\n".analyzeShadowrocketConfig().rules.firstOrNull(), line)

    @Test
    fun anAsnRuleWhoseValueTheBuildDroppedIsSkipped() {
        val status = rule("IP-ASN,13335,DIRECT").applicationStatus(listOf("geoip:as13335"))
        assertEquals(RuleApplication.Skipped, status.application)
        assertEquals(listOf("geoip:as13335"), status.values)
    }

    @Test
    fun aRuleTheBuildDidNotReportIsApplied() {
        assertEquals(RuleApplication.Applied, rule("DOMAIN-SUFFIX,a.com,PROXY").applicationStatus(listOf("geoip:as13335")).application)
    }

    @Test
    fun anUnknownTargetMessageIsMatchedByTheRuleTypeAndValue() {
        val message = "Rule \"DOMAIN-SUFFIX,a.com\": unknown target 'gone', routed to proxy"
        assertEquals(RuleApplication.UnknownTarget, rule("DOMAIN-SUFFIX,a.com,Gone").applicationStatus(listOf(message)).application)
    }

    @Test
    fun anOrRuleIsPartlySkippedWhenOnlyOneAlternativeLosesItsValue() {
        val status = rule("OR,((IP-ASN,13335),(DOMAIN-SUFFIX,a.com)),DIRECT").applicationStatus(listOf("geoip:as13335"))
        assertEquals(RuleApplication.PartlySkipped, status.application)
    }

    @Test
    fun finalAndDisabledRulesAreNeverFlagged() {
        assertEquals(RuleApplication.Applied, rule("FINAL,PROXY").applicationStatus(listOf("geoip:as13335")).application)
    }
}
